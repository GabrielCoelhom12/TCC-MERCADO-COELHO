package br.com.coelho.escalas.api;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Foto dos colaboradores: mesmo tratamento seguro da foto de usuário. */
class FotoColaboradorIntegracaoTest extends IntegracaoTest {

    @Test
    void enviaFotoDoColaborador() throws Exception {
        long id = criarColaborador();

        enviarArquivo("/api/funcionarios/" + id + "/foto", png(400, 300))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.foto", startsWith("data:image/jpeg;base64,")));

        get("/api/funcionarios").andExpect(jsonPath("$[?(@.id == " + id + ")].foto", hasItem(startsWith("data:image/jpeg"))));
    }

    @Test
    void naoExpoeOsBytesDaFoto() throws Exception {
        long id = criarColaborador();
        enviarArquivo("/api/funcionarios/" + id + "/foto", png(50, 50)).andExpect(status().isOk());
        get("/api/funcionarios").andExpect(jsonPath("$[?(@.id == " + id + ")].fotoDataUrl", empty()));
    }

    @Test
    void editarOsDadosMantemAFoto() throws Exception {
        long id = criarColaborador();
        enviarArquivo("/api/funcionarios/" + id + "/foto", png(80, 80)).andExpect(status().isOk());

        put("/api/funcionarios/" + id, Map.of("nome", "Zé Novo", "setorId", setor(), "salarioMensal", 2000))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.foto", startsWith("data:image/jpeg")));
    }

    @Test
    void removeFotoDoColaborador() throws Exception {
        long id = criarColaborador();
        enviarArquivo("/api/funcionarios/" + id + "/foto", png(80, 80)).andExpect(status().isOk());

        delete("/api/funcionarios/" + id + "/foto")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.foto").doesNotExist());
    }

    @Test
    void recusaArquivoInvalido() throws Exception {
        long id = criarColaborador();
        MockMultipartFile texto = new MockMultipartFile("arquivo", "x.jpg", "image/jpeg", "não é imagem".getBytes());
        enviarArquivo("/api/funcionarios/" + id + "/foto", texto)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem", containsString("PNG ou JPEG")));
    }

    @Test
    void gestorTambemPodeDefinirFotoDeColaborador() throws Exception {
        long id = criarColaborador();
        post("/api/usuarios", Map.of("login", "gestora", "nome", "Gestora", "perfil", "GESTOR", "senha", "Senha@Forte1"))
                .andExpect(status().isCreated());
        entrarComo("gestora", "Senha@Forte1");

        enviarArquivo("/api/funcionarios/" + id + "/foto", png(60, 60)).andExpect(status().isOk());
    }

    @Test
    void colaboradorInexistenteDevolve404() throws Exception {
        enviarArquivo("/api/funcionarios/999999/foto", png(60, 60)).andExpect(status().isNotFound());
        delete("/api/funcionarios/999999/foto").andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ auxiliares

    private long setor() throws Exception {
        return corpo(get("/api/setores")).get(0).get("id").asLong();
    }

    private long criarColaborador() throws Exception {
        return corpo(post("/api/funcionarios", Map.of("nome", "Zé Foto", "setorId", setor(), "salarioMensal", 2000))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private static MockMultipartFile png(int largura, int altura) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB), "png", out);
        return new MockMultipartFile("arquivo", "foto.png", "image/png", out.toByteArray());
    }
}
