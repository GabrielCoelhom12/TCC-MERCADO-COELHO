package br.com.coelho.escalas.servico;

import br.com.coelho.escalas.repositorio.UsuarioRepository;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quem está online no chat: conta as conexões WebSocket de cada usuário (várias abas contam como uma presença) e
 * avisa todos em /topic/presenca quando alguém entra ou sai. Estado em memória: em várias instâncias, trocar por um
 * broker externo.
 */
@Component
public class PresencaChat {

    public record Presenca(Long usuarioId, boolean online) {
    }

    private final Map<String, Integer> conexoes = new ConcurrentHashMap<>();
    private final UsuarioRepository usuarios;
    private final SimpMessagingTemplate mensageiro;

    public PresencaChat(UsuarioRepository usuarios, SimpMessagingTemplate mensageiro) {
        this.usuarios = usuarios;
        this.mensageiro = mensageiro;
    }

    public boolean online(String login) {
        return conexoes.getOrDefault(login, 0) > 0;
    }

    @EventListener
    public void conectou(SessionConnectedEvent evento) {
        Principal usuario = evento.getUser();
        if (usuario != null && conexoes.merge(usuario.getName(), 1, Integer::sum) == 1) {
            avisar(usuario.getName(), true);
        }
    }

    @EventListener
    public void desconectou(SessionDisconnectEvent evento) {
        Principal usuario = evento.getUser();
        if (usuario == null) {
            return;
        }
        if (conexoes.computeIfPresent(usuario.getName(), (login, n) -> n > 1 ? n - 1 : null) == null) {
            avisar(usuario.getName(), false);
        }
    }

    private void avisar(String login, boolean online) {
        usuarios.findByLogin(login).ifPresent(u -> mensageiro.convertAndSend("/topic/presenca", new Presenca(u.getId(), online)));
    }
}
