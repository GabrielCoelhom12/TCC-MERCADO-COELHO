package br.com.coelho.escalas.servico;

import br.com.coelho.escalas.dominio.Usuario;
import br.com.coelho.escalas.repositorio.UsuarioRepository;
import br.com.coelho.escalas.seguranca.JwtService;
import br.com.coelho.escalas.seguranca.LimiteTentativas;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Login por usuário e senha e consulta dos dados de quem está logado. */
@Service
public class AutenticacaoService {

    /** Usuário autenticado e o token JWT emitido para ele. */
    public record Sessao(Usuario usuario, String token) {
    }

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final LimiteTentativas limite;
    /** Comparado quando o login não existe, para a resposta levar o mesmo tempo e não revelar quais logins existem. */
    private final String hashFicticio;

    public AutenticacaoService(UsuarioRepository usuarios, PasswordEncoder encoder, JwtService jwt, LimiteTentativas limite) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.jwt = jwt;
        this.limite = limite;
        this.hashFicticio = encoder.encode("senha-ficticia-para-tempo-constante");
    }

    /**
     * A mensagem e o tempo de resposta são os mesmos para login inexistente e senha errada, para não revelar quais
     * logins existem. Depois de várias falhas o login (ou o IP) fica bloqueado por alguns minutos.
     */
    @Transactional(readOnly = true)
    public Sessao entrar(String login, String senha, String ip) {
        String chave = Usuario.normalizarLogin(login);
        limite.bloqueio(chave, ip).ifPresent(espera -> {
            throw new MuitasTentativasException(espera);
        });
        Optional<Usuario> encontrado = usuarios.findByLogin(chave);
        boolean senhaCorreta = encoder.matches(senha, encontrado.map(Usuario::getSenhaHash).orElse(hashFicticio))
                && encontrado.isPresent();
        if (!senhaCorreta) {
            limite.registrarFalha(chave, ip);
            throw new NaoAutenticadoException("Usuário ou senha inválidos.");
        }
        limite.registrarSucesso(chave);
        Usuario usuario = encontrado.get();
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
