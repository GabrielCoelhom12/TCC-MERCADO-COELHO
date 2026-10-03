package br.com.coelho.escalas.motor;

import br.com.coelho.escalas.dominio.Funcionario;
import br.com.coelho.escalas.dominio.ParametrosOperacionais.ModoFinanceiro;
import br.com.coelho.escalas.dominio.Setor;
import br.com.coelho.escalas.dominio.Turno;
import br.com.coelho.escalas.motor.Violacao.Regra;
import br.com.coelho.escalas.motor.Violacao.Severidade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static br.com.coelho.escalas.motor.Cenario.SEGUNDA;
import static br.com.coelho.escalas.motor.Cenario.turno;
import static org.assertj.core.api.Assertions.assertThat;

class ValidadorEscalaTest {

    private final ValidadorEscala validador = new ValidadorEscala(new MotorFinanceiro());
    private Cenario c;
    private Setor caixa;
    private Funcionario ana;

    @BeforeEach
    void setUp() {
        c = new Cenario();
        caixa = c.setor("Caixa", 0);
        ana = c.funcionario("Ana", caixa, "10");
    }

    private ResultadoValidacao validar(LocalDate inicio, LocalDate fim, List<Turno> turnos) {
        return validador.validar(inicio, fim, turnos, c.ctx());
    }

    private static List<Regra> regras(ResultadoValidacao r) {
        return r.violacoes().stream().map(Violacao::regra).toList();
    }

    @Test
    void escalaRegularEhAprovavel() {
        var r = validar(SEGUNDA, SEGUNDA, List.of(turno(ana, SEGUNDA, "07:00", "15:20", 60)));
        assertThat(r.violacoes()).isEmpty();
        assertThat(r.aprovavel()).isTrue();
        assertThat(r.dias().getFirst().situacao()).isEqualTo(ResumoDia.Situacao.OK);
    }

    @Test
    void fechamentoSeguidoDeAberturaFereInterjornada() {
        var r = validar(SEGUNDA, SEGUNDA.plusDays(1), List.of(
                turno(ana, SEGUNDA, "13:40", "22:00", 60),
                turno(ana, SEGUNDA.plusDays(1), "07:00", "15:20", 60)));
        assertThat(regras(r)).containsExactly(Regra.INTERJORNADA);
        assertThat(r.aprovavel()).isFalse();
    }

    @Test
    void turnoQueAtravessaAMeiaNoiteContaParaInterjornada() {
        var r = validar(SEGUNDA, SEGUNDA.plusDays(1), List.of(
                turno(ana, SEGUNDA, "18:00", "02:00", 60),
                turno(ana, SEGUNDA.plusDays(1), "12:00", "18:00", 15)));
        assertThat(regras(r)).containsExactly(Regra.INTERJORNADA); // apenas 10h de descanso
    }

    @Test
    void seteDiasSeguidosExigemRepousoSemanal() {
        List<Turno> turnos = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            turnos.add(turno(ana, SEGUNDA.plusDays(i), "08:00", "13:00", 15));
        }
        var r = validar(SEGUNDA, SEGUNDA.plusDays(6), turnos);
        assertThat(regras(r)).containsExactly(Regra.DIAS_CONSECUTIVOS);
        assertThat(r.violacoes().getFirst().data()).isEqualTo(SEGUNDA.plusDays(6));
    }

    @Test
    void diasConsecutivosConsideramEscalaAnterior() {
        List<Turno> turnos = new ArrayList<>();
        for (int i = -3; i < 4; i++) { // 3 dias da escala anterior + 4 desta
            turnos.add(turno(ana, SEGUNDA.plusDays(i), "08:00", "13:00", 15));
        }
        var r = validar(SEGUNDA, SEGUNDA.plusDays(6), turnos);
        assertThat(regras(r)).containsExactly(Regra.DIAS_CONSECUTIVOS);
    }

    @Test
    void terceiroDomingoSeguidoEhVetado() {
        LocalDate domingo = SEGUNDA.plusDays(6);
        var turnos = List.of(
                turno(ana, domingo.minusWeeks(2), "08:00", "13:00", 15),
                turno(ana, domingo.minusWeeks(1), "08:00", "13:00", 15),
                turno(ana, domingo, "08:00", "13:00", 15));
        var r = validar(SEGUNDA, domingo, turnos);
        assertThat(regras(r)).containsExactly(Regra.DOMINGOS_CONSECUTIVOS);
    }

    @Test
    void jornadaAcimaDe6hSemIntervaloDeUmaHora() {
        var r = validar(SEGUNDA, SEGUNDA, List.of(turno(ana, SEGUNDA, "08:00", "15:30", 15)));
        assertThat(regras(r)).containsExactly(Regra.INTERVALO_INTRAJORNADA);
    }

    @Test
    void jornadaAcimaDoMaximoDiarioEHoraExtra() {
        var r = validar(SEGUNDA, SEGUNDA, List.of(turno(ana, SEGUNDA, "07:00", "18:30", 60)));
        assertThat(regras(r)).containsExactly(Regra.JORNADA_DIARIA_MAXIMA);

        var r2 = validar(SEGUNDA, SEGUNDA, List.of(turno(ana, SEGUNDA, "07:00", "17:00", 60)));
        assertThat(regras(r2)).containsExactly(Regra.HORA_EXTRA_DIARIA);
        assertThat(r2.aprovavel()).isTrue(); // hora extra é alerta, não erro
    }

    @Test
    void semanaAcimaDe44hGeraAlerta() {
        List<Turno> turnos = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            turnos.add(turno(ana, SEGUNDA.plusDays(i), "07:00", "16:00", 60)); // 8h × 5 = 40h
        }
        turnos.add(turno(ana, SEGUNDA.plusDays(5), "07:00", "13:00", 15)); // + 5h45 = 45h45
        var r = validar(SEGUNDA, SEGUNDA.plusDays(6), turnos);
        assertThat(regras(r)).containsExactly(Regra.HORA_EXTRA_SEMANAL);
    }

    @Test
    void sobreposicaoDeTurnos() {
        var r = validar(SEGUNDA, SEGUNDA, List.of(
                turno(ana, SEGUNDA, "07:00", "11:00", 0),
                turno(ana, SEGUNDA, "10:00", "13:00", 0)));
        assertThat(regras(r)).contains(Regra.SOBREPOSICAO);
    }

    @Test
    void indisponibilidadeEAusencia() {
        ana.setDiasDisponiveis(EnumSet.complementOf(EnumSet.of(DayOfWeek.MONDAY)));
        c.ausencia(ana, SEGUNDA.plusDays(1), SEGUNDA.plusDays(10));
        var r = validar(SEGUNDA, SEGUNDA.plusDays(1), List.of(
                turno(ana, SEGUNDA, "07:00", "15:20", 60),
                turno(ana, SEGUNDA.plusDays(1), "07:00", "15:20", 60)));
        assertThat(regras(r)).containsExactly(Regra.INDISPONIBILIDADE, Regra.AUSENCIA);
    }

    @Test
    void setorAbaixoDoMinimoEhSubdimensionamento() {
        caixa.getMinimoPorDia().replaceAll((k, v) -> 2);
        var r = validar(SEGUNDA, SEGUNDA, List.of(turno(ana, SEGUNDA, "07:00", "15:20", 60)));
        assertThat(regras(r)).containsExactly(Regra.SETOR_ABAIXO_MINIMO);
        assertThat(r.dias().getFirst().situacao()).isEqualTo(ResumoDia.Situacao.ABAIXO_MINIMO);
    }

    /** Superdimensionamento: receita de 10.000 → teto 600 → L_max = ⌊600 / 80⌋ = 7. */
    private List<Turno> oitoOperadoresNaSegunda() {
        List<Turno> turnos = new ArrayList<>(List.of(turno(ana, SEGUNDA, "08:00", "17:00", 60)));
        for (int i = 0; i < 7; i++) {
            turnos.add(turno(c.funcionario("Op" + i, caixa, "10"), SEGUNDA, "08:00", "17:00", 60));
        }
        return turnos;
    }

    @Test
    void superdimensionamentoBloqueiaNoModoBloquear() {
        c.parametros.setModoFinanceiro(ModoFinanceiro.BLOQUEAR);
        var r = validar(SEGUNDA, SEGUNDA, oitoOperadoresNaSegunda());
        assertThat(regras(r)).containsExactlyInAnyOrder(Regra.OPERADORES_ACIMA_LMAX, Regra.CUSTO_ACIMA_TETO);
        assertThat(r.violacoes()).allMatch(v -> v.severidade() == Severidade.ERRO);
        assertThat(r.dias().getFirst().custoEscala()).isEqualByComparingTo("640.00");
        assertThat(r.dias().getFirst().situacao()).isEqualTo(ResumoDia.Situacao.ACIMA_LIMITE);
        assertThat(r.aprovavel()).isFalse();
    }

    @Test
    void superdimensionamentoApenasAlertaNoModoAlertar() {
        c.parametros.setModoFinanceiro(ModoFinanceiro.ALERTAR);
        var r = validar(SEGUNDA, SEGUNDA, oitoOperadoresNaSegunda());
        assertThat(r.violacoes()).isNotEmpty().allMatch(v -> v.severidade() == Severidade.ALERTA);
        assertThat(r.aprovavel()).isTrue();
    }

    @Test
    void minimoOperacionalNuncaEhBloqueadoMesmoQuandoInviavel() {
        // demanda mínima de 8 com L_max = 7: o mínimo é permitido, mas a gerência é alertada
        caixa.getMinimoPorDia().replaceAll((k, v) -> 8);
        var r = validar(SEGUNDA, SEGUNDA, oitoOperadoresNaSegunda());
        assertThat(regras(r)).containsExactly(Regra.PROJECAO_INVIAVEL);
        assertThat(r.aprovavel()).isTrue();
    }
}
