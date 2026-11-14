package br.com.coelho.escalas.motor.otimizacao;

import ai.timefold.solver.core.api.score.HardMediumSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.Joiners;
import ai.timefold.solver.core.api.score.stream.uni.UniConstraintStream;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Regras da escala para o Timefold, em três níveis:
 * <ul>
 *   <li><b>hard</b> — regras da CLT (as mesmas que o gerador guloso respeita): um turno por dia, descanso de 11 h entre
 *       jornadas (art. 66), jornada semanal, dias seguidos (art. 67) e domingos seguidos (Lei 10.101/2000), contando os
 *       turnos das escalas vizinhas;</li>
 *   <li><b>medium</b> — cada vaga sem ninguém: o otimizador só deixa uma vaga vazia se não houver como preenchê-la;</li>
 *   <li><b>soft</b> — o custo da escala (R$) e o equilíbrio de horas entre as pessoas (soma dos quadrados das horas
 *       semanais de cada uma, que cresce quando a carga se concentra em poucos).</li>
 * </ul>
 */
public class RestricoesEscala implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory f) {
        return new Constraint[]{
                umTurnoPorDia(f), umTurnoPorDiaComEscalasVizinhas(f),
                interjornada(f), interjornadaComEscalasVizinhas(f),
                jornadaSemanal(f), diasSeguidos(f), domingosSeguidos(f),
                vagaSemNinguem(f),
                custo(f), equilibrioDeHoras(f),
        };
    }

    // ------------------------------------------------------------------ hard: CLT

    Constraint umTurnoPorDia(ConstraintFactory f) {
        return f.forEachUniquePair(Vaga.class, Joiners.equal(Vaga::getFuncionario), Joiners.equal(Vaga::getData))
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint("Dois turnos no mesmo dia");
    }

    Constraint umTurnoPorDiaComEscalasVizinhas(ConstraintFactory f) {
        return f.forEach(Vaga.class)
                .join(TurnoFixo.class,
                        Joiners.equal(v -> v.getFuncionario().getId(), t -> t.jornada().funcionarioId()),
                        Joiners.equal(Vaga::getData, t -> t.jornada().data()))
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint("Dois turnos no mesmo dia (escala vizinha)");
    }

    Constraint interjornada(ConstraintFactory f) {
        return f.forEachUniquePair(Vaga.class, Joiners.equal(Vaga::getFuncionario))
                .filter((a, b) -> Jornada.feremInterjornada(a.jornada(), b.jornada()))
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint("Interjornada abaixo de 11 h");
    }

    Constraint interjornadaComEscalasVizinhas(ConstraintFactory f) {
        return f.forEach(Vaga.class)
                .join(TurnoFixo.class, Joiners.equal(v -> v.getFuncionario().getId(), t -> t.jornada().funcionarioId()))
                .filter((v, t) -> Jornada.feremInterjornada(v.jornada(), t.jornada()))
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint("Interjornada abaixo de 11 h (escala vizinha)");
    }

    /** Horas acima do limite semanal, só nas semanas em que o otimizador escalou alguém. */
    Constraint jornadaSemanal(ConstraintFactory f) {
        return jornadas(f)
                .groupBy(Jornada::chaveSemana, ConstraintCollectors.sum(Jornada::minutos),
                        ConstraintCollectors.sum(j -> j.daVaga() ? 1 : 0))
                .filter((chave, minutos, vagas) -> vagas > 0 && minutos > chave.limiteMinutos())
                .penalize(HardMediumSoftScore.ONE_HARD, (chave, minutos, vagas) -> (minutos - chave.limiteMinutos() + 59) / 60)
                .asConstraint("Jornada semanal acima do limite");
    }

    Constraint diasSeguidos(ConstraintFactory f) {
        return jornadas(f)
                .groupBy(Jornada::funcionarioId, ConstraintCollectors.toList())
                .filter((id, lista) -> excesso(lista, 1, lista.getFirst().regras().maxDiasConsecutivos()) > 0)
                .penalize(HardMediumSoftScore.ONE_HARD, (id, lista) -> excesso(lista, 1, lista.getFirst().regras().maxDiasConsecutivos()))
                .asConstraint("Dias seguidos acima do limite");
    }

    Constraint domingosSeguidos(ConstraintFactory f) {
        return jornadas(f)
                .filter(j -> j.data().getDayOfWeek() == DayOfWeek.SUNDAY)
                .groupBy(Jornada::funcionarioId, ConstraintCollectors.toList())
                .filter((id, lista) -> excesso(lista, 7, lista.getFirst().regras().maxDomingosConsecutivos()) > 0)
                .penalize(HardMediumSoftScore.ONE_HARD, (id, lista) -> excesso(lista, 7, lista.getFirst().regras().maxDomingosConsecutivos()))
                .asConstraint("Domingos seguidos acima do limite");
    }

    // ------------------------------------------------------------------ medium: cobertura

    Constraint vagaSemNinguem(ConstraintFactory f) {
        return f.forEachIncludingUnassigned(Vaga.class)
                .filter(v -> v.getFuncionario() == null)
                .penalize(HardMediumSoftScore.ONE_MEDIUM)
                .asConstraint("Vaga sem ninguem");
    }

    // ------------------------------------------------------------------ soft: custo e equilíbrio

    Constraint custo(ConstraintFactory f) {
        return f.forEach(Vaga.class)
                .penalize(HardMediumSoftScore.ONE_SOFT, Vaga::custoEmReais)
                .asConstraint("Custo da escala");
    }

    Constraint equilibrioDeHoras(ConstraintFactory f) {
        return f.forEach(Vaga.class)
                .groupBy(Vaga::getFuncionario, v -> v.jornada().semana(), ConstraintCollectors.sum(Vaga::getMinutos))
                .penalize(HardMediumSoftScore.ONE_SOFT, (pessoa, semana, minutos) -> (int) Math.round(Math.pow(minutos / 60.0, 2)))
                .asConstraint("Equilibrio de horas");
    }

    // ------------------------------------------------------------------ auxiliares

    /** Turnos das vagas preenchidas e das escalas vizinhas, juntos, para as regras de sequência. */
    private static UniConstraintStream<Jornada> jornadas(ConstraintFactory f) {
        return f.forEach(Vaga.class).map(Vaga::jornada)
                .concat(f.forEach(TurnoFixo.class).map(TurnoFixo::jornada));
    }

    /**
     * Quanto as sequências de dias trabalhados (de {@code passo} em {@code passo} dias: 1 = dias seguidos, 7 = domingos
     * seguidos) passam do máximo — só nas sequências que têm algum turno escolhido pelo otimizador.
     */
    static int excesso(List<Jornada> jornadas, int passo, int maximo) {
        Set<LocalDate> dias = jornadas.stream().map(Jornada::data).collect(Collectors.toCollection(TreeSet::new));
        Set<LocalDate> escolhidos = jornadas.stream().filter(Jornada::daVaga).map(Jornada::data).collect(Collectors.toSet());
        int total = 0;
        Set<LocalDate> vistos = new HashSet<>();
        for (LocalDate d : dias) {
            if (vistos.contains(d) || dias.contains(d.minusDays(passo))) {
                continue;
            }
            int tamanho = 0;
            boolean temEscolhido = false;
            for (LocalDate x = d; dias.contains(x); x = x.plusDays(passo)) {
                vistos.add(x);
                tamanho++;
                temEscolhido |= escolhidos.contains(x);
            }
            if (temEscolhido && tamanho > maximo) {
                total += tamanho - maximo;
            }
        }
        return total;
    }
}
