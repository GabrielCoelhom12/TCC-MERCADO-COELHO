package br.com.coelho.escalas.servico;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LimiteOtimizacoesTest {

    @Test
    void executaATarefaEDevolveOResultado() {
        LimiteOtimizacoes limite = new LimiteOtimizacoes(1);
        assertThat(limite.executar(() -> "escala otimizada")).isEqualTo("escala otimizada");
    }

    @Test
    void recusaQuandoJaHaOtimizacoesDemaisEmAndamento() {
        LimiteOtimizacoes limite = new LimiteOtimizacoes(1);
        // A segunda chamada acontece enquanto a primeira ainda está rodando.
        assertThatThrownBy(() -> limite.executar(() -> limite.executar(() -> "segunda")))
                .isInstanceOf(MuitasTentativasException.class)
                .hasMessageContaining("otimização");
    }

    @Test
    void permiteTantasSimultaneasQuantoConfigurado() {
        LimiteOtimizacoes limite = new LimiteOtimizacoes(2);
        assertThat(limite.executar(() -> limite.executar(() -> "segunda"))).isEqualTo("segunda");
    }

    @Test
    void liberaAVagaAoTerminarMesmoComErro() {
        LimiteOtimizacoes limite = new LimiteOtimizacoes(1);
        assertThatThrownBy(() -> limite.executar(() -> {
            throw new RegraNegocioException("falhou");
        })).isInstanceOf(RegraNegocioException.class);

        assertThat(limite.executar(() -> "de novo")).isEqualTo("de novo");
    }
}
