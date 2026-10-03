package br.com.coelho.escalas.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Gestão de usuários do sistema (somente administradores). */
class UsuarioIntegracaoTest extends IntegracaoTest {

    @Test
    void listaUsuariosSemExporSenha() throws Exception {
        get("/api/usuarios")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].login", hasItem("gestor")))
                .andExpect(jsonPath("$[0].senhaHash").doesNotExist())
                .andExpect(jsonPath("$[0].senha").doesNotExist());
    }

    @Test
    void criaUsuarioQueConsegueEntrar() throws Exception {
        post("/api/usuarios", novo("maria", "Gestor"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.login").value("maria"))
                .andExpect(jsonPath("$.nome").value("Maria Souza"))
                .andExpect(jsonPath("$.perfil").value("GESTOR"))
                .andExpect(jsonPath("$.ativo").value(true))
                .andExpect(jsonPath("$.senhaHash").doesNotExist());

        login("maria", "Senha@Forte1").andExpect(status().isOk()).andExpect(jsonPath("$.perfil").value("GESTOR"));
    }

    @Test
    void loginEhNormalizadoEUnico() throws Exception {
        Map<String, Object> req = novo("  Joao.Silva ", "Gestor");
        post("/api/usuarios", req).andExpect(status().isCreated()).andExpect(jsonPath("$.login").value("joao.silva"));
        post("/api/usuarios", novo("JOAO.SILVA", "Gestor"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem", containsString("já está em uso")));
    }

    @Test
    void validaCamposObrigatoriosESenhaMinima() throws Exception {
        post("/api/usuarios", Map.of("login", "", "nome", "", "perfil", "GESTOR", "senha", "123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem", containsString("senha")));
        Map<String, Object> semPerfil = novo("ana", "Gestor");
        semPerfil.remove("perfil");
        post("/api/usuarios", semPerfil).andExpect(status().isBadRequest());
    }

    @Test
    void editaDadosSemTrocarASenhaQuandoNaoInformada() throws Exception {
        long id = criar("pedro", "SUPERVISOR");

        put("/api/usuarios/" + id, Map.of("login", "pedro.alves", "nome", "Pedro Alves", "perfil", "GESTOR", "ativo", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.login").value("pedro.alves"))
                .andExpect(jsonPath("$.perfil").value("GESTOR"));

        login("pedro.alves", "Senha@Forte1").andExpect(status().isOk());
    }

    @Test
    void redefineSenha() throws Exception {
        long id = criar("lucia", "GESTOR");
        put("/api/usuarios/" + id, Map.of("login", "lucia", "nome", "Lúcia", "perfil", "GESTOR", "ativo", true,
                "senha", "Nova#Senha2"))
                .andExpect(status().isOk());

        login("lucia", "Senha@Forte1").andExpect(status().isUnauthorized());
        login("lucia", "Nova#Senha2").andExpect(status().isOk());
    }

    @Test
    void senhaCurtaNaEdicaoEhRecusada() throws Exception {
        long id = criar("rita", "GESTOR");
        put("/api/usuarios/" + id, Map.of("login", "rita", "nome", "Rita", "perfil", "GESTOR", "ativo", true, "senha", "curta"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void recusaSenhaFracaNaCriacao() throws Exception {
        Map<String, Object> req = novo("fraco", "Gestor");
        req.put("senha", "senhafraca1");
        post("/api/usuarios", req)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem", containsString("caractere especial")));
    }

    @Test
    void recusaSenhaFracaNaEdicao() throws Exception {
        long id = criar("rui", "GESTOR");
        put("/api/usuarios/" + id, Map.of("login", "rui", "nome", "Rui", "perfil", "GESTOR", "ativo", true, "senha", "SemEspecial1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem", containsString("caractere especial")));
    }

    @Test
    void usuarioDesativadoNaoConsegueEntrar() throws Exception {
        long id = criar("carlos", "GESTOR");
        put("/api/usuarios/" + id, Map.of("login", "carlos", "nome", "Carlos", "perfil", "GESTOR", "ativo", false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));

        login("carlos", "Senha@Forte1")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensagem", containsString("desativado")));
    }

    @Test
    void naoPermiteTirarOUltimoAdministrador() throws Exception {
        long gestor = idDe("gestor");
        put("/api/usuarios/" + gestor, Map.of("login", "gestor", "nome", "Gerência", "perfil", "GESTOR", "ativo", true))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem", containsString("administrador")));
    }

    @Test
    void naoPermiteDesativarAPropriaConta() throws Exception {
        criar("admin2", "ADMIN");
        long gestor = idDe("gestor");
        put("/api/usuarios/" + gestor, Map.of("login", "gestor", "nome", "Gerência", "perfil", "ADMIN", "ativo", false))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem", containsString("própria conta")));
    }

    @Test
    void comOutroAdministradorAtivoPodeRebaixar() throws Exception {
        criar("admin2", "ADMIN");
        long gestor = idDe("gestor");
        put("/api/usuarios/" + gestor, Map.of("login", "gestor", "nome", "Gerência", "perfil", "GESTOR", "ativo", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perfil").value("GESTOR"));
    }

    @Test
    void somenteAdministradorGerenciaUsuarios() throws Exception {
        criar("bia", "GESTOR");
        entrarComo("bia", "Senha@Forte1");

        get("/api/usuarios").andExpect(status().isForbidden());
        post("/api/usuarios", novo("hacker", "Admin")).andExpect(status().isForbidden());
        // As demais áreas continuam liberadas para o gestor.
        get("/api/setores").andExpect(status().isOk());
    }

    @Test
    void usuarioInexistenteDevolve404() throws Exception {
        put("/api/usuarios/999999", Map.of("login", "x", "nome", "X", "perfil", "GESTOR", "ativo", true))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ auxiliares

    private static Map<String, Object> novo(String login, String perfil) {
        return new HashMap<>(Map.of("login", login, "nome", "Maria Souza", "perfil", perfil.toUpperCase(),
                "senha", "Senha@Forte1"));
    }

    private long criar(String login, String perfil) throws Exception {
        return corpo(post("/api/usuarios", novo(login, perfil)).andExpect(status().isCreated())).get("id").asLong();
    }

    private long idDe(String login) throws Exception {
        for (JsonNode u : corpo(get("/api/usuarios"))) {
            if (u.get("login").asText().equals(login)) {
                return u.get("id").asLong();
            }
        }
        throw new AssertionError("Usuário " + login + " não encontrado");
    }
}
