package br.com.coelho.escalas.motor;

import br.com.coelho.escalas.dominio.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Motor de Cálculo e Otimização Financeira (2º pilar do projeto).
 *
 * <pre>
 *   C_escala(d) = Σ [ S_i × H_{i,d} × F_d ]
 *   L_min(d) ≤ Σ O_{i,d} ≤ L_max(d, R_proj)
 * </pre>
 *
 * Com histórico de fechamentos, R_proj(d) e a demanda dos setores que acompanham o movimento vêm da
 * {@link PrevisaoMovimento previsão}; sem ele, da configuração manual.
 */
@Component
public class MotorFinanceiro {

    private final PrevisaoMovimento previsor = new PrevisaoMovimento();
    private final PlanejadorDemanda planejador = new PlanejadorDemanda();

    private record Receita(BigDecimal valor, CapacidadeDia.OrigemReceita origem) {
    }

    public TipoDia tipoDia(LocalDate data, ContextoCalculo ctx) {
        return ctx.feriados().containsKey(data) ? TipoDia.FERIADO : TipoDia.de(data.getDayOfWeek());
    }

    public boolean domingoOuFeriado(LocalDate data, ContextoCalculo ctx) {
        return data.getDayOfWeek() == DayOfWeek.SUNDAY || ctx.feriados().containsKey(data);
    }

    /** F_d: 1,0 em dias úteis; fator de domingo e/ou de feriado (prevalece o maior). */
    public BigDecimal fator(LocalDate data, ContextoCalculo ctx) {
        ParametrosOperacionais p = ctx.parametros();
        BigDecimal fator = BigDecimal.ONE;
        if (data.getDayOfWeek() == DayOfWeek.SUNDAY) {
            fator = fator.max(p.getFatorDomingo());
        }
        Feriado feriado = ctx.feriados().get(data);
        if (feriado != null) {
            fator = fator.max(feriado.getFatorCusto() != null ? feriado.getFatorCusto() : p.getFatorFeriado());
        }
        return fator;
    }

    /** R_proj(d): projeção informada para a data; senão a prevista pelo histórico; senão a padrão do perfil do dia. */
    public BigDecimal receitaProjetada(LocalDate data, ContextoCalculo ctx) {
        return receita(data, ctx, previsor.prever(data, ctx)).valor();
    }

    private Receita receita(LocalDate data, ContextoCalculo ctx, PrevisaoMovimento.PrevisaoDia previsao) {
        BigDecimal informada = ctx.projecaoPorData().get(data);
        if (informada != null) {
            return new Receita(informada, CapacidadeDia.OrigemReceita.INFORMADA);
        }
        if (previsao != null) {
            return new Receita(previsao.vendas(), CapacidadeDia.OrigemReceita.PREVISAO);
        }
        return new Receita(ctx.projecaoPorTipo().getOrDefault(tipoDia(data, ctx), BigDecimal.ZERO), CapacidadeDia.OrigemReceita.PADRAO);
    }

    public PrevisaoMovimento.PrevisaoDia previsao(LocalDate data, ContextoCalculo ctx) {
        return previsor.prever(data, ctx);
    }

    /** Modelos de turno que valem no dia (dias úteis × domingos/feriados); se nenhum se aplica, todos. */
    public List<TurnoModelo> modelosDoDia(LocalDate data, List<TurnoModelo> modelos, ContextoCalculo ctx) {
        boolean especial = domingoOuFeriado(data, ctx);
        List<TurnoModelo> aplicaveis = modelos.stream().filter(m -> m.aplicavel(especial)).toList();
        return aplicaveis.isEmpty() ? modelos : aplicaveis;
    }

    public PlanejadorDemanda.PlanoSetor planejar(Setor setor, TipoDia tipo, PrevisaoMovimento.PrevisaoDia previsao,
                                                 List<TurnoModelo> modelosDia) {
        return planejador.planejar(setor, setor.minimoPara(tipo), previsao, modelosDia);
    }

    /** S_i × H_{i,d} × F_d, sem arredondamento (arredonda-se apenas o total). */
    BigDecimal custoBruto(Turno turno, BigDecimal fator) {
        return turno.getFuncionario().getCustoHora().multiply(turno.horasTrabalhadas()).multiply(fator);
    }

    public BigDecimal custoTurno(Turno turno, ContextoCalculo ctx) {
        return custoBruto(turno, fator(turno.getData(), ctx)).setScale(2, RoundingMode.HALF_UP);
    }

    /** C_escala(d) para os turnos de um único dia. */
    public BigDecimal custoEscala(LocalDate data, List<Turno> turnosDoDia, ContextoCalculo ctx) {
        BigDecimal fator = fator(data, ctx);
        return turnosDoDia.stream()
                .map(t -> custoBruto(t, fator))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** Custo estimado de um operador "médio" (dos colaboradores informados) trabalhando a jornada de referência no dia. */
    public BigDecimal custoMedioOperador(List<Funcionario> funcionarios, BigDecimal fator, ParametrosOperacionais p) {
        if (funcionarios.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal soma = funcionarios.stream().map(Funcionario::getCustoHora).reduce(BigDecimal.ZERO, BigDecimal::add);
        return soma.divide(BigDecimal.valueOf(funcionarios.size()), 6, RoundingMode.HALF_UP)
                .multiply(p.getJornadaReferenciaHoras())
                .multiply(fator)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** Calcula L_min, L_max e o teto financeiro do dia a partir da projeção de receita. */
    public CapacidadeDia capacidade(LocalDate data, ContextoCalculo ctx) {
        ParametrosOperacionais p = ctx.parametros();
        TipoDia tipo = tipoDia(data, ctx);
        BigDecimal fator = fator(data, ctx);
        PrevisaoMovimento.PrevisaoDia previsao = previsor.prever(data, ctx);
        Receita origem = receita(data, ctx, previsao);
        BigDecimal receita = origem.valor();
        BigDecimal teto = receita.multiply(p.getPercentualTetoFolha()).movePointLeft(2).setScale(2, RoundingMode.HALF_UP);

        List<Funcionario> elegiveis = ctx.funcionariosAtivos().stream()
                .filter(f -> f.getSetor() != null && f.getSetor().isAtivo())
                .toList();
        BigDecimal custoMedio = custoMedioOperador(elegiveis, fator, p);
        int lMax = custoMedio.signum() > 0 ? teto.divide(custoMedio, 0, RoundingMode.FLOOR).intValue() : 0;

        List<TurnoModelo> modelosDia = modelosDoDia(data, ctx.modelos(), ctx);
        Map<Long, Integer> minimos = new LinkedHashMap<>();
        Map<Long, PlanejadorDemanda.PlanoSetor> planos = new LinkedHashMap<>();
        int lMin = 0;
        for (Setor setor : ctx.setoresAtivos()) {
            PlanejadorDemanda.PlanoSetor plano = planejar(setor, tipo, previsao, modelosDia);
            planos.put(setor.getId(), plano);
            minimos.put(setor.getId(), plano.necessarios());
            lMin += plano.necessarios();
        }

        Feriado feriado = ctx.feriados().get(data);
        return new CapacidadeDia(data, tipo, feriado == null ? null : feriado.getDescricao(), fator, receita, teto,
                custoMedio, lMin, lMax, minimos, origem.origem(), previsao, planos);
    }
}
