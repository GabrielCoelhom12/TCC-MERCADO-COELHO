package br.com.coelho.escalas.servico;

import br.com.coelho.escalas.dominio.Feriado;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CalendarioFeriadosTest {

    @ParameterizedTest
    @CsvSource({"2024, 2024-03-31", "2025, 2025-04-20", "2026, 2026-04-05", "2027, 2027-03-28"})
    void calculaDomingoDePascoa(int ano, LocalDate esperado) {
        assertThat(CalendarioFeriados.pascoa(ano)).isEqualTo(esperado);
    }

    @Test
    void listaFeriadosFixosEMoveisEmOrdem() {
        List<Feriado> feriados = CalendarioFeriados.doAno(2026);

        assertThat(feriados).hasSize(12);
        assertThat(feriados).extracting(Feriado::getData).isSorted();
        assertThat(feriados).extracting(Feriado::getData)
                .contains(LocalDate.of(2026, 4, 3), LocalDate.of(2026, 6, 4), LocalDate.of(2026, 3, 9));
        assertThat(feriados).filteredOn(f -> f.getDescricao().equals("Aniversário de Joinville"))
                .singleElement()
                .extracting(Feriado::getAbrangencia).isEqualTo(Feriado.Abrangencia.MUNICIPAL);
    }
}
