package br.com.coelho.escalas.motor;

import br.com.coelho.escalas.dominio.TipoDia;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;

/**
 * Limites calculados pelo motor para um dia d, antes de qualquer alocação.
 *
 * @param fator              F_d (1,0 em dias úteis; maior em domingos e feriados)
 * @param receitaProjetada   R_proj(d)
 * @param tetoFolha          capacidade máxima de pagamento do dia = R_proj × % teto
 * @param custoMedioOperador custo estimado de um operador no dia = média(S_i) × jornada de referência × F_d
 * @param lMin               L_min(d): soma das demandas mínimas dos setores
 * @param lMax               L_max(d, R_proj): ⌊teto / custo médio do operador⌋
 * @param minimoPorSetor     pessoas necessárias em cada setor no dia (id → operadores): o piso manual ou, nos setores
 *                           que acompanham o movimento, o necessário para cobrir as faixas previstas
 * @param origemReceita      de onde veio R_proj(d)
 * @param previsao           previsão de movimento do dia (nula sem histórico)
 * @param planos             planejamento de cada setor (faixas e turnos que as cobrem)
 */
public record CapacidadeDia(
        LocalDate data,
        TipoDia tipoDia,
        String feriado,
        BigDecimal fator,
        BigDecimal receitaProjetada,
        BigDecimal tetoFolha,
        BigDecimal custoMedioOperador,
        int lMin,
        int lMax,
        Map<Long, Integer> minimoPorSetor,
        OrigemReceita origemReceita,
        PrevisaoMovimento.PrevisaoDia previsao,
        Map<Long, PlanejadorDemanda.PlanoSetor> planos
) {
    public enum OrigemReceita {
        /** Projeção informada pela gerência para a data (prevalece sobre tudo). */
        INFORMADA,
        /** Prevista a partir do histórico de fechamentos. */
        PREVISAO,
        /** Projeção padrão do perfil do dia (sem histórico). */
        PADRAO
    }

    public CapacidadeDia {
        Objects.requireNonNull(origemReceita);
        planos = planos == null ? Map.of() : planos;
    }

    /** A projeção de receita não comporta nem o mínimo operacional. */
    @JsonProperty("inviavel")
    public boolean inviavel() {
        return lMin > lMax;
    }
}
