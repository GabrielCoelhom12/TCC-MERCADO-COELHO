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
 * <p>A {@linkplain Tendencia tendência} de cada data é só a hipótese inicial usada enquanto a loja não tem histórico
 * daquela data; depois, vale o efeito medido nos fechamentos da própria loja.</p>
 */
public final class CalendarioSazonal {

    /** Hipótese inicial de variação das vendas, antes de existir histórico da data. */
    public enum Tendencia {
        MUITO_ALTA(1.30),
        ALTA(1.15),
        /** Impacto incerto (depende de campanha, região, clima): só ajusta depois de medido no histórico. */
        VARIAVEL(1.0);

        private final double fator;

        Tendencia(double fator) {
            this.fator = fator;
        }
    }

    /** Peso padrão da hipótese inicial: ela "vale" 2 ocorrências na média com o histórico da loja. */
    public static final int PESO_HIPOTESE_PADRAO = 2;
    /**
     * Peso maior para as datas com poucos dias por ano (Dia das Mães e dos Pais: só sábado e domingo). Com tão poucas
     * ocorrências, um ano atípico (chuva, campanha da concorrência) mudaria demais a previsão; com peso 4, a primeira
     * ocorrência responde por 1/5 do efeito, e não por 1/3.
     */
    public static final int PESO_HIPOTESE_POUCAS_OCORRENCIAS = 4;

    public enum DataComemorativa {
        SEMANA_DO_NATAL("Semana do Natal", Tendencia.MUITO_ALTA),
        VESPERAS_DE_ANO_NOVO("Vésperas de Ano-Novo", Tendencia.ALTA),
        /**
         * Só os dias seguintes ao Natal e ao Ano-Novo (26/12 e 02/01), de propósito: são os dias de "ressaca" depois
         * de um feriado, com troca de presentes e reposição de estoque doméstico. 27 e 28/12 já são dias comuns, e 29
         * a 31/12 entram nas {@linkplain #VESPERAS_DE_ANO_NOVO vésperas de Ano-Novo}.
         */
        POS_FESTAS("Depois das festas de fim de ano", Tendencia.VARIAVEL),
        SEMANA_SANTA("Semana Santa", Tendencia.ALTA),
        PRE_CARNAVAL("Pré-Carnaval", Tendencia.ALTA),
        DIA_DAS_MAES("Dia das Mães", Tendencia.ALTA, PESO_HIPOTESE_POUCAS_OCORRENCIAS),
        DIA_DOS_PAIS("Dia dos Pais", Tendencia.ALTA, PESO_HIPOTESE_POUCAS_OCORRENCIAS),
        DIA_DOS_NAMORADOS("Dia dos Namorados", Tendencia.VARIAVEL),
        BLACK_FRIDAY("Black Friday", Tendencia.VARIAVEL);

        private final String nome;
        private final Tendencia tendencia;
        private final int pesoHipotese;

        DataComemorativa(String nome, Tendencia tendencia) {
            this(nome, tendencia, PESO_HIPOTESE_PADRAO);
        }

        DataComemorativa(String nome, Tendencia tendencia, int pesoHipotese) {
            this.nome = nome;
            this.tendencia = tendencia;
            this.pesoHipotese = pesoHipotese;
        }

        /** Quantas ocorrências "virtuais" a hipótese inicial vale na média com o histórico da loja. */
        public int pesoHipotese() {
            return pesoHipotese;
        }

        public String nome() {
            return nome;
        }

        public Tendencia tendencia() {
            return tendencia;
        }

        /** Fator usado enquanto não há ocorrências desta data no histórico (1,0 = nenhum ajuste). */
        public double hipoteseInicial() {
            return tendencia.fator;
        }
    }

    private CalendarioSazonal() {
    }

    /** A data comemorativa que influencia o dia, se houver. */
    public static Optional<DataComemorativa> em(LocalDate d) {
        int ano = d.getYear();
        int dia = d.getDayOfMonth();
        Month mes = d.getMonth();

        if (mes == Month.DECEMBER && dia >= 20 && dia <= 24) {
            return Optional.of(DataComemorativa.SEMANA_DO_NATAL);
        }
        if (mes == Month.DECEMBER && dia >= 29) {
            return Optional.of(DataComemorativa.VESPERAS_DE_ANO_NOVO);
        }
        if ((mes == Month.DECEMBER && dia == 26) || (mes == Month.JANUARY && dia == 2)) {
            return Optional.of(DataComemorativa.POS_FESTAS);
        }

        LocalDate pascoa = pascoa(ano);
        if (d.equals(pascoa.minusDays(3)) || d.equals(pascoa.minusDays(1))) {
            return Optional.of(DataComemorativa.SEMANA_SANTA);
        }
        LocalDate tercaDeCarnaval = pascoa.minusDays(47);
        if (d.equals(tercaDeCarnaval.minusDays(4)) || d.equals(tercaDeCarnaval.minusDays(3))) {
            return Optional.of(DataComemorativa.PRE_CARNAVAL);
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
