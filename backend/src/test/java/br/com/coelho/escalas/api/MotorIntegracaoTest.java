package br.com.coelho.escalas.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fluxo 3 — motor financeiro: capacidade diária, simulador de custos e painel consolidado. */
class MotorIntegracaoTest extends IntegracaoTest {

    @Test
    void calculaCapacidadeDeCadaDia() throws Exception {
        get("/api/motor/capacidade?inicio=2030-03-04&fim=2030-03-10")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(7)))
                .andExpect(jsonPath("$[0].lMin", greaterThan(0)))
                .andExpect(jsonPath("$[0].tetoFolha", greaterThan(0.0)));
    }

    @Test
    void recusaPeriodoDeCapacidadeInvalido() throws Exception {
        get("/api/motor/capacidade?inicio=2030-03-10&fim=2030-03-04").andExpect(status().isUnprocessableEntity());
        get("/api/motor/capacidade?inicio=2030-01-01&fim=2030-12-31").andExpect(status().isUnprocessableEntity());
    }

    @Test
    void simulaQuadroProposto() throws Exception {
        JsonNode setores = corpo(get("/api/setores"));
        long caixa = setores.get(0).get("id").asLong();

        post("/api/motor/simular", Map.of("data", "2030-03-08", "proposta", Map.of(String.valueOf(caixa), 30)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.veredito").value("SUPERDIMENSIONADA"))
                .andExpect(jsonPath("$.setores", not(empty())));

        post("/api/motor/simular", Map.of("data", "2030-03-08"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.veredito").value("SUBDIMENSIONADA"));
    }

    @Test
    void simulacaoExigeData() throws Exception {
        post("/api/motor/simular", new HashMap<>()).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void painelConsolidaOPeriodo() throws Exception {
        get("/api/motor/painel?inicio=2030-03-04&fim=2030-03-10")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dias", hasSize(7)));
        get("/api/motor/painel?inicio=2030-03-10&fim=2030-03-04").andExpect(status().isUnprocessableEntity());
    }
}
