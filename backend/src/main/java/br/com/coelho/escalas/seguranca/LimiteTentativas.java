package br.com.coelho.escalas.seguranca;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Proteção contra força bruta no login (OWASP ASVS V2.2): falhas são contadas numa janela de {@link #JANELA}; ao
 * atingir um limite, novas tentativas são recusadas até o fim do bloqueio, mesmo com a senha certa. Há três contadores:
 * <ul>
 *   <li><b>login + IP</b> ({@value #MAX_FALHAS_POR_LOGIN_E_IP} falhas): o caso comum, alguém tentando senhas de uma
 *       conta. Bloqueia só aquele login vindo daquele IP: quem errou a senha não tranca a conta para todo mundo;</li>
 *   <li><b>IP</b> ({@value #MAX_FALHAS_POR_IP} falhas): um mesmo cliente percorrendo muitos logins;</li>
 *   <li><b>login</b>, somando todos os IPs ({@value #MAX_FALHAS_POR_LOGIN} falhas): ataque distribuído contra uma
 *       conta. O limite é alto de propósito: para trancar o gestor de má-fé, seria preciso usar dezenas de IPs.</li>
 * </ul>
 * Fica em memória: suficiente para uma instância da aplicação.
 */
@Component
public class LimiteTentativas {

    public static final int MAX_FALHAS_POR_LOGIN_E_IP = 5;
    public static final int MAX_FALHAS_POR_IP = 30;
    public static final int MAX_FALHAS_POR_LOGIN = 50;
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

    /** Tempo restante de bloqueio para este login, este IP ou este login neste IP (o maior), se houver. */
    public Optional<Duration> bloqueio(String login, String ip) {
        Instant agora = relogio.instant();
        return Stream.of(chaveLoginEIp(login, ip), chaveIp(ip), chaveLogin(login))
                .map(registros::get)
                .filter(r -> r != null && r.bloqueadoAte() != null && agora.isBefore(r.bloqueadoAte()))
                .map(r -> Duration.between(agora, r.bloqueadoAte()))
                .max(Duration::compareTo);
    }

    public void registrarFalha(String login, String ip) {
        Instant agora = relogio.instant();
        contar(chaveLoginEIp(login, ip), MAX_FALHAS_POR_LOGIN_E_IP, agora);
        contar(chaveIp(ip), MAX_FALHAS_POR_IP, agora);
        contar(chaveLogin(login), MAX_FALHAS_POR_LOGIN, agora);
        if (registros.size() > LIMPEZA_ACIMA_DE) {
            registros.entrySet().removeIf(e -> vencido(e.getValue(), agora));
        }
    }

    /**
     * O login certo zera as falhas dessa conta nesse IP. As do IP e as da conta somadas de todos os IPs continuam
     * valendo até a janela vencer: acertar a senha uma vez não apaga um ataque em andamento.
     */
    public void registrarSucesso(String login, String ip) {
        registros.remove(chaveLoginEIp(login, ip));
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

    private static String chaveLoginEIp(String login, String ip) {
        return "login-ip:" + (login == null ? "" : login) + "|" + (ip == null ? "" : ip);
    }
}
