package br.com.coelho.escalas.servico;

import br.com.coelho.escalas.dominio.Usuario;
import br.com.coelho.escalas.repositorio.UsuarioRepository;
import br.com.coelho.escalas.seguranca.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Login por usuário e senha e consulta dos dados de quem está logado. */
@Service
public class AutenticacaoService {

    /** Usuário autenticado e o token JWT emitido para ele. */
    public record Sessao(Usuario usuario, String token) {
    }

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AutenticacaoService(UsuarioRepository usuarios, PasswordEncoder encoder, JwtService jwt) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    /** A mensagem é a mesma para login inexistente e senha errada, para não revelar quais logins existem. */
    @Transactional(readOnly = true)
    public Sessao entrar(String login, String senha) {
        Usuario usuario = usuarios.findByLogin(Usuario.normalizarLogin(login))
                .filter(u -> encoder.matches(senha, u.getSenhaHash()))
                .orElseThrow(() -> new NaoAutenticadoException("Usuário ou senha inválidos."));
        if (!usuario.isAtivo()) {
            throw new NaoAutenticadoException("Usuário desativado. Procure um administrador.");
        }
        return new Sessao(usuario, jwt.gerar(usuario));
    }

    @Transactional(readOnly = true)
    public Usuario atual(String login) {
        return usuarios.findByLogin(login).orElseThrow(() -> new NaoAutenticadoException("Sessão inválida."));
    }
}
