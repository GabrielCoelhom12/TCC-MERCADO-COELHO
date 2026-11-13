package br.com.coelho.escalas.config;

import br.com.coelho.escalas.seguranca.AutenticacaoStomp;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/** WebSocket/STOMP do chat em /ws. Broker em memória: suficiente para uma instância da aplicação. */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final AutenticacaoStomp autenticacao;
    private final String[] origens;

    /**
     * @param origens origens extras permitidas (o frontend de desenvolvimento roda em outra porta); a própria origem
     *                da aplicação é sempre aceita.
     */
    public WebSocketConfig(AutenticacaoStomp autenticacao,
                           @Value("${app.ws.origens:http://localhost:5173}") String[] origens) {
        this.autenticacao = autenticacao;
        this.origens = origens;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registro) {
        registro.addEndpoint("/ws").setAllowedOriginPatterns(origens);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registro) {
        registro.enableSimpleBroker("/topic", "/queue");
        registro.setApplicationDestinationPrefixes("/app");
        registro.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registro) {
        registro.interceptors(autenticacao);
    }
}
