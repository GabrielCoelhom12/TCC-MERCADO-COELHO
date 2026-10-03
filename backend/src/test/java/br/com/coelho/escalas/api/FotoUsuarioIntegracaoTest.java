package br.com.coelho.escalas.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Foto de perfil dos usuários: validada, recortada e reduzida no servidor. */
class FotoUsuarioIntegracaoTest extends IntegracaoTest {

    @Test
    void enviaFotoQueEhRecortadaEReduzida() throws Exception {
        long id = criarUsuario("foto1");

        JsonNode resposta = corpo(enviarArquivo("/api/usuarios/" + id + "/foto", imagem("png", 800, 500))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.foto", startsWith("data:image/jpeg;base64,"))));

        BufferedImage salva = decodificar(resposta.get("foto").asText());
        assertThat(salva.getWidth()).isEqualTo(256);
        assertThat(salva.getHeight()).isEqualTo(256);
        get("/api/usuarios").andExpect(jsonPath("$[?(@.login == 'foto1')].foto").value(org.hamcrest.Matchers.hasItem(startsWith("data:image/jpeg"))));
    }

    @Test
    void aceitaJpeg() throws Exception {
        long id = criarUsuario("foto2");
        enviarArquivo("/api/usuarios/" + id + "/foto", imagem("jpg", 300, 300)).andExpect(status().isOk());
    }

    @Test
    void recusaArquivoQueNaoEhImagemMesmoComTipoDeImagem() throws Exception {
        long id = criarUsuario("foto3");
        MockMultipartFile falso = new MockMultipartFile("arquivo", "foto.png", "image/png", "<script>alert(1)</script>".getBytes());
        enviarArquivo("/api/usuarios/" + id + "/foto", falso)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem", org.hamcrest.Matchers.containsString("PNG ou JPEG")));
    }

    @Test
    void recusaArquivoMaiorQue2Mb() throws Exception {
        long id = criarUsuario("foto4");
        MockMultipartFile grande = new MockMultipartFile("arquivo", "foto.png", "image/png", new byte[2 * 1024 * 1024 + 1]);
        enviarArquivo("/api/usuarios/" + id + "/foto", grande)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem", org.hamcrest.Matchers.containsString("2 MB")));
    }

    @Test
    void removeFoto() throws Exception {
        long id = criarUsuario("foto5");
        enviarArquivo("/api/usuarios/" + id + "/foto", imagem("png", 100, 100)).andExpect(status().isOk());

        delete("/api/usuarios/" + id + "/foto")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.foto").doesNotExist());
    }

    @Test
    void loginDevolveAFotoDoUsuario() throws Exception {
        long id = criarUsuario("foto6");
        enviarArquivo("/api/usuarios/" + id + "/foto", imagem("png", 120, 120)).andExpect(status().isOk());

        login("foto6", "Senha@Forte1").andExpect(jsonPath("$.foto", startsWith("data:image/jpeg;base64,")));
        entrarComo("foto6", "Senha@Forte1");
        get("/api/auth/me").andExpect(jsonPath("$.foto", startsWith("data:image/jpeg;base64,")));
    }

    @Test
    void somenteAdministradorAlteraFotos() throws Exception {
        long id = criarUsuario("foto7");
        entrarComo("foto7", "Senha@Forte1");
        enviarArquivo("/api/usuarios/" + id + "/foto", imagem("png", 50, 50)).andExpect(status().isForbidden());
    }

    @Test
    void usuarioInexistenteDevolve404() throws Exception {
        enviarArquivo("/api/usuarios/999999/foto", imagem("png", 50, 50)).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ auxiliares

    private long criarUsuario(String login) throws Exception {
        return corpo(post("/api/usuarios", Map.of("login", login, "nome", "Teste", "perfil", "GESTOR", "senha", "Senha@Forte1"))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private static MockMultipartFile imagem(String formato, int largura, int altura) throws Exception {
        BufferedImage img = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(255, 126, 49));
        g.fillRect(0, 0, largura, altura);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, formato, out);
        String tipo = formato.equals("png") ? "image/png" : "image/jpeg";
        return new MockMultipartFile("arquivo", "foto." + formato, tipo, out.toByteArray());
    }

    private static BufferedImage decodificar(String dataUrl) throws Exception {
        byte[] bytes = Base64.getDecoder().decode(dataUrl.substring(dataUrl.indexOf(',') + 1));
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }
}
