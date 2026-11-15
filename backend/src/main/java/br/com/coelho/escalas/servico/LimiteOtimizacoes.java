package br.com.coelho.escalas.servico;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

/**
 * Limita quantas otimizações (Timefold) rodam ao mesmo tempo. Cada uma ocupa um núcleo do processador e uma thread do
 * servidor por até {@code app.otimizacao.segundos}; sem limite, vários cliques seguidos poderiam deixar a aplicação
 * lenta para todos. Quem passa do limite recebe HTTP 429 na hora, em vez de esperar numa fila.
 */
@Component
public class LimiteOtimizacoes {

    /** Sugestão de espera devolvida no cabeçalho Retry-After: o tempo de uma otimização. */
    private static final Duration ESPERA = Duration.ofSeconds(5);

    private final Semaphore vagas;

    public LimiteOtimizacoes(@Value("${app.otimizacao.simultaneas:2}") int simultaneas) {
        this.vagas = new Semaphore(simultaneas);
    }

    public <T> T executar(Supplier<T> otimizacao) {
        if (!vagas.tryAcquire()) {
            throw new MuitasTentativasException(
                    "Já há outra otimização de escala em andamento. Aguarde alguns segundos e tente de novo.", ESPERA);
        }
        try {
            return otimizacao.get();
        } finally {
            vagas.release();
        }
    }
}
