package br.com.coelho.escalas.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fluxo 2 — ciclo de vida da escala: criação, geração automática, edição na matriz, validação e aprovação. */
class EscalaIntegracaoTest extends IntegracaoTest {

    /** Segunda-feira longe do cenário de demonstração, para não sobrepor a escala já existente. */
    private static final String SEGUNDA = "2030-03-04";
    private static final String DOMINGO = "2030-03-10";

    @Test
    void criaComNomePadraoEAtualiza() throws Exception {
        long id = criar(null);
        get("/api/escalas").andExpect(jsonPath("$[*].nome", hasItem("Escala 04/03 a 10/03")));

        put("/api/escalas/" + id, Map.of("nome", " Semana de março ", "observacao", "Revisada"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Semana de março"))
                .andExpect(jsonPath("$.observacao").value("Revisada"));
    }

    @Test
    void recusaPeriodosInvalidos() throws Exception {
        post("/api/escalas", Map.of("nome", "X")).andExpect(status().isUnprocessableEntity());
        post("/api/escalas", periodo("2030-03-10", "2030-03-04"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem", containsString("posterior")));
        post("/api/escalas", periodo("2030-01-01", "2030-06-01"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem", containsString("no máximo")));

        criar("Base");
        post("/api/escalas", periodo("2030-03-08", "2030-03-14"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem", containsString("Já existe")));
    }

    @Test
    void geraValidaAprovaEReabre() throws Exception {
        long id = criar("Fluxo completo");

        JsonNode geracao = corpo(post("/api/escalas/" + id + "/gerar", Map.of()).andExpect(status().isOk()));
        assertThat(geracao.get("turnosCriados").asInt()).isPositive();
        assertThat(geracao.at("/matriz/turnos").size()).isEqualTo(geracao.get("turnosCriados").asInt());

        get("/api/escalas/" + id + "/matriz")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.funcionarios", not(empty())))
                .andExpect(jsonPath("$.modelos", not(empty())))
                .andExpect(jsonPath("$.validacao.dias", hasSize(7)));

        // A escala gerada pelo motor cobre a demanda mínima sem violar regras, logo é aprovável.
        get("/api/escalas/" + id + "/validacao")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aprovavel").value(true));

        post("/api/escalas/" + id + "/aprovar", Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APROVADA"))
                .andExpect(jsonPath("$.aprovadaPor").value("gestor"));

        // Escala aprovada fica bloqueada para edição.
        post("/api/escalas/" + id + "/gerar", Map.of()).andExpect(status().isUnprocessableEntity());
        delete("/api/escalas/" + id).andExpect(status().isUnprocessableEntity());

        post("/api/escalas/" + id + "/reabrir", Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RASCUNHO"))
                .andExpect(jsonPath("$.aprovadaPor").doesNotExist());

        post("/api/escalas/" + id + "/limpar", Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.turnos", empty()));
        delete("/api/escalas/" + id).andExpect(status().isNoContent());
        get("/api/escalas/" + id + "/matriz").andExpect(status().isNotFound());
    }

    @Test
    void escalaVaziaNaoPodeSerAprovada() throws Exception {
        long id = criar("Vazia");
        post("/api/escalas/" + id + "/aprovar", Map.of())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem", containsString("não pode ser aprovada")))
                .andExpect(jsonPath("$.violacoes[*].regra", hasItem("SETOR_ABAIXO_MINIMO")));
    }

    @Test
    void editaCelulasDaMatriz() throws Exception {
        long id = criar("Edição manual");
        long funcionario = idDoPrimeiro("/api/funcionarios");
        long modelo = idDoPrimeiro("/api/turnos-modelo");
        modoFinanceiro("ALERTAR");

        // Turno a partir de um modelo.
        put("/api/escalas/" + id + "/celula", celula(funcionario, SEGUNDA, modelo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.turnos", hasSize(1)))
                .andExpect(jsonPath("$.turnos[0].custo", greaterThan(0.0)));

        // Turno personalizado substitui o anterior.
        Map<String, Object> pers = new HashMap<>(Map.of("funcionarioId", funcionario, "data", SEGUNDA,
                "horaInicio", "09:00", "horaFim", "15:00"));
        put("/api/escalas/" + id + "/celula", pers)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.turnos", hasSize(1)))
                .andExpect(jsonPath("$.turnos[0].rotulo").value("Pers."))
                .andExpect(jsonPath("$.turnos[0].intervaloMinutos").value(0));

        // Sem modelo e sem horário = folga.
        put("/api/escalas/" + id + "/celula", Map.of("funcionarioId", funcionario, "data", SEGUNDA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.turnos", empty()));
    }

    @Test
    void recusaCelulasInvalidas() throws Exception {
        long id = criar("Células inválidas");
        long funcionario = idDoPrimeiro("/api/funcionarios");

        put("/api/escalas/" + id + "/celula", celula(funcionario, "2030-04-01", null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem", containsString("fora do período")));
        put("/api/escalas/" + id + "/celula", celula(999999L, SEGUNDA, null)).andExpect(status().isNotFound());
        put("/api/escalas/" + id + "/celula", celula(funcionario, SEGUNDA, 999999L)).andExpect(status().isNotFound());
        put("/api/escalas/" + id + "/celula", Map.of("funcionarioId", funcionario, "data", SEGUNDA,
                "horaInicio", "09:00", "horaFim", "09:00"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void motorBloqueiaAlocacaoQueEstouraOLimite() throws Exception {
        long id = criar("Bloqueio financeiro");
        post("/api/escalas/" + id + "/gerar", Map.of()).andExpect(status().isOk());
        modoFinanceiro("BLOQUEAR");
        put("/api/projecoes/padrao", Map.of("DOMINGO", 1)).andExpect(status().isOk());

        // O gerador aloca exatamente L_min no domingo; com receita mínima, qualquer operador extra estoura L_max/teto.
        JsonNode matriz = corpo(get("/api/escalas/" + id + "/matriz"));
        long modeloDomingo = idDoModelo(matriz, "DOM");
        long folguista = funcionarioSemTurnoNo(matriz, DOMINGO);

        put("/api/escalas/" + id + "/celula", celula(folguista, DOMINGO, modeloDomingo))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem", containsString("Alocação bloqueada")));
    }

    @Test
    void escalaInexistenteDevolve404() throws Exception {
        get("/api/escalas/999999/matriz").andExpect(status().isNotFound());
        put("/api/escalas/999999", Map.of("nome", "X")).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ auxiliares

    private long criar(String nome) throws Exception {
        Map<String, Object> req = periodo(SEGUNDA, DOMINGO);
        if (nome != null) {
            req.put("nome", nome);
        }
        return corpo(post("/api/escalas", req).andExpect(status().isCreated())).get("id").asLong();
    }

    private static Map<String, Object> periodo(String inicio, String fim) {
        return new HashMap<>(Map.of("dataInicio", inicio, "dataFim", fim));
    }

    private static Map<String, Object> celula(long funcionario, String data, Long modelo) {
        Map<String, Object> m = new HashMap<>(Map.of("funcionarioId", funcionario, "data", data));
        m.put("turnoModeloId", modelo);
        return m;
    }

    private void modoFinanceiro(String modo) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> p = new HashMap<>(json.treeToValue(corpo(get("/api/parametros")), Map.class));
        p.put("modoFinanceiro", modo);
        put("/api/parametros", p).andExpect(status().isOk());
    }

    private long idDoPrimeiro(String url) throws Exception {
        return corpo(get(url)).get(0).get("id").asLong();
    }

    private static long idDoModelo(JsonNode matriz, String sigla) {
        for (JsonNode m : matriz.get("modelos")) {
            if (m.get("sigla").asText().equals(sigla)) {
                return m.get("id").asLong();
            }
        }
        throw new AssertionError("Modelo " + sigla + " não encontrado");
    }

    private static long funcionarioSemTurnoNo(JsonNode matriz, String data) {
        for (JsonNode f : matriz.get("funcionarios")) {
            boolean escalado = false;
            for (JsonNode t : matriz.get("turnos")) {
                escalado |= t.get("funcionarioId").asLong() == f.get("id").asLong() && t.get("data").asText().equals(data);
            }
            boolean disponivel = f.get("diasDisponiveis").toString().contains("SUNDAY");
            if (!escalado && disponivel && f.get("ativo").asBoolean()) {
                return f.get("id").asLong();
            }
        }
        throw new AssertionError("Nenhum colaborador livre em " + data);
    }
}
