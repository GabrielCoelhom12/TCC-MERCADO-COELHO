package br.com.coelho.escalas.motor.otimizacao;

import br.com.coelho.escalas.dominio.Turno;

import java.math.BigDecimal;

/**
 * Turno de uma escala vizinha, que o otimizador não muda mas precisa respeitar (interjornada, dias seguidos, domingos
 * seguidos e jornada semanal na virada das semanas).
 */
public record TurnoFixo(Jornada jornada) {

    static TurnoFixo de(Turno t, Regras regras) {
        int minutos = t.horasTrabalhadas().multiply(BigDecimal.valueOf(60)).intValue();
        return new TurnoFixo(new Jornada(t.getFuncionario().getId(), t.getData(), t.inicio(), t.fim(), minutos, false, regras));
    }
}
