package br.com.coelho.escalas.servico;

import java.time.Duration;

/** Login bloqueado temporariamente por excesso de senhas erradas (HTTP 429). */
public class MuitasTentativasException extends RuntimeException {

    private final Duration espera;

    public MuitasTentativasException(Duration espera) {
        super("Muitas tentativas de login. Tente novamente em " + minutos(espera) + ".");
        this.espera = espera;
    }

    public Duration getEspera() {
        return espera;
    }

    private static String minutos(Duration espera) {
        long m = Math.max(1, (espera.getSeconds() + 59) / 60);
        return m == 1 ? "1 minuto" : m + " minutos";
    }
}
