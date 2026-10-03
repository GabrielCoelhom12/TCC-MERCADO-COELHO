package br.com.coelho.escalas.seguranca;

import br.com.coelho.escalas.dominio.Usuario;
import br.com.coelho.escalas.repositorio.UsuarioRepository;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Set;

/**
 * Segurança do WebSocket: o CONNECT precisa trazer o mesmo JWT da API (cabeçalho Authorization) de um usuário ativo;
 * só é possível se inscrever na própria fila do chat e no canal de presença; nada é enviado pelo socket (as mensagens
 * entram pela API REST, onde são validadas).
 */
public class AutenticacaoStomp implements ChannelInterceptor {

    static final Set<String> DESTINOS_PERMITIDOS = Set.of("/user/queue/chat", "/topic/presenca");

    private final JwtService jwt;
    private final UsuarioRepository usuarios;

    public AutenticacaoStomp(JwtService jwt, UsuarioRepository usuarios) {
        this.jwt = jwt;
        this.usuarios = usuarios;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor acesso = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (acesso == null || acesso.getCommand() == null) {
            return message;
        }
        StompCommand comando = acesso.getCommand();
        if (comando == StompCommand.CONNECT) {
            acesso.setUser(autenticar(acesso.getFirstNativeHeader("Authorization")));
        } else if (comando == StompCommand.SUBSCRIBE) {
            if (acesso.getUser() == null || !DESTINOS_PERMITIDOS.contains(acesso.getDestination())) {
                throw new MessagingException("Destino não permitido.");
            }
        } else if (comando == StompCommand.SEND) {
            throw new MessagingException("Envie mensagens pela API.");
        }
        return message;
    }

    private UsernamePasswordAuthenticationToken autenticar(String cabecalho) {
        if (cabecalho == null || !cabecalho.startsWith("Bearer ")) {
            throw new MessagingException("Não autenticado.");
        }
        var claims = jwt.validar(cabecalho.substring(7)).orElseThrow(() -> new MessagingException("Sessão inválida ou expirada."));
        Usuario usuario = usuarios.findByLogin(claims.getSubject()).filter(Usuario::isAtivo)
                .orElseThrow(() -> new MessagingException("Usuário inexistente ou desativado."));
        return new UsernamePasswordAuthenticationToken(usuario.getLogin(), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getPerfil().name())));
    }
}
