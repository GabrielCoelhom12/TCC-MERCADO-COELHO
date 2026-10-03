package br.com.coelho.escalas.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Entrega em tempo real via WebSocket/STOMP com um cliente real. Sem transação de teste: o servidor (outra thread)
 * precisa enxergar os dados, que são removidos ao final.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ChatWebSocketTest extends IntegracaoTest {

    private static final String SENHA = "Senha@Forte1";

    @LocalServerPort
    private int porta;

    @Autowired
    private JdbcTemplate jdbc;

    private final String sufixo = UUID.randomUUID().toString().substring(0, 8);
    private final List<Long> usuariosCriados = new ArrayList<>();
    private final List<StompSession> sessoes = new ArrayList<>();
    private WebSocketStompClient cliente;

    @BeforeEach
    void prepararCliente() {
        cliente = new WebSocketStompClient(new StandardWebSocketClient());
        MappingJackson2MessageConverter conversor = new MappingJackson2MessageConverter();
        conversor.setObjectMapper(json);
        cliente.setMessageConverter(conversor);
    }

    @AfterEach
    void limpar() {
        sessoes.stream().filter(StompSession::isConnected).forEach(StompSession::disconnect);
        cliente.stop();
        if (usuariosCriados.isEmpty()) {
            return;
        }
        String ids = usuariosCriados.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElseThrow();
        String conversas = "select conversa_id from participante_conversa where usuario_id in (" + ids + ")";
        jdbc.update("delete from mensagem where conversa_id in (" + conversas + ")");
        jdbc.update("delete from chamada where conversa_id in (" + conversas + ")");
        jdbc.update("delete from anexo_imagem where conversa_id in (" + conversas + ")");
        List<Long> idsConversas = jdbc.queryForList(conversas, Long.class);
        if (!idsConversas.isEmpty()) {
            String lista = idsConversas.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElseThrow();
            jdbc.update("delete from participante_conversa where conversa_id in (" + lista + ")");
            jdbc.update("delete from conversa where id in (" + lista + ")");
        }
        jdbc.update("delete from usuario where id in (" + ids + ")");
    }

    @Test
    void conexaoSemTokenEhRecusada() {
        assertThatThrownBy(() -> conectar(null)).isInstanceOf(Exception.class);
    }

    @Test
    void conexaoComTokenInvalidoEhRecusada() {
        assertThatThrownBy(() -> conectar("token-falso")).isInstanceOf(Exception.class);
    }

    @Test
    void mensagemChegaEmTempoRealSoParaOsParticipantes() throws Exception {
        long ana = criarUsuario("ana");
        criarUsuario("bruno");
        String tokenGestor = token();

        BlockingQueue<JsonNode> eventosAna = inscrever(conectarComo("ana"), "/user/queue/chat");
        BlockingQueue<JsonNode> eventosBruno = inscrever(conectarComo("bruno"), "/user/queue/chat");
        Thread.sleep(400); // tempo para o broker registrar as inscrições

        usarToken(tokenGestor);
        long conversa = corpo(post("/api/chat/conversas/diretas", Map.of("usuarioId", ana))).get("id").asLong();
        post("/api/chat/conversas/" + conversa + "/mensagens", Map.of("texto", "Oi, Ana!")).andExpect(status().isCreated());

        JsonNode evento = aguardar(eventosAna, e -> e.path("tipo").asText().equals("MENSAGEM"));
        assertThat(evento.path("conversaId").asLong()).isEqualTo(conversa);
        assertThat(evento.at("/mensagem/texto").asText()).isEqualTo("Oi, Ana!");
        assertThat(eventosBruno.poll(1, TimeUnit.SECONDS)).as("Bruno não participa da conversa").isNull();
    }

    @Test
    void avisaQuemEstaOnline() throws Exception {
        long ana = criarUsuario("ana");
        criarUsuario("bruno");
        String tokenGestor = token();

        BlockingQueue<JsonNode> presencas = inscrever(conectarComo("bruno"), "/topic/presenca");
        Thread.sleep(400);
        StompSession sessaoAna = conectarComo("ana");

        JsonNode online = aguardar(presencas, e -> e.path("usuarioId").asLong() == ana);
        assertThat(online.path("online").asBoolean()).isTrue();

        usarToken(tokenGestor);
        JsonNode contatos = corpo(get("/api/chat/usuarios"));
        assertThat(contatos.findValues("id").stream().anyMatch(id -> id.asLong() == ana)).isTrue();
        for (JsonNode c : contatos) {
            if (c.get("id").asLong() == ana) {
                assertThat(c.get("online").asBoolean()).isTrue();
            }
        }

        sessaoAna.disconnect();
        JsonNode offline = aguardar(presencas, e -> e.path("usuarioId").asLong() == ana && !e.path("online").asBoolean());
        assertThat(offline).isNotNull();
    }

    @Test
    void chamadaDeVozESinalizacaoChegamEmTempoReal() throws Exception {
        long ana = criarUsuario("ana");
        String tokenGestor = token();
        BlockingQueue<JsonNode> eventosAna = inscrever(conectarComo("ana"), "/user/queue/chat");
        Thread.sleep(400);

        usarToken(tokenGestor);
        long conversa = corpo(post("/api/chat/conversas/diretas", Map.of("usuarioId", ana))).get("id").asLong();
        long chamada = corpo(post("/api/chat/conversas/" + conversa + "/chamadas", Map.of()).andExpect(status().isCreated()))
                .get("id").asLong();

        JsonNode convite = aguardar(eventosAna, e -> e.path("tipo").asText().equals("CHAMADA"));
        assertThat(convite.at("/chamada/id").asLong()).isEqualTo(chamada);
        assertThat(convite.at("/chamada/status").asText()).isEqualTo("TOCANDO");

        post("/api/chat/chamadas/" + chamada + "/sinal", Map.of("tipo", "offer", "dados", "{\"sdp\":\"v=0\"}"))
                .andExpect(status().isNoContent());
        JsonNode sinal = aguardar(eventosAna, e -> e.path("tipo").asText().equals("SINAL"));
        assertThat(sinal.path("chamadaId").asLong()).isEqualTo(chamada);
        assertThat(sinal.at("/sinal/tipo").asText()).isEqualTo("offer");
        assertThat(sinal.at("/sinal/dados").asText()).isEqualTo("{\"sdp\":\"v=0\"}");
    }

    @Test
    void quemFechaAPaginaDerrubaAChamada() throws Exception {
        long ana = criarUsuario("ana");
        String tokenGestor = token();
        StompSession sessaoAna = conectarComo("ana");
        BlockingQueue<JsonNode> eventosAna = inscrever(sessaoAna, "/user/queue/chat");
        Thread.sleep(400);

        usarToken(tokenGestor);
        long conversa = corpo(post("/api/chat/conversas/diretas", Map.of("usuarioId", ana))).get("id").asLong();
        post("/api/chat/conversas/" + conversa + "/chamadas", Map.of()).andExpect(status().isCreated());
        aguardar(eventosAna, e -> e.path("tipo").asText().equals("CHAMADA"));

        sessaoAna.disconnect();
        long limite = System.currentTimeMillis() + 5000;
        String resultado = "";
        while (System.currentTimeMillis() < limite && resultado.isEmpty()) {
            Thread.sleep(200);
            JsonNode ultimas = corpo(get("/api/chat/conversas/" + conversa + "/mensagens"));
            resultado = ultimas.isEmpty() ? "" : ultimas.get(ultimas.size() - 1).at("/chamada/resultado").asText();
        }
        assertThat(resultado).isEqualTo("PERDIDA");
    }

    @Test
    void naoPermiteInscricaoEmOutrosDestinos() throws Exception {
        criarUsuario("ana");
        StompSession sessao = conectarComo("ana");
        sessao.subscribe("/topic/segredos", new StompSessionHandlerAdapter() {
        });
        Thread.sleep(800);
        assertThat(sessao.isConnected()).as("o servidor encerra a sessão").isFalse();
    }

    // ------------------------------------------------------------------ auxiliares

    private long criarUsuario(String nome) throws Exception {
        long id = corpo(post("/api/usuarios", Map.of("login", nome + "-ws-" + sufixo, "nome", nome, "perfil", "GESTOR", "senha", SENHA))
                .andExpect(status().isCreated())).get("id").asLong();
        usuariosCriados.add(id);
        return id;
    }

    private StompSession conectarComo(String nome) throws Exception {
        String anterior = token();
        entrarComo(nome + "-ws-" + sufixo, SENHA);
        String tokenUsuario = token();
        usarToken(anterior);
        return conectar(tokenUsuario);
    }

    private StompSession conectar(String tokenJwt) throws Exception {
        StompHeaders cabecalhos = new StompHeaders();
        if (tokenJwt != null) {
            cabecalhos.add("Authorization", "Bearer " + tokenJwt);
        }
        StompSession sessao = cliente.connectAsync("ws://localhost:" + porta + "/ws", new WebSocketHttpHeaders(), cabecalhos,
                new StompSessionHandlerAdapter() {
                }).get(5, TimeUnit.SECONDS);
        sessoes.add(sessao);
        return sessao;
    }

    private static BlockingQueue<JsonNode> inscrever(StompSession sessao, String destino) {
        BlockingQueue<JsonNode> fila = new LinkedBlockingQueue<>();
        sessao.subscribe(destino, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return JsonNode.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                fila.add((JsonNode) payload);
            }
        });
        return fila;
    }

    private static JsonNode aguardar(BlockingQueue<JsonNode> fila, Predicate<JsonNode> condicao) throws InterruptedException {
        long limite = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < limite) {
            JsonNode e = fila.poll(limite - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
            if (e != null && condicao.test(e)) {
                return e;
            }
        }
        throw new AssertionError("Evento esperado não chegou em 5 s");
    }
}
