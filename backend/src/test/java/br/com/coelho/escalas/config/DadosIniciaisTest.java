package br.com.coelho.escalas.config;

import br.com.coelho.escalas.seguranca.SenhaForteValidator;
import org.junit.jupiter.api.RepeatedTest;

import static org.assertj.core.api.Assertions.assertThat;

class DadosIniciaisTest {

    @RepeatedTest(50)
    void senhaGeradaParaOAdministradorAtendeAPolitica() {
        String senha = DadosIniciais.senhaAleatoria();
        assertThat(senha).hasSize(16);
        assertThat(new SenhaForteValidator().isValid(senha, null)).isTrue();
    }
}
