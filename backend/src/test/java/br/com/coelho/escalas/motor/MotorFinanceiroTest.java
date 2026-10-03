package br.com.coelho.escalas.motor;

import br.com.coelho.escalas.dominio.Funcionario;
import br.com.coelho.escalas.dominio.Setor;
import br.com.coelho.escalas.dominio.TipoDia;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static br.com.coelho.escalas.motor.Cenario.SEGUNDA;
import static br.com.coelho.escalas.motor.Cenario.turno;
import static org.assertj.core.api.Assertions.assertThat;

class MotorFinanceiroTest {

    private final MotorFinanceiro motor = new MotorFinanceiro();

    @Test
    void custoHoraIncluiEncargos() {
        Funcionario f = new Funcionario();
        f.setSalarioMensal(new BigDecimal("2200"));
        f.setCargaHorariaMensal(220);
        f.setPercentualEncargos(new BigDecimal("50"));
        assertThat(f.getCustoHora()).isEqualByComparingTo("15.00");
    }

    @Test
    void custoDaEscalaSegueAFormulaSomatorioDeSiHiFd() {
        Cenario c = new Cenario();
        Setor caixa = c.setor("Caixa", 0);
        Funcionario a = c.funcionario("A", caixa, "10");
        Funcionario b = c.funcionario("B", caixa, "12");
        // 8h trabalhadas (09h às 18h com 1h de intervalo) e 6h (08h às 14h15 com 15 min)
        var turnos = List.of(turno(a, SEGUNDA, "09:00", "18:00", 60), turno(b, SEGUNDA, "08:00", "14:15", 15));

        // dia útil: F_d = 1,0 → 10×8 + 12×6 = 152
        assertThat(motor.custoEscala(SEGUNDA, turnos, c.ctx())).isEqualByComparingTo("152.00");
    }

    @Test
    void fatorDeDomingoEFeriado() {
        Cenario c = new Cenario();
        c.parametros.setFatorDomingo(new BigDecimal("1.5"));
        c.parametros.setFatorFeriado(new BigDecimal("2.0"));
        var domingo = SEGUNDA.plusDays(6);
        var feriadoNaTerca = SEGUNDA.plusDays(1);
        c.feriado(feriadoNaTerca);

        assertThat(motor.fator(SEGUNDA, c.ctx())).isEqualByComparingTo("1.0");
        assertThat(motor.fator(domingo, c.ctx())).isEqualByComparingTo("1.5");
        assertThat(motor.fator(feriadoNaTerca, c.ctx())).isEqualByComparingTo("2.0");

        c.feriado(domingo); // feriado no domingo: prevalece o maior fator
        assertThat(motor.fator(domingo, c.ctx())).isEqualByComparingTo("2.0");
        assertThat(motor.tipoDia(domingo, c.ctx())).isEqualTo(TipoDia.FERIADO);
    }

    @Test
    void custoNoDomingoAplicaOFator() {
        Cenario c = new Cenario();
        c.parametros.setFatorDomingo(new BigDecimal("1.5"));
        Funcionario a = c.funcionario("A", c.setor("Caixa", 0), "10");
        var domingo = SEGUNDA.plusDays(6);
        assertThat(motor.custoEscala(domingo, List.of(turno(a, domingo, "09:00", "18:00", 60)), c.ctx()))
                .isEqualByComparingTo("120.00");
    }

    @Test
    void lMaxDecorreDaReceitaProjetadaEDoCustoMedio() {
        Cenario c = new Cenario();
        Setor caixa = c.setor("Caixa", 2);
        Setor padaria = c.setor("Padaria", 1);
        c.funcionario("A", caixa, "10");
        c.funcionario("B", padaria, "10");
        // R_proj = 10.000, teto 6% = 600, custo médio = 10 × 8h × 1,0 = 80 → L_max = ⌊600/80⌋ = 7
        CapacidadeDia cap = motor.capacidade(SEGUNDA, c.ctx());
        assertThat(cap.tetoFolha()).isEqualByComparingTo("600.00");
        assertThat(cap.custoMedioOperador()).isEqualByComparingTo("80.00");
        assertThat(cap.lMax()).isEqualTo(7);
        assertThat(cap.lMin()).isEqualTo(3);
        assertThat(cap.inviavel()).isFalse();
    }

    @Test
    void projecaoEspecificaDaDataSobrepoeAPadrao() {
        Cenario c = new Cenario();
        c.funcionario("A", c.setor("Caixa", 0), "10");
        c.projecaoPorData.put(SEGUNDA, new BigDecimal("20000"));
        assertThat(motor.receitaProjetada(SEGUNDA, c.ctx())).isEqualByComparingTo("20000");
        assertThat(motor.capacidade(SEGUNDA, c.ctx()).lMax()).isEqualTo(15);
    }

    @Test
    void domingoComReceitaBaixaFicaInviavel() {
        Cenario c = new Cenario();
        c.parametros.setFatorDomingo(new BigDecimal("2.0"));
        c.funcionario("A", c.setor("Caixa", 5), "10");
        c.projecaoPorTipo.put(TipoDia.DOMINGO, new BigDecimal("5000"));
        // teto 300 / (10 × 8 × 2) = 1,875 → L_max = 1 < L_min = 5
        CapacidadeDia cap = motor.capacidade(SEGUNDA.plusDays(6), c.ctx());
        assertThat(cap.lMax()).isEqualTo(1);
        assertThat(cap.inviavel()).isTrue();
    }
}
