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

    // ------------------------------------------------------------------ datas comemorativas

    @Test
    void dataComemorativaSemHistoricoUsaAHipoteseInicial() {
        Cenario c = new Cenario();
        for (String data : new String[]{"2026-12-15", "2026-11-24", "2026-11-17"}) { // terças comuns
            c.movimento(LocalDate.parse(data), "30000", 100, 100, 100, 100, 100);
        }

        PrevisaoMovimento.PrevisaoDia p = previsao.prever(LocalDate.of(2026, 12, 22), c.ctx()); // terça da semana do Natal

        assertThat(p.vendas()).isEqualByComparingTo("33000.00");
        assertThat(p.clientes()).isEqualTo(550);
        assertThat(p.explicacao()).anyMatch(e -> e.contains("Semana do Natal: +10%") && e.contains("hipótese inicial"));
    }

    @Test
    void dataComemorativaCombinaAHipoteseComOQueALojaJaViveu() {
        Cenario c = new Cenario();
        c.movimento(LocalDate.of(2026, 5, 9), "45000", 150, 150, 150, 150, 150); // sábado do Dia das Mães: +50%
        for (String data : new String[]{"2027-04-24", "2027-04-17", "2027-03-20"}) { // sábados comuns
            c.movimento(LocalDate.parse(data), "30000", 100, 100, 100, 100, 100);
        }

        PrevisaoMovimento.PrevisaoDia p = previsao.prever(LocalDate.of(2027, 5, 8), c.ctx()); // sábado do Dia das Mães

        // Dia das Mães tem poucos dias por ano: a hipótese vale 4 ocorrências. (1 × 1,50 + 4 × 1,20) / 5 = 1,26
        assertThat(p.vendas()).isEqualByComparingTo("37800.00");
        assertThat(p.explicacao()).anyMatch(e -> e.contains("Dia das Mães: +26%")
                && e.contains("+20%") && e.contains("1 ocorrência") && e.contains("+50%"));
    }

    @Test
    void datasComMaisDiasPorAnoAprendemMaisRapidoQueODiaDasMaes() {
        Cenario c = new Cenario();
        c.movimento(LocalDate.of(2025, 12, 22), "45000", 150, 150, 150, 150, 150); // segunda da semana do Natal: +50%
        c.movimento(LocalDate.of(2025, 12, 15), "30000", 100, 100, 100, 100, 100); // base de 2025 (segunda comum)
        for (String data : new String[]{"2026-12-15", "2026-12-08", "2026-12-01"}) { // terças comuns
            c.movimento(LocalDate.parse(data), "30000", 100, 100, 100, 100, 100);
        }

        PrevisaoMovimento.PrevisaoDia p = previsao.prever(LocalDate.of(2026, 12, 22), c.ctx()); // terça da semana do Natal

        // Peso padrão 2: (1 × 1,50 + 2 × hipótese 1,10) / 3 = 1,2333
        assertThat(p.vendas()).isEqualByComparingTo("37000.00");
        assertThat(p.explicacao()).anyMatch(e -> e.contains("Semana do Natal: +23%"));
    }

    @Test
    void dataComemorativaPassadaNaoEntraNaMediaDoDiaDaSemana() {
        Cenario c = new Cenario();
        c.movimento(LocalDate.of(2026, 5, 16), "30000", 100, 100, 100, 100, 100);
        c.movimento(LocalDate.of(2026, 5, 9), "45000", 150, 150, 150, 150, 150); // Dia das Mães
        c.movimento(LocalDate.of(2026, 4, 25), "30000", 100, 100, 100, 100, 100);
        c.movimento(LocalDate.of(2026, 4, 18), "30000", 100, 100, 100, 100, 100);

        PrevisaoMovimento.PrevisaoDia p = previsao.prever(LocalDate.of(2026, 5, 23), c.ctx()); // sábado comum

        assertThat(p.vendas()).isEqualByComparingTo("30000.00");
        assertThat(p.amostras()).isEqualTo(3);
    }

    @Test
    void dataComemorativaPrevaleceSobreAVesperaDeFeriado() {
        Cenario c = new Cenario();
        c.feriado(LocalDate.of(2026, 12, 25));
        for (String data : new String[]{"2026-12-17", "2026-11-26", "2026-11-12"}) { // quintas comuns
            c.movimento(LocalDate.parse(data), "30000", 100, 100, 100, 100, 100);
        }

        PrevisaoMovimento.PrevisaoDia p = previsao.prever(LocalDate.of(2026, 12, 24), c.ctx());

        assertThat(p.vendas()).as("só o efeito da véspera de Natal (+150%), sem somar o da véspera de feriado")
                .isEqualByComparingTo("75000.00");
        assertThat(p.explicacao()).noneMatch(e -> e.startsWith("Véspera de feriado"));
    }

    /** O efeito de uma data passada é medido contra as semanas em volta dela, e não contra as mais recentes. */
    @Test
    void efeitoDaDataPassadaDescontaOCrescimentoDaLoja() {
        Cenario c = new Cenario();
        c.movimento(LocalDate.of(2025, 12, 31), "66000", 220, 220, 220, 220, 220); // quarta, véspera de Ano-Novo
        // Quartas comuns em volta de 31/12/2025, quando a loja vendia 30 mil...
        for (String data : new String[]{"2025-11-12", "2025-11-19", "2025-11-26", "2025-12-17",
                "2026-01-14", "2026-01-21", "2026-01-28", "2026-02-11"}) {
            c.movimento(LocalDate.parse(data), "30000", 100, 100, 100, 100, 100);
        }
        // ...e um ano depois, quando a loja cresceu e vende 40 mil por dia.
        for (String data : new String[]{"2026-11-11", "2026-11-18", "2026-11-25", "2026-12-16",
                "2026-11-19", "2026-11-26", "2026-12-17"}) {
            c.movimento(LocalDate.parse(data), "40000", 130, 130, 130, 130, 130);
        }

        PrevisaoMovimento.PrevisaoDia p = previsao.prever(LocalDate.of(2026, 12, 31), c.ctx()); // quinta

        // Medido: 66 mil ÷ 30 mil = +120% (e não 66 ÷ 40 = +65%, que confundiria o crescimento com o efeito).
        assertThat(p.explicacao()).anyMatch(e -> e.contains("Véspera de Ano-Novo") && e.contains("1 ocorrência")
                && e.contains("+120%"));
        assertThat(p.vendas()).isEqualByComparingTo("88000.00");
    }

    // ------------------------------------------------------------------ feriados e vésperas

    @Test
    void feriadoEmDiaUtilUsaOMesmoDiaDaSemanaComOEfeitoAprendido() {
        Cenario c = new Cenario();
        for (String data : new String[]{"2026-09-21", "2026-10-19", "2026-10-26"}) { // segundas comuns
            c.movimento(LocalDate.parse(data), "30000", 100, 100, 100, 100, 100);
        }
        for (String data : new String[]{"2026-09-14", "2026-09-28"}) { // feriados em segundas: +20%
            c.feriado(LocalDate.parse(data));
            c.movimento(LocalDate.parse(data), "36000", 120, 120, 120, 120, 120);
        }
        c.feriado(LocalDate.of(2026, 10, 17)); // feriado num sábado: não entra na conta dos dias úteis
        c.movimento(LocalDate.of(2026, 10, 17), "50000", 170, 170, 170, 170, 170);
        c.feriado(LocalDate.of(2026, 11, 16));

        PrevisaoMovimento.PrevisaoDia p = previsao.prever(LocalDate.of(2026, 11, 16), c.ctx()); // segunda, feriado

        assertThat(p.vendas()).isEqualByComparingTo("36000.00");
        assertThat(p.explicacao()).anyMatch(e -> e.startsWith("Feriado em dia útil: média das últimas 3 segundas")
                && e.contains("+20%") && e.contains("2 feriados"));
    }

    /** Com vésperas suficientes no histórico e efeito desprezível, a véspera não recebe ajuste (e não quebra). */
    @Test
    void vesperaSemEfeitoMedidoNaoRecebeAjuste() {
        Cenario c = new Cenario();
        for (String feriado : new String[]{"2026-09-16", "2026-09-30", "2026-10-28"}) { // quartas
            c.feriado(LocalDate.parse(feriado));
        }
        for (String data : new String[]{"2026-09-15", "2026-09-29", "2026-09-22", "2026-10-13", "2026-10-20"}) {
            c.movimento(LocalDate.parse(data), "30000", 100, 100, 100, 100, 100); // vésperas e terças comuns iguais
        }

        PrevisaoMovimento.PrevisaoDia p = previsao.prever(LocalDate.of(2026, 10, 27), c.ctx()); // terça, véspera

        assertThat(p.vendas()).isEqualByComparingTo("30000.00");
        assertThat(p.explicacao()).anyMatch(e -> e.contains("Véspera de feriado: sem efeito relevante"));
    }

    // ------------------------------------------------------------------ assertividade

    @Test
    void assertividadeComparaAPrevisaoComOResultadoReal() {
        Cenario c = new Cenario();
        LocalDate primeira = LocalDate.of(2026, 9, 18);
        for (int semana = 0; semana < 4; semana++) {
            c.movimento(primeira.plusWeeks(semana), "30000", 100, 100, 100, 100, 100);
        }
        c.movimento(primeira.plusWeeks(4), "33000", 110, 110, 110, 110, 110); // vendeu 10% acima do previsto

        PrevisaoMovimento.Assertividade a = previsao.assertividade(primeira, primeira.plusWeeks(4), c.ctx());

        // A 1ª sexta não tem histórico; nas 4 seguintes os erros foram 0, 0, 0 e 3.000/33.000 = 9,09%.
        assertThat(a.dias()).isEqualTo(4);
        assertThat(a.erroMedioPercentual()).isEqualByComparingTo("2.3");
        assertThat(a.viesPercentual()).as("negativo: a previsão ficou abaixo do real").isEqualByComparingTo("-2.3");
    }

    @Test
    void semDiasComparaveisNaoHaAssertividade() {
        Cenario c = new Cenario();
        c.movimento(SEXTA, "30000", 100, 100, 100, 100, 100);
        assertThat(previsao.assertividade(SEXTA.minusDays(30), SEXTA, c.ctx())).isNull();
    }
}
