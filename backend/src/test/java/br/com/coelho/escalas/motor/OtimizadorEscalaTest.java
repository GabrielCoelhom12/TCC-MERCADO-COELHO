package br.com.coelho.escalas.motor;

import br.com.coelho.escalas.dominio.Funcionario;
import br.com.coelho.escalas.dominio.Setor;
import br.com.coelho.escalas.dominio.Turno;
import br.com.coelho.escalas.dominio.TurnoModelo;
import br.com.coelho.escalas.motor.otimizacao.OtimizadorEscala;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class OtimizadorEscalaTest {

    private static final LocalDate SEGUNDA = Cenario.SEGUNDA;
    private static final LocalDate DOMINGO = SEGUNDA.plusDays(6);

    private final MotorFinanceiro motor = new MotorFinanceiro();
    private final OtimizadorEscala otimizador = new OtimizadorEscala(motor, Duration.ofSeconds(2));
    private final ValidadorEscala validador = new ValidadorEscala(motor);
    private Cenario c;
    private Setor caixa;

    @BeforeEach
    void setUp() {
        c = new Cenario();
        caixa = c.setor("Caixa", 2);
    }

    private GeradorEscala.ResultadoGeracao otimizar(List<Turno> vizinhos) {
        return otimizador.gerar(SEGUNDA, DOMINGO, vizinhos, c.modelos, c.ctx());
    }

    private List<Violacao.Regra> erros(List<Turno> turnos) {
        ResultadoValidacao r = validador.validar(SEGUNDA, DOMINGO, turnos, c.ctx());
        return r.violacoes().stream().filter(Violacao::erro).map(Violacao::regra).toList();
    }

    @Test
    void cobreAsVagasDaSemanaSemFerirNenhumaRegra() {
        for (int i = 0; i < 6; i++) {
            c.funcionario("Pessoa " + i, caixa, "10");
        }

        var r = otimizar(List.of());

        assertThat(r.deficits()).isEmpty();
        assertThat(r.turnos()).hasSize(14);
        assertThat(erros(r.turnos())).isEmpty();
    }

    @Test
    void respeitaDisponibilidadeAusenciasEOsTurnosDasEscalasVizinhas() {
        Funcionario ana = c.funcionario("Ana", caixa, "10");
        Funcionario bruno = c.funcionario("Bruno", caixa, "10");
        for (int i = 0; i < 4; i++) {
            c.funcionario("Pessoa " + i, caixa, "10");
        }
        ana.setDiasDisponiveis(EnumSet.complementOf(EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY)));
        c.ausencia(bruno, SEGUNDA.plusDays(2), SEGUNDA.plusDays(4));
        // Bruno fechou a loja no domingo anterior: não pode abrir na segunda (interjornada de 11 h).
        List<Turno> vizinhos = new ArrayList<>(List.of(Cenario.turno(bruno, SEGUNDA.minusDays(1), "13:40", "22:00", 60)));

        var r = otimizar(vizinhos);

        List<Turno> todos = new ArrayList<>(vizinhos);
        todos.addAll(r.turnos());
        assertThat(erros(todos)).isEmpty();
        assertThat(r.turnos()).noneMatch(t -> t.getFuncionario() == ana
                && (t.getData().getDayOfWeek() == DayOfWeek.MONDAY || t.getData().getDayOfWeek() == DayOfWeek.TUESDAY));
        assertThat(r.turnos()).noneMatch(t -> t.getFuncionario() == bruno && !t.getData().isBefore(SEGUNDA.plusDays(2))
                && !t.getData().isAfter(SEGUNDA.plusDays(4)));
    }

    @Test
    void semGenteSuficienteInformaAsVagasDescobertas() {
        c.funcionario("Única", caixa, "10");

        var r = otimizar(List.of());

        // Uma pessoa trabalha no máximo 6 dias seguidos e um turno por dia: sobra 1 vaga por dia, e mais uma num dos 7.
        assertThat(r.turnos()).hasSizeLessThanOrEqualTo(6);
        assertThat(r.deficits()).isNotEmpty();
        assertThat(r.deficits().stream().mapToInt(d -> d.necessarios() - d.alocados()).sum()).isEqualTo(14 - r.turnos().size());
        assertThat(erros(r.turnos())).doesNotContain(Violacao.Regra.DIAS_CONSECUTIVOS, Violacao.Regra.INTERJORNADA,
                Violacao.Regra.SOBREPOSICAO);
    }

    @Test
    void comCustosIguaisDistribuiAsHorasDeFormaEquilibrada() {
        Setor padaria = c.setor("Padaria", 1);
        caixa.getMinimoPorDia().replaceAll((tipo, n) -> 0);
        for (int i = 0; i < 3; i++) {
            c.funcionario("Padeiro " + i, padaria, "10");
        }

        var r = otimizar(List.of());

        Map<String, Long> porPessoa = r.turnos().stream().collect(Collectors.groupingBy(t -> t.getFuncionario().getNome(), Collectors.counting()));
        assertThat(r.turnos()).hasSize(7);
        assertThat(porPessoa.values()).allSatisfy(n -> assertThat(n).isBetween(2L, 3L));
    }

    @Test
    void naoPerdeParaOGulosoEmVagasCobertas() {
        for (int i = 0; i < 3; i++) {
            c.funcionario("Pessoa " + i, caixa, String.valueOf(10 + i));
        }
        List<TurnoModelo> modelos = c.modelos;
        var guloso = new GeradorEscala(motor).gerar(SEGUNDA, DOMINGO, List.of(), modelos, c.ctx());

        var otimizado = otimizar(List.of());

        assertThat(otimizado.turnos().size()).isGreaterThanOrEqualTo(guloso.turnos().size());
        assertThat(erros(otimizado.turnos())).isEmpty();
    }
}
