package br.com.coelho.escalas.seguranca;

import br.com.coelho.escalas.dominio.Usuario;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SEGREDO = "dGVzdGUtZXNjYWxhcy1jb2VsaG8tY2hhdmUtc29tZW50ZS1wYXJhLXRlc3Rlcw==";
    private final Usuario usuario = new Usuario("ana", "hash", "Ana", Usuario.Perfil.GESTOR);

    @Test
    void tokenGeradoEhValidadoComOsDadosDoUsuario() {
        JwtService jwt = new JwtService(SEGREDO, 1);
        var claims = jwt.validar(jwt.gerar(usuario)).orElseThrow();
        assertThat(claims.getSubject()).isEqualTo("ana");
        assertThat(claims.get("perfil", String.class)).isEqualTo("GESTOR");
    }

    @Test
    void tokenAssinadoComOutraChaveEhRecusado() {
        String token = new JwtService(SEGREDO, 1).gerar(usuario);
        assertThat(new JwtService("", 1).validar(token)).isEmpty();
    }

    @Test
    void tokenExpiradoOuMalformadoEhRecusado() {
        JwtService jwt = new JwtService(SEGREDO, -1);
        assertThat(jwt.validar(jwt.gerar(usuario))).isEmpty();
        assertThat(jwt.validar("abc")).isEmpty();
        assertThat(jwt.validar("")).isEmpty();
    }

    @Test
    void semSegredoConfiguradoGeraChaveAleatoria() {
        JwtService a = new JwtService(null, 1);
        JwtService b = new JwtService(" ", 1);
        assertThat(a.validar(a.gerar(usuario))).isPresent();
        assertThat(b.validar(a.gerar(usuario))).isEmpty();
    }
}
