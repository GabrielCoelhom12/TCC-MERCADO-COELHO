package br.com.coelho.escalas.api;

import br.com.coelho.escalas.servico.GeracaoAutomaticaService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Rascunho automático das próximas semanas a partir da previsão de movimento. */
class GeracaoAutomaticaIntegracaoTest extends IntegracaoTest {

    /** Quarta-feira, 01/01/2031: a "próxima semana" é de 06 a 12/01/2031. */
    private static final LocalDate HOJE = LocalDate.of(2031, 1, 1);

    @Autowired
    private GeracaoAutomaticaService geracao;

    @Test
    void criaORascunhoDaProximaSemanaJaGerado() throws Exception {
        assertThat(geracao.executar(HOJE)).hasSize(1);

        JsonNode escala = escalaQueComecaEm("2031-01-06");
        assertThat(escala).isNotNull();
        assertThat(escala.get("dataFim").asText()).isEqualTo("2031-01-12");
        assertThat(escala.get("status").asText()).isEqualTo("RASCUNHO");
        assertThat(escala.get("geradaAutomaticamente").asBoolean()).isTrue();
        assertThat(corpo(get("/api/escalas/" + escala.get("id").asLong() + "/matriz")).get("turnos").size()).isPositive();
    }

    @Test
    void naoDuplicaNemMexeEmEscalaExistente() throws Exception {
        post("/api/escalas", Map.of("nome", "Feita pelo gestor", "dataInicio", "2031-01-06", "dataFim", "2031-01-12"))
                .andExpect(status().isCreated());

        assertThat(geracao.executar(HOJE)).isEmpty();
        assertThat(escalaQueComecaEm("2031-01-06").get("nome").asText()).isEqualTo("Feita pelo gestor");
    }

    @Test
    void geraAsSemanasDeAntecedenciaConfiguradas() throws Exception {
        parametros(Map.of("semanasAntecedencia", 2));
        assertThat(geracao.executar(HOJE)).hasSize(2);
        assertThat(escalaQueComecaEm("2031-01-13")).isNotNull();
        assertThat(geracao.executar(HOJE)).isEmpty();
    }

    @Test
    void desligadaNaoGeraNada() throws Exception {
        parametros(Map.of("geracaoAutomatica", false));
        assertThat(geracao.executar(HOJE)).isEmpty();
        assertThat(escalaQueComecaEm("2031-01-06")).isNull();
    }

    @Test
    void parametrosValidamAAntecedencia() throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> p = new HashMap<>(json.treeToValue(corpo(get("/api/parametros")), Map.class));
        p.put("semanasAntecedencia", 9);
        put("/api/parametros", p).andExpect(status().isUnprocessableEntity());
    }

    // ------------------------------------------------------------------ auxiliares

    private void parametros(Map<String, Object> mudancas) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> p = new HashMap<>(json.treeToValue(corpo(get("/api/parametros")), Map.class));
        p.putAll(mudancas);
        put("/api/parametros", p).andExpect(status().isOk());
    }

    private JsonNode escalaQueComecaEm(String data) throws Exception {
        for (JsonNode e : corpo(get("/api/escalas"))) {
            if (e.get("dataInicio").asText().equals(data)) {
                return e;
            }
        }
        return null;
    }
}
