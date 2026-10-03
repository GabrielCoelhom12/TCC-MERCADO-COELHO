package br.com.coelho.escalas.motor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

final class Formato {

    private static final Locale PT_BR = Locale.of("pt", "BR");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM");

    private Formato() {
    }

    static String moeda(BigDecimal valor) {
        return NumberFormat.getCurrencyInstance(PT_BR).format(valor).replace("\u00A0", " ").replace("\u202F", " ");
    }

    static String horas(BigDecimal horas) {
        long minutos = horas.multiply(BigDecimal.valueOf(60)).setScale(0, RoundingMode.HALF_UP).longValue();
        return String.format("%dh%02d", minutos / 60, minutos % 60);
    }

    static String data(LocalDate data) {
        return DATA.format(data);
    }
}
