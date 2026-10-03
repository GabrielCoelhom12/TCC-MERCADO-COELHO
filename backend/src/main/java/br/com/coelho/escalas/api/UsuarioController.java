package br.com.coelho.escalas.api;

import br.com.coelho.escalas.dominio.Usuario;
import br.com.coelho.escalas.repositorio.UsuarioRepository;
import br.com.coelho.escalas.seguranca.SenhaForte;
import br.com.coelho.escalas.servico.FotoPerfil;
import br.com.coelho.escalas.servico.NaoEncontradoException;
import br.com.coelho.escalas.servico.RegraNegocioException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import java.util.List;

/** Gestão de usuários do sistema. Acesso restrito ao perfil ADMIN (ver SecurityConfig). */
@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    public record NovoUsuarioRequest(@NotBlank String login, @NotBlank String nome, @NotNull Usuario.Perfil perfil,
                                     @NotBlank @SenhaForte String senha) {
    }

    /** Senha opcional: quando ausente, a senha atual é mantida. */
    public record EdicaoUsuarioRequest(@NotBlank String login, @NotBlank String nome, @NotNull Usuario.Perfil perfil,
                                       Boolean ativo, @SenhaForte String senha) {
    }

    /** Representação pública: nunca expõe o hash da senha. */
    public record UsuarioResposta(Long id, String login, String nome, Usuario.Perfil perfil, boolean ativo, String foto) {
        static UsuarioResposta de(Usuario u) {
            return new UsuarioResposta(u.getId(), u.getLogin(), u.getNome(), u.getPerfil(), u.isAtivo(),
                    FotoPerfil.dataUrl(u.getFoto()));
        }
    }

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;

    public UsuarioController(UsuarioRepository usuarios, PasswordEncoder encoder) {
        this.usuarios = usuarios;
        this.encoder = encoder;
    }

    @GetMapping
    public List<UsuarioResposta> listar() {
        return usuarios.findAllByOrderByNomeAsc().stream().map(UsuarioResposta::de).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public UsuarioResposta criar(@Valid @RequestBody NovoUsuarioRequest req) {
        Usuario u = new Usuario(loginUnico(req.login(), null), encoder.encode(req.senha()), req.nome().trim(), req.perfil());
        return UsuarioResposta.de(usuarios.save(u));
    }

    @PutMapping("/{id}")
    @Transactional
    public UsuarioResposta atualizar(@PathVariable Long id, @Valid @RequestBody EdicaoUsuarioRequest req, Authentication auth) {
        Usuario u = buscar(id);
        boolean ativo = req.ativo() == null || req.ativo();

        if (!ativo && u.getLogin().equals(auth.getName())) {
            throw new RegraNegocioException("Você não pode desativar a sua própria conta.");
        }
        boolean eraAdminAtivo = u.getPerfil() == Usuario.Perfil.ADMIN && u.isAtivo();
        boolean seraAdminAtivo = req.perfil() == Usuario.Perfil.ADMIN && ativo;
        if (eraAdminAtivo && !seraAdminAtivo && usuarios.countByPerfilAndAtivoTrue(Usuario.Perfil.ADMIN) <= 1) {
            throw new RegraNegocioException("É preciso manter pelo menos um administrador ativo.");
        }

        u.setLogin(loginUnico(req.login(), id));
        u.setNome(req.nome().trim());
        u.setPerfil(req.perfil());
        u.setAtivo(ativo);
        if (req.senha() != null) {
            u.setSenhaHash(encoder.encode(req.senha()));
        }
        return UsuarioResposta.de(u);
    }

    @PutMapping("/{id}/foto")
    @Transactional
    public UsuarioResposta definirFoto(@PathVariable Long id, @RequestParam("arquivo") MultipartFile arquivo) throws IOException {
        Usuario u = buscar(id);
        u.setFoto(FotoPerfil.normalizar(arquivo.getBytes()));
        return UsuarioResposta.de(u);
    }

    @DeleteMapping("/{id}/foto")
    @Transactional
    public UsuarioResposta removerFoto(@PathVariable Long id) {
        Usuario u = buscar(id);
        u.setFoto(null);
        return UsuarioResposta.de(u);
    }

    private Usuario buscar(Long id) {
        return usuarios.findById(id).orElseThrow(() -> new NaoEncontradoException("Usuário", id));
    }

    private String loginUnico(String bruto, Long ignorarId) {
        String login = bruto.trim().toLowerCase();
        boolean emUso = ignorarId == null ? usuarios.findByLogin(login).isPresent() : usuarios.existsByLoginAndIdNot(login, ignorarId);
        if (emUso) {
            throw new RegraNegocioException("O login \"" + login + "\" já está em uso.");
        }
        return login;
    }
}
