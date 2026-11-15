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
                .andExpect(jsonPath("$.ausencia.motivo").value("FERIAS"))
                .andExpect(jsonPath("$.ausencia.funcionarioId").value(funcionario))
                .andExpect(jsonPath("$.coberturas", empty())));
        long id = f.at("/ausencia/id").asLong();
        get("/api/ferias").andExpect(jsonPath("$[*].id", hasItem((int) id)));

        // Editar o próprio período não conta como sobreposição.
        put("/api/ferias/" + id, ferias(funcionario, SEGUNDA, "2031-03-21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ausencia.fim").value("2031-03-21"));

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
    void previaMostraDiasRetornoEQuemCobreCadaTurnoJaEscalado() throws Exception {
        long escala = criarEscalaGerada();
        JsonNode matriz = corpo(get("/api/escalas/" + escala + "/matriz"));
        long funcionario = matriz.at("/turnos/0/funcionarioId").asLong();
        int turnosDele = turnosDe(matriz, funcionario);

        JsonNode previa = corpo(get("/api/ferias/analise?funcionarioId=" + funcionario + "&inicio=" + SEGUNDA + "&fim=2031-03-16")
                .andExpect(status().isOk()));

        assertThat(previa.get("dias").asInt()).isEqualTo(14);
        assertThat(previa.get("retorno").asText()).isEqualTo("2031-03-17");
        assertThat(previa.get("valida").asBoolean()).isTrue();
        assertThat(previa.get("coberturas")).hasSize(turnosDele);
        for (JsonNode c : previa.get("coberturas")) {
            assertThat(c.get("substitutoId").asLong()).isNotEqualTo(funcionario);
            assertThat(c.get("substitutoNome").asText()).isNotBlank();
        }
        // A prévia não altera a escala.
        assertThat(turnosDe(corpo(get("/api/escalas/" + escala + "/matriz")), funcionario)).isEqualTo(turnosDele);
    }

    @Test
    void registrarRepassaOsTurnosMesmoEmEscalaAprovada() throws Exception {
        long escala = criarEscalaGerada();
        post("/api/escalas/" + escala + "/aprovar", Map.of()).andExpect(status().isOk());
        JsonNode antes = corpo(get("/api/escalas/" + escala + "/matriz"));
        long funcionario = antes.at("/turnos/0/funcionarioId").asLong();
        int turnosDele = turnosDe(antes, funcionario);

        JsonNode registro = corpo(post("/api/ferias", ferias(funcionario, SEGUNDA, "2031-03-16")).andExpect(status().isCreated()));
        assertThat(registro.get("coberturas")).hasSize(turnosDele);

        JsonNode depois = corpo(get("/api/escalas/" + escala + "/matriz"));
        assertThat(turnosDe(depois, funcionario)).isZero();
        assertThat(depois.get("turnos")).hasSize(antes.get("turnos").size());
        assertThat(depois.at("/escala/status").asText()).isEqualTo("APROVADA");
        assertThat(depois.at("/escala/observacao").asText()).contains("(férias)");
        assertThat(depois.at("/validacao/violacoes").findValuesAsText("regra")).doesNotContain("AUSENCIA");
    }

    // ------------------------------------------------------------------ auxiliares

    private long criarEscalaGerada() throws Exception {
        long escala = corpo(post("/api/escalas", Map.of("dataInicio", SEGUNDA, "dataFim", "2031-03-09"))
                .andExpect(status().isCreated())).get("id").asLong();
        post("/api/escalas/" + escala + "/gerar", Map.of()).andExpect(status().isOk());
        return escala;
    }

    private static int turnosDe(JsonNode matriz, long funcionario) {
        int n = 0;
        for (JsonNode t : matriz.get("turnos")) {
            n += t.get("funcionarioId").asLong() == funcionario ? 1 : 0;
        }
        return n;
    }

    private long primeiroFuncionario() throws Exception {
        return corpo(get("/api/funcionarios")).get(0).get("id").asLong();
    }

    private static Map<String, Object> ferias(long funcionario, String inicio, String fim) {
        return new HashMap<>(Map.of("funcionarioId", funcionario, "inicio", inicio, "fim", fim));
    }
}
