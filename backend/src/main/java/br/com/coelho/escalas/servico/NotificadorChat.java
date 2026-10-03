package br.com.coelho.escalas.servico;

import br.com.coelho.escalas.dominio.Usuario;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.List;

/**
 * Entrega eventos do chat e das chamadas em /user/queue/chat de cada destinatário. Dentro de uma transação, o envio
 * espera o commit: ninguém recebe aviso de algo que acabou desfeito.
 */
@Component
public class NotificadorChat {

    private static final String DESTINO = "/queue/chat";

    private final SimpMessagingTemplate mensageiro;

    public NotificadorChat(SimpMessagingTemplate mensageiro) {
        this.mensageiro = mensageiro;
    }

    public void enviar(Collection<Usuario> destinatarios, Object evento) {
        List<String> logins = destinatarios.stream().map(Usuario::getLogin).distinct().toList();
        Runnable envio = () -> logins.forEach(l -> mensageiro.convertAndSendToUser(l, DESTINO, evento));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    envio.run();
                }
            });
        } else {
            envio.run();
        }
    }
}
