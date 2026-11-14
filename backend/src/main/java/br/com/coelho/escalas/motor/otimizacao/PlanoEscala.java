package br.com.coelho.escalas.motor.otimizacao;

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.timefold.solver.core.api.score.HardMediumSoftScore;

import java.util.List;

/**
 * O problema (e a solução) que o Timefold otimiza: as vagas a preencher e os turnos fixos das escalas vizinhas.
 * Pontuação em três níveis: hard (regras da CLT, nunca violadas), medium (vagas sem ninguém) e soft (custo e
 * equilíbrio de horas).
 */
@PlanningSolution
public class PlanoEscala {

    @PlanningEntityCollectionProperty
    private List<Vaga> vagas;

    @ProblemFactCollectionProperty
    private List<TurnoFixo> fixos;

    @PlanningScore
    private HardMediumSoftScore score;

    /** Exigido pelo Timefold. */
    public PlanoEscala() {
    }

    PlanoEscala(List<Vaga> vagas, List<TurnoFixo> fixos) {
        this.vagas = vagas;
        this.fixos = fixos;
    }

    public List<Vaga> getVagas() {
        return vagas;
    }

    public List<TurnoFixo> getFixos() {
        return fixos;
    }

    public HardMediumSoftScore getScore() {
        return score;
    }

    // Setters exigidos pelo Timefold, que clona a solução durante a busca.

    public void setVagas(List<Vaga> vagas) {
        this.vagas = vagas;
    }

    public void setFixos(List<TurnoFixo> fixos) {
        this.fixos = fixos;
    }

    public void setScore(HardMediumSoftScore score) {
        this.score = score;
    }
}
