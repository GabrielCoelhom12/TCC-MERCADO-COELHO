package br.com.coelho.escalas.seguranca;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.HeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.StaticHeadersWriter;

import java.nio.charset.StandardCharsets;

/**
 * Autenticação por JWT (sem sessão) e autorização por perfil:
 * <ul>
 *   <li><b>ADMIN</b>: tudo, inclusive usuários;</li>
 *   <li><b>GESTOR</b>: tudo, menos usuários;</li>
 *   <li><b>SUPERVISOR</b>: consulta (sem dados salariais, ver {@link SerializacaoConfidencial}) e as ações do dia a dia —
 *       trocar colaborador num turno, registrar um afastamento (atestado), lançar o fechamento do dia e usar o chat.</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

    static final String SEM_PERMISSAO = "{\"mensagem\":\"Seu perfil não permite esta ação.\",\"violacoes\":[]}";

    @Bean
    SecurityFilterChain filtros(HttpSecurity http, JwtFiltro jwtFiltro) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable) // sem cookies de sessão: o token vai no cabeçalho Authorization
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers("/api/usuarios/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/**").authenticated()
                        // Ações do dia a dia, liberadas também ao SUPERVISOR.
                        .requestMatchers("/api/chat/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/escalas/*/turnos/*/substituir").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/movimento/fechamentos/*").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/afastamentos").authenticated()
                        // Qualquer outra alteração: só quem administra a operação.
                        .requestMatchers("/api/**").hasAnyRole("ADMIN", "GESTOR")
                        .anyRequest().permitAll())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler(semPermissao()))
                .headers(h -> h
                        .addHeaderWriter(politicaDeConteudo())
                        .referrerPolicy(r -> r.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy",
                                "camera=(), microphone=(), geolocation=(), payment=(), usb=()")))
                .addFilterBefore(jwtFiltro, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /** 403 em JSON, no mesmo formato dos outros erros, para o frontend mostrar a mensagem. */
    private static AccessDeniedHandler semPermissao() {
        return (request, response, negado) -> {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(SEM_PERMISSAO);
        };
    }

    /**
     * Content-Security-Policy: só recursos da própria origem (scripts, fontes, imagens) e nenhum enquadramento. O
     * WebSocket do chat é liberado explicitamente para o próprio host, porque nem todo navegador inclui ws/wss em 'self'.
     */
    static HeaderWriter politicaDeConteudo() {
        return (request, response) -> response.setHeader("Content-Security-Policy", String.join("; ",
                "default-src 'self'",
                "script-src 'self'",
                "style-src 'self' 'unsafe-inline'",
                "img-src 'self' data: blob:",
                "font-src 'self' data:",
                "connect-src 'self' " + webSocketDe(request),
                // O botão Imprimir carrega o PDF da escala num quadro invisível a partir de um blob.
                "frame-src 'self' blob:",
                "frame-ancestors 'none'",
                "base-uri 'self'",
                "form-action 'self'",
                "object-src 'none'"));
    }

    private static String webSocketDe(HttpServletRequest request) {
        boolean portaPadrao = request.getServerPort() == 80 || request.getServerPort() == 443;
        String host = request.getServerName() + (portaPadrao ? "" : ":" + request.getServerPort());
        return (request.isSecure() ? "wss://" : "ws://") + host;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
