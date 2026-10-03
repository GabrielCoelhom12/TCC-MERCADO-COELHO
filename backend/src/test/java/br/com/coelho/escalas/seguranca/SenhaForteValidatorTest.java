package br.com.coelho.escalas.seguranca;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class SenhaForteValidatorTest {

    private final SenhaForteValidator validador = new SenhaForteValidator();

    @ParameterizedTest
    @ValueSource(strings = {"Senha@Forte1", "Coelho#2026", "Ação!2026x", "Ab1!Ab1!", "Ünïcødé$9"})
    void aceitaSenhasQueAtendemTodosOsRequisitos(String senha) {
        assertThat(validador.isValid(senha, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Ab1!Ab1",          // 7 caracteres
            "senha@forte1",     // sem maiúscula
            "SENHA@FORTE1",     // sem minúscula
            "Senha@Forte",      // sem número
            "SenhaForte12",     // sem caractere especial
            "Senha Forte 12",   // espaço não conta como especial
            ""})
    void recusaSenhasFracas(String senha) {
        assertThat(validador.isValid(senha, null)).isFalse();
    }

    @ParameterizedTest
    @NullSource
    void nuloEhValidoParaPermitirSenhaOpcionalNaEdicao(String senha) {
        assertThat(validador.isValid(senha, null)).isTrue();
    }

    @org.junit.jupiter.api.Test
    void recusaSenhaAcimaDoLimiteDoBcrypt() {
        assertThat(validador.isValid("Aa1!" + "x".repeat(61), null)).isFalse();
        assertThat(validador.isValid("Aa1!" + "x".repeat(60), null)).isTrue();
    }
}
