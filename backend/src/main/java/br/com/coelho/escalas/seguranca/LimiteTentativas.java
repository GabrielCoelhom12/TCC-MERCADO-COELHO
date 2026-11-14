package br.com.coelho.escalas.seguranca;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Proteção contra força bruta no login (OWASP ASVS V2.2): falhas são contadas por login e por IP numa janela de
 * {@link #JANELA}; ao atingir o limite, novas tentativas são recusadas até o fim do bloqueio, mesmo com a senha certa.
 *
 * <p>O contador por login protege uma conta contra tentativas vindas de vários IPs; o por IP impede que um mesmo
 * cliente percorra muitos logins. Fica em memória: suficiente para uma instância da aplicação.
 */
@Component
public class LimiteTentativas {

    public static final int MAX_FALHAS_POR_LOGIN = 5;
    public static final int MAX_FALHAS_POR_IP = 30;
    public static final Duration JANELA = Duration.ofMinutes(15);
    /** Acima disso, entradas vencidas são descartadas para a memória não crescer sem limite. */
    private static final int LIMPEZA_ACIMA_DE = 10_000;

    private record Registro(int falhas, Instant inicio, Instant bloqueadoAte) {
    }

    private final Clock relogio;
    private final Map<String, Registro> registros = new ConcurrentHashMap<>();

    public LimiteTentativas() {
        this(Clock.systemUTC());
    }

    LimiteTentativas(Clock relogio) {
        this.relogio = relogio;
    }

    /** Tempo restante de bloqueio para este login ou este IP (o maior dos dois), se houver. */
    public Optional<Duration> bloqueio(String login, String ip) {
        Instant agora = relogio.instant();
        return java.util.stream.Stream.of(chaveLogin(login), chaveIp(ip))
                .map(registros::get)
                .filter(r -> r != null && r.bloqueadoAte() != null && agora.isBefore(r.bloqueadoAte()))
                .map(r -> Duration.between(agora, r.bloqueadoAte()))
                .max(Duration::compareTo);
    }

    public void registrarFalha(String login, String ip) {
        Instant agora = relogio.instant();
        contar(chaveLogin(login), MAX_FALHAS_POR_LOGIN, agora);
        contar(chaveIp(ip), MAX_FALHAS_POR_IP, agora);
        if (registros.size() > LIMPEZA_ACIMA_DE) {
            registros.entrySet().removeIf(e -> vencido(e.getValue(), agora));
        }
    }

    /** O login certo zera as falhas da conta (as do IP continuam valendo até a janela vencer). */
    public void registrarSucesso(String login) {
        registros.remove(chaveLogin(login));
    }

    /** Esquece todas as falhas (usado entre testes). */
    public void limpar() {
        registros.clear();
    }

    private void contar(String chave, int maximo, Instant agora) {
        registros.compute(chave, (k, r) -> {
            Registro atual = r == null || vencido(r, agora) ? new Registro(0, agora, null) : r;
            int falhas = atual.falhas() + 1;
            Instant bloqueio = falhas >= maximo ? agora.plus(JANELA) : atual.bloqueadoAte();
            return new Registro(falhas, atual.inicio(), bloqueio);
        });
    }

    private static boolean vencido(Registro r, Instant agora) {
        boolean janelaVencida = !agora.isBefore(r.inicio().plus(JANELA));
        boolean semBloqueio = r.bloqueadoAte() == null || !agora.isBefore(r.bloqueadoAte());
        return janelaVencida && semBloqueio;
    }

    private static String chaveLogin(String login) {
        return "login:" + (login == null ? "" : login);
    }

    private static String chaveIp(String ip) {
        return "ip:" + (ip == null ? "" : ip);
    }
}
