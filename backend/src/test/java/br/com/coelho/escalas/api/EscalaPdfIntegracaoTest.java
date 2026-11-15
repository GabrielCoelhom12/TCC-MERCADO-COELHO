package br.com.coelho.escalas.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PDF da escala para imprimir ou baixar: logo, colaboradores por setor, turnos, folgas e ausências. */
class EscalaPdfIntegracaoTest extends IntegracaoTest {

    private static final String SEGUNDA = "2031-03-03";
    private static final String DOMINGO = "2031-03-09";

    @Test
    void baixaOPdfDaSemanaComTurnosFolgasEAusencias() throws Exception {
        long escala = corpo(post("/api/escalas", Map.of("nome", "Semana de março", "dataInicio", SEGUNDA, "dataFim", DOMINGO))
                .andExpect(status().isCreated())).get("id").asLong();
        post("/api/escalas/" + escala + "/gerar", Map.of()).andExpect(status().isOk());
        JsonNode matriz = corpo(get("/api/escalas/" + escala + "/matriz"));
        JsonNode turno = matriz.at("/turnos/0");
        String nome = nomeDo(matriz, turno.get("funcionarioId").asLong());
        // Um atestado aparece só como "Ausente": dado de saúde não vai para a escala impressa (LGPD).
        long afastado = turno.get("funcionarioId").asLong();
        post("/api/afastamentos", Map.of("funcionarioId", afastado, "motivo", "ATESTADO", "inicio", DOMINGO, "fim", DOMINGO))
                .andExpect(status().isCreated());

        MockHttpServletResponse resposta = get("/api/escalas/" + escala + "/pdf").andExpect(status().isOk()).andReturn().getResponse();

        assertThat(resposta.getContentType()).isEqualTo(MediaType.APPLICATION_PDF_VALUE);
        assertThat(resposta.getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .startsWith("attachment").contains("escala-2031-03-03-a-2031-03-09.pdf");
        String texto = texto(resposta.getContentAsByteArray());
        assertThat(texto).contains("ESCALA DE TRABALHO", "Semana de março", "03/03/2031 a 09/03/2031", "Rascunho");
        assertThat(texto).contains(nome, "Folga", "Ausente", "Pessoas escaladas", "Supermercado Coelho");
        assertThat(texto).contains(turno.get("horaInicio").asText().substring(0, 5));
        assertThat(texto).doesNotContain("Atestado", "R$");
    }

    @Test
    void imprimirAbreOPdfNoNavegadorEmVezDeBaixar() throws Exception {
        long escala = corpo(post("/api/escalas", Map.of("dataInicio", SEGUNDA, "dataFim", DOMINGO))).get("id").asLong();

        String disposicao = get("/api/escalas/" + escala + "/pdf?imprimir=true").andExpect(status().isOk())
                .andReturn().getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION);

        assertThat(disposicao).startsWith("inline");
    }

    @Test
    void supervisorTambemBaixaEEscalaInexistenteDa404() throws Exception {
        long escala = corpo(post("/api/escalas", Map.of("dataInicio", SEGUNDA, "dataFim", DOMINGO))).get("id").asLong();
        post("/api/usuarios", Map.of("login", "sup.pdf", "nome", "Supervisor", "perfil", "SUPERVISOR", "senha", "Senha@Forte1"))
                .andExpect(status().isCreated());
        entrarComo("sup.pdf", "Senha@Forte1");

        get("/api/escalas/" + escala + "/pdf").andExpect(status().isOk());
        get("/api/escalas/999999/pdf").andExpect(status().isNotFound());
    }

    private static String texto(byte[] pdf) throws Exception {
        try (PdfReader leitor = new PdfReader(pdf)) {
            PdfTextExtractor extrator = new PdfTextExtractor(leitor);
            StringBuilder sb = new StringBuilder();
            for (int p = 1; p <= leitor.getNumberOfPages(); p++) {
                sb.append(extrator.getTextFromPage(p)).append('\n');
            }
            return sb.toString();
        }
    }

    private static String nomeDo(JsonNode matriz, long funcionario) {
        for (JsonNode f : matriz.get("funcionarios")) {
            if (f.get("id").asLong() == funcionario) {
                return f.get("nome").asText();
            }
        }
        throw new AssertionError("Colaborador " + funcionario + " não está na matriz");
    }
}
