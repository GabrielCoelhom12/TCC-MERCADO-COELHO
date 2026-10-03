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

/** Fluxo 1 — cadastros e regras sindicais (setores, colaboradores, turnos, calendário, projeções e parâmetros). */
class CadastrosIntegracaoTest extends IntegracaoTest {

    // ------------------------------------------------------------------ setores

    @Test
    void criaAtualizaEExcluiSetor() throws Exception {
        JsonNode criado = corpo(post("/api/setores", Map.of("nome", " Hortifruti ", "minimoPorDia", Map.of("SEGUNDA", 2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Hortifruti"))
                .andExpect(jsonPath("$.cor").value("#F26A1B"))
                .andExpect(jsonPath("$.minimoPorDia.SEGUNDA").value(2)));
        long id = criado.get("id").asLong();

        put("/api/setores/" + id, Map.of("nome", "Hortifrúti", "cor", "#00AA00", "ativo", false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cor").value("#00AA00"))
                .andExpect(jsonPath("$.ativo").value(false));

        delete("/api/setores/" + id).andExpect(status().isNoContent());
        get("/api/setores").andExpect(jsonPath("$[*].nome", not(hasItem("Hortifrúti"))));
    }

    @Test
    void setorComColaboradoresNaoPodeSerExcluido() throws Exception {
        long id = primeiroId("/api/setores");
        delete("/api/setores/" + id)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem", containsString("possui colaboradores")));
    }

    @Test
    void demandaMinimaNegativaEhRecusada() throws Exception {
        post("/api/setores", Map.of("nome", "Teste", "minimoPorDia", Map.of("DOMINGO", -1)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void setorInexistenteDevolve404() throws Exception {
        put("/api/setores/999999", Map.of("nome", "X")).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ colaboradores e ausências

    @Test
    void cadastraColaboradorComValoresPadrao() throws Exception {
        long setor = primeiroId("/api/setores");
        JsonNode f = corpo(post("/api/funcionarios", Map.of("nome", " Zeca Silva ", "matricula", " ", "setorId", setor,
                "salarioMensal", 2000))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Zeca Silva"))
                .andExpect(jsonPath("$.matricula").doesNotExist())
                .andExpect(jsonPath("$.cargaHorariaMensal").value(220))
                .andExpect(jsonPath("$.diasDisponiveis", hasSize(7))));
        long id = f.get("id").asLong();

        put("/api/funcionarios/" + id, Map.of("nome", "Zeca", "matricula", "Z01", "setorId", setor, "salarioMensal", 2100,
                "cargaHorariaMensal", 180, "percentualEncargos", 30, "ativo", true, "diasDisponiveis", List.of("MONDAY")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matricula").value("Z01"))
                .andExpect(jsonPath("$.diasDisponiveis", contains("MONDAY")));

        // Sem histórico de escala: é excluído de fato.
        delete("/api/funcionarios/" + id).andExpect(status().isOk()).andExpect(jsonPath("$.resultado").value("excluido"));
    }

    @Test
    void colaboradorComHistoricoEhApenasDesativado() throws Exception {
        long escala = primeiroId("/api/escalas");
        JsonNode matriz = corpo(get("/api/escalas/" + escala + "/matriz"));
        long funcionario = matriz.get("turnos").get(0).get("funcionarioId").asLong();

        delete("/api/funcionarios/" + funcionario)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("desativado"));
    }

    @Test
    void colaboradorInvalidoEhRecusado() throws Exception {
        post("/api/funcionarios", Map.of("nome", "", "salarioMensal", 0))
                .andExpect(status().isBadRequest());
        post("/api/funcionarios", Map.of("nome", "X", "setorId", 999999, "salarioMensal", 1000))
                .andExpect(status().isNotFound());
    }

    @Test
    void registraEListaAusencias() throws Exception {
        long funcionario = primeiroId("/api/funcionarios");
        JsonNode a = corpo(post("/api/funcionarios/" + funcionario + "/ausencias",
                Map.of("inicio", "2031-01-10", "fim", "2031-01-12"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.motivo").value("OUTRO")));

        get("/api/funcionarios/" + funcionario + "/ausencias").andExpect(jsonPath("$[*].inicio", hasItem("2031-01-10")));
        get("/api/ausencias").andExpect(jsonPath("$", not(empty())));

        delete("/api/ausencias/" + a.get("id").asLong()).andExpect(status().isNoContent());
    }

    @Test
    void ausenciaComFimAntesDoInicioEhRecusada() throws Exception {
        long funcionario = primeiroId("/api/funcionarios");
        post("/api/funcionarios/" + funcionario + "/ausencias", Map.of("inicio", "2031-01-12", "fim", "2031-01-10"))
                .andExpect(status().isUnprocessableEntity());
    }

    // ------------------------------------------------------------------ modelos de turno

    @Test
    void crudDeModeloDeTurno() throws Exception {
        JsonNode m = corpo(post("/api/turnos-modelo", Map.of("nome", "noturno", "horaInicio", "22:00", "horaFim", "06:00",
                "intervaloMinutos", 60))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sigla").value("NOT"))
                .andExpect(jsonPath("$.aplicacao").value("TODOS")));
        long id = m.get("id").asLong();

        put("/api/turnos-modelo/" + id, Map.of("nome", "Noturno", "sigla", " NT ", "horaInicio", "22:00", "horaFim", "06:00",
                "intervaloMinutos", 60, "aplicacao", "DIAS_UTEIS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sigla").value("NT"));

        delete("/api/turnos-modelo/" + id).andExpect(status().isNoContent());
        put("/api/turnos-modelo/" + id, Map.of("nome", "X", "horaInicio", "08:00", "horaFim", "09:00", "intervaloMinutos", 0))
                .andExpect(status().isNotFound());
    }

    @Test
    void modeloDeTurnoInconsistenteEhRecusado() throws Exception {
        post("/api/turnos-modelo", Map.of("nome", "X", "horaInicio", "08:00", "horaFim", "08:00", "intervaloMinutos", 0))
                .andExpect(status().isUnprocessableEntity());
        post("/api/turnos-modelo", Map.of("nome", "X", "horaInicio", "08:00", "horaFim", "09:00", "intervaloMinutos", 60))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem", containsString("intervalo")));
    }

    // ------------------------------------------------------------------ feriados

    @Test
    void crudDeFeriadoEImportacaoAnual() throws Exception {
        JsonNode f = corpo(post("/api/feriados", Map.of("data", "2040-06-01", "descricao", " Festa local ", "fatorCusto", 2.5))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.abrangencia").value("NACIONAL")));
        long id = f.get("id").asLong();

        put("/api/feriados/" + id, Map.of("data", "2040-06-02", "descricao", "Festa", "abrangencia", "MUNICIPAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descricao").value("Festa"));
        delete("/api/feriados/" + id).andExpect(status().isNoContent());

        post("/api/feriados/importar/2041", Map.of()).andExpect(jsonPath("$.importados").value(12));
        post("/api/feriados/importar/2041", Map.of()).andExpect(jsonPath("$.importados").value(0));
        get("/api/feriados").andExpect(jsonPath("$[*].descricao", hasItem("Natal")));
    }

    @Test
    void feriadoDuplicadoGeraConflito() throws Exception {
        post("/api/feriados", Map.of("data", "2042-02-02", "descricao", "A")).andExpect(status().isCreated());
        post("/api/feriados", Map.of("data", "2042-02-02", "descricao", "B")).andExpect(status().isConflict());
    }

    @Test
    void feriadoInexistenteDevolve404() throws Exception {
        put("/api/feriados/999999", Map.of("data", "2040-01-01", "descricao", "X")).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ projeções de receita

    @Test
    void atualizaProjecoesPadraoEPorData() throws Exception {
        put("/api/projecoes/padrao", Map.of("DOMINGO", 25000))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.padrao.DOMINGO").value(25000));

        JsonNode p = corpo(post("/api/projecoes/datas", Map.of("data", "2040-12-24", "valor", 70000, "observacao", "Véspera"))
                .andExpect(status().isCreated()));
        long id = p.get("id").asLong();
        put("/api/projecoes/datas/" + id, Map.of("data", "2040-12-23", "valor", 65000))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("2040-12-23"));
        get("/api/projecoes").andExpect(jsonPath("$.especificas[*].data", hasItem("2040-12-23")));
        delete("/api/projecoes/datas/" + id).andExpect(status().isNoContent());
        put("/api/projecoes/datas/" + id, Map.of("data", "2040-12-23", "valor", 1)).andExpect(status().isNotFound());
    }

    @Test
    void projecaoNegativaEhRecusada() throws Exception {
        put("/api/projecoes/padrao", Map.of("SEGUNDA", -1)).andExpect(status().isUnprocessableEntity());
    }

    // ------------------------------------------------------------------ parâmetros

    @Test
    void salvaParametrosValidos() throws Exception {
        Map<String, Object> p = parametros();
        p.put("fatorDomingo", 1.8);
        p.put("modoFinanceiro", null);
        put("/api/parametros", p)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fatorDomingo").value(1.8))
                .andExpect(jsonPath("$.modoFinanceiro").value("BLOQUEAR"));
    }

    @Test
    void recusaParametrosInvalidos() throws Exception {
        Map<String, Object> fator = parametros();
        fator.put("fatorFeriado", 0.5);
        put("/api/parametros", fator).andExpect(status().isUnprocessableEntity());

        Map<String, Object> dias = parametros();
        dias.put("maxDiasConsecutivos", 0);
        put("/api/parametros", dias).andExpect(status().isUnprocessableEntity());

        Map<String, Object> jornada = parametros();
        jornada.put("jornadaMaximaDiariaHoras", 7);
        put("/api/parametros", jornada)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem", containsString("jornada máxima")));
    }

    // ------------------------------------------------------------------ auxiliares

    @SuppressWarnings("unchecked")
    private Map<String, Object> parametros() throws Exception {
        return new HashMap<>(json.treeToValue(corpo(get("/api/parametros")), Map.class));
    }

    private long primeiroId(String url) throws Exception {
        JsonNode lista = corpo(get(url));
        assertThat(lista.size()).isPositive();
        return lista.get(0).get("id").asLong();
    }
}
