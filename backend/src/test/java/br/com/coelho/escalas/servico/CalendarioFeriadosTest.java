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

        assertThat(feriados).hasSize(11);
        assertThat(feriados).extracting(Feriado::getData).isSorted();
        assertThat(feriados).extracting(Feriado::getData)
                .contains(LocalDate.of(2026, 4, 3), LocalDate.of(2026, 3, 21));
    }

    /** O Supermercado Coelho fica em Indaial/SC: o feriado municipal é o aniversário da cidade (21 de março). */
    @Test
    void feriadoMunicipalEhOAniversarioDeIndaial() {
        List<Feriado> feriados = CalendarioFeriados.doAno(2026);

        assertThat(feriados).filteredOn(f -> f.getAbrangencia() == Feriado.Abrangencia.MUNICIPAL)
                .singleElement()
                .satisfies(f -> {
                    assertThat(f.getDescricao()).isEqualTo("Aniversário de Indaial");
                    assertThat(f.getData()).isEqualTo(LocalDate.of(2026, 3, 21));
                });
        assertThat(feriados).extracting(Feriado::getDescricao).noneMatch(d -> d.contains("Joinville"));
    }

    /** Em Indaial, Corpus Christi é ponto facultativo, não feriado: o comércio abre normalmente. */
    @Test
    void corpusChristiNaoEhFeriadoEmIndaial() {
        assertThat(CalendarioFeriados.doAno(2026)).extracting(Feriado::getData)
                .doesNotContain(LocalDate.of(2026, 6, 4));
    }
}
