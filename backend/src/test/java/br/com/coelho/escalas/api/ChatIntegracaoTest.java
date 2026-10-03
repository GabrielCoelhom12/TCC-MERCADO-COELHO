package br.com.coelho.escalas.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Chat entre usuários: conversas privadas, grupos, mensagens, imagens, menções a escalas e leitura. */
class ChatIntegracaoTest extends IntegracaoTest {

    private static final String SENHA = "Senha@Forte1";

    private long gestor;
    private long ana;
    private long bruno;

    @BeforeEach
    void criarUsuarios() throws Exception {
        gestor = idDoUsuario("gestor");
        ana = criarUsuario("ana.chat", "Ana Chat");
        bruno = criarUsuario("bruno.chat", "Bruno Chat");
    }

    // ------------------------------------------------------------------ contatos

    @Test
    void listaUsuariosAtivosParaConversar() throws Exception {
        get("/api/chat/usuarios")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nome", hasItems("Ana Chat", "Bruno Chat")))
                .andExpect(jsonPath("$[*].id", not(hasItem((int) gestor))))
                .andExpect(jsonPath("$[0].online").value(false))
                .andExpect(jsonPath("$[0].senhaHash").doesNotExist());
    }

    // ------------------------------------------------------------------ conversas privadas

    @Test
    void abreConversaPrivadaUmaUnicaVezPorPar() throws Exception {
        long id = abrirDireta(ana);
        assertThat(abrirDireta(ana)).isEqualTo(id);

        entrarComo("ana.chat", SENHA);
        assertThat(abrirDireta(gestor)).as("a mesma conversa vista pelo outro lado").isEqualTo(id);
        get("/api/chat/conversas")
                .andExpect(jsonPath("$[0].tipo").value("DIRETA"))
                .andExpect(jsonPath("$[0].nome").value("Gerência Supermercado Coelho"))
                .andExpect(jsonPath("$[0].participantes", hasSize(2)));
    }

    @Test
    void naoAbreConversaConsigoMesmo() throws Exception {
        post("/api/chat/conversas/diretas", Map.of("usuarioId", gestor)).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void naoAbreConversaComUsuarioInexistente() throws Exception {
        post("/api/chat/conversas/diretas", Map.of("usuarioId", 999999)).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ mensagens e leitura

    @Test
    void enviaMensagensEControlaNaoLidas() throws Exception {
        long conversa = abrirDireta(ana);
        enviar(conversa, "Bom dia, Ana!");
        long ultima = enviar(conversa, "  Pode cobrir o sábado?  ");

        entrarComo("ana.chat", SENHA);
        get("/api/chat/conversas")
                .andExpect(jsonPath("$[0].naoLidas").value(2))
                .andExpect(jsonPath("$[0].ultimaMensagem.texto").value("Pode cobrir o sábado?"))
                .andExpect(jsonPath("$[0].ultimaMensagem.autorNome").value("Gerência Supermercado Coelho"));
        get("/api/chat/conversas/" + conversa + "/mensagens")
                .andExpect(jsonPath("$[*].texto", contains("Bom dia, Ana!", "Pode cobrir o sábado?")));

        post("/api/chat/conversas/" + conversa + "/lida", Map.of()).andExpect(status().isNoContent());
        get("/api/chat/conversas").andExpect(jsonPath("$[0].naoLidas").value(0));

        entrarComo("gestor", "senha-de-teste");
        get("/api/chat/conversas").andExpect(jsonPath("$[0].leituras['" + ana + "']").value((int) ultima));
    }

    @Test
    void minhasMensagensNaoContamComoNaoLidas() throws Exception {
        long conversa = abrirDireta(ana);
        enviar(conversa, "Oi");
        get("/api/chat/conversas").andExpect(jsonPath("$[0].naoLidas").value(0));
    }

    @Test
    void recusaMensagemVaziaOuLongaDemais() throws Exception {
        long conversa = abrirDireta(ana);
        post("/api/chat/conversas/" + conversa + "/mensagens", Map.of("texto", "   "))
                .andExpect(status().isUnprocessableEntity());
        post("/api/chat/conversas/" + conversa + "/mensagens", Map.of("texto", "x".repeat(4001)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void quemNaoParticipaNaoVeNemEscreve() throws Exception {
        long conversa = abrirDireta(ana);
        long msg = enviar(conversa, "Confidencial");

        entrarComo("bruno.chat", SENHA);
        get("/api/chat/conversas").andExpect(jsonPath("$", empty()));
        get("/api/chat/conversas/" + conversa + "/mensagens").andExpect(status().isNotFound());
        post("/api/chat/conversas/" + conversa + "/mensagens", Map.of("texto", "intruso")).andExpect(status().isNotFound());
        post("/api/chat/conversas/" + conversa + "/lida", Map.of()).andExpect(status().isNotFound());
        get("/api/chat/mensagens/" + msg + "/imagem").andExpect(status().isNotFound());
    }

    @Test
    void paginaOHistoricoDoMaisRecenteParaOMaisAntigo() throws Exception {
        long conversa = abrirDireta(ana);
        long[] ids = new long[5];
        for (int i = 0; i < 5; i++) {
            ids[i] = enviar(conversa, "m" + i);
        }
        get("/api/chat/conversas/" + conversa + "/mensagens?limite=2")
                .andExpect(jsonPath("$[*].texto", contains("m3", "m4")));
        get("/api/chat/conversas/" + conversa + "/mensagens?limite=2&antesDe=" + ids[3])
                .andExpect(jsonPath("$[*].texto", contains("m1", "m2")));
    }

    @Test
    void mencionaUmaEscala() throws Exception {
        JsonNode escala = corpo(get("/api/escalas")).get(0);
        long conversa = abrirDireta(ana);

        post("/api/chat/conversas/" + conversa + "/mensagens",
                Map.of("texto", "Confere esta escala", "escalaId", escala.get("id").asLong()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.escala.id").value(escala.get("id").asInt()))
                .andExpect(jsonPath("$.escala.nome").value(escala.get("nome").asText()));

        // A menção sozinha já é uma mensagem válida.
        post("/api/chat/conversas/" + conversa + "/mensagens", Map.of("escalaId", escala.get("id").asLong()))
                .andExpect(status().isCreated());
        post("/api/chat/conversas/" + conversa + "/mensagens", Map.of("texto", "x", "escalaId", 999999))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ imagens

    @Test
    void enviaImagemQueSoParticipantesConseguemVer() throws Exception {
        long conversa = abrirDireta(ana);
        JsonNode msg = corpo(enviarArquivo(HttpMethod.POST, "/api/chat/conversas/" + conversa + "/imagens",
                png(2400, 1200), Map.of("texto", "Quadro de horários"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.texto").value("Quadro de horários"))
                .andExpect(jsonPath("$.imagem", startsWith("/api/chat/mensagens/"))));

        byte[] bytes = get(msg.get("imagem").asText())
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(header().string("Cache-Control", containsString("private")))
                .andReturn().getResponse().getContentAsByteArray();
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
        assertThat(Math.max(img.getWidth(), img.getHeight())).as("reduzida").isEqualTo(1600);
        assertThat(img.getWidth()).as("mantém a proporção").isEqualTo(2 * img.getHeight());

        get("/api/chat/conversas").andExpect(jsonPath("$[0].ultimaMensagem.texto").value("Quadro de horários"));
    }

    @Test
    void recusaArquivoQueNaoEhImagem() throws Exception {
        long conversa = abrirDireta(ana);
        MockMultipartFile falso = new MockMultipartFile("arquivo", "x.png", "image/png", "nada".getBytes());
        enviarArquivo(HttpMethod.POST, "/api/chat/conversas/" + conversa + "/imagens", falso, Map.of())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem", containsString("PNG ou JPEG")));
    }

    // ------------------------------------------------------------------ grupos

    @Test
    void criaGrupoVisivelParaTodosOsMembros() throws Exception {
        long grupo = criarGrupo("Escala do fim de semana", List.of(ana, bruno));
        enviar(grupo, "Pessoal, conferem o sábado?");

        entrarComo("bruno.chat", SENHA);
        get("/api/chat/conversas")
                .andExpect(jsonPath("$[0].id").value((int) grupo))
                .andExpect(jsonPath("$[0].tipo").value("GRUPO"))
                .andExpect(jsonPath("$[0].nome").value("Escala do fim de semana"))
                .andExpect(jsonPath("$[0].participantes", hasSize(3)))
                .andExpect(jsonPath("$[0].naoLidas").value(1));
    }

    @Test
    void grupoPrecisaDeNomeEDeOutrosMembros() throws Exception {
        post("/api/chat/conversas/grupos", Map.of("nome", " ", "participantes", List.of(ana)))
                .andExpect(status().isBadRequest());
        post("/api/chat/conversas/grupos", Map.of("nome", "Só eu", "participantes", List.of(gestor)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void membrosAdicionamPessoasERenomeiamOGrupo() throws Exception {
        long grupo = criarGrupo("Padaria", List.of(ana));

        entrarComo("ana.chat", SENHA);
        post("/api/chat/conversas/" + grupo + "/participantes", Map.of("usuarios", List.of(bruno)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantes", hasSize(3)));
        put("/api/chat/conversas/" + grupo, Map.of("nome", "Padaria e confeitaria"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Padaria e confeitaria"));

        entrarComo("bruno.chat", SENHA);
        get("/api/chat/conversas").andExpect(jsonPath("$[0].nome").value("Padaria e confeitaria"));
    }

    @Test
    void membroSaiDoGrupoEPerdeOAcesso() throws Exception {
        long grupo = criarGrupo("Açougue", List.of(ana, bruno));

        entrarComo("ana.chat", SENHA);
        delete("/api/chat/conversas/" + grupo + "/participantes/eu").andExpect(status().isNoContent());
        get("/api/chat/conversas").andExpect(jsonPath("$", empty()));
        get("/api/chat/conversas/" + grupo + "/mensagens").andExpect(status().isNotFound());

        entrarComo("bruno.chat", SENHA);
        get("/api/chat/conversas").andExpect(jsonPath("$[0].participantes", hasSize(2)));
    }

    @Test
    void conversaPrivadaNaoPodeSerRenomeadaNemAbandonada() throws Exception {
        long conversa = abrirDireta(ana);
        put("/api/chat/conversas/" + conversa, Map.of("nome", "X")).andExpect(status().isUnprocessableEntity());
        delete("/api/chat/conversas/" + conversa + "/participantes/eu").andExpect(status().isUnprocessableEntity());
        post("/api/chat/conversas/" + conversa + "/participantes", Map.of("usuarios", List.of(bruno)))
                .andExpect(status().isUnprocessableEntity());
    }

    // ------------------------------------------------------------------ auxiliares

    private long criarUsuario(String login, String nome) throws Exception {
        return corpo(post("/api/usuarios", Map.of("login", login, "nome", nome, "perfil", "GESTOR", "senha", SENHA))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private long idDoUsuario(String login) throws Exception {
        for (JsonNode u : corpo(get("/api/usuarios"))) {
            if (u.get("login").asText().equals(login)) {
                return u.get("id").asLong();
            }
        }
        throw new AssertionError(login);
    }

    private long abrirDireta(long usuarioId) throws Exception {
        return corpo(post("/api/chat/conversas/diretas", Map.of("usuarioId", usuarioId)).andExpect(status().isOk()))
                .get("id").asLong();
    }

    private long criarGrupo(String nome, List<Long> membros) throws Exception {
        return corpo(post("/api/chat/conversas/grupos", Map.of("nome", nome, "participantes", membros))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private long enviar(long conversa, String texto) throws Exception {
        return corpo(post("/api/chat/conversas/" + conversa + "/mensagens", Map.of("texto", texto))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private static MockMultipartFile png(int largura, int altura) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB), "png", out);
        return new MockMultipartFile("arquivo", "quadro.png", "image/png", out.toByteArray());
    }
}
