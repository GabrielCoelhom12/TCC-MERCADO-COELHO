package br.com.coelho.escalas.motor.otimizacao;

import ai.timefold.solver.core.api.domain.common.PlanningId;
import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;
import br.com.coelho.escalas.dominio.Funcionario;
import br.com.coelho.escalas.dominio.TurnoModelo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Uma pessoa necessária num dia, setor e turno — a "vaga" que o Timefold preenche. A demanda (quantas vagas e em quais
 * turnos) vem do mesmo planejador do gerador guloso; o otimizador decide quem ocupa cada uma. Os candidatos já excluem
 * quem não pode trabalhar no dia (indisponível ou ausente); sem ninguém, a vaga fica vazia e vira déficit.
 */
@PlanningEntity
public class Vaga {

    @PlanningId
    private Long id;
    private LocalDate data;
    private Long setorId;
    private String setorNome;
    private TurnoModelo modelo;
    private LocalDateTime inicio;
    private LocalDateTime fim;
    private int minutos;
    private BigDecimal fator;
    private List<Funcionario> candidatos;
    private Regras regras;

    @PlanningVariable(valueRangeProviderRefs = "candidatos", allowsUnassigned = true)
    private Funcionario funcionario;

    /** Exigido pelo Timefold. */
    public Vaga() {
    }

    Vaga(Long id, LocalDate data, Long setorId, String setorNome, TurnoModelo modelo, LocalDateTime inicio, LocalDateTime fim,
         int minutos, BigDecimal fator, List<Funcionario> candidatos, Regras regras) {
        this.id = id;
        this.data = data;
        this.setorId = setorId;
        this.setorNome = setorNome;
        this.modelo = modelo;
        this.inicio = inicio;
        this.fim = fim;
        this.minutos = minutos;
        this.fator = fator;
        this.candidatos = candidatos;
        this.regras = regras;
    }

    @ValueRangeProvider(id = "candidatos")
    public List<Funcionario> getCandidatos() {
        return candidatos;
    }

    /** Custo do turno com a pessoa escolhida, em reais (base da pontuação soft). */
    int custoEmReais() {
        return funcionario.getCustoHora().multiply(BigDecimal.valueOf(minutos)).multiply(fator)
                .divide(BigDecimal.valueOf(60), 0, RoundingMode.HALF_UP).intValue();
    }

    Jornada jornada() {
        return new Jornada(funcionario.getId(), data, inicio, fim, minutos, true, regras);
    }

    public Long getId() {
        return id;
    }

    public LocalDate getData() {
        return data;
    }

    public Long getSetorId() {
        return setorId;
    }

    public String getSetorNome() {
        return setorNome;
    }

    public TurnoModelo getModelo() {
        return modelo;
    }

    public LocalDateTime getInicio() {
        return inicio;
    }

    public LocalDateTime getFim() {
        return fim;
    }

    public int getMinutos() {
        return minutos;
    }

    public Regras getRegras() {
        return regras;
    }

    public Funcionario getFuncionario() {
        return funcionario;
    }

    public void setFuncionario(Funcionario funcionario) {
        this.funcionario = funcionario;
    }
}
