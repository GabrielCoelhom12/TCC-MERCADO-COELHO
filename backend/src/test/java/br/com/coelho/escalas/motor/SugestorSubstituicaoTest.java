package br.com.coelho.escalas.motor;

import br.com.coelho.escalas.dominio.Funcionario;
import br.com.coelho.escalas.dominio.Setor;
import br.com.coelho.escalas.dominio.Turno;
import br.com.coelho.escalas.motor.SugestorSubstituicao.Candidato;
import br.com.coelho.escalas.motor.SugestorSubstituicao.Sugestao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static br.com.coelho.escalas.motor.Cenario.SEGUNDA;
import static br.com.coelho.escalas.motor.Cenario.turno;
import static org.assertj.core.api.Assertions.assertThat;

class SugestorSubstituicaoTest {

    private static final LocalDate QUARTA = SEGUNDA.plusDays(2);

    private final MotorFinanceiro motor = new MotorFinanceiro();
    private final SugestorSubstituicao sugestor = new SugestorSubstituicao(new ValidadorEscala(motor), motor);
    private Cenario c;
    private Setor caixa;
    private Setor acougue;
    private Turno turnoDaAna;
    private final List<Turno> janela = new ArrayList<>();

    @BeforeEach
    void setUp() {
        c = new Cenario();
        caixa = c.setor("Caixa", 0);
        acougue = c.setor("Açougue", 0);
        Funcionario ana = c.funcionario("Ana", caixa, "10");
        janela.add(turno(ana, SEGUNDA, "07:00", "15:20", 60));
        turnoDaAna = turno(ana, QUARTA, "07:00", "15:20", 60);
        janela.add(turnoDaAna);
    }

    private Sugestao sugerir() {
        return sugestor.sugerir(turnoDaAna, janela, c.ctx());
    }

    private static Candidato candidato(Sugestao s, String nome) {
        return s.candidatos().stream().filter(x -> x.nome().equals(nome)).findFirst().orElseThrow();
    }

    @Test
    void resumeASemanaDoTitular() {
        var s = sugerir();

        assertThat(s.titular().nome()).isEqualTo("Ana");
        assertThat(s.titular().horasSemana()).isEqualByComparingTo("14.67");
        assertThat(s.titular().limiteSemanal()).isEqualByComparingTo("44");
        assertThat(s.titular().diasEscalados()).containsExactly(SEGUNDA, QUARTA);
    }

    @Test
    void naoSugereOProprioTitularNemQuemJaTrabalhaNoDiaNemInativos() {
        Funcionario bruno = c.funcionario("Bruno", caixa, "10");
        janela.add(turno(bruno, QUARTA, "13:40", "22:00", 60));
        c.funcionario("Carla", caixa, "10").setAtivo(false);
        c.funcionario("Davi", caixa, "10");

        assertThat(sugerir().candidatos()).extracting(Candidato::nome).containsExactly("Davi");
    }

    @Test
    void mostraAsHorasDaSemanaAntesEDepoisDaTrocaEOCustoDoTurno() {
        Funcionario bruno = c.funcionario("Bruno", caixa, "12");
        janela.add(turno(bruno, SEGUNDA.plusDays(1), "08:00", "13:00", 15));

        Candidato b = candidato(sugerir(), "Bruno");

        assertThat(b.apto()).isTrue();
        assertThat(b.mesmoSetor()).isTrue();
        assertThat(b.horasSemana()).isEqualByComparingTo("4.75");
        assertThat(b.horasComTroca()).isEqualByComparingTo("12.08");
        assertThat(b.custoTurno()).isEqualByComparingTo(new BigDecimal("88.00"));
        assertThat(b.impedimentos()).isEmpty();
    }

    @Test
    void indisponibilidadeEAusenciaImpedemATroca() {
        Funcionario bruno = c.funcionario("Bruno", caixa, "10");
        bruno.setDiasDisponiveis(EnumSet.complementOf(EnumSet.of(DayOfWeek.WEDNESDAY)));
        Funcionario carla = c.funcionario("Carla", caixa, "10");
        c.ausencia(carla, SEGUNDA, SEGUNDA.plusDays(6));

        var s = sugerir();

        assertThat(candidato(s, "Bruno").apto()).isFalse();
        assertThat(candidato(s, "Bruno").impedimentos()).singleElement().asString().contains("indisponibilidade");
        assertThat(candidato(s, "Carla").apto()).isFalse();
        assertThat(candidato(s, "Carla").impedimentos()).singleElement().asString().contains("ausência");
    }

    @Test
    void quemFechouNaVesperaNaoPodeAbrirPorCausaDaInterjornada() {
        Funcionario bruno = c.funcionario("Bruno", caixa, "10");
        janela.add(turno(bruno, QUARTA.minusDays(1), "13:40", "22:00", 60));

        Candidato b = candidato(sugerir(), "Bruno");

        assertThat(b.apto()).isFalse();
        assertThat(b.impedimentos()).singleElement().asString().contains("entre jornadas");
    }

    @Test
    void horaExtraSemanalNaoImpedeMasViraAlerta() {
        Funcionario bruno = c.funcionario("Bruno", caixa, "10");
        for (int i = 0; i < 7; i++) {
            if (i != 2 && i != 5) {
                janela.add(turno(bruno, SEGUNDA.plusDays(i), "07:00", "16:00", 60)); // 8h por dia
            }
        }

        Candidato b = candidato(sugerir(), "Bruno");

        assertThat(b.apto()).isTrue();
        assertThat(b.horasComTroca()).isEqualByComparingTo("47.33");
        assertThat(b.alertas()).singleElement().asString().contains("semanais");
    }

    @Test
    void planoDeCoberturaRepassaCadaTurnoConsiderandoAsTrocasAnteriores() {
        Turno quinta = turno(turnoDaAna.getFuncionario(), QUARTA.plusDays(1), "07:00", "15:20", 60);
        janela.add(quinta);
        c.funcionario("Bruno", caixa, "10");
        c.funcionario("Carla", caixa, "10");

        var plano = sugestor.planejarCobertura(List.of(quinta, turnoDaAna), janela, c.ctx());

        // Em ordem de data; na quinta, Bruno já soma as horas da quarta, então Carla (0h) é a escolhida.
        assertThat(plano).extracting(SugestorSubstituicao.Cobertura::turno).containsExactly(turnoDaAna, quinta);
        assertThat(plano).extracting(p -> p.substituto().nome()).containsExactly("Bruno", "Carla");
    }

    @Test
    void semNinguemAptoOTurnoFicaSemSubstituto() {
        Funcionario bruno = c.funcionario("Bruno", caixa, "10");
        bruno.setDiasDisponiveis(EnumSet.of(DayOfWeek.MONDAY));

        var plano = sugestor.planejarCobertura(List.of(turnoDaAna), janela, c.ctx());

        assertThat(plano).singleElement().extracting(SugestorSubstituicao.Cobertura::substituto).isNull();
    }

    @Test
    void ordenaAptosDoMesmoSetorEComMenosHorasPrimeiro() {
        Funcionario bruno = c.funcionario("Bruno", caixa, "10");
        janela.add(turno(bruno, SEGUNDA, "07:00", "15:20", 60));
        c.funcionario("Carla", caixa, "10");
        c.funcionario("Davi", acougue, "10");
        Funcionario eva = c.funcionario("Eva", caixa, "10");
        eva.setDiasDisponiveis(EnumSet.of(DayOfWeek.MONDAY));

        var s = sugerir();

        assertThat(s.candidatos()).extracting(Candidato::nome).containsExactly("Carla", "Bruno", "Davi", "Eva");
        assertThat(candidato(s, "Davi").mesmoSetor()).isFalse();
    }
}
