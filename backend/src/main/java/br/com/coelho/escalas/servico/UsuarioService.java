package br.com.coelho.escalas.servico;

import br.com.coelho.escalas.dominio.Usuario;
import br.com.coelho.escalas.repositorio.UsuarioRepository;
import br.com.coelho.escalas.seguranca.SenhaForte;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Contas de acesso ao sistema: logins únicos, senha forte e garantia de que sempre resta um administrador ativo. */
@Service
public class UsuarioService {

    public record NovoUsuario(@NotBlank String login, @NotBlank String nome, @NotNull Usuario.Perfil perfil,
                              @NotBlank @SenhaForte String senha) {
    }

    /** Senha opcional: quando ausente, a senha atual é mantida. */
    public record EdicaoUsuario(@NotBlank String login, @NotBlank String nome, @NotNull Usuario.Perfil perfil,
                                Boolean ativo, @SenhaForte String senha) {
    }

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;

    public UsuarioService(UsuarioRepository usuarios, PasswordEncoder encoder) {
        this.usuarios = usuarios;
        this.encoder = encoder;
    }

    @Transactional(readOnly = true)
    public List<Usuario> listar() {
        return usuarios.findAllByOrderByNomeAsc();
    }

    @Transactional
    public Usuario criar(NovoUsuario dados) {
        String login = loginUnico(dados.login(), null);
        return usuarios.save(new Usuario(login, encoder.encode(dados.senha()), dados.nome().trim(), dados.perfil()));
    }

    /** @param loginDoSolicitante quem está fazendo a alteração (ninguém desativa a própria conta) */
    @Transactional
    public Usuario atualizar(Long id, EdicaoUsuario dados, String loginDoSolicitante) {
        Usuario u = buscar(id);
        boolean ativo = dados.ativo() == null || dados.ativo();

        if (!ativo && u.getLogin().equals(loginDoSolicitante)) {
            throw new RegraNegocioException("Você não pode desativar a sua própria conta.");
        }
        boolean eraAdminAtivo = u.getPerfil() == Usuario.Perfil.ADMIN && u.isAtivo();
        boolean seraAdminAtivo = dados.perfil() == Usuario.Perfil.ADMIN && ativo;
        if (eraAdminAtivo && !seraAdminAtivo && usuarios.countByPerfilAndAtivoTrue(Usuario.Perfil.ADMIN) <= 1) {
            throw new RegraNegocioException("É preciso manter pelo menos um administrador ativo.");
        }

        u.setLogin(loginUnico(dados.login(), id));
        u.setNome(dados.nome().trim());
        u.setPerfil(dados.perfil());
        u.setAtivo(ativo);
        if (dados.senha() != null) {
            u.setSenhaHash(encoder.encode(dados.senha()));
        }
        return u;
    }

    @Transactional
    public Usuario definirFoto(Long id, byte[] arquivo) {
        Usuario u = buscar(id);
        u.setFoto(FotoPerfil.normalizar(arquivo));
        return u;
    }

    @Transactional
    public Usuario removerFoto(Long id) {
        Usuario u = buscar(id);
        u.setFoto(null);
        return u;
    }

    private Usuario buscar(Long id) {
        return usuarios.findById(id).orElseThrow(() -> new NaoEncontradoException("Usuário", id));
    }

    private String loginUnico(String informado, Long ignorarId) {
        String login = Usuario.normalizarLogin(informado);
        boolean emUso = ignorarId == null ? usuarios.findByLogin(login).isPresent() : usuarios.existsByLoginAndIdNot(login, ignorarId);
        if (emUso) {
            throw new RegraNegocioException("O login \"" + login + "\" já está em uso.");
        }
        return login;
    }
}
