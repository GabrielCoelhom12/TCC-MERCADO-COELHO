package br.com.coelho.escalas.motor.otimizacao;

import br.com.coelho.escalas.dominio.ParametrosOperacionais;

import java.math.BigDecimal;

/** Limites da CLT e da convenção, vindos dos Parâmetros, que as restrições do otimizador consultam. */
public record Regras(long interjornadaMinutos, int semanalMinutos, int maxDiasConsecutivos, int maxDomingosConsecutivos) {

    static Regras de(ParametrosOperacionais p) {
        return new Regras(p.getInterjornadaMinimaHoras().multiply(BigDecimal.valueOf(60)).longValue(),
                p.getJornadaSemanalHoras().multiply(BigDecimal.valueOf(60)).intValue(),
                p.getMaxDiasConsecutivos(), p.getMaxDomingosConsecutivos());
    }
}
