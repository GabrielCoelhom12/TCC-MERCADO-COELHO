package br.com.coelho.escalas.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Geração com otimização (Timefold) e comparação com o gerador guloso, no cenário de demonstração. */
class OtimizacaoIntegracaoTest extends IntegracaoTest {

    private static final String SEGUNDA = "2030-03-04";
    private static final String DOMINGO = "2030-03-10";

    @Test
    void otimizaAEscalaCobrindoADemandaSemErros() throws Exception {
        long escala = criar();

        JsonNode geracao = corpo(post("/api/escalas/" + escala + "/otimizar", Map.of()).andExpect(status().isOk()));

        assertThat(geracao.get("turnosCriados").asInt()).isPositive();
        assertThat(geracao.get("deficits")).isEmpty();
        assertThat(geracao.at("/matriz/turnos")).hasSize(geracao.get("turnosCriados").asInt());
        assertThat(geracao.at("/matriz/validacao/erros").asInt()).isZero();
        assertThat(geracao.at("/matriz/validacao/aprovavel").asBoolean()).isTrue();
    }

    @Test
    void comparaOsDoisAlgoritmosSemAlterarAEscala() throws Exception {
        long escala = criar();
        post("/api/escalas/" + escala + "/gerar", Map.of()).andExpect(status().isOk());
        int antes = corpo(get("/api/escalas/" + escala + "/matriz")).get("turnos").size();

        JsonNode c = corpo(get("/api/escalas/" + escala + "/comparacao").andExpect(status().isOk()));

        assertThat(c.at("/guloso/algoritmo").asText()).isEqualTo("Guloso");
        assertThat(c.at("/otimizado/algoritmo").asText()).contains("Timefold");
        for (String lado : new String[]{"guloso", "otimizado"}) {
            assertThat(c.at("/" + lado + "/turnos").asInt()).isPositive();
            assertThat(c.at("/" + lado + "/custo").decimalValue()).isPositive();
            assertThat(c.at("/" + lado + "/desvioPadraoHoras").isNumber()).isTrue();
            assertThat(c.at("/" + lado + "/milissegundos").asLong()).isNotNegative();
        }
        assertThat(c.at("/otimizado/vagasDescobertas").asInt()).isLessThanOrEqualTo(c.at("/guloso/vagasDescobertas").asInt());
        assertThat(c.at("/otimizado/erros").asLong()).isZero();
        assertThat(corpo(get("/api/escalas/" + escala + "/matriz")).get("turnos")).hasSize(antes);
    }

    @Test
    void escalaAprovadaNaoEOtimizadaESupervisorNaoOtimiza() throws Exception {
        long escala = criar();
        post("/api/escalas/" + escala + "/gerar", Map.of()).andExpect(status().isOk());
        post("/api/escalas/" + escala + "/aprovar", Map.of()).andExpect(status().isOk());
        post("/api/escalas/" + escala + "/otimizar", Map.of())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value(org.hamcrest.Matchers.containsString("Reabra")));

        post("/api/usuarios", Map.of("login", "sup.otimiza", "nome", "Supervisor", "perfil", "SUPERVISOR", "senha", "Senha@Forte1"))
                .andExpect(status().isCreated());
        entrarComo("sup.otimiza", "Senha@Forte1");
        post("/api/escalas/" + escala + "/otimizar", Map.of()).andExpect(status().isForbidden());
        get("/api/escalas/" + escala + "/comparacao").andExpect(status().isOk());
        get("/api/escalas/999999/comparacao").andExpect(status().isNotFound());
    }

    private long criar() throws Exception {
        return corpo(post("/api/escalas", Map.of("dataInicio", SEGUNDA, "dataFim", DOMINGO)).andExpect(status().isCreated()))
                .get("id").asLong();
    }
}
