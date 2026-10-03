package br.com.coelho.escalas.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Base dos testes de integração: sobe a aplicação completa contra o PostgreSQL de teste (com o cenário
 * de demonstração carregado) e desfaz as alterações de cada teste ao final. O servidor roda numa porta real para que
 * os testes de WebSocket compartilhem o mesmo contexto (e o mesmo esquema de banco) dos demais.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
abstract class IntegracaoTest {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    private String token;

    @BeforeEach
    void autenticar() throws Exception {
        entrarComo("gestor", "senha-de-teste");
    }

    /** Troca o usuário das próximas requisições. */
    protected void entrarComo(String login, String senha) throws Exception {
        token = corpo(login(login, senha)).get("token").asText();
    }

    protected ResultActions login(String login, String senha) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("login", login, "senha", senha))));
    }

    protected ResultActions get(String url) throws Exception {
        return mvc.perform(autenticado(MockMvcRequestBuilders.get(url)));
    }

    protected ResultActions post(String url, Object corpo) throws Exception {
        return mvc.perform(comCorpo(MockMvcRequestBuilders.post(url), corpo));
    }

    protected ResultActions put(String url, Object corpo) throws Exception {
        return mvc.perform(comCorpo(MockMvcRequestBuilders.put(url), corpo));
    }

    protected ResultActions delete(String url) throws Exception {
        return mvc.perform(autenticado(MockMvcRequestBuilders.delete(url)));
    }

    protected ResultActions enviarArquivo(String url, MockMultipartFile arquivo) throws Exception {
        return enviarArquivo(HttpMethod.PUT, url, arquivo, Map.of());
    }

    protected ResultActions enviarArquivo(HttpMethod metodo, String url, MockMultipartFile arquivo, Map<String, String> campos)
            throws Exception {
        var req = MockMvcRequestBuilders.multipart(metodo, url).file(arquivo);
        campos.forEach(req::param);
        return mvc.perform(autenticado(req));
    }

    /** Token JWT do usuário atual (para clientes que não passam pelo MockMvc, como o WebSocket). */
    protected String token() {
        return token;
    }

    protected void usarToken(String outro) {
        token = outro;
    }

    protected JsonNode corpo(ResultActions resultado) throws Exception {
        return json.readTree(resultado.andReturn().getResponse().getContentAsString());
    }

    private MockHttpServletRequestBuilder autenticado(MockHttpServletRequestBuilder req) {
        return req.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private MockHttpServletRequestBuilder comCorpo(MockHttpServletRequestBuilder req, Object corpo) throws Exception {
        return autenticado(req).contentType(MediaType.APPLICATION_JSON)
                .content(corpo instanceof String s ? s : json.writeValueAsString(corpo));
    }
}
