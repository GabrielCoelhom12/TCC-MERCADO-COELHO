package br.com.coelho.escalas.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fechamento do dia, faixas de horário e previsão de movimento. */
class MovimentoIntegracaoTest extends IntegracaoTest {

    private static Map<String, Object> faixa(String inicio, String fim, int clientes, double vendas) {
        return Map.of("inicio", inicio, "fim", fim, "clientes", clientes, "vendas", vendas);
    }

    private static Map<String, Object> fechamento(int... clientes) {
        String[][] horas = {{"07:00", "10:00"}, {"10:00", "13:00"}, {"13:00", "16:00"}, {"16:00", "19:00"}, {"19:00", "22:00"}};
        List<Map<String, Object>> faixas = new java.util.ArrayList<>();
        for (int i = 0; i < clientes.length; i++) {
            faixas.add(faixa(horas[i][0], horas[i][1], clientes[i], clientes[i] * 60.0));
        }
        return Map.of("faixas", faixas, "observacao", "Fechamento de teste");
    }

    // ------------------------------------------------------------------ faixas

    @Test
    void temFaixasPadrao() throws Exception {
        get("/api/movimento/faixas")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].inicio").value("07:00:00"))
                .andExpect(jsonPath("$[4].fim").value("22:00:00"));
    }

    @Test
    void substituiAsFaixasValidando() throws Exception {
        put("/api/movimento/faixas", List.of(Map.of("inicio", "08:00", "fim", "12:00"), Map.of("inicio", "12:00", "fim", "20:00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
        put("/api/movimento/faixas", List.of(Map.of("inicio", "08:00", "fim", "12:00"), Map.of("inicio", "11:00", "fim", "20:00")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem", containsString("sobrepostas")));
        put("/api/movimento/faixas", List.of(Map.of("inicio", "12:00", "fim", "08:00")))
                .andExpect(status().isUnprocessableEntity());
        put("/api/movimento/faixas", List.of()).andExpect(status().isUnprocessableEntity());
    }

    // ------------------------------------------------------------------ fechamento

    @Test
    void lancaECorrigeOFechamentoDoDia() throws Exception {
        put("/api/movimento/fechamentos/2025-03-07", fechamento(50, 100, 100, 200, 50))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("2025-03-07"))
                .andExpect(jsonPath("$.clientes").value(500))
                .andExpect(jsonPath("$.vendas").value(30000.0))
                .andExpect(jsonPath("$.registradoPor").value("gestor"))
                .andExpect(jsonPath("$.faixas", hasSize(5)));

        put("/api/movimento/fechamentos/2025-03-07", fechamento(60, 100, 100, 200, 50))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientes").value(510));

        get("/api/movimento/fechamentos/2025-03-07").andExpect(jsonPath("$.clientes").value(510));
        get("/api/movimento/fechamentos?inicio=2025-03-01&fim=2025-03-31")
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].observacao").value("Fechamento de teste"));
    }

    @Test
    void recusaFechamentoInvalido() throws Exception {
        put("/api/movimento/fechamentos/2099-01-01", fechamento(10))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem", containsString("futuro")));
        put("/api/movimento/fechamentos/2025-03-07", Map.of("faixas", List.of(faixa("07:00", "10:00", -1, 10))))
                .andExpect(status().isBadRequest());
        put("/api/movimento/fechamentos/2025-03-07", Map.of("faixas", List.of()))
                .andExpect(status().isUnprocessableEntity());
        put("/api/movimento/fechamentos/2025-03-07",
                Map.of("faixas", List.of(faixa("07:00", "12:00", 1, 1), faixa("11:00", "13:00", 1, 1))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void removeFechamento() throws Exception {
        put("/api/movimento/fechamentos/2025-03-07", fechamento(10, 10)).andExpect(status().isOk());
        delete("/api/movimento/fechamentos/2025-03-07").andExpect(status().isNoContent());
        get("/api/movimento/fechamentos/2025-03-07").andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ previsão

    @Test
    void preveOMovimentoEAsPessoasNecessariasAPartirDoHistorico() throws Exception {
        JsonNode caixa = null;
        for (JsonNode s : corpo(get("/api/setores"))) {
            if (s.get("nome").asText().equals("Frente de Caixa")) {
                caixa = s;
            }
        }
        assertThat(caixa).isNotNull();
        put("/api/setores/" + caixa.get("id").asLong(), Map.of("nome", "Frente de Caixa", "clientesPorColaboradorHora", 30))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientesPorColaboradorHora").value(30));

        put("/api/movimento/fechamentos/2025-03-14", fechamento(90, 90, 180, 360, 270)).andExpect(status().isOk());
        put("/api/movimento/fechamentos/2025-03-07", fechamento(90, 90, 180, 360, 270)).andExpect(status().isOk());

        JsonNode dia = corpo(get("/api/movimento/previsao?inicio=2025-03-21&fim=2025-03-21").andExpect(status().isOk())).get(0);

        assertThat(dia.get("origemReceita").asText()).isEqualTo("PREVISAO");
        assertThat(dia.at("/previsao/clientes").asInt()).isEqualTo(990);
        assertThat(dia.at("/previsao/pico/inicio").asText()).isEqualTo("16:00:00");
        assertThat(dia.at("/previsao/explicacao").toString()).contains("sextas");
        JsonNode setorCaixa = null;
        for (JsonNode s : dia.get("setores")) {
            if (s.get("nome").asText().equals("Frente de Caixa")) {
                setorCaixa = s;
            }
        }
        assertThat(setorCaixa.at("/faixas/3/pessoas").asInt()).isEqualTo(4); // 360 clientes / (30 × 3h)
        assertThat(setorCaixa.get("necessarios").asInt()).isGreaterThanOrEqualTo(setorCaixa.get("minimoManual").asInt());
        assertThat(setorCaixa.get("turnosSugeridos").size()).isPositive();
    }

    @Test
    void semHistoricoAPrevisaoUsaAConfiguracao() throws Exception {
        get("/api/movimento/previsao?inicio=2024-01-08&fim=2024-01-09")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].origemReceita").value("PADRAO"))
                .andExpect(jsonPath("$[0].previsao").doesNotExist());
    }

    @Test
    void recusaPeriodoDePrevisaoInvalido() throws Exception {
        get("/api/movimento/previsao?inicio=2025-03-10&fim=2025-03-01").andExpect(status().isUnprocessableEntity());
        get("/api/movimento/previsao?inicio=2025-01-01&fim=2025-03-01").andExpect(status().isUnprocessableEntity());
    }

    @Test
    void setorRecusaCapacidadeInvalida() throws Exception {
        long id = corpo(get("/api/setores")).get(0).get("id").asLong();
        put("/api/setores/" + id, Map.of("nome", "X", "clientesPorColaboradorHora", 0)).andExpect(status().isBadRequest());
    }
}
