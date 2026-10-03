package br.com.coelho.escalas.servico;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * Servidores ICE para o WebRTC. STUN descobre o endereço público; TURN retransmite o áudio quando a conexão direta
 * é bloqueada (comum em redes corporativas). As credenciais TURN são temporárias e assinadas com o segredo
 * compartilhado com o servidor TURN (padrão "use-auth-secret" do coturn): nenhum segredo vai para o navegador.
 */
@Component
public class ServidoresIce {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ServidorIce(List<String> urls, String username, String credential) {
    }

    public record Configuracao(List<ServidorIce> iceServers, int tempoToqueSegundos) {
    }

    private final List<String> stun;
    private final List<String> turn;
    private final String segredoTurn;
    private final Duration validade;
    private final int tempoToqueSegundos;

    public ServidoresIce(@Value("${app.chamada.stun:stun:stun.l.google.com:19302}") String stun,
                         @Value("${app.chamada.turn.urls:}") String turn,
                         @Value("${app.chamada.turn.segredo:}") String segredoTurn,
                         @Value("${app.chamada.turn.validade-horas:12}") long validadeHoras,
                         @Value("${app.chamada.tempo-toque-segundos:30}") int tempoToqueSegundos) {
        this.stun = lista(stun);
        this.turn = lista(turn);
        this.segredoTurn = segredoTurn;
        this.validade = Duration.ofHours(validadeHoras);
        this.tempoToqueSegundos = tempoToqueSegundos;
    }

    public Configuracao para(Long usuarioId) {
        List<ServidorIce> servidores = new ArrayList<>();
        if (!stun.isEmpty()) {
            servidores.add(new ServidorIce(stun, null, null));
        }
        if (!turn.isEmpty() && !segredoTurn.isBlank()) {
            String usuario = Instant.now().plus(validade).getEpochSecond() + ":" + usuarioId;
            servidores.add(new ServidorIce(turn, usuario, assinar(usuario)));
        }
        return new Configuracao(servidores, tempoToqueSegundos);
    }

    private String assinar(String usuario) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(segredoTurn.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return Base64.getEncoder().encodeToString(mac.doFinal(usuario.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao gerar credencial TURN.", e);
        }
    }

    private static List<String> lista(String valor) {
        return Arrays.stream(valor.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
