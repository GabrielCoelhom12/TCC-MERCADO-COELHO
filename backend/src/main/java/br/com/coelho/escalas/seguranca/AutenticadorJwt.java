package br.com.coelho.escalas.seguranca;

import br.com.coelho.escalas.dominio.Usuario;
import br.com.coelho.escalas.repositorio.UsuarioRepository;
import io.jsonwebtoken.Claims;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Converte o cabeçalho {@code Authorization: Bearer <jwt>} na autenticação do Spring Security. É o ponto único de
 * autenticação da API REST ({@link JwtFiltro}) e do WebSocket ({@link AutenticacaoStomp}).
 *
 * <p>O token só identifica o usuário: a situação (ativo) e o perfil são lidos do banco a cada requisição, de modo que
 * desativar uma conta ou trocar o seu perfil vale imediatamente, sem esperar o token expirar. Da mesma forma, um token
 * emitido antes da última troca de senha é recusado.
 */
@Component
public class AutenticadorJwt {

    private static final String PREFIXO = "Bearer ";

    private final JwtService jwt;
    private final UsuarioRepository usuarios;

    public AutenticadorJwt(JwtService jwt, UsuarioRepository usuarios) {
        this.jwt = jwt;
        this.usuarios = usuarios;
    }

    /**
     * Vazio quando o cabeçalho está ausente, o token é inválido/expirado, o usuário não existe mais ou foi desativado,
     * ou a senha foi trocada depois da emissão do token.
     */
    public Optional<UsernamePasswordAuthenticationToken> autenticar(String cabecalhoAuthorization) {
        if (cabecalhoAuthorization == null || !cabecalhoAuthorization.startsWith(PREFIXO)) {
            return Optional.empty();
        }
        return jwt.validar(cabecalhoAuthorization.substring(PREFIXO.length()))
                .flatMap(claims -> usuarios.findByLogin(claims.getSubject())
                        .filter(Usuario::isAtivo)
                        .filter(u -> emitidoDepoisDaSenha(claims, u)))
                .map(AutenticadorJwt::autenticacaoDe);
    }

    /** O token guarda o instante de emissão em segundos; a comparação usa a mesma precisão. */
    private static boolean emitidoDepoisDaSenha(Claims claims, Usuario u) {
        if (u.getSenhaAlteradaEm() == null) {
            return true;
        }
        Instant emitido = claims.getIssuedAt() == null ? Instant.EPOCH : claims.getIssuedAt().toInstant();
        return !emitido.isBefore(u.getSenhaAlteradaEm().truncatedTo(ChronoUnit.SECONDS));
    }

    private static UsernamePasswordAuthenticationToken autenticacaoDe(Usuario usuario) {
        var perfil = new SimpleGrantedAuthority("ROLE_" + usuario.getPerfil().name());
        return new UsernamePasswordAuthenticationToken(usuario.getLogin(), null, List.of(perfil));
    }
}
