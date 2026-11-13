package br.com.coelho.escalas.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Operação de férias: registro com as regras da CLT, edição, análise de impacto e remoção. */
class FeriasIntegracaoTest extends IntegracaoTest {

    /** Segunda-feira longe do cenário de demonstração. */
    private static final String SEGUNDA = "2031-03-03";

    @Test
    void registraListaEditaERemove() throws Exception {
        long funcionario = primeiroFuncionario();

        JsonNode f = corpo(post("/api/ferias", ferias(funcionario, SEGUNDA, "2031-03-16"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.motivo").value("FERIAS"))
                .andExpect(jsonPath("$.funcionarioId").value(funcionario)));
        long id = f.get("id").asLong();
        get("/api/ferias").andExpect(jsonPath("$[*].id", hasItem((int) id)));

        // Editar o próprio período não conta como sobreposição.
        put("/api/ferias/" + id, ferias(funcionario, SEGUNDA, "2031-03-21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fim").value("2031-03-21"));

        delete("/api/ferias/" + id).andExpect(status().isNoContent());
        delete("/api/ferias/" + id).andExpect(status().isNotFound());
        get("/api/ferias").andExpect(jsonPath("$[*].id", not(hasItem((int) id))));
    }

    @Test
    void recusaPeriodosForaDaCltESobrepostos() throws Exception {
        long funcionario = primeiroFuncionario();

        post("/api/ferias", ferias(funcionario, "2031-03-07", "2031-03-20")) // sexta-feira
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem", containsString("art. 134, §3º")));
        post("/api/ferias", ferias(funcionario, SEGUNDA, "2031-03-05"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem", containsString("ao menos 5 dias")));

        post("/api/ferias", ferias(funcionario, SEGUNDA, "2031-03-16")).andExpect(status().isCreated());
        post("/api/ferias", ferias(funcionario, "2031-03-10", "2031-03-23"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem", containsString("Já existe férias de 03/03 a 16/03")));

        post("/api/ferias", ferias(999999L, SEGUNDA, "2031-03-16")).andExpect(status().isNotFound());
        put("/api/ferias/999999", ferias(funcionario, SEGUNDA, "2031-03-16")).andExpect(status().isNotFound());
    }

    @Test
    void ausenciaDoTipoFeriasNoCadastroDeColaboradoresSegueAsMesmasRegras() throws Exception {
        long funcionario = primeiroFuncionario();
        post("/api/funcionarios/" + funcionario + "/ausencias", Map.of("inicio", "2031-03-07", "fim", "2031-03-20", "motivo", "FERIAS"))
                .andExpect(status().isUnprocessableEntity());

        // Outros motivos não seguem as regras de férias, e não aparecem na operação de férias.
        long atestado = corpo(post("/api/funcionarios/" + funcionario + "/ausencias",
                Map.of("inicio", "2031-03-07", "fim", "2031-03-08", "motivo", "ATESTADO"))
                .andExpect(status().isCreated())).get("id").asLong();
        get("/api/ferias").andExpect(jsonPath("$[*].id", not(hasItem((int) atestado))));
        delete("/api/ferias/" + atestado).andExpect(status().isNotFound());
    }

    @Test
    void analiseMostraDiasRetornoETurnosJaEscalados() throws Exception {
        long escala = corpo(post("/api/escalas", Map.of("dataInicio", SEGUNDA, "dataFim", "2031-03-09"))
                .andExpect(status().isCreated())).get("id").asLong();
        JsonNode turno = corpo(post("/api/escalas/" + escala + "/gerar", Map.of())).at("/matriz/turnos/0");
        long funcionario = turno.get("funcionarioId").asLong();

        JsonNode analise = corpo(get("/api/ferias/analise?funcionarioId=" + funcionario + "&inicio=" + SEGUNDA + "&fim=2031-03-16")
                .andExpect(status().isOk()));

        assertThat(analise.get("dias").asInt()).isEqualTo(14);
        assertThat(analise.get("retorno").asText()).isEqualTo("2031-03-17");
        assertThat(analise.get("valida").asBoolean()).isTrue();
        assertThat(analise.get("alertas").get(0).asText()).contains("já escalado");
    }

    // ------------------------------------------------------------------ auxiliares

    private long primeiroFuncionario() throws Exception {
        return corpo(get("/api/funcionarios")).get(0).get("id").asLong();
    }

    private static Map<String, Object> ferias(long funcionario, String inicio, String fim) {
        return new HashMap<>(Map.of("funcionarioId", funcionario, "inicio", inicio, "fim", fim));
    }
}
