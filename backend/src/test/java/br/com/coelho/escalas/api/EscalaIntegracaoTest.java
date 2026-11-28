package br.com.coelho.escalas.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
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
    void sugereSubstitutosETrocaOColaboradorDoTurno() throws Exception {
        long id = criar("Troca de colaborador");
        modoFinanceiro("ALERTAR");
        JsonNode matriz = corpo(post("/api/escalas/" + id + "/gerar", Map.of()).andExpect(status().isOk())).get("matriz");
        JsonNode turno = matriz.get("turnos").get(0);
        long turnoId = turno.get("id").asLong();
        long titular = turno.get("funcionarioId").asLong();

        JsonNode sugestao = corpo(get("/api/escalas/" + id + "/turnos/" + turnoId + "/substitutos").andExpect(status().isOk()));
        assertThat(sugestao.at("/titular/funcionarioId").asLong()).isEqualTo(titular);
        assertThat(sugestao.at("/titular/horasSemana").decimalValue()).isPositive();
        assertThat(sugestao.at("/titular/limiteSemanal").decimalValue()).isEqualByComparingTo("44");
        JsonNode primeiro = sugestao.get("candidatos").get(0);
        assertThat(primeiro.get("apto").asBoolean()).isTrue();
        long substituto = primeiro.get("funcionarioId").asLong();

        JsonNode depois = corpo(post("/api/escalas/" + id + "/turnos/" + turnoId + "/substituir", Map.of("funcionarioId", substituto))
                .andExpect(status().isOk()));
        JsonNode trocado = turnoPorId(depois, turnoId);
        assertThat(trocado.get("funcionarioId").asLong()).isEqualTo(substituto);
        assertThat(trocado.get("horaInicio").asText()).isEqualTo(turno.get("horaInicio").asText());
        assertThat(trocado.get("data").asText()).isEqualTo(turno.get("data").asText());
    }

    @Test
    void recusaSubstitutoInvalidoETurnoDeOutraEscala() throws Exception {
        long id = criar("Troca recusada");
        modoFinanceiro("ALERTAR");
        JsonNode matriz = corpo(post("/api/escalas/" + id + "/gerar", Map.of()).andExpect(status().isOk())).get("matriz");
        JsonNode turno = matriz.get("turnos").get(0);
        long turnoId = turno.get("id").asLong();
        String substituir = "/api/escalas/" + id + "/turnos/" + turnoId + "/substituir";

        // O próprio titular (ou quem já trabalha no dia) não está entre os candidatos.
        post(substituir, Map.of("funcionarioId", turno.get("funcionarioId").asLong()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem", containsString("não pode cobrir")));
        post(substituir, Map.of("funcionarioId", 999999L)).andExpect(status().isNotFound());
        get("/api/escalas/" + id + "/turnos/999999/substitutos").andExpect(status().isNotFound());
        get("/api/escalas/999999/turnos/" + turnoId + "/substitutos").andExpect(status().isNotFound());
    }

    /** Urgência: alguém avisa que não vem e a gerente troca na hora, sem reabrir a escala já aprovada. */
    @Test
    void trocaDeUrgenciaEmEscalaAprovadaFicaRegistradaNoHistorico() throws Exception {
        long id = criar("Troca de urgência");
        modoFinanceiro("ALERTAR");
        JsonNode turno = corpo(post("/api/escalas/" + id + "/gerar", Map.of()).andExpect(status().isOk())).at("/matriz/turnos/0");
        long turnoId = turno.get("id").asLong();
        JsonNode sugestao = corpo(get("/api/escalas/" + id + "/turnos/" + turnoId + "/substitutos"));
        JsonNode candidato = sugestao.at("/candidatos/0");
        long titular = sugestao.at("/titular/funcionarioId").asLong();
        post("/api/escalas/" + id + "/aprovar", Map.of()).andExpect(status().isOk());
        get("/api/escalas/" + id + "/trocas").andExpect(status().isOk()).andExpect(jsonPath("$", empty()));

        JsonNode depois = corpo(post("/api/escalas/" + id + "/turnos/" + turnoId + "/substituir",
                Map.of("funcionarioId", candidato.get("funcionarioId").asLong())).andExpect(status().isOk()));

        assertThat(turnoPorId(depois, turnoId).get("funcionarioId").asLong()).isEqualTo(candidato.get("funcionarioId").asLong());
        assertThat(depois.at("/escala/status").asText()).isEqualTo("APROVADA");
        assertThat(depois.at("/escala/observacao").isNull()).as("a observação continua livre para o gestor").isTrue();

        JsonNode troca = corpo(get("/api/escalas/" + id + "/trocas").andExpect(status().isOk())).get(0);
        assertThat(troca.get("data").asText()).isEqualTo(turno.get("data").asText());
        assertThat(troca.get("horaInicio").asText()).isEqualTo(turno.get("horaInicio").asText());
        assertThat(troca.get("titularId").asLong()).isEqualTo(titular);
        assertThat(troca.get("substitutoId").asLong()).isEqualTo(candidato.get("funcionarioId").asLong());
        assertThat(troca.get("substitutoNome").asText()).isEqualTo(candidato.get("nome").asText());
        assertThat(troca.get("urgencia").asBoolean()).isTrue();
        assertThat(troca.get("registradaPor").asText()).isEqualTo("gestor");
        assertThat(troca.get("registradaEm").isNull()).isFalse();
    }

    @Test
    void trocaEmRascunhoTambemFicaNoHistoricoSemSerUrgencia() throws Exception {
        long id = criar("Troca em rascunho");
        modoFinanceiro("ALERTAR");
        long turnoId = corpo(post("/api/escalas/" + id + "/gerar", Map.of()).andExpect(status().isOk())).at("/matriz/turnos/0/id").asLong();
        long candidato = corpo(get("/api/escalas/" + id + "/turnos/" + turnoId + "/substitutos")).at("/candidatos/0/funcionarioId").asLong();

        post("/api/escalas/" + id + "/turnos/" + turnoId + "/substituir", Map.of("funcionarioId", candidato)).andExpect(status().isOk());

        get("/api/escalas/" + id + "/trocas")
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].urgencia").value(false));
        get("/api/escalas/999999/trocas").andExpect(status().isNotFound());
    }

    /** O limite de custo segue o modo de Parâmetros também na urgência: no modo BLOQUEAR, o orçamento não estoura. */
    @Test
    void trocaDeUrgenciaRespeitaOBloqueioFinanceiro() throws Exception {
        long id = criar("Urgência com bloqueio");
        modoFinanceiro("ALERTAR");
        JsonNode matriz = corpo(post("/api/escalas/" + id + "/gerar", Map.of()).andExpect(status().isOk())).get("matriz");

        // O gerador põe exatamente o mínimo no domingo; um operador a mais deixa o dia acima do mínimo, onde vale o teto.
        long extra = funcionarioSemTurnoNo(matriz, DOMINGO);
        matriz = corpo(put("/api/escalas/" + id + "/celula", celula(extra, DOMINGO, idDoModelo(matriz, "DOM")))
                .andExpect(status().isOk()));
        post("/api/escalas/" + id + "/aprovar", Map.of()).andExpect(status().isOk());
        modoFinanceiro("BLOQUEAR");
        put("/api/projecoes/padrao", Map.of("DOMINGO", 1)).andExpect(status().isOk()); // teto do domingo quase zero

        // Troca de um turno do domingo por alguém mais caro que o titular: aumenta o custo de um dia acima do teto.
        JsonNode turno = null;
        JsonNode maisCaro = null;
        for (JsonNode t : matriz.get("turnos")) {
            if (!t.get("data").asText().equals(DOMINGO) || maisCaro != null) {
                continue;
            }
            for (JsonNode c : corpo(get("/api/escalas/" + id + "/turnos/" + t.get("id").asLong() + "/substitutos")).get("candidatos")) {
                if (c.get("apto").asBoolean() && c.get("custoTurno").decimalValue().compareTo(t.get("custo").decimalValue()) > 0) {
                    turno = t;
                    maisCaro = c;
                    break;
                }
            }
        }
        assertThat(maisCaro).as("cenário de teste: um substituto mais caro apto no domingo").isNotNull();
        long turnoId = turno.get("id").asLong();

        post("/api/escalas/" + id + "/turnos/" + turnoId + "/substituir", Map.of("funcionarioId", maisCaro.get("funcionarioId").asLong()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem", containsString("Alocação bloqueada")));

        // A troca recusada não entra no histórico. (A volta do turno ao titular é o rollback da requisição, que este
        // teste não enxerga: ele roda inteiro numa transação só.)
        get("/api/escalas/" + id + "/trocas").andExpect(jsonPath("$", empty()));
    }

    @Test
    void naoTrocaTurnoDeDiaQueJaPassouEmEscalaAprovada() throws Exception {
        long id = corpo(post("/api/escalas", periodo("2020-03-02", "2020-03-08")).andExpect(status().isCreated())).get("id").asLong();
        modoFinanceiro("ALERTAR");
        JsonNode turno = corpo(post("/api/escalas/" + id + "/gerar", Map.of()).andExpect(status().isOk())).at("/matriz/turnos/0");
        long turnoId = turno.get("id").asLong();
        long candidato = corpo(get("/api/escalas/" + id + "/turnos/" + turnoId + "/substitutos")).at("/candidatos/0/funcionarioId").asLong();
        post("/api/escalas/" + id + "/aprovar", Map.of()).andExpect(status().isOk());

        post("/api/escalas/" + id + "/turnos/" + turnoId + "/substituir", Map.of("funcionarioId", candidato))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem", containsString("já passou")));
    }

    @Test
    void celulaEmOutroSetorExigeAHabilidade() throws Exception {
        JsonNode setores = corpo(get("/api/setores"));
        long principal = setores.get(0).get("id").asLong();
        long habilidade = setores.get(1).get("id").asLong();
        long semHabilidade = setores.get(2).get("id").asLong();
        long versatil = corpo(post("/api/funcionarios", Map.of("nome", "Versátil", "setorId", principal, "salarioMensal", 2000,
                "habilidades", List.of(habilidade))).andExpect(status().isCreated())).get("id").asLong();
        long id = criar("Habilidades");
        long modelo = idDoModelo(corpo(get("/api/escalas/" + id + "/matriz")), "ABE");

        Map<String, Object> outroSetor = celula(versatil, SEGUNDA, modelo);
        outroSetor.put("setorId", habilidade);
        JsonNode matriz = corpo(put("/api/escalas/" + id + "/celula", outroSetor).andExpect(status().isOk()));

        assertThat(matriz.at("/turnos/0/setorId").asLong()).as("o turno cobre o setor da habilidade").isEqualTo(habilidade);
        JsonNode linha = null;
        for (JsonNode f : matriz.get("funcionarios")) {
            if (f.get("id").asLong() == versatil) {
                linha = f;
            }
        }
        assertThat(linha.get("habilidades").toString()).isEqualTo("[" + habilidade + "]");

        outroSetor.put("setorId", semHabilidade);
        put("/api/escalas/" + id + "/celula", outroSetor)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem", containsString("não trabalha neste setor")));

        // Sem setor informado, vale o principal.
        corpo(put("/api/escalas/" + id + "/celula", celula(versatil, SEGUNDA, modelo)).andExpect(status().isOk()));
        assertThat(corpo(get("/api/escalas/" + id + "/matriz")).at("/turnos/0/setorId").asLong()).isEqualTo(principal);
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

    private static JsonNode turnoPorId(JsonNode matriz, long id) {
        for (JsonNode t : matriz.get("turnos")) {
            if (t.get("id").asLong() == id) {
                return t;
            }
        }
        throw new AssertionError("Turno " + id + " não encontrado");
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
