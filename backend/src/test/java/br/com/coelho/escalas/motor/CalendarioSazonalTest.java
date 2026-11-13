package br.com.coelho.escalas.motor;

import br.com.coelho.escalas.motor.CalendarioSazonal.DataComemorativa;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Datas comemorativas que mudam o movimento do supermercado (fixas e móveis). */
class CalendarioSazonalTest {

    private static DataComemorativa em(String data) {
        return CalendarioSazonal.em(LocalDate.parse(data)).orElse(null);
    }

    @Test
    void calculaAPascoaDeCadaAno() {
        assertThat(CalendarioSazonal.pascoa(2026)).isEqualTo(LocalDate.of(2026, 4, 5));
        assertThat(CalendarioSazonal.pascoa(2027)).isEqualTo(LocalDate.of(2027, 3, 28));
    }

    @Test
    void natalConcentraOMovimentoDe20a24DeDezembro() {
        assertThat(em("2026-12-19")).isNull();
        assertThat(em("2026-12-20")).isEqualTo(DataComemorativa.SEMANA_DO_NATAL);
        assertThat(em("2026-12-24")).isEqualTo(DataComemorativa.SEMANA_DO_NATAL);
        assertThat(em("2026-12-25")).as("o próprio feriado é tratado como feriado").isNull();
    }

    @Test
    void anoNovoEDiasDepoisDasFestas() {
        assertThat(em("2026-12-29")).isEqualTo(DataComemorativa.VESPERAS_DE_ANO_NOVO);
        assertThat(em("2026-12-31")).isEqualTo(DataComemorativa.VESPERAS_DE_ANO_NOVO);
        assertThat(em("2026-12-26")).isEqualTo(DataComemorativa.POS_FESTAS);
        assertThat(em("2027-01-02")).isEqualTo(DataComemorativa.POS_FESTAS);
    }

    @Test
    void datasMoveisSeguemAPascoa() {
        // Páscoa 2026: 05/04 → Quinta-feira Santa 02/04 e Sábado de Aleluia 04/04; Carnaval na terça 17/02.
        assertThat(em("2026-04-02")).isEqualTo(DataComemorativa.SEMANA_SANTA);
        assertThat(em("2026-04-04")).isEqualTo(DataComemorativa.SEMANA_SANTA);
        assertThat(em("2026-04-03")).as("Sexta-feira Santa é feriado").isNull();
        assertThat(em("2026-02-13")).isEqualTo(DataComemorativa.PRE_CARNAVAL);
        assertThat(em("2026-02-14")).isEqualTo(DataComemorativa.PRE_CARNAVAL);
        assertThat(em("2026-02-12")).isNull();
    }

    @Test
    void diaDasMaesEDosPaisIncluemOSabadoAnterior() {
        // 2º domingo de maio (10/05/2026) e de agosto (09/08/2026).
        assertThat(em("2026-05-09")).isEqualTo(DataComemorativa.DIA_DAS_MAES);
        assertThat(em("2026-05-10")).isEqualTo(DataComemorativa.DIA_DAS_MAES);
        assertThat(em("2026-05-03")).as("1º domingo de maio").isNull();
        assertThat(em("2026-08-08")).isEqualTo(DataComemorativa.DIA_DOS_PAIS);
        assertThat(em("2026-08-09")).isEqualTo(DataComemorativa.DIA_DOS_PAIS);
    }

    @Test
    void namoradosEBlackFriday() {
        assertThat(em("2026-06-12")).isEqualTo(DataComemorativa.DIA_DOS_NAMORADOS);
        // Black Friday: a sexta depois da 4ª quinta-feira de novembro (26/11/2026).
        assertThat(em("2026-11-27")).isEqualTo(DataComemorativa.BLACK_FRIDAY);
        assertThat(em("2026-11-20")).isNull();
    }

    @Test
    void diaComumNaoTemDataComemorativa() {
        assertThat(em("2026-10-23")).isNull();
    }

    @Test
    void hipoteseInicialSegueATendenciaDoEstudoDeSazonalidade() {
        assertThat(DataComemorativa.SEMANA_DO_NATAL.hipoteseInicial()).isEqualTo(1.30);
        assertThat(DataComemorativa.DIA_DAS_MAES.hipoteseInicial()).isEqualTo(1.15);
        assertThat(DataComemorativa.BLACK_FRIDAY.hipoteseInicial())
                .as("impacto variável: só ajusta depois de medido no histórico").isEqualTo(1.0);
    }
}
