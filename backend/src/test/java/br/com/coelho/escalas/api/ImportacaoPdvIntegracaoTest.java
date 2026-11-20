package br.com.coelho.escalas.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Importação do relatório de vendas do PDV como fechamentos do dia (valores fictícios). */
class ImportacaoPdvIntegracaoTest extends IntegracaoTest {

    private static final String URL = "/api/movimento/importar";
    private static final String CABECALHO =
            "Data;Venda Bruta;Venda Cancelada;Valor Desconto;Venda Líquida;Qtde. Cupom;Cupom Médio;Qtde. Cancelado\n";

    private ResultActions importar(String linhas) throws Exception {
        MockMultipartFile arquivo = new MockMultipartFile("arquivo", "vendas.csv", "text/csv",
                (CABECALHO + linhas).getBytes(StandardCharsets.UTF_8));
        return enviarArquivo(HttpMethod.POST, URL, arquivo, Map.of());
    }

    @Test
    void lancaOFechamentoDeCadaDiaDistribuindoPelasFaixas() throws Exception {
        JsonNode r = corpo(importar(
                "03/06/2024 - SEG;31000.0;800.0;200.0;30000.0;602.0;50.0;2.0\n"
                        + "04/06/2024 - TER;20.0;20.0;0.0;0.0;3.0;0.0;3.0\n" // teste do PDV: sem venda
                        + ";31020.0;820.0;200.0;30000.0;605.0;50.0;5.0\n")
                .andExpect(status().isOk()));

        assertThat(r.get("criados").asInt()).isEqualTo(1);
        assertThat(r.get("ignorados").asInt()).isEqualTo(1);
        assertThat(r.get("avisos").toString()).contains("sem venda");

        JsonNode f = corpo(get("/api/movimento/fechamentos/2024-06-03").andExpect(status().isOk()));
        assertThat(f.get("vendas").decimalValue()).isEqualByComparingTo("30000.00");
        assertThat(f.get("clientes").asInt()).isEqualTo(600); // cupons válidos
        assertThat(f.get("faixas")).hasSize(5);
        assertThat(f.get("observacao").asText()).isEqualTo("Importado do relatório do PDV");
        // Dia útil sem histórico lançado à mão: perfil padrão, com o pico no fim da tarde (16h–19h).
        int pico = 0;
        for (int i = 1; i < 5; i++) {
            if (f.at("/faixas/" + i + "/clientes").asInt() > f.at("/faixas/" + pico + "/clientes").asInt()) {
                pico = i;
            }
        }
        assertThat(f.at("/faixas/" + pico + "/inicio").asText()).startsWith("16:00");
    }

    @Test
    void importarDeNovoAtualizaSemDuplicarEMantemADivisaoLancadaAMao() throws Exception {
        put("/api/movimento/fechamentos/2024-06-05", Map.of("faixas", List.of(
                Map.of("inicio", "07:00", "fim", "10:00", "clientes", 100, "vendas", 5000),
                Map.of("inicio", "10:00", "fim", "13:00", "clientes", 300, "vendas", 15000)),
                "observacao", "Chuva forte")).andExpect(status().isOk());

        JsonNode r = corpo(importar("05/06/2024 - QUA;41000;0;1000;40000;800;50;0\n").andExpect(status().isOk()));

        assertThat(r.get("atualizados").asInt()).isEqualTo(1);
        assertThat(r.get("criados").asInt()).isZero();
        JsonNode f = corpo(get("/api/movimento/fechamentos/2024-06-05"));
        assertThat(f.get("vendas").decimalValue()).isEqualByComparingTo("40000.00");
        assertThat(f.get("observacao").asText()).as("a observação do gestor fica").isEqualTo("Chuva forte");
        // A divisão do gestor (25% de 7h às 10h, 75% de 10h às 13h) é mantida com os totais do PDV.
        assertThat(f.at("/faixas/0/clientes").asInt()).isEqualTo(200);
        assertThat(f.at("/faixas/1/clientes").asInt()).isEqualTo(600);

        importar("05/06/2024 - QUA;41000;0;1000;40000;800;50;0\n").andExpect(jsonPath("$.atualizados").value(1));
    }

    @Test
    void ignoraDiasFuturosERecusaArquivoInvalido() throws Exception {
        String amanha = LocalDate.now().plusDays(1).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        importar(amanha + ";100;0;0;100;5;20;0\n")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value("Nenhum dia com venda foi encontrado no arquivo."));

        MockMultipartFile outro = new MockMultipartFile("arquivo", "x.csv", "text/csv", "a;b\n1;2\n".getBytes(StandardCharsets.UTF_8));
        enviarArquivo(HttpMethod.POST, URL, outro, Map.of()).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void supervisorNaoImporta() throws Exception {
        post("/api/usuarios", Map.of("login", "sup.pdv", "nome", "Supervisor", "perfil", "SUPERVISOR", "senha", "Senha@Forte1"))
                .andExpect(status().isCreated());
        entrarComo("sup.pdv", "Senha@Forte1");

        importar("03/06/2024 - SEG;100;0;0;100;5;20;0\n").andExpect(status().isForbidden());
    }
}
