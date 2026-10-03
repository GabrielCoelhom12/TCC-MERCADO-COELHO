package br.com.coelho.escalas.motor;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/** Previsão de movimento (clientes e vendas) a partir dos fechamentos diários. */
class PrevisaoMovimentoTest {

    private final PrevisaoMovimento previsao = new PrevisaoMovimento();

    /** Sexta-feira, 23/10/2026: fora da janela de pagamento (dias 1 a 10) e sem feriado próximo. */
    private static final LocalDate SEXTA = LocalDate.of(2026, 10, 23);

    @Test
    void semHistoricoNaoHaPrevisao() {
        assertThat(previsao.prever(SEXTA, new Cenario().ctx())).isNull();
    }

    @Test
    void usaAMediaPonderadaDasMesmasDiasDaSemanaRecentes() {
        Cenario c = new Cenario();
        c.movimento(SEXTA.minusWeeks(2), "40000", 100, 100, 100, 100, 100); // 500 clientes
        c.movimento(SEXTA.minusWeeks(1), "50000", 120, 120, 120, 120, 120); // 600 clientes (mais recente pesa mais)
        c.movimento(SEXTA.minusDays(1), "10000", 50, 50, 50, 50, 50);       // quinta: outro dia da semana, ignorada

        PrevisaoMovimento.PrevisaoDia p = previsao.prever(SEXTA, c.ctx());

        // pesos 2 (mais recente) e 1: (2×50.000 + 1×40.000) / 3
        assertThat(p.vendas()).isEqualByComparingTo("46666.67");
        assertThat(p.clientes()).isEqualTo(567);
        assertThat(p.amostras()).isEqualTo(2);
        assertThat(p.explicacao()).anyMatch(e -> e.contains("Média das últimas 2 sextas"));
    }

    @Test
    void consideraNoMaximoAsOitoOcorrenciasMaisRecentes() {
        Cenario c = new Cenario();
        for (int semana = 1; semana <= 10; semana++) {
            String vendas = semana <= 8 ? "30000" : "999999"; // as duas mais antigas não entram
            c.movimento(SEXTA.minusWeeks(semana), vendas, 60, 60, 60, 60, 60);
        }
        PrevisaoMovimento.PrevisaoDia p = previsao.prever(SEXTA, c.ctx());
        assertThat(p.vendas()).isEqualByComparingTo("30000");
        assertThat(p.amostras()).isEqualTo(8);
    }

    @Test
    void naoUsaDadosDoProprioDiaNemPosteriores() {
        Cenario c = new Cenario();
        c.movimento(SEXTA.minusWeeks(1), "30000", 60, 60, 60, 60, 60);
        c.movimento(SEXTA, "90000", 300, 300, 300, 300, 300);
        c.movimento(SEXTA.plusWeeks(1), "90000", 300, 300, 300, 300, 300);
        assertThat(previsao.prever(SEXTA, c.ctx()).vendas()).isEqualByComparingTo("30000");
    }

    @Test
    void distribuiOsClientesPorFaixaConformeOHistoricoEIndicaOPico() {
        Cenario c = new Cenario();
        c.movimento(SEXTA.minusWeeks(1), "30000", 50, 100, 100, 200, 50);
        c.movimento(SEXTA.minusWeeks(2), "30000", 50, 100, 100, 200, 50);

        PrevisaoMovimento.PrevisaoDia p = previsao.prever(SEXTA, c.ctx());

        assertThat(p.faixas()).extracting(PrevisaoMovimento.FaixaPrevista::clientes).containsExactly(50, 100, 100, 200, 50);
        assertThat(p.faixas().get(3).inicio()).isEqualTo(LocalTime.of(16, 0));
        assertThat(p.pico().inicio()).isEqualTo(LocalTime.of(16, 0));
        assertThat(p.explicacao()).anyMatch(e -> e.contains("Pico previsto: 16:00–19:00"));
    }

    @Test
    void vesperaDeFeriadoUsaOEfeitoPadraoQuandoHaPoucasVesperas() {
        Cenario c = new Cenario();
        LocalDate vespera = LocalDate.of(2026, 11, 19); // quinta, véspera do feriado de 20/11
        c.feriado(vespera.plusDays(1));
        c.movimento(vespera.minusWeeks(1), "30000", 100, 100, 100, 100, 100);
        c.movimento(vespera.minusWeeks(2), "30000", 100, 100, 100, 100, 100);

        PrevisaoMovimento.PrevisaoDia p = previsao.prever(vespera, c.ctx());

        assertThat(p.vendas()).isEqualByComparingTo("34500.00"); // +15% padrão
        assertThat(p.explicacao()).anyMatch(e -> e.contains("Véspera de feriado: +15%") && e.contains("padrão"));
    }

    @Test
    void vesperaDeFeriadoAprendeOEfeitoComOHistorico() {
        Cenario c = new Cenario();
        // Duas vésperas passadas (quintas) venderam 30% acima das quintas normais.
        LocalDate v1 = LocalDate.of(2026, 9, 17);
        LocalDate v2 = LocalDate.of(2026, 10, 15);
        c.feriado(v1.plusDays(1));
        c.feriado(v2.plusDays(1));
        c.movimento(v1, "26000", 80, 80, 80, 80, 80);
        c.movimento(v2, "26000", 80, 80, 80, 80, 80);
        c.movimento(LocalDate.of(2026, 9, 24), "20000", 60, 60, 60, 60, 60);
        c.movimento(LocalDate.of(2026, 10, 22), "20000", 60, 60, 60, 60, 60);
        c.movimento(LocalDate.of(2026, 10, 29), "20000", 60, 60, 60, 60, 60);

        LocalDate vespera = LocalDate.of(2026, 11, 19);
        c.feriado(vespera.plusDays(1));
        PrevisaoMovimento.PrevisaoDia p = previsao.prever(vespera, c.ctx());

        assertThat(p.vendas()).isEqualByComparingTo("26000.00");
        assertThat(p.explicacao()).anyMatch(e -> e.contains("Véspera de feriado: +30%") && e.contains("2 vésperas"));
    }

    @Test
    void inicioDoMesAprendeOEfeitoDoPagamento() {
        Cenario c = new Cenario();
        // Segundas normais vendem 30 mil; segundas de início de mês (dias 1 a 10) vendem 10% a mais.
        for (String data : new String[]{"2026-10-26", "2026-10-19", "2026-10-12", "2026-09-28"}) {
            c.movimento(LocalDate.parse(data), "30000", 100, 100, 100, 100, 100);
        }
        for (String data : new String[]{"2026-10-05", "2026-09-07", "2026-08-10", "2026-08-03"}) {
            c.movimento(LocalDate.parse(data), "33000", 110, 110, 110, 110, 110);
        }

        PrevisaoMovimento.PrevisaoDia p = previsao.prever(LocalDate.of(2026, 11, 2), c.ctx());

        assertThat(p.vendas()).isEqualByComparingTo("33000.00");
        assertThat(p.explicacao()).anyMatch(e -> e.contains("Início do mês (pagamento): +10%") && e.contains("4 dias"));
    }

    @Test
    void semEvidenciaNaoInventaEfeitoDeInicioDoMes() {
        Cenario c = new Cenario();
        c.movimento(LocalDate.of(2026, 10, 26), "30000", 100, 100, 100, 100, 100);
        c.movimento(LocalDate.of(2026, 10, 19), "30000", 100, 100, 100, 100, 100);

        PrevisaoMovimento.PrevisaoDia p = previsao.prever(LocalDate.of(2026, 11, 2), c.ctx());

        assertThat(p.vendas()).isEqualByComparingTo("30000");
        assertThat(p.explicacao()).noneMatch(e -> e.contains("Início do mês"));
    }

    @Test
    void feriadoUsaOsFeriadosAnterioresOuODomingo() {
        Cenario c = new Cenario();
        LocalDate feriado = LocalDate.of(2026, 11, 20);
        c.feriado(feriado);
        c.movimento(LocalDate.of(2026, 11, 15), "18000", 90, 90, 0, 0, 0); // domingo

        PrevisaoMovimento.PrevisaoDia semFeriadosNoHistorico = previsao.prever(feriado, c.ctx());
        assertThat(semFeriadosNoHistorico.vendas()).isEqualByComparingTo("18000");
        assertThat(semFeriadosNoHistorico.explicacao()).anyMatch(e -> e.contains("domingos"));

        LocalDate feriadoPassado = LocalDate.of(2026, 11, 2);
        c.feriado(feriadoPassado);
        c.movimento(feriadoPassado, "22000", 120, 100, 0, 0, 0);
        assertThat(previsao.prever(feriado, c.ctx()).vendas()).isEqualByComparingTo("22000");
    }

    @Test
    void semOcorrenciasDoMesmoDiaUsaAMediaGeral() {
        Cenario c = new Cenario();
        c.movimento(SEXTA.minusDays(1), "20000", 60, 60, 60, 60, 60); // quinta
        c.movimento(SEXTA.minusDays(2), "30000", 60, 60, 60, 60, 60); // quarta
        PrevisaoMovimento.PrevisaoDia p = previsao.prever(SEXTA, c.ctx());
        assertThat(p.vendas()).isEqualByComparingTo(new BigDecimal("23333.33")); // quinta (mais recente) pesa 2, quarta 1
        assertThat(p.explicacao()).anyMatch(e -> e.contains("Ainda não há sextas"));
    }

    @Test
    void usaOArtigoCertoParaSabadoEDomingo() {
        Cenario c = new Cenario();
        LocalDate sabado = LocalDate.of(2026, 10, 24);
        c.movimento(sabado.minusWeeks(1), "60000", 100, 100, 100, 100, 100);
        c.movimento(sabado.minusWeeks(2), "60000", 100, 100, 100, 100, 100);
        c.movimento(sabado.minusDays(6), "20000", 100, 100, 0, 0, 0); // domingo 18/10

        assertThat(previsao.prever(sabado, c.ctx()).explicacao()).anyMatch(e -> e.startsWith("Média dos últimos 2 sábados"));
        assertThat(previsao.prever(sabado.plusDays(1), c.ctx()).explicacao()).anyMatch(e -> e.startsWith("Média do último domingo"));
    }
}
