package br.com.coelho.escalas.api;

import br.com.coelho.escalas.dominio.Usuario;
import br.com.coelho.escalas.servico.AutenticacaoService;
import br.com.coelho.escalas.servico.FotoPerfil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Login e dados do usuário logado. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** Limites de tamanho evitam processar entradas gigantes (o BCrypt só considera os primeiros 72 bytes). */
    public record LoginRequest(@NotBlank @Size(max = 100) String login, @NotBlank @Size(max = 128) String senha) {
    }

    /** O token só é preenchido na resposta do login. */
    public record LoginResponse(String token, Long id, String nome, String login, String perfil, String foto) {
        static LoginResponse de(Usuario u, String token) {
            return new LoginResponse(token, u.getId(), u.getNome(), u.getLogin(), u.getPerfil().name(),
                    FotoPerfil.dataUrl(u.getFoto()));
        }
    }

    private final AutenticacaoService autenticacao;

    public AuthController(AutenticacaoService autenticacao) {
        this.autenticacao = autenticacao;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        // Atrás do proxy, o endereço real do cliente vem do X-Forwarded-For (server.forward-headers-strategy).
        AutenticacaoService.Sessao sessao = autenticacao.entrar(req.login(), req.senha(), http.getRemoteAddr());
        return LoginResponse.de(sessao.usuario(), sessao.token());
    }

    @GetMapping("/me")
    public LoginResponse me(Authentication auth) {
        return LoginResponse.de(autenticacao.atual(auth.getName()), null);
    }
}
