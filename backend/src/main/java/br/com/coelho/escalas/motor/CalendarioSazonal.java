package br.com.coelho.escalas.motor;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.TemporalAdjusters;
import java.util.Optional;

/**
 * Datas comemorativas que mudam o movimento de um supermercado, conforme o estudo de sazonalidade do projeto
 * (ABRAS/SuperHiper, Scanntech e calendário oficial de feriados).
 *
 * <p>O dia do próprio feriado (Natal, Sexta-feira Santa…) não entra aqui: ele já é tratado como feriado pela
 * previsão. Aqui ficam os dias <b>em torno</b> das datas, quando o movimento costuma se concentrar.</p>
 *
 * <p>A hipótese inicial de cada data é usada enquanto a loja não tem histórico daquela data; depois, ela é combinada
 * com o efeito medido nos fechamentos da própria loja. Os valores foram calibrados com o histórico real de vendas da
 * loja parceira (efeito de cada data sobre os mesmos dias da semana em volta), arredondados para baixo. Os dados da
 * loja são confidenciais e não fazem parte do repositório. Ver docs/experimento-previsao.md.</p>
 */
public final class CalendarioSazonal {

    /** Hipótese neutra: impacto incerto (depende de campanha, região, clima); só ajusta depois de medido. */
    public static final double VARIAVEL = 1.0;

    /** Peso padrão da hipótese inicial: ela "vale" 2 ocorrências na média com o histórico da loja. */
    public static final int PESO_HIPOTESE_PADRAO = 2;
    /**
     * Peso maior para as datas com poucos dias por ano (Dia das Mães e dos Pais: só sábado e domingo). Com tão poucas
     * ocorrências, um ano atípico (chuva, campanha da concorrência) mudaria demais a previsão; com peso 4, a primeira
     * ocorrência responde por 1/5 do efeito, e não por 1/3.
     */
    public static final int PESO_HIPOTESE_POUCAS_OCORRENCIAS = 4;

    /**
     * Cada data tem a própria hipótese. Datas com comportamento muito diferente ficam separadas, para que uma não dilua
     * a outra: a véspera de Natal vende bem mais que os dias 20 a 22.
     */
    public enum DataComemorativa {
        /** 20 a 22/12: aumento moderado. */
        SEMANA_DO_NATAL("Semana do Natal", 1.10),
        /** 23/12. */
        ANTEVESPERA_DE_NATAL("Antevéspera de Natal", 1.60),
        /** 24/12: o dia de maior movimento do ano. */
        VESPERA_DE_NATAL("Véspera de Natal", 2.50),
        /** 30/12. */
        ANTEVESPERA_DE_ANO_NOVO("Antevéspera de Ano-Novo", 1.65),
        /** 31/12. */
        VESPERA_DE_ANO_NOVO("Véspera de Ano-Novo", 2.20),
        /**
         * Os dias seguintes ao Natal e ao Ano-Novo (26/12 e 02/01): "ressaca" depois do feriado, com troca de presentes
         * e reposição de estoque doméstico. Impacto incerto; muitas lojas nem abrem em 26/12.
         */
        POS_FESTAS("Depois das festas de fim de ano", VARIAVEL),
        /** Quinta-feira Santa. O sábado de Aleluia não mostrou efeito consistente e não entra. */
        QUINTA_FEIRA_SANTA("Quinta-feira Santa", 1.25),
        /** Sexta e sábado antes do Carnaval: sem efeito consistente; só ajusta depois de medido. */
        PRE_CARNAVAL("Pré-Carnaval", VARIAVEL),
        /** Segunda-feira de Carnaval (ponto facultativo). A terça não mostrou efeito consistente. */
        SEGUNDA_DE_CARNAVAL("Segunda-feira de Carnaval", 1.20),
        /** Sábado e domingo. */
        DIA_DAS_MAES("Dia das Mães", 1.20, PESO_HIPOTESE_POUCAS_OCORRENCIAS),
        /** Sábado e domingo. */
        DIA_DOS_PAIS("Dia dos Pais", 1.25, PESO_HIPOTESE_POUCAS_OCORRENCIAS),
        DIA_DOS_NAMORADOS("Dia dos Namorados", VARIAVEL),
        /** No supermercado, a Black Friday não mostrou aumento consistente de movimento. */
        BLACK_FRIDAY("Black Friday", VARIAVEL);

        private final String nome;
        private final double hipotese;
        private final int pesoHipotese;

        DataComemorativa(String nome, double hipotese) {
            this(nome, hipotese, PESO_HIPOTESE_PADRAO);
        }

        DataComemorativa(String nome, double hipotese, int pesoHipotese) {
            this.nome = nome;
            this.hipotese = hipotese;
            this.pesoHipotese = pesoHipotese;
        }

        /** Quantas ocorrências "virtuais" a hipótese inicial vale na média com o histórico da loja. */
        public int pesoHipotese() {
            return pesoHipotese;
        }

        public String nome() {
            return nome;
        }

        /** Fator usado enquanto não há ocorrências desta data no histórico (1,0 = nenhum ajuste). */
        public double hipoteseInicial() {
            return hipotese;
        }
    }

    private CalendarioSazonal() {
    }

    /** A data comemorativa que influencia o dia, se houver. */
    public static Optional<DataComemorativa> em(LocalDate d) {
        int ano = d.getYear();
        int dia = d.getDayOfMonth();
        Month mes = d.getMonth();

        if (mes == Month.DECEMBER) {
            switch (dia) {
                case 20, 21, 22 -> {
                    return Optional.of(DataComemorativa.SEMANA_DO_NATAL);
                }
                case 23 -> {
                    return Optional.of(DataComemorativa.ANTEVESPERA_DE_NATAL);
                }
                case 24 -> {
                    return Optional.of(DataComemorativa.VESPERA_DE_NATAL);
                }
                case 26 -> {
                    return Optional.of(DataComemorativa.POS_FESTAS);
                }
                case 30 -> {
                    return Optional.of(DataComemorativa.ANTEVESPERA_DE_ANO_NOVO);
                }
                case 31 -> {
                    return Optional.of(DataComemorativa.VESPERA_DE_ANO_NOVO);
                }
                default -> {
                    // 25/12 é feriado; 27 a 29/12 não mostraram padrão na loja parceira.
                }
            }
        }
        if (mes == Month.JANUARY && dia == 2) {
            return Optional.of(DataComemorativa.POS_FESTAS);
        }

        LocalDate pascoa = pascoa(ano);
        if (d.equals(pascoa.minusDays(3))) {
            return Optional.of(DataComemorativa.QUINTA_FEIRA_SANTA);
        }
        LocalDate tercaDeCarnaval = pascoa.minusDays(47);
        if (d.equals(tercaDeCarnaval.minusDays(4)) || d.equals(tercaDeCarnaval.minusDays(3))) {
            return Optional.of(DataComemorativa.PRE_CARNAVAL);
        }
        if (d.equals(tercaDeCarnaval.minusDays(1))) {
            return Optional.of(DataComemorativa.SEGUNDA_DE_CARNAVAL);
        }

        if (domingoOuSabadoAnterior(d, segundoDomingo(ano, Month.MAY))) {
            return Optional.of(DataComemorativa.DIA_DAS_MAES);
        }
        if (domingoOuSabadoAnterior(d, segundoDomingo(ano, Month.AUGUST))) {
            return Optional.of(DataComemorativa.DIA_DOS_PAIS);
        }
        if (mes == Month.JUNE && dia == 12) {
            return Optional.of(DataComemorativa.DIA_DOS_NAMORADOS);
        }
        // Black Friday: a sexta depois do Dia de Ação de Graças (4ª quinta-feira de novembro nos EUA).
        LocalDate acaoDeGracas = LocalDate.of(ano, Month.NOVEMBER, 1)
                .with(TemporalAdjusters.dayOfWeekInMonth(4, DayOfWeek.THURSDAY));
        if (d.equals(acaoDeGracas.plusDays(1))) {
            return Optional.of(DataComemorativa.BLACK_FRIDAY);
        }
        return Optional.empty();
    }

    /** Domingo de Páscoa pelo algoritmo de Meeus/Jones/Butcher (calendário gregoriano). */
    public static LocalDate pascoa(int ano) {
        int a = ano % 19;
        int b = ano / 100;
        int c = ano % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int mes = (h + l - 7 * m + 114) / 31;
        int dia = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(ano, mes, dia);
    }

    private static LocalDate segundoDomingo(int ano, Month mes) {
        return LocalDate.of(ano, mes, 1).with(TemporalAdjusters.dayOfWeekInMonth(2, DayOfWeek.SUNDAY));
    }

    private static boolean domingoOuSabadoAnterior(LocalDate d, LocalDate domingo) {
        return d.equals(domingo) || d.equals(domingo.minusDays(1));
    }
}
