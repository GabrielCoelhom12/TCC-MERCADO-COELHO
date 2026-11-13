package br.com.coelho.escalas.api;

import br.com.coelho.escalas.dominio.Usuario;
import br.com.coelho.escalas.servico.FotoPerfil;
import br.com.coelho.escalas.servico.UsuarioService;
import br.com.coelho.escalas.servico.UsuarioService.EdicaoUsuario;
import br.com.coelho.escalas.servico.UsuarioService.NovoUsuario;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/** Gestão de usuários do sistema. Acesso restrito ao perfil ADMIN (ver SecurityConfig). */
@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    /** Representação pública: nunca expõe o hash da senha. */
    public record UsuarioResposta(Long id, String login, String nome, Usuario.Perfil perfil, boolean ativo, String foto) {
        static UsuarioResposta de(Usuario u) {
            return new UsuarioResposta(u.getId(), u.getLogin(), u.getNome(), u.getPerfil(), u.isAtivo(),
                    FotoPerfil.dataUrl(u.getFoto()));
        }
    }

    private final UsuarioService usuarios;

    public UsuarioController(UsuarioService usuarios) {
        this.usuarios = usuarios;
    }

    @GetMapping
    public List<UsuarioResposta> listar() {
        return usuarios.listar().stream().map(UsuarioResposta::de).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResposta criar(@Valid @RequestBody NovoUsuario req) {
        return UsuarioResposta.de(usuarios.criar(req));
    }

    @PutMapping("/{id}")
    public UsuarioResposta atualizar(@PathVariable Long id, @Valid @RequestBody EdicaoUsuario req, Authentication auth) {
        return UsuarioResposta.de(usuarios.atualizar(id, req, auth.getName()));
    }

    @PutMapping("/{id}/foto")
    public UsuarioResposta definirFoto(@PathVariable Long id, @RequestParam("arquivo") MultipartFile arquivo) throws IOException {
        return UsuarioResposta.de(usuarios.definirFoto(id, arquivo.getBytes()));
    }

    @DeleteMapping("/{id}/foto")
    public UsuarioResposta removerFoto(@PathVariable Long id) {
        return UsuarioResposta.de(usuarios.removerFoto(id));
    }
}
