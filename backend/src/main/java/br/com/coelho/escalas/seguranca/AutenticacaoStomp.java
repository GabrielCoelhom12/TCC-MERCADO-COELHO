package br.com.coelho.escalas.seguranca;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Segurança do WebSocket: o CONNECT precisa trazer o mesmo JWT da API (cabeçalho Authorization) de um usuário ativo;
 * só é possível se inscrever na própria fila do chat e no canal de presença; nada é enviado pelo socket (as mensagens
 * entram pela API REST, onde são validadas).
 */
@Component
public class AutenticacaoStomp implements ChannelInterceptor {

    static final Set<String> DESTINOS_PERMITIDOS = Set.of("/user/queue/chat", "/topic/presenca");

    private final AutenticadorJwt autenticador;

    public AutenticacaoStomp(AutenticadorJwt autenticador) {
        this.autenticador = autenticador;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor acesso = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (acesso == null || acesso.getCommand() == null) {
            return message;
        }
        StompCommand comando = acesso.getCommand();
        if (comando == StompCommand.CONNECT) {
            acesso.setUser(autenticador.autenticar(acesso.getFirstNativeHeader("Authorization"))
                    .orElseThrow(() -> new MessagingException("Sessão inválida ou expirada.")));
        } else if (comando == StompCommand.SUBSCRIBE) {
            if (acesso.getUser() == null || !DESTINOS_PERMITIDOS.contains(acesso.getDestination())) {
                throw new MessagingException("Destino não permitido.");
            }
        } else if (comando == StompCommand.SEND) {
            throw new MessagingException("Envie mensagens pela API.");
        }
        return message;
    }
}
