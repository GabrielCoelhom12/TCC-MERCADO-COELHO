package br.com.coelho.escalas.api;

import br.com.coelho.escalas.dominio.Usuario;
import br.com.coelho.escalas.repositorio.UsuarioRepository;
import br.com.coelho.escalas.seguranca.JwtService;
import br.com.coelho.escalas.servico.FotoPerfil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record LoginRequest(@NotBlank String login, @NotBlank String senha) {
    }

    public record LoginResponse(String token, Long id, String nome, String login, String perfil, String foto) {
    }

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthController(UsuarioRepository usuarios, PasswordEncoder encoder, JwtService jwt) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        Usuario usuario = usuarios.findByLogin(req.login().trim().toLowerCase())
                .filter(u -> encoder.matches(req.senha(), u.getSenhaHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário ou senha inválidos."));
        if (!usuario.isAtivo()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário desativado. Procure um administrador.");
        }
        return new LoginResponse(jwt.gerar(usuario), usuario.getId(), usuario.getNome(), usuario.getLogin(), usuario.getPerfil().name(),
                FotoPerfil.dataUrl(usuario.getFoto()));
    }

    @GetMapping("/me")
    public LoginResponse me(Authentication auth) {
        Usuario usuario = usuarios.findByLogin(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return new LoginResponse(null, usuario.getId(), usuario.getNome(), usuario.getLogin(), usuario.getPerfil().name(),
                FotoPerfil.dataUrl(usuario.getFoto()));
    }
}
