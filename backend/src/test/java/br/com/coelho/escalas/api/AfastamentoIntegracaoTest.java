package br.com.coelho.escalas.api;

import br.com.coelho.escalas.config.AjusteEsquema;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Operação de afastamentos: atestados, licenças e outros, com os turnos já escalados repassados na hora. */
class AfastamentoIntegracaoTest extends IntegracaoTest {

    /** Segunda-feira longe do cenário de demonstração. */
    private static final String SEGUNDA = "2031-03-03";
    private static final String DOMINGO = "2031-03-09";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AjusteEsquema ajusteEsquema;

    @Test
    void atestadoRepassaOsTurnosDoPeriodo() throws Exception {
        long escala = criarEscalaGerada(SEGUNDA, DOMINGO);
        JsonNode antes = corpo(get("/api/escalas/" + escala + "/matriz"));
        long funcionario = antes.at("/turnos/0/funcionarioId").asLong();
        String dia = antes.at("/turnos/0/data").asText();

        JsonNode registro = corpo(post("/api/afastamentos", afastamento(funcionario, "ATESTADO", dia, dia))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ausencia.motivo").value("ATESTADO")));
        assertThat(registro.get("coberturas")).hasSize(1);
        assertThat(registro.at("/coberturas/0/substitutoId").asLong()).isNotEqualTo(funcionario);
        assertThat(registro.at("/coberturas/0/passado").asBoolean()).isFalse();

        JsonNode depois = corpo(get("/api/escalas/" + escala + "/matriz"));
        assertThat(turnosDe(depois, funcionario, dia)).isZero();
        assertThat(depois.at("/escala/observacao").asText()).contains("(atestado médico)");
        assertThat(depois.at("/validacao/violacoes").findValuesAsText("regra")).doesNotContain("AUSENCIA");
    }

    @Test
    void previaDaLicencaMaternidadeNaoAlteraNada() throws Exception {
        long escala = criarEscalaGerada(SEGUNDA, DOMINGO);
        JsonNode antes = corpo(get("/api/escalas/" + escala + "/matriz"));
        long funcionario = antes.at("/turnos/0/funcionarioId").asLong();

        JsonNode previa = corpo(get("/api/afastamentos/analise?funcionarioId=" + funcionario
                + "&motivo=LICENCA_MATERNIDADE&inicio=" + SEGUNDA + "&fim=2031-06-30").andExpect(status().isOk()));

        assertThat(previa.get("dias").asInt()).isEqualTo(120);
        assertThat(previa.get("valida").asBoolean()).isTrue();
        assertThat(previa.get("alertas")).isEmpty();
        assertThat(previa.get("coberturas").size()).isEqualTo(turnosDe(antes, funcionario, null));
        assertThat(turnosDe(corpo(get("/api/escalas/" + escala + "/matriz")), funcionario, null))
                .isEqualTo(turnosDe(antes, funcionario, null));
    }

    @Test
    void turnosDeDiasQueJaPassaramSaoRetiradosSemSubstituto() throws Exception {
        long escala = criarEscalaGerada("2020-03-02", "2020-03-08");
        JsonNode antes = corpo(get("/api/escalas/" + escala + "/matriz"));
        long funcionario = antes.at("/turnos/0/funcionarioId").asLong();
        int dele = turnosDe(antes, funcionario, null);

        JsonNode registro = corpo(post("/api/afastamentos", afastamento(funcionario, "ATESTADO", "2020-03-02", "2020-03-08"))
                .andExpect(status().isCreated()));

        assertThat(registro.get("coberturas")).hasSize(dele);
        for (JsonNode c : registro.get("coberturas")) {
            assertThat(c.get("passado").asBoolean()).isTrue();
            assertThat(c.get("substitutoId").isNull()).isTrue();
        }
        JsonNode depois = corpo(get("/api/escalas/" + escala + "/matriz"));
        assertThat(turnosDe(depois, funcionario, null)).isZero();
        assertThat(depois.at("/escala/observacao").asText()).contains("não trabalhou");
    }

    @Test
    void listaEditaERemoveSemMisturarComFerias() throws Exception {
        long funcionario = primeiroFuncionario();
        long id = corpo(post("/api/afastamentos", afastamento(funcionario, "LUTO", SEGUNDA, "2031-03-04"))
                .andExpect(status().isCreated())).at("/ausencia/id").asLong();
        long ferias = corpo(post("/api/ferias", Map.of("funcionarioId", funcionario, "inicio", "2031-04-07", "fim", "2031-04-20"))
                .andExpect(status().isCreated())).at("/ausencia/id").asLong();

        get("/api/afastamentos")
                .andExpect(jsonPath("$[*].id", hasItem((int) id)))
                .andExpect(jsonPath("$[*].id", not(hasItem((int) ferias))));

        put("/api/afastamentos/" + id, afastamento(funcionario, "LUTO", SEGUNDA, "2031-03-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ausencia.fim").value("2031-03-07"));
        delete("/api/afastamentos/" + ferias).andExpect(status().isNotFound());
        delete("/api/afastamentos/" + id).andExpect(status().isNoContent());
        delete("/api/afastamentos/" + id).andExpect(status().isNotFound());
    }

    @Test
    void recusaFeriasSobreposicaoEColaboradorInexistente() throws Exception {
        long funcionario = primeiroFuncionario();
        post("/api/afastamentos", afastamento(funcionario, "FERIAS", SEGUNDA, DOMINGO))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem", containsString("operação de Férias")));
        post("/api/afastamentos", afastamento(funcionario, "ATESTADO", SEGUNDA, "2031-03-05")).andExpect(status().isCreated());
        post("/api/afastamentos", afastamento(funcionario, "ACIDENTE_TRABALHO", "2031-03-04", "2031-03-10"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem", containsString("Já existe atestado médico")));
        post("/api/afastamentos", afastamento(999999L, "ATESTADO", SEGUNDA, SEGUNDA)).andExpect(status().isNotFound());
        put("/api/afastamentos/999999", afastamento(funcionario, "ATESTADO", SEGUNDA, SEGUNDA)).andExpect(status().isNotFound());
    }

    @Test
    void supervisorRegistraMasNaoRemove() throws Exception {
        long funcionario = primeiroFuncionario();
        post("/api/usuarios", Map.of("login", "sup.atestado", "nome", "Supervisor", "perfil", "SUPERVISOR", "senha", "Senha@Forte1"))
                .andExpect(status().isCreated());
        entrarComo("sup.atestado", "Senha@Forte1");

        long id = corpo(post("/api/afastamentos", afastamento(funcionario, "ATESTADO", SEGUNDA, SEGUNDA))
                .andExpect(status().isCreated())).at("/ausencia/id").asLong();
        get("/api/afastamentos").andExpect(status().isOk());
        put("/api/afastamentos/" + id, afastamento(funcionario, "ATESTADO", SEGUNDA, "2031-03-04")).andExpect(status().isForbidden());
        delete("/api/afastamentos/" + id).andExpect(status().isForbidden());
    }

    @Test
    void ausenciaPeloCadastroDeColaboradoresTambemRepassaOsTurnos() throws Exception {
        long escala = criarEscalaGerada(SEGUNDA, DOMINGO);
        JsonNode antes = corpo(get("/api/escalas/" + escala + "/matriz"));
        long funcionario = antes.at("/turnos/0/funcionarioId").asLong();
        String dia = antes.at("/turnos/0/data").asText();

        post("/api/funcionarios/" + funcionario + "/ausencias", Map.of("inicio", dia, "fim", dia, "motivo", "ATESTADO"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.motivo").value("ATESTADO"));

        assertThat(turnosDe(corpo(get("/api/escalas/" + escala + "/matriz")), funcionario, dia)).isZero();
    }

    /** Bancos criados antes dos novos tipos têm uma restrição que só aceita os quatro tipos antigos. */
    @Test
    void bancoComARestricaoAntigaPassaAAceitarOsNovosTipos() throws Exception {
        jdbc.execute("alter table ausencia drop constraint if exists ausencia_motivo_check");
        jdbc.execute("alter table ausencia add constraint ausencia_motivo_check check (motivo in ('FERIAS','ATESTADO','FOLGA_ACORDADA','OUTRO'))");

        ajusteEsquema.run(null);

        post("/api/afastamentos", afastamento(primeiroFuncionario(), "LICENCA_PATERNIDADE", SEGUNDA, "2031-03-07"))
                .andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------ auxiliares

    private long criarEscalaGerada(String inicio, String fim) throws Exception {
        long escala = corpo(post("/api/escalas", Map.of("dataInicio", inicio, "dataFim", fim))
                .andExpect(status().isCreated())).get("id").asLong();
        post("/api/escalas/" + escala + "/gerar", Map.of()).andExpect(status().isOk());
        return escala;
    }

    private long primeiroFuncionario() throws Exception {
        return corpo(get("/api/funcionarios")).get(0).get("id").asLong();
    }

    /** Turnos do colaborador na matriz (num dia, ou em todos quando {@code dia} é nulo). */
    private static int turnosDe(JsonNode matriz, long funcionario, String dia) {
        int n = 0;
        for (JsonNode t : matriz.get("turnos")) {
            boolean doDia = dia == null || t.get("data").asText().equals(dia);
            n += t.get("funcionarioId").asLong() == funcionario && doDia ? 1 : 0;
        }
        return n;
    }

    private static Map<String, Object> afastamento(long funcionario, String motivo, String inicio, String fim) {
        return new HashMap<>(Map.of("funcionarioId", funcionario, "motivo", motivo, "inicio", inicio, "fim", fim));
    }
}
