package br.com.coelho.escalas.motor;

import br.com.coelho.escalas.dominio.Setor;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static br.com.coelho.escalas.motor.Cenario.SEGUNDA;
import static org.assertj.core.api.Assertions.assertThat;

class SimuladorCustoTest {

    private final SimuladorCusto simulador = new SimuladorCusto(new MotorFinanceiro());

    @Test
    void propostaInchadaEhCortadaPeloOperadorMaisCaro() {
        Cenario c = new Cenario();
        Setor caixa = c.setor("Caixa", 2);
        Setor acougue = c.setor("Açougue", 1);
        c.funcionario("Caixa", caixa, "10");      // 80/dia
        c.funcionario("Açougueiro", acougue, "15"); // 120/dia
        // custo médio = 12,5 × 8 = 100 → L_max = ⌊600/100⌋ = 6; proposta com 9 operadores
        var s = simulador.simular(SEGUNDA, Map.of(caixa.getId(), 5, acougue.getId(), 4), c.ctx());

        assertThat(s.capacidade().lMax()).isEqualTo(6);
        assertThat(s.veredito()).isEqualTo(SimuladorCusto.Veredito.SUPERDIMENSIONADA);
        assertThat(s.custoProposto()).isEqualByComparingTo("880.00");    // 5×80 + 4×120
        // corta o açougue (operador mais caro) até o mínimo: 5 caixas + 1 açougue = 6 operadores, R$ 520 ≤ teto de R$ 600
        assertThat(s.operadoresRecomendados()).isEqualTo(6);
        assertThat(s.custoRecomendado()).isEqualByComparingTo("520.00"); // 5×80 + 1×120
        assertThat(s.economia()).isEqualByComparingTo("360.00");
    }

    @Test
    void propostaAbaixoDoMinimoEhElevada() {
        Cenario c = new Cenario();
        Setor caixa = c.setor("Caixa", 3);
        c.funcionario("Caixa", caixa, "10");
        var s = simulador.simular(SEGUNDA, Map.of(caixa.getId(), 1), c.ctx());
        assertThat(s.veredito()).isEqualTo(SimuladorCusto.Veredito.SUBDIMENSIONADA);
        assertThat(s.operadoresRecomendados()).isEqualTo(3);
    }
}
