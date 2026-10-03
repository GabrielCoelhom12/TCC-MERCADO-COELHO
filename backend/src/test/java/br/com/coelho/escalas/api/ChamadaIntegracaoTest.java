package br.com.coelho.escalas.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Chamadas de voz um a um: ciclo de vida, regras de ocupado, sinalização WebRTC e registro no histórico. */
class ChamadaIntegracaoTest extends IntegracaoTest {

    private static final String SENHA = "Senha@Forte1";

    private long gestor;
    private long ana;
    private long bruno;
    private long conversaComAna;

    @BeforeEach
    void preparar() throws Exception {
        gestor = idDoUsuario("gestor");
        ana = criarUsuario("ana.voz", "Ana Voz");
        bruno = criarUsuario("bruno.voz", "Bruno Voz");
        conversaComAna = abrirDireta(ana);
    }

    @Test
    void iniciaChamadaQueFicaTocando() throws Exception {
        post("/api/chat/conversas/" + conversaComAna + "/chamadas", Map.of())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("TOCANDO"))
                .andExpect(jsonPath("$.conversaId").value((int) conversaComAna))
                .andExpect(jsonPath("$.chamador.id").value((int) gestor))
                .andExpect(jsonPath("$.destinatario.id").value((int) ana))
                .andExpect(jsonPath("$.destinatario.nome").value("Ana Voz"));
    }

    @Test
    void naoLigaParaGrupo() throws Exception {
        long grupo = corpo(post("/api/chat/conversas/grupos", Map.of("nome", "Turma", "participantes", List.of(ana, bruno)))
                .andExpect(status().isCreated())).get("id").asLong();
        post("/api/chat/conversas/" + grupo + "/chamadas", Map.of())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem", containsString("conversas privadas")));
    }

    @Test
    void quemNaoParticipaNaoLigaNemSinaliza() throws Exception {
        long chamada = ligar(conversaComAna);
        entrarComo("bruno.voz", SENHA);
        post("/api/chat/conversas/" + conversaComAna + "/chamadas", Map.of()).andExpect(status().isNotFound());
        post("/api/chat/chamadas/" + chamada + "/atender", Map.of()).andExpect(status().isNotFound());
        post("/api/chat/chamadas/" + chamada + "/sinal", Map.of("tipo", "offer", "dados", "{}")).andExpect(status().isNotFound());
        post("/api/chat/chamadas/" + chamada + "/encerrar", Map.of()).andExpect(status().isNotFound());
    }

    @Test
    void atendidaEEncerradaFicaNoHistoricoComADuracao() throws Exception {
        long chamada = ligar(conversaComAna);

        entrarComo("ana.voz", SENHA);
        post("/api/chat/chamadas/" + chamada + "/atender", Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_ANDAMENTO"))
                .andExpect(jsonPath("$.atendidaEm").isNotEmpty());

        entrarComo("gestor", "senha-de-teste");
        post("/api/chat/chamadas/" + chamada + "/encerrar", Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENCERRADA"));

        get("/api/chat/conversas/" + conversaComAna + "/mensagens")
                .andExpect(jsonPath("$[-1].chamada.resultado").value("ATENDIDA"))
                .andExpect(jsonPath("$[-1].chamada.duracaoSegundos", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$[-1].autorId").value((int) gestor))
                .andExpect(jsonPath("$[-1].texto").doesNotExist());
    }

    @Test
    void somenteQuemRecebeAtendeOuRecusa() throws Exception {
        long chamada = ligar(conversaComAna);
        post("/api/chat/chamadas/" + chamada + "/atender", Map.of())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem", containsString("quem recebe")));
        post("/api/chat/chamadas/" + chamada + "/recusar", Map.of()).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void recusadaFicaNoHistorico() throws Exception {
        long chamada = ligar(conversaComAna);
        entrarComo("ana.voz", SENHA);
        post("/api/chat/chamadas/" + chamada + "/recusar", Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECUSADA"));
        get("/api/chat/conversas/" + conversaComAna + "/mensagens")
                .andExpect(jsonPath("$[-1].chamada.resultado").value("RECUSADA"));
        post("/api/chat/chamadas/" + chamada + "/atender", Map.of()).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void canceladaAntesDeAtenderViraChamadaPerdidaNaoLida() throws Exception {
        long chamada = ligar(conversaComAna);
        post("/api/chat/chamadas/" + chamada + "/encerrar", Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PERDIDA"));

        entrarComo("ana.voz", SENHA);
        get("/api/chat/conversas")
                .andExpect(jsonPath("$[0].naoLidas").value(1))
                .andExpect(jsonPath("$[0].ultimaMensagem.chamada.resultado").value("PERDIDA"));
    }

    @Test
    void encerrarDuasVezesNaoDuplicaOHistorico() throws Exception {
        long chamada = ligar(conversaComAna);
        post("/api/chat/chamadas/" + chamada + "/encerrar", Map.of()).andExpect(status().isOk());
        post("/api/chat/chamadas/" + chamada + "/encerrar", Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PERDIDA"));
        get("/api/chat/conversas/" + conversaComAna + "/mensagens").andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void naoLigaParaQuemJaEstaEmChamada() throws Exception {
        ligar(conversaComAna);

        entrarComo("bruno.voz", SENHA);
        long brunoComAna = abrirDireta(ana);
        post("/api/chat/conversas/" + brunoComAna + "/chamadas", Map.of())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem", containsString("Ana Voz está em outra chamada")));

        long brunoComGestor = abrirDireta(gestor);
        post("/api/chat/conversas/" + brunoComGestor + "/chamadas", Map.of())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem", containsString("em outra chamada")));
    }

    @Test
    void depoisDeEncerradaPodeLigarDeNovo() throws Exception {
        long primeira = ligar(conversaComAna);
        post("/api/chat/chamadas/" + primeira + "/encerrar", Map.of()).andExpect(status().isOk());
        assertThat(ligar(conversaComAna)).isNotEqualTo(primeira);
    }

    @Test
    void sinalizacaoSoDuranteAChamadaEComTipoValido() throws Exception {
        long chamada = ligar(conversaComAna);
        post("/api/chat/chamadas/" + chamada + "/sinal", Map.of("tipo", "offer", "dados", "{\"sdp\":\"v=0\"}"))
                .andExpect(status().isNoContent());
        post("/api/chat/chamadas/" + chamada + "/sinal", Map.of("tipo", "ice", "dados", "{\"candidate\":\"x\"}"))
                .andExpect(status().isNoContent());
        post("/api/chat/chamadas/" + chamada + "/sinal", Map.of("tipo", "qualquer", "dados", "{}"))
                .andExpect(status().isBadRequest());
        post("/api/chat/chamadas/" + chamada + "/sinal", Map.of("tipo", "offer", "dados", "x".repeat(20_001)))
                .andExpect(status().isBadRequest());

        post("/api/chat/chamadas/" + chamada + "/encerrar", Map.of()).andExpect(status().isOk());
        post("/api/chat/chamadas/" + chamada + "/sinal", Map.of("tipo", "ice", "dados", "{}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void entregaServidoresIceComCredencialTurnTemporaria() throws Exception {
        JsonNode config = corpo(get("/api/chat/chamadas/config").andExpect(status().isOk()));
        JsonNode servidores = config.get("iceServers");
        assertThat(servidores.get(0).get("urls").get(0).asText()).startsWith("stun:");

        JsonNode turn = servidores.get(1);
        assertThat(turn.get("urls").get(0).asText()).isEqualTo("turn:turn.teste.local:3478");
        String usuario = turn.get("username").asText();
        assertThat(usuario).matches("\\d+:" + gestor);
        long expira = Long.parseLong(usuario.split(":")[0]);
        assertThat(expira).isGreaterThan(System.currentTimeMillis() / 1000);

        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec("segredo-turn-de-teste".getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        String esperada = Base64.getEncoder().encodeToString(mac.doFinal(usuario.getBytes(StandardCharsets.UTF_8)));
        assertThat(turn.get("credential").asText()).isEqualTo(esperada);
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

    private long ligar(long conversa) throws Exception {
        return corpo(post("/api/chat/conversas/" + conversa + "/chamadas", Map.of()).andExpect(status().isCreated()))
                .get("id").asLong();
    }
}
