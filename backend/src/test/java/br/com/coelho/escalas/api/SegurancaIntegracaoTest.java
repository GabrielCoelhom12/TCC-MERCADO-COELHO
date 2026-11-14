package br.com.coelho.escalas.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controles de segurança da API: permissões por perfil, dados salariais ocultos de quem não pode vê-los, limite de
 * tentativas de login, sessões encerradas ao trocar a senha, limites de entrada e cabeçalhos de segurança.
 */
class SegurancaIntegracaoTest extends IntegracaoTest {

    private static final String SENHA = "Senha@Forte1";

    // ------------------------------------------------------------------ permissões por perfil

    @Test
    void supervisorConsultaMasNaoAlteraCadastrosNemEscalas() throws Exception {
        long funcionario = primeiroId("/api/funcionarios");
        long setor = primeiroId("/api/setores");
        entrarComo(criarUsuario("sup.consulta", "SUPERVISOR"), SENHA);

        get("/api/escalas").andExpect(status().isOk());
        get("/api/funcionarios").andExpect(status().isOk());
        get("/api/parametros").andExpect(status().isOk());
        get("/api/ferias").andExpect(status().isOk());

        String negado = "Seu perfil não permite esta ação.";
        post("/api/escalas", Map.of("dataInicio", "2032-01-05", "dataFim", "2032-01-11"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensagem").value(negado));
        put("/api/parametros", Map.of()).andExpect(status().isForbidden());
        put("/api/funcionarios/" + funcionario, Map.of()).andExpect(status().isForbidden());
        delete("/api/funcionarios/" + funcionario).andExpect(status().isForbidden());
        delete("/api/setores/" + setor).andExpect(status().isForbidden());
        post("/api/ferias", Map.of()).andExpect(status().isForbidden());
        post("/api/motor/simular", Map.of()).andExpect(status().isForbidden());
        post("/api/feriados/importar/2032", Map.of()).andExpect(status().isForbidden());
        delete("/api/movimento/fechamentos/2032-01-05").andExpect(status().isForbidden());
    }

    @Test
    void supervisorTrocaColaboradorELancaFechamento() throws Exception {
        entrarComo(criarUsuario("sup.dia", "SUPERVISOR"), SENHA);

        // Não é 403: a regra de negócio responde (aqui, turno inexistente).
        post("/api/escalas/999999/turnos/999999/substituir", Map.of("funcionarioId", 1))
                .andExpect(status().isNotFound());
        put("/api/movimento/fechamentos/2032-01-05", Map.of("faixas", java.util.List.of()))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403));
    }

    @Test
    void gestorNaoAdministraUsuarios() throws Exception {
        entrarComo(criarUsuario("gestor.loja", "GESTOR"), SENHA);
        get("/api/usuarios").andExpect(status().isForbidden());
        post("/api/escalas", Map.of("dataInicio", "2032-01-05", "dataFim", "2032-01-11")).andExpect(status().isCreated());
    }

    @Test
    void salariosFicamOcultosParaOSupervisor() throws Exception {
        JsonNode doGestor = corpo(get("/api/funcionarios")).get(0);
        assertThat(doGestor.get("salarioMensal").isNumber()).isTrue();
        assertThat(doGestor.get("custoHora").isNumber()).isTrue();

        entrarComo(criarUsuario("sup.salario", "SUPERVISOR"), SENHA);
        JsonNode doSupervisor = corpo(get("/api/funcionarios")).get(0);
        assertThat(doSupervisor.get("nome").asText()).isEqualTo(doGestor.get("nome").asText());
        assertThat(doSupervisor.get("salarioMensal").isNull()).isTrue();
        assertThat(doSupervisor.get("percentualEncargos").isNull()).isTrue();
        assertThat(doSupervisor.get("custoHora").isNull()).isTrue();

        long escala = primeiroId("/api/escalas");
        JsonNode matriz = corpo(get("/api/escalas/" + escala + "/matriz"));
        assertThat(matriz.at("/funcionarios/0/custoHora").isNull()).isTrue();
        if (matriz.get("turnos").size() > 0) {
            assertThat(matriz.at("/turnos/0/custo").isNull()).isTrue();
        }
    }

    // ------------------------------------------------------------------ login

    @Test
    void bloqueiaOLoginDepoisDeCincoSenhasErradas() throws Exception {
        criarUsuario("alvo", "GESTOR");
        for (int i = 0; i < 5; i++) {
            login("alvo", "Errada@123").andExpect(status().isUnauthorized());
        }

        // Nem a senha certa passa enquanto durar o bloqueio.
        login("alvo", SENHA)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.mensagem", containsString("Muitas tentativas")));
        // Outros usuários continuam entrando.
        login("gestor", "senha-de-teste").andExpect(status().isOk());
    }

    @Test
    void loginComSucessoZeraAsFalhas() throws Exception {
        criarUsuario("distraido", "GESTOR");
        for (int i = 0; i < 4; i++) {
            login("distraido", "Errada@123").andExpect(status().isUnauthorized());
        }
        login("distraido", SENHA).andExpect(status().isOk());
        for (int i = 0; i < 4; i++) {
            login("distraido", "Errada@123").andExpect(status().isUnauthorized());
        }
        login("distraido", SENHA).andExpect(status().isOk());
    }

    @Test
    void recusaLoginESenhaGigantes() throws Exception {
        login("x".repeat(101), SENHA).andExpect(status().isBadRequest());
        login("gestor", "x".repeat(129)).andExpect(status().isBadRequest());
    }

    @Test
    void trocarASenhaEncerraAsSessoesAnteriores() throws Exception {
        String login = criarUsuario("rotativo", "GESTOR");
        long id = idDoUsuario(login);
        String tokenDoAdmin = token();
        entrarComo(login, SENHA);
        String tokenAntigo = token();
        get("/api/auth/me").andExpect(status().isOk());

        Thread.sleep(1100); // o token guarda o instante de emissão em segundos
        usarToken(tokenDoAdmin);
        put("/api/usuarios/" + id, Map.of("login", login, "nome", "Rotativo", "perfil", "GESTOR", "ativo", true,
                "senha", "Nova@Senha2")).andExpect(status().isOk());

        usarToken(tokenAntigo);
        get("/api/auth/me").andExpect(status().isUnauthorized());
        entrarComo(login, "Nova@Senha2");
        get("/api/auth/me").andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ limites de entrada

    @Test
    void simuladorRecusaQuantidadesAbsurdas() throws Exception {
        long setor = primeiroId("/api/setores");
        post("/api/motor/simular", simulacao(setor, 2_000_000_000))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem", containsString("entre 0 e")));
        post("/api/motor/simular", simulacao(setor, -1)).andExpect(status().isUnprocessableEntity());
        post("/api/motor/simular", simulacao(setor, 3)).andExpect(status().isOk());
    }

    @Test
    void fechamentosTemPeriodoMaximo() throws Exception {
        get("/api/movimento/fechamentos?inicio=2020-01-01&fim=2030-01-01").andExpect(status().isUnprocessableEntity());
        get("/api/movimento/fechamentos?inicio=2026-01-01&fim=2026-03-01").andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ cabeçalhos

    @Test
    void respostasTrazemOsCabecalhosDeSeguranca() throws Exception {
        get("/api/auth/me")
                .andExpect(header().string("Content-Security-Policy", allOf(
                        containsString("default-src 'self'"), containsString("frame-ancestors 'none'"),
                        containsString("object-src 'none'"))))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().string("Permissions-Policy", containsString("camera=()")));
    }

    // ------------------------------------------------------------------ auxiliares

    /** Cria o usuário como administrador (o usuário atual) e devolve o login. */
    private String criarUsuario(String login, String perfil) throws Exception {
        post("/api/usuarios", Map.of("login", login, "nome", "Teste " + login, "perfil", perfil, "senha", SENHA))
                .andExpect(status().isCreated());
        return login;
    }

    private long idDoUsuario(String login) throws Exception {
        for (JsonNode u : corpo(get("/api/usuarios"))) {
            if (u.get("login").asText().equals(login)) {
                return u.get("id").asLong();
            }
        }
        throw new AssertionError("Usuário " + login + " não encontrado");
    }

    private long primeiroId(String url) throws Exception {
        return corpo(get(url)).get(0).get("id").asLong();
    }

    private static Map<String, Object> simulacao(long setor, int pessoas) {
        Map<String, Object> m = new HashMap<>();
        m.put("data", "2032-01-05");
        m.put("proposta", Map.of(String.valueOf(setor), pessoas));
        return m;
    }
}
