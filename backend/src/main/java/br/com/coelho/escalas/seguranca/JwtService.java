package br.com.coelho.escalas.seguranca;

import br.com.coelho.escalas.dominio.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final SecretKey chave;
    private final long expiracaoHoras;

    public JwtService(@Value("${app.jwt.secret}") String segredo, @Value("${app.jwt.expiracao-horas}") long expiracaoHoras) {
        this.chave = chaveDe(segredo);
        this.expiracaoHoras = expiracaoHoras;
    }

    /** Sem segredo configurado, gera uma chave aleatória: nenhum segredo fica versionado no repositório. */
    private static SecretKey chaveDe(String segredo) {
        if (segredo == null || segredo.isBlank()) {
            log.warn("APP_JWT_SECRET não definido: usando chave aleatória (tokens expiram ao reiniciar).");
            return Jwts.SIG.HS256.key().build();
        }
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(segredo));
    }

    public String gerar(Usuario usuario) {
        Instant agora = Instant.now();
        return Jwts.builder()
                .subject(usuario.getLogin())
                .claim("perfil", usuario.getPerfil().name())
                .claim("nome", usuario.getNome())
                .issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plus(expiracaoHoras, ChronoUnit.HOURS)))
                .signWith(chave)
                .compact();
    }

    public Optional<Claims> validar(String token) {
        try {
            return Optional.of(Jwts.parser().verifyWith(chave).build().parseSignedClaims(token).getPayload());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
