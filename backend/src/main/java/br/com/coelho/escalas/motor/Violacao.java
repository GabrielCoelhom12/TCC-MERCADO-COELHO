package br.com.coelho.escalas.motor;

import java.time.LocalDate;

public record Violacao(
        Severidade severidade,
        Regra regra,
        LocalDate data,
        Long funcionarioId,
        String funcionarioNome,
        Long setorId,
        String mensagem
) {
    public enum Severidade {
        /** Impede a aprovação da escala. */
        ERRO,
        /** Exige atenção da gerência, mas não bloqueia. */
        ALERTA
    }

    public enum Regra {
        SOBREPOSICAO,
        JORNADA_DIARIA_MAXIMA,
        HORA_EXTRA_DIARIA,
        INTERVALO_INTRAJORNADA,
        INTERJORNADA,
        HORA_EXTRA_SEMANAL,
        DIAS_CONSECUTIVOS,
        DOMINGOS_CONSECUTIVOS,
        INDISPONIBILIDADE,
        AUSENCIA,
        FUNCIONARIO_INATIVO,
        SETOR_ABAIXO_MINIMO,
        /** Faixa de horário com menos pessoas que o movimento previsto pede (pico descoberto). */
        FAIXA_ABAIXO_DEMANDA,
        OPERADORES_ACIMA_LMAX,
        CUSTO_ACIMA_TETO,
        PROJECAO_INVIAVEL
    }

    public boolean erro() {
        return severidade == Severidade.ERRO;
    }

    public boolean financeira() {
        return regra == Regra.OPERADORES_ACIMA_LMAX || regra == Regra.CUSTO_ACIMA_TETO;
    }
}
