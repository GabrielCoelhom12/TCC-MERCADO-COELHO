package br.com.coelho.escalas.seguranca;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Autentica cada requisição da API pelo JWT do cabeçalho Authorization (sem sessão no servidor). */
@Component
public class JwtFiltro extends OncePerRequestFilter {

    private final AutenticadorJwt autenticador;

    public JwtFiltro(AutenticadorJwt autenticador) {
        this.autenticador = autenticador;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        autenticador.autenticar(request.getHeader(HttpHeaders.AUTHORIZATION))
                .ifPresent(SecurityContextHolder.getContext()::setAuthentication);
        chain.doFilter(request, response);
    }
}
