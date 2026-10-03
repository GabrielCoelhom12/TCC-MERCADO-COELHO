package br.com.coelho.escalas.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.Map;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthIntegracaoTest extends IntegracaoTest {

    @Test
    void loginValidoDevolveTokenEPerfil() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("login", " gestor ", "senha", "senha-de-teste"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.perfil").value("ADMIN"));
    }

    @Test
    void senhaErradaDevolve401() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("login", "gestor", "senha", "errada"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensagem").value("Usuário ou senha inválidos."));
    }

    @Test
    void camposObrigatoriosSaoValidados() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("login", "", "senha", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value(org.hamcrest.Matchers.startsWith("Dados inválidos")));
    }

    @Test
    void meDevolveUsuarioAutenticado() throws Exception {
        get("/api/auth/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.login").value("gestor"))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void apiExigeToken() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/setores")).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenInvalidoEhRecusado() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/setores").header(HttpHeaders.AUTHORIZATION, "Bearer token-falso"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rotasDoFrontendSaoEncaminhadasParaASpa() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/escalas/12"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }
}
