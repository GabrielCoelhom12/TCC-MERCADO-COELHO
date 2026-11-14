package br.com.coelho.escalas.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Documentos anexados a um afastamento (atestado, declaração...). São dado de saúde: qualquer perfil pode enviar, mas
 * só a gerência abre ou remove.
 */
class DocumentoAfastamentoIntegracaoTest extends IntegracaoTest {

    private static final byte[] PDF = "%PDF-1.4\n1 0 obj << >> endobj\ntrailer << >>\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);

    @Test
    void anexaListaAbreERemoveUmPdf() throws Exception {
        long afastamento = criarAfastamento();

        JsonNode doc = corpo(enviarArquivo(HttpMethod.POST, "/api/afastamentos/" + afastamento + "/documentos",
                new MockMultipartFile("arquivo", "atestado março.pdf", "application/pdf", PDF), Map.of())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nomeArquivo").value("atestado março.pdf"))
                .andExpect(jsonPath("$.tipo").value("application/pdf"))
                .andExpect(jsonPath("$.enviadoPor").value("gestor"))
                .andExpect(jsonPath("$.conteudo").doesNotExist()));
        long id = doc.get("id").asLong();

        get("/api/afastamentos/" + afastamento + "/documentos").andExpect(jsonPath("$[*].id", contains((int) id)));
        get("/api/afastamentos").andExpect(jsonPath("$[?(@.id == " + afastamento + ")].documentos", contains(1)));

        MockHttpServletResponse arquivo = get("/api/afastamentos/documentos/" + id).andExpect(status().isOk()).andReturn().getResponse();
        assertThat(arquivo.getContentType()).isEqualTo("application/pdf");
        assertThat(arquivo.getContentAsByteArray()).isEqualTo(PDF);
        assertThat(arquivo.getHeader(HttpHeaders.CONTENT_DISPOSITION)).startsWith("inline").contains("filename");
        assertThat(arquivo.getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");

        delete("/api/afastamentos/documentos/" + id).andExpect(status().isNoContent());
        get("/api/afastamentos/documentos/" + id).andExpect(status().isNotFound());
    }

    @Test
    void fotoDoAtestadoERegravadaEmJpeg() throws Exception {
        long afastamento = criarAfastamento();

        long id = corpo(enviarArquivo(HttpMethod.POST, "/api/afastamentos/" + afastamento + "/documentos", png(), Map.of())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("image/jpeg"))
                .andExpect(jsonPath("$.nomeArquivo").value("foto do atestado.jpg"))).get("id").asLong();

        byte[] salvo = get("/api/afastamentos/documentos/" + id).andReturn().getResponse().getContentAsByteArray();
        assertThat(salvo[0] & 0xFF).isEqualTo(0xFF);
        assertThat(salvo[1] & 0xFF).isEqualTo(0xD8);
    }

    @Test
    void recusaArquivosQueNaoSaoPdfNemImagem() throws Exception {
        long afastamento = criarAfastamento();
        String url = "/api/afastamentos/" + afastamento + "/documentos";

        enviarArquivo(HttpMethod.POST, url, new MockMultipartFile("arquivo", "virus.pdf", "application/pdf",
                "MZ isto não é um pdf".getBytes(StandardCharsets.UTF_8)), Map.of())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem", containsString("PDF, JPG ou PNG")));
        enviarArquivo(HttpMethod.POST, url, new MockMultipartFile("arquivo", "vazio.pdf", "application/pdf", new byte[0]), Map.of())
                .andExpect(status().isUnprocessableEntity());
        enviarArquivo(HttpMethod.POST, "/api/afastamentos/999999/documentos",
                new MockMultipartFile("arquivo", "a.pdf", "application/pdf", PDF), Map.of())
                .andExpect(status().isNotFound());
    }

    @Test
    void supervisorEnviaMasNaoAbreNemRemove() throws Exception {
        long afastamento = criarAfastamento();
        post("/api/usuarios", Map.of("login", "sup.doc", "nome", "Supervisor", "perfil", "SUPERVISOR", "senha", "Senha@Forte1"))
                .andExpect(status().isCreated());
        entrarComo("sup.doc", "Senha@Forte1");

        long id = corpo(enviarArquivo(HttpMethod.POST, "/api/afastamentos/" + afastamento + "/documentos",
                new MockMultipartFile("arquivo", "atestado.pdf", "application/pdf", PDF), Map.of())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.enviadoPor").value("sup.doc"))).get("id").asLong();

        get("/api/afastamentos/" + afastamento + "/documentos").andExpect(status().isOk());
        get("/api/afastamentos/documentos/" + id).andExpect(status().isForbidden());
        delete("/api/afastamentos/documentos/" + id).andExpect(status().isForbidden());
    }

    @Test
    void removerOAfastamentoApagaOsDocumentos() throws Exception {
        long afastamento = criarAfastamento();
        long id = corpo(enviarArquivo(HttpMethod.POST, "/api/afastamentos/" + afastamento + "/documentos",
                new MockMultipartFile("arquivo", "atestado.pdf", "application/pdf", PDF), Map.of())).get("id").asLong();

        delete("/api/afastamentos/" + afastamento).andExpect(status().isNoContent());

        get("/api/afastamentos/documentos/" + id).andExpect(status().isNotFound());
    }

    @Test
    void limiteDeDezDocumentosPorAfastamento() throws Exception {
        long afastamento = criarAfastamento();
        String url = "/api/afastamentos/" + afastamento + "/documentos";
        for (int i = 0; i < 10; i++) {
            enviarArquivo(HttpMethod.POST, url, new MockMultipartFile("arquivo", "doc" + i + ".pdf", "application/pdf", PDF), Map.of())
                    .andExpect(status().isCreated());
        }
        enviarArquivo(HttpMethod.POST, url, new MockMultipartFile("arquivo", "doc10.pdf", "application/pdf", PDF), Map.of())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem", containsString("10 documentos")));
    }

    // ------------------------------------------------------------------ auxiliares

    private long criarAfastamento() throws Exception {
        long funcionario = corpo(get("/api/funcionarios")).get(0).get("id").asLong();
        return corpo(post("/api/afastamentos", Map.of("funcionarioId", funcionario, "motivo", "ATESTADO",
                "inicio", "2031-03-03", "fim", "2031-03-04")).andExpect(status().isCreated())).at("/ausencia/id").asLong();
    }

    private static MockMultipartFile png() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(600, 800, BufferedImage.TYPE_INT_RGB), "png", out);
        return new MockMultipartFile("arquivo", "foto do atestado.png", "image/png", out.toByteArray());
    }
}
