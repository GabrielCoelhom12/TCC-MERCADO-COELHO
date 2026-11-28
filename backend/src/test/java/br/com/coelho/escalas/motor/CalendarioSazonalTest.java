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
    void natalSeparaAVesperaEAAntevesperaDoRestoDaSemana() {
        assertThat(em("2026-12-19")).isNull();
        assertThat(em("2026-12-20")).isEqualTo(DataComemorativa.SEMANA_DO_NATAL);
        assertThat(em("2026-12-22")).isEqualTo(DataComemorativa.SEMANA_DO_NATAL);
        assertThat(em("2026-12-23")).isEqualTo(DataComemorativa.ANTEVESPERA_DE_NATAL);
        assertThat(em("2026-12-24")).isEqualTo(DataComemorativa.VESPERA_DE_NATAL);
        assertThat(em("2026-12-25")).as("o próprio feriado é tratado como feriado").isNull();
    }

    @Test
    void anoNovoEDiasDepoisDasFestas() {
        assertThat(em("2026-12-27")).isNull();
        assertThat(em("2026-12-29")).as("sem padrão consistente").isNull();
        assertThat(em("2026-12-30")).isEqualTo(DataComemorativa.ANTEVESPERA_DE_ANO_NOVO);
        assertThat(em("2026-12-31")).isEqualTo(DataComemorativa.VESPERA_DE_ANO_NOVO);
        assertThat(em("2026-12-26")).isEqualTo(DataComemorativa.POS_FESTAS);
        assertThat(em("2027-01-02")).isEqualTo(DataComemorativa.POS_FESTAS);
    }

    @Test
    void datasMoveisSeguemAPascoa() {
        // Páscoa 2026: 05/04 → Quinta-feira Santa 02/04; Carnaval na terça 17/02.
        assertThat(em("2026-04-02")).isEqualTo(DataComemorativa.QUINTA_FEIRA_SANTA);
        assertThat(em("2026-04-03")).as("Sexta-feira Santa é feriado").isNull();
        assertThat(em("2026-04-04")).as("sábado de Aleluia sem efeito consistente").isNull();
        assertThat(em("2026-02-12")).isNull();
        assertThat(em("2026-02-13")).isEqualTo(DataComemorativa.PRE_CARNAVAL);
        assertThat(em("2026-02-14")).isEqualTo(DataComemorativa.PRE_CARNAVAL);
        assertThat(em("2026-02-16")).isEqualTo(DataComemorativa.SEGUNDA_DE_CARNAVAL);
        assertThat(em("2026-02-17")).as("terça de Carnaval sem efeito consistente").isNull();
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
    void cadaDataTemAPropriaHipoteseInicial() {
        assertThat(DataComemorativa.VESPERA_DE_NATAL.hipoteseInicial())
                .isGreaterThan(DataComemorativa.ANTEVESPERA_DE_NATAL.hipoteseInicial());
        assertThat(DataComemorativa.ANTEVESPERA_DE_NATAL.hipoteseInicial())
                .isGreaterThan(DataComemorativa.SEMANA_DO_NATAL.hipoteseInicial());
        assertThat(DataComemorativa.VESPERA_DE_NATAL.hipoteseInicial()).isEqualTo(2.50);
        assertThat(DataComemorativa.DIA_DAS_MAES.hipoteseInicial()).isEqualTo(1.20);
        assertThat(DataComemorativa.BLACK_FRIDAY.hipoteseInicial())
                .as("impacto variável: só ajusta depois de medido no histórico").isEqualTo(1.0);
    }
}
