package br.com.coelho.escalas.servico;

import java.time.Duration;

/** Pedido recusado por excesso de tentativas ou de carga; pode ser repetido depois de {@link #getEspera()} (HTTP 429). */
public class MuitasTentativasException extends RuntimeException {

    private final Duration espera;

    /** Login bloqueado temporariamente por excesso de senhas erradas. */
    public MuitasTentativasException(Duration espera) {
        this("Muitas tentativas de login. Tente novamente em " + minutos(espera) + ".", espera);
    }

    public MuitasTentativasException(String mensagem, Duration espera) {
        super(mensagem);
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
