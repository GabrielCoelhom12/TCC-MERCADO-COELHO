package br.com.coelho.escalas.seguranca;

import br.com.coelho.escalas.dominio.Usuario;
import br.com.coelho.escalas.repositorio.UsuarioRepository;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Converte o cabeçalho {@code Authorization: Bearer <jwt>} na autenticação do Spring Security. É o ponto único de
 * autenticação da API REST ({@link JwtFiltro}) e do WebSocket ({@link AutenticacaoStomp}).
 *
 * <p>O token só identifica o usuário: a situação (ativo) e o perfil são lidos do banco a cada requisição, de modo que
 * desativar uma conta ou trocar o seu perfil vale imediatamente, sem esperar o token expirar.
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

    /** Vazio quando o cabeçalho está ausente, o token é inválido/expirado ou o usuário não existe mais ou foi desativado. */
    public Optional<UsernamePasswordAuthenticationToken> autenticar(String cabecalhoAuthorization) {
        if (cabecalhoAuthorization == null || !cabecalhoAuthorization.startsWith(PREFIXO)) {
            return Optional.empty();
        }
        return jwt.validar(cabecalhoAuthorization.substring(PREFIXO.length()))
                .flatMap(claims -> usuarios.findByLogin(claims.getSubject()))
                .filter(Usuario::isAtivo)
                .map(AutenticadorJwt::autenticacaoDe);
    }

    private static UsernamePasswordAuthenticationToken autenticacaoDe(Usuario usuario) {
        var perfil = new SimpleGrantedAuthority("ROLE_" + usuario.getPerfil().name());
        return new UsernamePasswordAuthenticationToken(usuario.getLogin(), null, List.of(perfil));
    }
}
