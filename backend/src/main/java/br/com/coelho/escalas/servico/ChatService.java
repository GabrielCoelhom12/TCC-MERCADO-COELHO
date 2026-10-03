package br.com.coelho.escalas.servico;

import br.com.coelho.escalas.dominio.*;
import br.com.coelho.escalas.repositorio.*;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Chat entre usuários. Mensagens entram pela API REST (validadas e persistidas); a entrega em tempo real é feita por
 * eventos STOMP em /user/queue/chat, enviados somente aos participantes e somente após o commit.
 */
@Service
public class ChatService {

    public static final int MAX_NOME_GRUPO = 80;
    private static final int LIMITE_PADRAO = 50;
    private static final int LIMITE_MAXIMO = 100;

    public record Contato(Long id, String nome, String foto, boolean online) {
    }

    public record EscalaMencionada(Long id, String nome) {
    }

    /** Registro de chamada de voz no histórico: ATENDIDA (com duração), RECUSADA ou PERDIDA. */
    public record RegistroChamada(String resultado, Integer duracaoSegundos) {
    }

    public record MensagemChat(Long id, Long conversaId, Long autorId, String autorNome, String texto, String imagem,
                               EscalaMencionada escala, RegistroChamada chamada, LocalDateTime enviadaEm) {
    }

    public record ConversaChat(Long id, Conversa.Tipo tipo, String nome, String foto, List<Contato> participantes,
                               MensagemChat ultimaMensagem, long naoLidas, Map<Long, Long> leituras,
                               LocalDateTime atualizadaEm) {
    }

    /** Evento em tempo real: MENSAGEM, CONVERSA (criada/alterada), SAIU (removida da lista) ou LEITURA. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EventoChat(String tipo, Long conversaId, MensagemChat mensagem, ConversaChat conversa,
                             Long usuarioId, Long mensagemId) {
    }

    private final UsuarioRepository usuarios;
    private final ConversaRepository conversas;
    private final ParticipanteConversaRepository participantes;
    private final MensagemRepository mensagens;
    private final AnexoImagemRepository anexos;
    private final EscalaRepository escalas;
    private final PresencaChat presenca;
    private final NotificadorChat notificador;

    public ChatService(UsuarioRepository usuarios, ConversaRepository conversas, ParticipanteConversaRepository participantes,
                       MensagemRepository mensagens, AnexoImagemRepository anexos, EscalaRepository escalas,
                       PresencaChat presenca, NotificadorChat notificador) {
        this.usuarios = usuarios;
        this.conversas = conversas;
        this.participantes = participantes;
        this.mensagens = mensagens;
        this.anexos = anexos;
        this.escalas = escalas;
        this.presenca = presenca;
        this.notificador = notificador;
    }

    // ------------------------------------------------------------------ consultas

    @Transactional(readOnly = true)
    public List<Contato> contatos(String login) {
        Usuario eu = usuario(login);
        return usuarios.findAllByOrderByNomeAsc().stream()
                .filter(u -> u.isAtivo() && !u.getId().equals(eu.getId()))
                .map(this::contato)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ConversaChat> conversas(String login) {
        Usuario eu = usuario(login);
        List<Long> ids = participantes.findByUsuarioId(eu.getId()).stream().map(p -> p.getConversa().getId()).toList();
        return resumos(eu, ids).stream()
                .sorted(Comparator.comparing(ConversaChat::atualizadaEm).reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MensagemChat> mensagens(String login, Long conversaId, Long antesDe, Integer limite) {
        participacao(usuario(login), conversaId);
        int tamanho = limite == null ? LIMITE_PADRAO : Math.max(1, Math.min(LIMITE_MAXIMO, limite));
        List<Mensagem> recentes = new ArrayList<>(mensagens.recentes(conversaId, antesDe, PageRequest.of(0, tamanho)));
        Collections.reverse(recentes);
        return recentes.stream().map(ChatService::dto).toList();
    }

    @Transactional(readOnly = true)
    public byte[] imagem(String login, Long mensagemId) {
        Mensagem m = mensagens.findById(mensagemId).orElseThrow(() -> new NaoEncontradoException("Mensagem", mensagemId));
        participacao(usuario(login), m.getConversa().getId());
        if (m.getImagemId() == null) {
            throw new NaoEncontradoException("Imagem da mensagem", mensagemId);
        }
        return anexos.findById(m.getImagemId()).orElseThrow(() -> new NaoEncontradoException("Imagem", m.getImagemId())).getDados();
    }

    // ------------------------------------------------------------------ conversas

    @Transactional
    public ConversaChat abrirDireta(String login, Long outroId) {
        Usuario eu = usuario(login);
        if (eu.getId().equals(outroId)) {
            throw new RegraNegocioException("Escolha outra pessoa para conversar.");
        }
        Usuario outro = usuarios.findById(outroId).orElseThrow(() -> new NaoEncontradoException("Usuário", outroId));
        String chave = Conversa.chaveDireta(eu.getId(), outro.getId());
        Conversa c = conversas.findByChaveDireta(chave).orElseGet(() -> {
            if (!outro.isAtivo()) {
                throw new RegraNegocioException(outro.getNome() + " está desativado(a).");
            }
            Conversa nova = new Conversa();
            nova.setTipo(Conversa.Tipo.DIRETA);
            nova.setChaveDireta(chave);
            nova.setCriadaPor(eu);
            conversas.save(nova);
            participantes.save(new ParticipanteConversa(nova, eu));
            participantes.save(new ParticipanteConversa(nova, outro));
            return nova;
        });
        return resumo(eu, c.getId());
    }

    @Transactional
    public ConversaChat criarGrupo(String login, String nome, Collection<Long> membros) {
        Usuario eu = usuario(login);
        List<Usuario> outros = buscarUsuarios(membros, eu);
        if (outros.isEmpty()) {
            throw new RegraNegocioException("Adicione pelo menos uma pessoa ao grupo.");
        }
        Conversa c = new Conversa();
        c.setTipo(Conversa.Tipo.GRUPO);
        c.setNome(nome.trim());
        c.setCriadaPor(eu);
        conversas.save(c);
        participantes.save(new ParticipanteConversa(c, eu));
        outros.forEach(u -> participantes.save(new ParticipanteConversa(c, u)));
        avisarConversa(c.getId());
        return resumo(eu, c.getId());
    }

    @Transactional
    public ConversaChat renomear(String login, Long conversaId, String nome) {
        Usuario eu = usuario(login);
        Conversa c = grupo(participacao(eu, conversaId), "Conversas privadas não têm nome.");
        c.setNome(nome.trim());
        avisarConversa(conversaId);
        return resumo(eu, conversaId);
    }

    @Transactional
    public ConversaChat adicionar(String login, Long conversaId, Collection<Long> novos) {
        Usuario eu = usuario(login);
        Conversa c = grupo(participacao(eu, conversaId),
                "Não é possível adicionar pessoas a uma conversa privada. Crie um grupo.");
        Set<Long> atuais = participantes.findByConversaId(conversaId).stream()
                .map(p -> p.getUsuario().getId()).collect(Collectors.toSet());
        buscarUsuarios(novos, eu).stream()
                .filter(u -> !atuais.contains(u.getId()))
                .forEach(u -> participantes.save(new ParticipanteConversa(c, u)));
        avisarConversa(conversaId);
        return resumo(eu, conversaId);
    }

    @Transactional
    public void sair(String login, Long conversaId) {
        Usuario eu = usuario(login);
        ParticipanteConversa p = participacao(eu, conversaId);
        grupo(p, "Não é possível sair de uma conversa privada.");
        participantes.delete(p);
        participantes.flush();
        enviarAposCommit(List.of(eu), new EventoChat("SAIU", conversaId, null, null, null, null));
        avisarConversa(conversaId);
    }

    // ------------------------------------------------------------------ mensagens

    @Transactional
    public MensagemChat enviar(String login, Long conversaId, String texto, Long escalaId) {
        Usuario eu = usuario(login);
        ParticipanteConversa p = participacao(eu, conversaId);
        String limpo = texto == null || texto.isBlank() ? null : texto.trim();
        if (limpo == null && escalaId == null) {
            throw new RegraNegocioException("Escreva uma mensagem.");
        }
        Mensagem m = nova(p, limpo);
        if (escalaId != null) {
            Escala e = escalas.findById(escalaId).orElseThrow(() -> new NaoEncontradoException("Escala", escalaId));
            m.setEscalaId(e.getId());
            m.setEscalaNome(e.getNome());
        }
        return publicar(p, m);
    }

    @Transactional
    public MensagemChat enviarImagem(String login, Long conversaId, byte[] arquivo, String texto) {
        Usuario eu = usuario(login);
        ParticipanteConversa p = participacao(eu, conversaId);
        if (texto != null && texto.length() > Mensagem.MAX_TEXTO) {
            throw new RegraNegocioException("A mensagem pode ter no máximo " + Mensagem.MAX_TEXTO + " caracteres.");
        }
        AnexoImagem anexo = anexos.save(new AnexoImagem(conversaId, ImagemChat.normalizar(arquivo)));
        Mensagem m = nova(p, texto == null || texto.isBlank() ? null : texto.trim());
        m.setImagemId(anexo.getId());
        return publicar(p, m);
    }

    @Transactional
    public void marcarLida(String login, Long conversaId) {
        Usuario eu = usuario(login);
        ParticipanteConversa p = participacao(eu, conversaId);
        mensagens.findTopByConversaIdOrderByIdDesc(conversaId).ifPresent(ultima -> {
            if (p.getUltimaMensagemLidaId() == null || p.getUltimaMensagemLidaId() < ultima.getId()) {
                p.setUltimaMensagemLidaId(ultima.getId());
                enviarAposCommit(membros(conversaId),
                        new EventoChat("LEITURA", conversaId, null, null, eu.getId(), ultima.getId()));
            }
        });
    }

    /** Registra o resultado de uma chamada de voz como mensagem do chamador (entra nas não lidas de quem recebeu). */
    @Transactional
    public void registrarChamada(Long conversaId, Usuario chamador, String resultado, Integer duracaoSegundos) {
        ParticipanteConversa p = participacao(chamador, conversaId);
        Mensagem m = nova(p, null);
        m.setChamadaResultado(resultado);
        m.setChamadaDuracaoSegundos(duracaoSegundos);
        publicar(p, m);
    }

    private Mensagem nova(ParticipanteConversa autor, String texto) {
        Mensagem m = new Mensagem();
        m.setConversa(autor.getConversa());
        m.setAutor(autor.getUsuario());
        m.setTexto(texto);
        return m;
    }

    private MensagemChat publicar(ParticipanteConversa autor, Mensagem m) {
        mensagens.save(m);
        autor.getConversa().setAtualizadaEm(m.getEnviadaEm());
        autor.setUltimaMensagemLidaId(m.getId()); // quem envia já leu
        MensagemChat dto = dto(m);
        enviarAposCommit(membros(m.getConversa().getId()), new EventoChat("MENSAGEM", m.getConversa().getId(), dto, null, null, null));
        return dto;
    }

    // ------------------------------------------------------------------ montagem dos resumos

    private ConversaChat resumo(Usuario para, Long conversaId) {
        return resumos(para, List.of(conversaId)).get(0);
    }

    private List<ConversaChat> resumos(Usuario para, List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, List<ParticipanteConversa>> porConversa = participantes.findByConversaIdIn(ids).stream()
                .collect(Collectors.groupingBy(p -> p.getConversa().getId()));
        Map<Long, Mensagem> ultimas = mensagens.ultimasDe(ids).stream()
                .collect(Collectors.toMap(m -> m.getConversa().getId(), m -> m));
        Map<Long, Long> naoLidas = participantes.contarNaoLidas(para.getId()).stream()
                .collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));

        List<ConversaChat> lista = new ArrayList<>();
        for (Long id : ids) {
            List<ParticipanteConversa> membros = porConversa.getOrDefault(id, List.of());
            if (membros.isEmpty()) {
                continue;
            }
            Conversa c = membros.get(0).getConversa();
            Usuario outro = membros.stream().map(ParticipanteConversa::getUsuario)
                    .filter(u -> !u.getId().equals(para.getId())).findFirst().orElse(para);
            boolean direta = c.getTipo() == Conversa.Tipo.DIRETA;
            Map<Long, Long> leituras = membros.stream().filter(p -> p.getUltimaMensagemLidaId() != null)
                    .collect(Collectors.toMap(p -> p.getUsuario().getId(), ParticipanteConversa::getUltimaMensagemLidaId));
            Mensagem ultima = ultimas.get(id);
            lista.add(new ConversaChat(c.getId(), c.getTipo(),
                    direta ? outro.getNome() : c.getNome(),
                    direta ? FotoPerfil.dataUrl(outro.getFoto()) : null,
                    membros.stream().map(p -> contato(p.getUsuario())).sorted(Comparator.comparing(Contato::nome)).toList(),
                    ultima == null ? null : dto(ultima),
                    naoLidas.getOrDefault(id, 0L),
                    leituras,
                    c.getAtualizadaEm()));
        }
        return lista;
    }

    public Contato contato(Usuario u) {
        return new Contato(u.getId(), u.getNome(), FotoPerfil.dataUrl(u.getFoto()), presenca.online(u.getLogin()));
    }

    private static MensagemChat dto(Mensagem m) {
        return new MensagemChat(m.getId(), m.getConversa().getId(), m.getAutor().getId(), m.getAutor().getNome(), m.getTexto(),
                m.getImagemId() == null ? null : "/api/chat/mensagens/" + m.getId() + "/imagem",
                m.getEscalaId() == null ? null : new EscalaMencionada(m.getEscalaId(), m.getEscalaNome()),
                m.getChamadaResultado() == null ? null : new RegistroChamada(m.getChamadaResultado(), m.getChamadaDuracaoSegundos()),
                m.getEnviadaEm());
    }

    // ------------------------------------------------------------------ eventos em tempo real

    /** Cada membro recebe a conversa sob o seu ponto de vista (nome/foto da conversa privada, não lidas). */
    private void avisarConversa(Long conversaId) {
        participantes.flush();
        for (Usuario membro : membros(conversaId)) {
            ConversaChat visao = resumo(membro, conversaId);
            enviarAposCommit(List.of(membro), new EventoChat("CONVERSA", conversaId, null, visao, null, null));
        }
    }

    private void enviarAposCommit(Collection<Usuario> destinatarios, EventoChat evento) {
        notificador.enviar(destinatarios, evento);
    }

    // ------------------------------------------------------------------ auxiliares

    private Usuario usuario(String login) {
        return usuarios.findByLogin(login).orElseThrow(() -> new NaoEncontradoException("Usuário", login));
    }

    /** Quem não participa recebe 404, sem revelar se a conversa existe. */
    private ParticipanteConversa participacao(Usuario eu, Long conversaId) {
        return participantes.findByConversaIdAndUsuarioId(conversaId, eu.getId())
                .orElseThrow(() -> new NaoEncontradoException("Conversa", conversaId));
    }

    private List<Usuario> membros(Long conversaId) {
        return participantes.findByConversaId(conversaId).stream().map(ParticipanteConversa::getUsuario).toList();
    }

    private static Conversa grupo(ParticipanteConversa p, String mensagemSeDireta) {
        if (p.getConversa().getTipo() != Conversa.Tipo.GRUPO) {
            throw new RegraNegocioException(mensagemSeDireta);
        }
        return p.getConversa();
    }

    private List<Usuario> buscarUsuarios(Collection<Long> ids, Usuario eu) {
        return ids.stream().distinct().filter(id -> !id.equals(eu.getId()))
                .map(id -> usuarios.findById(id).orElseThrow(() -> new NaoEncontradoException("Usuário", id)))
                .toList();
    }
}
