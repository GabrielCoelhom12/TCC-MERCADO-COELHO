package br.com.coelho.escalas.motor.otimizacao;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;

/**
 * Um turno de alguém — de uma vaga preenchida pelo otimizador ({@code daVaga}) ou já fixo numa escala vizinha — na
 * forma usada pelas restrições que olham a sequência de dias da pessoa (semana, dias seguidos, domingos, interjornada).
 */
record Jornada(Long funcionarioId, LocalDate data, LocalDateTime inicio, LocalDateTime fim, int minutos, boolean daVaga,
               Regras regras) {

    LocalDate semana() {
        return data.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /** Chave do agrupamento semanal (o limite segue junto para a restrição comparar). */
    ChaveSemana chaveSemana() {
        return new ChaveSemana(funcionarioId, semana(), regras.semanalMinutos());
    }

    /** Descanso, em minutos, entre o fim de um turno e o início do outro (negativo: os dois se sobrepõem). */
    static long descanso(Jornada a, Jornada b) {
        Jornada primeiro = a.inicio().isBefore(b.inicio()) ? a : b;
        Jornada segundo = primeiro == a ? b : a;
        return Duration.between(primeiro.fim(), segundo.inicio()).toMinutes();
    }

    /** Turnos de dias vizinhos com descanso abaixo do mínimo (CLT art. 66). */
    static boolean feremInterjornada(Jornada a, Jornada b) {
        long dias = Math.abs(a.data().toEpochDay() - b.data().toEpochDay());
        return dias == 1 && descanso(a, b) < a.regras().interjornadaMinutos();
    }

    record ChaveSemana(Long funcionarioId, LocalDate semana, int limiteMinutos) {
    }
}
