package br.com.coelho.escalas.servico;

import br.com.coelho.escalas.dominio.Feriado;
import br.com.coelho.escalas.dominio.Feriado.Abrangencia;
import br.com.coelho.escalas.motor.CalendarioSazonal;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.List;

/** Feriados nacionais (fixos e móveis) e o municipal de Joinville/SC, sede do Supermercado Coelho. */
public final class CalendarioFeriados {

    private CalendarioFeriados() {
    }

    public static List<Feriado> doAno(int ano) {
        List<Feriado> lista = new ArrayList<>();
        fixo(lista, ano, 1, 1, "Confraternização Universal", Abrangencia.NACIONAL);
        fixo(lista, ano, 3, 9, "Aniversário de Joinville", Abrangencia.MUNICIPAL);
        fixo(lista, ano, 4, 21, "Tiradentes", Abrangencia.NACIONAL);
        fixo(lista, ano, 5, 1, "Dia do Trabalho", Abrangencia.NACIONAL);
        fixo(lista, ano, 9, 7, "Independência do Brasil", Abrangencia.NACIONAL);
        fixo(lista, ano, 10, 12, "Nossa Senhora Aparecida", Abrangencia.NACIONAL);
        fixo(lista, ano, 11, 2, "Finados", Abrangencia.NACIONAL);
        fixo(lista, ano, 11, 15, "Proclamação da República", Abrangencia.NACIONAL);
        fixo(lista, ano, 11, 20, "Dia Nacional de Zumbi e da Consciência Negra", Abrangencia.NACIONAL);
        fixo(lista, ano, 12, 25, "Natal", Abrangencia.NACIONAL);
        LocalDate pascoa = pascoa(ano);
        lista.add(new Feriado(pascoa.minusDays(2), "Sexta-feira Santa", Abrangencia.NACIONAL));
        lista.add(new Feriado(pascoa.plusDays(60), "Corpus Christi", Abrangencia.MUNICIPAL));
        lista.sort((a, b) -> a.getData().compareTo(b.getData()));
        return lista;
    }

    private static void fixo(List<Feriado> lista, int ano, int mes, int dia, String nome, Abrangencia abrangencia) {
        lista.add(new Feriado(MonthDay.of(mes, dia).atYear(ano), nome, abrangencia));
    }

    /** Domingo de Páscoa (o cálculo fica no motor, que também o usa nas datas comemorativas). */
    public static LocalDate pascoa(int ano) {
        return CalendarioSazonal.pascoa(ano);
    }
}
