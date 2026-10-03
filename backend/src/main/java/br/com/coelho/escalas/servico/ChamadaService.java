package br.com.coelho.escalas.servico;

import br.com.coelho.escalas.dominio.Chamada;
import br.com.coelho.escalas.dominio.Conversa;
import br.com.coelho.escalas.dominio.ParticipanteConversa;
import br.com.coelho.escalas.dominio.Usuario;
import br.com.coelho.escalas.repositorio.ChamadaRepository;
import br.com.coelho.escalas.repositorio.ParticipanteConversaRepository;
import br.com.coelho.escalas.repositorio.UsuarioRepository;
import br.com.coelho.escalas.servico.ChatService.Contato;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Chamadas de voz um a um. O servidor só controla o estado da chamada e repassa a sinalização WebRTC (offer, answer e
 * candidatos ICE) entre os dois lados; o áudio vai direto de um navegador ao outro.
 */
@Service
public class ChamadaService {

    public record ChamadaDTO(Long id, Long conversaId, Contato chamador, Contato destinatario, Chamada.Status status,
                             LocalDateTime iniciadaEm, LocalDateTime atendidaEm, LocalDateTime encerradaEm) {
    }

    public record Sinal(String tipo, String dados) {
    }

    /** Evento "CHAMADA": novo estado da chamada, enviado aos dois lados. */
    public record EventoChamada(String tipo, Long conversaId, ChamadaDTO chamada) {
    }

    /** Evento "SINAL": dados de sinalização WebRTC, enviados só ao outro lado. */
    public record EventoSinal(String tipo, Long chamadaId, Sinal sinal) {
    }

    private final ChamadaRepository chamadas;
    private final ParticipanteConversaRepository participantes;
    private final UsuarioRepository usuarios;
    private final ChatService chat;
    private final NotificadorChat notificador;

    public ChamadaService(ChamadaRepository chamadas, ParticipanteConversaRepository participantes, UsuarioRepository usuarios,
                          ChatService chat, NotificadorChat notificador) {
        this.chamadas = chamadas;
        this.participantes = participantes;
        this.usuarios = usuarios;
        this.chat = chat;
        this.notificador = notificador;
    }

    @Transactional
    public ChamadaDTO iniciar(String login, Long conversaId) {
        Usuario eu = usuario(login);
        ParticipanteConversa p = participantes.findByConversaIdAndUsuarioId(conversaId, eu.getId())
                .orElseThrow(() -> new NaoEncontradoException("Conversa", conversaId));
        if (p.getConversa().getTipo() != Conversa.Tipo.DIRETA) {
            throw new RegraNegocioException("Chamadas de voz são apenas para conversas privadas.");
        }
        Usuario outro = participantes.findByConversaId(conversaId).stream().map(ParticipanteConversa::getUsuario)
                .filter(u -> !u.getId().equals(eu.getId())).findFirst()
                .orElseThrow(() -> new RegraNegocioException("A conversa não tem outro participante."));
        if (emChamada(eu)) {
            throw new RegraNegocioException("Você já está em outra chamada.");
        }
        if (emChamada(outro)) {
            throw new RegraNegocioException(outro.getNome() + " está em outra chamada.");
        }
        Chamada c = new Chamada();
        c.setConversa(p.getConversa());
        c.setChamador(eu);
        c.setDestinatario(outro);
        chamadas.save(c);
        return avisar(c);
    }

    @Transactional
    public ChamadaDTO atender(String login, Long id) {
        Usuario eu = usuario(login);
        Chamada c = daChamada(eu, id);
        exigirDestinatarioTocando(c, eu, "Só quem recebe a chamada pode atender.");
        c.setStatus(Chamada.Status.EM_ANDAMENTO);
        c.setAtendidaEm(LocalDateTime.now());
        return avisar(c);
    }

    @Transactional
    public ChamadaDTO recusar(String login, Long id) {
        Usuario eu = usuario(login);
        Chamada c = daChamada(eu, id);
        exigirDestinatarioTocando(c, eu, "Só quem recebe a chamada pode recusar.");
        return finalizar(c, Chamada.Status.RECUSADA);
    }

    /** Desligar: antes de atender vira chamada perdida; depois, encerrada com a duração. Repetir não tem efeito. */
    @Transactional
    public ChamadaDTO encerrar(String login, Long id) {
        Chamada c = daChamada(usuario(login), id);
        if (!c.ativa()) {
            return dto(c);
        }
        return finalizar(c, c.getStatus() == Chamada.Status.TOCANDO ? Chamada.Status.PERDIDA : Chamada.Status.ENCERRADA);
    }

    @Transactional(readOnly = true)
    public void sinalizar(String login, Long id, Sinal sinal) {
        Usuario eu = usuario(login);
        Chamada c = daChamada(eu, id);
        if (!c.ativa()) {
            throw new RegraNegocioException("A chamada já foi encerrada.");
        }
        notificador.enviar(List.of(c.outroLado(eu)), new EventoSinal("SINAL", c.getId(), sinal));
    }

    /** Se alguém fecha a página no meio de uma chamada, ela é encerrada para o outro lado não ficar esperando. */
    @EventListener
    @Transactional
    public void usuarioSaiu(PresencaChat.UsuarioFicouOffline evento) {
        usuarios.findByLogin(evento.login()).ifPresent(u ->
                chamadas.doUsuarioComStatus(u.getId(), Chamada.ATIVAS).forEach(c ->
                        finalizar(c, c.getStatus() == Chamada.Status.TOCANDO ? Chamada.Status.PERDIDA : Chamada.Status.ENCERRADA)));
    }

    // ------------------------------------------------------------------ auxiliares

    private ChamadaDTO finalizar(Chamada c, Chamada.Status status) {
        c.setStatus(status);
        c.setEncerradaEm(LocalDateTime.now());
        String resultado = switch (status) {
            case ENCERRADA -> "ATENDIDA";
            case RECUSADA -> "RECUSADA";
            default -> "PERDIDA";
        };
        chat.registrarChamada(c.getConversa().getId(), c.getChamador(), resultado, c.duracaoSegundos());
        return avisar(c);
    }

    private ChamadaDTO avisar(Chamada c) {
        ChamadaDTO dto = dto(c);
        notificador.enviar(List.of(c.getChamador(), c.getDestinatario()), new EventoChamada("CHAMADA", c.getConversa().getId(), dto));
        return dto;
    }

    private ChamadaDTO dto(Chamada c) {
        return new ChamadaDTO(c.getId(), c.getConversa().getId(), chat.contato(c.getChamador()), chat.contato(c.getDestinatario()),
                c.getStatus(), c.getIniciadaEm(), c.getAtendidaEm(), c.getEncerradaEm());
    }

    private static void exigirDestinatarioTocando(Chamada c, Usuario eu, String mensagem) {
        if (!c.getDestinatario().getId().equals(eu.getId())) {
            throw new RegraNegocioException(mensagem);
        }
        if (c.getStatus() != Chamada.Status.TOCANDO) {
            throw new RegraNegocioException("A chamada não está mais tocando.");
        }
    }

    /** Quem não participa recebe 404, sem revelar se a chamada existe. */
    private Chamada daChamada(Usuario eu, Long id) {
        return chamadas.findById(id).filter(c -> c.participa(eu))
                .orElseThrow(() -> new NaoEncontradoException("Chamada", id));
    }

    private boolean emChamada(Usuario u) {
        return !chamadas.doUsuarioComStatus(u.getId(), Chamada.ATIVAS).isEmpty();
    }

    private Usuario usuario(String login) {
        return usuarios.findByLogin(login).orElseThrow(() -> new NaoEncontradoException("Usuário", login));
    }
}
