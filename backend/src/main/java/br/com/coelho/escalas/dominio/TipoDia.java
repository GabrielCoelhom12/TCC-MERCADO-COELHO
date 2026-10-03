package br.com.coelho.escalas.dominio;

import java.time.DayOfWeek;

/**
 * Perfil de calendário de um dia. Feriados têm perfil próprio (demanda mínima e
 * projeção de receita específicas), independentemente do dia da semana em que caem.
 */
public enum TipoDia {
    SEGUNDA("Segunda"),
    TERCA("Terça"),
    QUARTA("Quarta"),
    QUINTA("Quinta"),
    SEXTA("Sexta"),
    SABADO("Sábado"),
    DOMINGO("Domingo"),
    FERIADO("Feriado");

    private final String rotulo;

    TipoDia(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }

    public static TipoDia de(DayOfWeek dia) {
        return values()[dia.getValue() - 1];
    }
}
