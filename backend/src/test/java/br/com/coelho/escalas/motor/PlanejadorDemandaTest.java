package br.com.coelho.escalas.motor;

import br.com.coelho.escalas.dominio.Setor;
import br.com.coelho.escalas.dominio.TurnoModelo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static br.com.coelho.escalas.motor.Cenario.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Converte a previsão de clientes por faixa em pessoas necessárias e escolhe os turnos que cobrem o movimento. */
class PlanejadorDemandaTest {

    private final PlanejadorDemanda planejador = new PlanejadorDemanda();
    private static final TurnoModelo INTERMEDIARIO =
            new TurnoModelo("Intermediário", "INT", LocalTime.of(10, 0), LocalTime.of(18, 20), 60, TurnoModelo.Aplicacao.DIAS_UTEIS);
    private static final List<TurnoModelo> MODELOS = List.of(ABERTURA, FECHAMENTO, INTERMEDIARIO);

    private static PrevisaoMovimento.PrevisaoDia previsao(int... clientes) {
        List<PrevisaoMovimento.FaixaPrevista> faixas = new java.util.ArrayList<>();
        for (int i = 0; i < clientes.length; i++) {
            faixas.add(new PrevisaoMovimento.FaixaPrevista(FAIXAS[i][0], FAIXAS[i][1], clientes[i]));
        }
        return new PrevisaoMovimento.PrevisaoDia(LocalDate.of(2026, 10, 23), BigDecimal.TEN,
                java.util.Arrays.stream(clientes).sum(), faixas, 4, List.of());
    }

    private static Setor caixa(Integer clientesPorHora) {
        Setor s = new Setor("Caixa", "#000");
        s.setId(1L);
        s.setClientesPorColaboradorHora(clientesPorHora);
        return s;
    }

    @Test
    void calculaAsPessoasNecessariasEmCadaFaixa() {
        // 30 clientes/hora por pessoa; faixas de 3h → cada pessoa atende 90 clientes na faixa.
        var plano = planejador.planejar(caixa(30), 1, previsao(90, 180, 181, 270, 0), MODELOS);
        assertThat(plano.faixas()).extracting(PlanejadorDemanda.DemandaFaixa::pessoas).containsExactly(1, 2, 3, 3, 0);
    }

    @Test
    void escolheOsTurnosQueCobremAsFaixasComMenosPessoas() {
        // Manhã fraca, pico à tarde: a solução precisa de 3 fechamentos e 1 abertura.
        var plano = planejador.planejar(caixa(30), 1, previsao(90, 90, 180, 270, 270), MODELOS);

        assertThat(plano.turnosSugeridos()).extracting(TurnoModelo::getSigla)
                .containsExactlyInAnyOrder("FEC", "FEC", "FEC", "ABE");
        assertThat(plano.necessarios()).isEqualTo(4);
        assertThat(plano.faixas()).allSatisfy(f -> assertThat(cobertura(plano.turnosSugeridos(), f)).isGreaterThanOrEqualTo(f.pessoas()));
    }

    @Test
    void oMinimoManualContinuaValendoComoPiso() {
        var plano = planejador.planejar(caixa(30), 6, previsao(30, 30, 30, 30, 30), MODELOS);
        assertThat(plano.necessarios()).isEqualTo(6);
        assertThat(plano.minimoManual()).isEqualTo(6);
    }

    @Test
    void setorSemCapacidadeDeAtendimentoUsaSoODemandaFixa() {
        var plano = planejador.planejar(caixa(null), 3, previsao(900, 900, 900, 900, 900), MODELOS);
        assertThat(plano.necessarios()).isEqualTo(3);
        assertThat(plano.faixas()).isEmpty();
        assertThat(plano.turnosSugeridos()).isEmpty();
    }

    @Test
    void semPrevisaoUsaSoODemandaFixa() {
        var plano = planejador.planejar(caixa(30), 2, null, MODELOS);
        assertThat(plano.necessarios()).isEqualTo(2);
        assertThat(plano.faixas()).isEmpty();
    }

    @Test
    void faixaQueNenhumTurnoCobreNaoTravaOPlanejamento() {
        // Só o turno de domingo (8h–13h45): as faixas da tarde ficam sem cobertura possível.
        var plano = planejador.planejar(caixa(30), 0, previsao(90, 90, 90, 90, 90), List.of(DOMINGO));
        assertThat(plano.turnosSugeridos()).extracting(TurnoModelo::getSigla).containsExactly("DOM");
    }

    @Test
    void turnoCobreAFaixaQuandoPegaPeloMenosMetadeDela() {
        assertThat(PlanejadorDemanda.cobre(LocalTime.of(7, 0), LocalTime.of(15, 20), LocalTime.of(13, 0), LocalTime.of(16, 0))).isTrue();
        assertThat(PlanejadorDemanda.cobre(LocalTime.of(13, 40), LocalTime.of(22, 0), LocalTime.of(13, 0), LocalTime.of(16, 0))).isTrue();
        assertThat(PlanejadorDemanda.cobre(LocalTime.of(7, 0), LocalTime.of(15, 20), LocalTime.of(16, 0), LocalTime.of(19, 0))).isFalse();
        assertThat(PlanejadorDemanda.cobre(LocalTime.of(22, 0), LocalTime.of(6, 0), LocalTime.of(19, 0), LocalTime.of(22, 0))).isFalse();
    }

    private static long cobertura(List<TurnoModelo> turnos, PlanejadorDemanda.DemandaFaixa f) {
        return turnos.stream().filter(t -> PlanejadorDemanda.cobre(t.getHoraInicio(), t.getHoraFim(), f.inicio(), f.fim())).count();
    }
}
