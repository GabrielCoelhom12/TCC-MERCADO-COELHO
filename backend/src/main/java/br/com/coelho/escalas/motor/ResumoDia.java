package br.com.coelho.escalas.motor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Situação consolidada de um dia após a alocação: o "rodapé" de cada coluna da matriz. */
public record ResumoDia(
        LocalDate data,
        String tipoDia,
        String feriado,
        BigDecimal fator,
        BigDecimal receitaProjetada,
        BigDecimal tetoFolha,
        BigDecimal custoEscala,
        BigDecimal custoMedioOperador,
        int operadores,
        int lMin,
        int lMax,
        Situacao situacao,
        List<ResumoSetor> setores,
        /** Clientes previstos no dia e a faixa de pico (nulos sem histórico de movimento). */
        Integer clientesPrevistos,
        String pico
) {
    public enum Situacao {
        /** L_min ≤ ΣO ≤ L_max e custo dentro do teto. */
        OK,
        /** Algum setor abaixo da demanda mínima (understaffing). */
        ABAIXO_MINIMO,
        /** Acima de L_max ou do teto financeiro (overstaffing). */
        ACIMA_LIMITE,
        /** Projeção de receita não comporta o mínimo operacional (L_min > L_max). */
        INVIAVEL,
        /** Nenhuma alocação no dia. */
        VAZIO
    }

    /**
     * @param faixas cobertura das faixas de movimento (só nos setores que acompanham o movimento)
     */
    public record ResumoSetor(Long setorId, String nome, String cor, int operadores, int minimo, BigDecimal custo,
                              List<CoberturaFaixa> faixas) {
    }

    /** Pessoas do setor presentes na faixa × pessoas previstas como necessárias. */
    public record CoberturaFaixa(String inicio, String fim, int clientes, int necessarios, int alocados) {
    }
}
