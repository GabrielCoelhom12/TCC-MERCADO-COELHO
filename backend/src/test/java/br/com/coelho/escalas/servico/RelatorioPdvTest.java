package br.com.coelho.escalas.servico;

import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Leitura do relatório de vendas diárias do PDV (valores fictícios). */
class RelatorioPdvTest {

    private static final String CABECALHO =
            "Data;Venda Bruta;Venda Cancelada;Valor Desconto;Venda Líquida;Qtde. Cupom;Cupom Médio;Qtde. Cancelado\n";

    @Test
    void leOsDiasComVendaLiquidaEClientesSemOsCuponsCancelados() {
        String csv = (char) 0xFEFF + CABECALHO // BOM de arquivo UTF-8 salvo pelo Excel
                + "03/06/2024 - SEG;1100.0;80.0;20.0;1000.0;50.0;20.0;2.0\n"
                + "04/06/2024 - TER;1500.5;0.0;0.0;1500.5;60.0;25.0;0.0\n"
                + ";;;;;;;\n"
                + ";2600.5;80.0;20.0;2500.5;110.0;45.0;2.0\n"; // totais

        var leitura = RelatorioPdv.ler(csv.getBytes(StandardCharsets.UTF_8));

        assertThat(leitura.avisos()).isEmpty();
        assertThat(leitura.dias()).hasSize(2);
        assertThat(leitura.dias().get(0).data()).isEqualTo(LocalDate.of(2024, 6, 3));
        assertThat(leitura.dias().get(0).vendaLiquida()).isEqualByComparingTo("1000.00");
        assertThat(leitura.dias().get(0).clientes()).isEqualTo(48);
        assertThat(leitura.dias().get(1).vendaLiquida()).isEqualByComparingTo("1500.50");
    }

    @Test
    void aceitaWindows1252VirgulaDecimalEColunasEmOutraOrdem() {
        String csv = "Qtde. Cupom;Data;Venda Líquida\n" + "10;05/06/2024;1.234,56\n";

        var leitura = RelatorioPdv.ler(csv.getBytes(Charset.forName("windows-1252")));

        assertThat(leitura.dias()).singleElement().satisfies(d -> {
            assertThat(d.data()).isEqualTo(LocalDate.of(2024, 6, 5));
            assertThat(d.vendaLiquida()).isEqualByComparingTo("1234.56");
            assertThat(d.clientes()).isEqualTo(10);
        });
    }

    @Test
    void linhaInvalidaViraAvisoSemDerrubarAsOutras() {
        String csv = CABECALHO + "32/13/2024 - XXX;1;0;0;1;1;1;0\n" + "06/06/2024 - QUI;100;0;0;100;5;20;0\n";

        var leitura = RelatorioPdv.ler(csv.getBytes(StandardCharsets.UTF_8));

        assertThat(leitura.dias()).hasSize(1);
        assertThat(leitura.avisos()).singleElement().asString().contains("Linha 2");
    }

    @Test
    void arquivoSemAsColunasDoRelatorioERecusado() {
        assertThatThrownBy(() -> RelatorioPdv.ler("nome;valor\nx;1\n".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("Venda Líquida");
    }
}
