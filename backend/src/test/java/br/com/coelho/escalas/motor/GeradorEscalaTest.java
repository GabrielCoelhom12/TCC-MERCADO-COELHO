package br.com.coelho.escalas.motor;

import br.com.coelho.escalas.dominio.Funcionario;
import br.com.coelho.escalas.dominio.Setor;
import br.com.coelho.escalas.dominio.TipoDia;
import br.com.coelho.escalas.dominio.Turno;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static br.com.coelho.escalas.motor.Cenario.*;
import static org.assertj.core.api.Assertions.assertThat;

class GeradorEscalaTest {

    private final MotorFinanceiro motor = new MotorFinanceiro();
    private final GeradorEscala gerador = new GeradorEscala(motor);
    private final ValidadorEscala validador = new ValidadorEscala(motor);
    private final List<br.com.coelho.escalas.dominio.TurnoModelo> modelos = List.of(ABERTURA, FECHAMENTO, DOMINGO);

    @Test
    void geraExatamenteOMinimoESemNenhumErro() {
        Cenario c = new Cenario();
        Setor caixa = c.setor("Caixa", 3);
        caixa.getMinimoPorDia().put(TipoDia.SABADO, 4);
        caixa.getMinimoPorDia().put(TipoDia.DOMINGO, 2);
        Setor padaria = c.setor("Padaria", 1);
        for (int i = 0; i < 6; i++) {
            c.funcionario("Caixa " + i, caixa, String.valueOf(9 + i));
        }
        c.funcionario("Padeiro 1", padaria, "12");
        c.funcionario("Padeiro 2", padaria, "12");

        var resultado = gerador.gerar(SEGUNDA, SEGUNDA.plusDays(13), List.of(), modelos, c.ctx());

        assertThat(resultado.deficits()).isEmpty();
        var validacao = validador.validar(SEGUNDA, SEGUNDA.plusDays(13), resultado.turnos(), c.ctx());
        assertThat(validacao.violacoes()).filteredOn(Violacao::erro).isEmpty();
        validacao.dias().forEach(d -> assertThat(d.operadores()).isEqualTo(d.lMin()));
    }

    @Test
    void naoEscalaAusentesEReportaDeficit() {
        Cenario c = new Cenario();
        Setor acougue = c.setor("Açougue", 2);
        Funcionario a = c.funcionario("A", acougue, "10");
        Funcionario b = c.funcionario("B", acougue, "10");
        c.ausencia(b, SEGUNDA, SEGUNDA.plusDays(6));

        var resultado = gerador.gerar(SEGUNDA, SEGUNDA.plusDays(6), List.of(), modelos, c.ctx());

        assertThat(resultado.turnos()).allMatch(t -> t.getFuncionario() == a);
        assertThat(resultado.deficits()).hasSize(7);
        // A trabalha 6 dias (repouso semanal obrigatório) e respeita a interjornada
        assertThat(resultado.turnos()).hasSize(6);
        var validacao = validador.validar(SEGUNDA, SEGUNDA.plusDays(6), resultado.turnos(), c.ctx());
        assertThat(validacao.violacoes()).noneMatch(v -> v.funcionarioId() != null && v.erro());
    }

    @Test
    void respeitaTurnosDeEscalasVizinhas() {
        Cenario c = new Cenario();
        Setor caixa = c.setor("Caixa", 1);
        Funcionario a = c.funcionario("A (barato)", caixa, "8");
        c.funcionario("B", caixa, "12");
        // A trabalhou os 6 dias anteriores: não pode ser escalado na segunda
        List<Turno> anteriores = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            anteriores.add(turno(a, SEGUNDA.minusDays(i), "08:00", "13:00", 15));
        }
        var resultado = gerador.gerar(SEGUNDA, SEGUNDA, anteriores, modelos, c.ctx());
        assertThat(resultado.turnos()).singleElement().extracting(t -> t.getFuncionario().getNome()).isEqualTo("B");
    }

    @Test
    void prefereOColaboradorDeMenorCustoEmEmpate() {
        Cenario c = new Cenario();
        Setor caixa = c.setor("Caixa", 1);
        c.funcionario("Caro", caixa, "20");
        c.funcionario("Barato", caixa, "10");
        var resultado = gerador.gerar(SEGUNDA, SEGUNDA, List.of(), modelos, c.ctx());
        assertThat(resultado.turnos()).singleElement().extracting(t -> t.getFuncionario().getNome()).isEqualTo("Barato");
    }
}
