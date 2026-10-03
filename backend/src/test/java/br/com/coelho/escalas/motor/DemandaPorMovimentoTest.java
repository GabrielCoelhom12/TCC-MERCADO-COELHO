package br.com.coelho.escalas.motor;

import br.com.coelho.escalas.dominio.Funcionario;
import br.com.coelho.escalas.dominio.Setor;
import br.com.coelho.escalas.dominio.Turno;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static br.com.coelho.escalas.motor.Cenario.*;
import static org.assertj.core.api.Assertions.assertThat;

/** A previsão de movimento alimentando o motor financeiro, o gerador e o validador. */
class DemandaPorMovimentoTest {

    private final MotorFinanceiro motor = new MotorFinanceiro();
    private final GeradorEscala gerador = new GeradorEscala(motor);
    private final ValidadorEscala validador = new ValidadorEscala(motor);

    /** Sexta, 23/10/2026. */
    private static final LocalDate SEXTA = LocalDate.of(2026, 10, 23);

    /** Caixa atende 30 clientes/hora por pessoa; histórico de sextas com pico à tarde. */
    private Cenario sextaDePico(int funcionarios) {
        Cenario c = new Cenario();
        Setor caixa = c.setor("Caixa", 1);
        caixa.setClientesPorColaboradorHora(30);
        for (int i = 0; i < funcionarios; i++) {
            c.funcionario("Operador " + i, caixa, "10");
        }
        c.movimento(SEXTA.minusWeeks(1), "40000", 90, 90, 180, 270, 270);
        c.movimento(SEXTA.minusWeeks(2), "40000", 90, 90, 180, 270, 270);
        return c;
    }

    @Test
    void semHistoricoOMotorUsaAConfiguracaoManual() {
        Cenario c = new Cenario();
        c.setor("Caixa", 3).setClientesPorColaboradorHora(30);
        CapacidadeDia cap = motor.capacidade(SEXTA, c.ctx());
        assertThat(cap.previsao()).isNull();
        assertThat(cap.receitaProjetada()).isEqualByComparingTo("10000");
        assertThat(cap.lMin()).isEqualTo(3);
    }

    @Test
    void aReceitaDoDiaVemDaPrevisao() {
        CapacidadeDia cap = motor.capacidade(SEXTA, sextaDePico(4).ctx());
        assertThat(cap.previsao()).isNotNull();
        assertThat(cap.receitaProjetada()).isEqualByComparingTo("40000");
        assertThat(cap.origemReceita()).isEqualTo(CapacidadeDia.OrigemReceita.PREVISAO);
    }

    @Test
    void projecaoInformadaParaADataPrevaleceSobreAPrevisao() {
        Cenario c = sextaDePico(4);
        c.projecaoPorData.put(SEXTA, new BigDecimal("55000"));
        CapacidadeDia cap = motor.capacidade(SEXTA, c.ctx());
        assertThat(cap.receitaProjetada()).isEqualByComparingTo("55000");
        assertThat(cap.origemReceita()).isEqualTo(CapacidadeDia.OrigemReceita.INFORMADA);
    }

    @Test
    void oPicoPrevistoAumentaADemandaDoSetor() {
        CapacidadeDia cap = motor.capacidade(SEXTA, sextaDePico(4).ctx());
        assertThat(cap.minimoPorSetor().values()).containsExactly(4);
        assertThat(cap.lMin()).isEqualTo(4);
        assertThat(cap.planos().values().iterator().next().faixas()).hasSize(5);
    }

    @Test
    void geradorCobreOPicoReforcandoOFechamento() {
        Cenario c = sextaDePico(6);
        var resultado = gerador.gerar(SEXTA, SEXTA, List.of(), c.modelos, c.ctx());

        assertThat(resultado.deficits()).isEmpty();
        assertThat(resultado.turnos()).extracting(Turno::getRotulo).containsExactlyInAnyOrder("FEC", "FEC", "FEC", "ABE");
        var validacao = validador.validar(SEXTA, SEXTA, resultado.turnos(), c.ctx());
        assertThat(validacao.violacoes()).noneMatch(v -> v.regra() == Violacao.Regra.FAIXA_ABAIXO_DEMANDA);
    }

    @Test
    void validadorAlertaQuandoUmaEdicaoDeixaOPicoDescoberto() {
        Cenario c = sextaDePico(4);
        List<Turno> turnos = new ArrayList<>();
        for (Funcionario f : c.funcionarios) {
            turnos.add(turno(f, SEXTA, "07:00", "15:20", 60)); // todos de manhã
        }

        var validacao = validador.validar(SEXTA, SEXTA, turnos, c.ctx());

        assertThat(validacao.violacoes()).filteredOn(v -> v.regra() == Violacao.Regra.FAIXA_ABAIXO_DEMANDA)
                .hasSize(2)
                .allMatch(v -> v.severidade() == Violacao.Severidade.ALERTA)
                .anyMatch(v -> v.mensagem().contains("16:00–19:00") && v.mensagem().contains("0 de 3"));
        var setor = validacao.dias().get(0).setores().get(0);
        assertThat(setor.faixas()).extracting(ResumoDia.CoberturaFaixa::alocados).containsExactly(4, 4, 4, 0, 0);
        assertThat(validacao.dias().get(0).clientesPrevistos()).isEqualTo(900);
        assertThat(validacao.dias().get(0).pico()).isEqualTo("16:00–19:00");
    }
}
