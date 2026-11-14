package br.com.coelho.escalas.motor;

import br.com.coelho.escalas.dominio.Confidencial;
import br.com.coelho.escalas.dominio.Funcionario;
import br.com.coelho.escalas.dominio.Turno;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/**
 * Troca de colaborador em um turno: resume a semana do titular e simula o mesmo turno com cada colega,
 * passando-o pelas regras individuais do validador (CLT, disponibilidade, ausências). Só o que a troca
 * acrescenta conta: erros impedem a troca; alertas (ex.: hora extra) apenas avisam.
 */
@Component
public class SugestorSubstituicao {

    /** Janela, em dias, em volta do turno para as regras de continuidade (interjornada, dias seguidos...). */
    private static final int MARGEM_DIAS = 7;

    public record Disponibilidade(Long funcionarioId, String nome, String setorNome, String setorCor,
                                  BigDecimal horasSemana, BigDecimal limiteSemanal, List<LocalDate> diasEscalados,
                                  Set<DayOfWeek> diasDisponiveis) {
    }

    public record Candidato(Long funcionarioId, String nome, String setorNome, String setorCor, boolean mesmoSetor,
                            BigDecimal horasSemana, BigDecimal horasComTroca, @Confidencial BigDecimal custoTurno, boolean apto,
                            List<String> impedimentos, List<String> alertas) {
    }

    public record Sugestao(Disponibilidade titular, List<Candidato> candidatos) {
    }

    /** Quem assume o turno; {@code substituto} nulo quando ninguém pode cobri-lo. */
    public record Cobertura(Turno turno, Candidato substituto) {
    }

    private final ValidadorEscala validador;
    private final MotorFinanceiro motor;

    public SugestorSubstituicao(ValidadorEscala validador, MotorFinanceiro motor) {
        this.validador = validador;
        this.motor = motor;
    }

    /**
     * @param turno  turno a ser coberto por outra pessoa
     * @param janela turnos de todos os colaboradores em volta da data (ao menos a semana e {@value MARGEM_DIAS} dias de margem)
     */
    public Sugestao sugerir(Turno turno, List<Turno> janela, ContextoCalculo ctx) {
        Funcionario titular = turno.getFuncionario();
        LocalDate data = turno.getData();
        Map<Long, List<Turno>> porFuncionario = new HashMap<>();
        janela.forEach(t -> porFuncionario.computeIfAbsent(t.getFuncionario().getId(), k -> new ArrayList<>()).add(t));

        List<Turno> doTitular = porFuncionario.getOrDefault(titular.getId(), List.of());
        Disponibilidade resumo = new Disponibilidade(titular.getId(), titular.getNome(), titular.getSetor().getNome(),
                titular.getSetor().getCor(), horasNaSemana(doTitular, data), ctx.parametros().getJornadaSemanalHoras(),
                doTitular.stream().map(Turno::getData).filter(d -> mesmaSemana(d, data)).distinct().sorted().toList(),
                titular.getDiasDisponiveis());

        List<Candidato> candidatos = ctx.funcionariosAtivos().stream()
                .filter(f -> !f.getId().equals(titular.getId()))
                .filter(f -> porFuncionario.getOrDefault(f.getId(), List.of()).stream().noneMatch(t -> t.getData().equals(data)))
                .map(f -> avaliar(f, turno, porFuncionario.getOrDefault(f.getId(), List.of()), ctx))
                .sorted(Comparator.comparing((Candidato c) -> !c.apto())
                        .thenComparing(c -> !c.mesmoSetor())
                        .thenComparing(Candidato::horasSemana)
                        .thenComparing(Candidato::nome))
                .toList();
        return new Sugestao(resumo, candidatos);
    }

    /**
     * Repassa vários turnos de um mesmo titular (ex.: ao sair de férias), em ordem de data. Cada escolha entra na
     * simulação das seguintes, para não sobrecarregar a mesma pessoa nem ferir interjornada entre os dias cobertos.
     * Os turnos recebidos não são alterados.
     */
    public List<Cobertura> planejarCobertura(List<Turno> aCobrir, List<Turno> janela, ContextoCalculo ctx) {
        Map<Long, Funcionario> porId = new HashMap<>();
        ctx.funcionarios().forEach(f -> porId.put(f.getId(), f));
        List<Turno> simulada = new ArrayList<>(janela);
        List<Cobertura> plano = new ArrayList<>();
        for (Turno t : aCobrir.stream().sorted(Comparator.comparing(Turno::inicio)).toList()) {
            Candidato escolhido = sugerir(t, simulada, ctx).candidatos().stream().filter(Candidato::apto).findFirst().orElse(null);
            plano.add(new Cobertura(t, escolhido));
            simulada.remove(t);
            if (escolhido != null) {
                simulada.add(new Turno(porId.get(escolhido.funcionarioId()), t.getData(), t.getHoraInicio(), t.getHoraFim(),
                        t.getIntervaloMinutos(), t.getRotulo()));
            }
        }
        return plano;
    }

    private Candidato avaliar(Funcionario f, Turno turno, List<Turno> seus, ContextoCalculo ctx) {
        LocalDate data = turno.getData();
        Turno simulado = new Turno(f, data, turno.getHoraInicio(), turno.getHoraFim(), turno.getIntervaloMinutos(), turno.getRotulo());
        List<Turno> comTroca = new ArrayList<>(seus);
        comTroca.add(simulado);

        LocalDate de = data.minusDays(MARGEM_DIAS);
        LocalDate ate = data.plusDays(MARGEM_DIAS);
        List<Violacao> antes = validador.validarColaborador(seus, de, ate, ctx);
        List<Violacao> novas = validador.validarColaborador(comTroca, de, ate, ctx).stream()
                .filter(v -> !antes.contains(v)).toList();

        List<String> impedimentos = novas.stream().filter(Violacao::erro).map(Violacao::mensagem).toList();
        List<String> alertas = novas.stream().filter(v -> !v.erro()).map(Violacao::mensagem).toList();
        return new Candidato(f.getId(), f.getNome(), f.getSetor().getNome(), f.getSetor().getCor(),
                f.getSetor().getId().equals(turno.getFuncionario().getSetor().getId()),
                horasNaSemana(seus, data), horasNaSemana(comTroca, data), motor.custoTurno(simulado, ctx),
                impedimentos.isEmpty(), impedimentos, alertas);
    }

    private static BigDecimal horasNaSemana(List<Turno> turnos, LocalDate data) {
        return turnos.stream().filter(t -> mesmaSemana(t.getData(), data)).map(Turno::horasTrabalhadas)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    /** Semana de segunda a domingo, como na regra de jornada semanal do validador. */
    private static boolean mesmaSemana(LocalDate d, LocalDate referencia) {
        LocalDate segunda = referencia.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return !d.isBefore(segunda) && !d.isAfter(segunda.plusDays(6));
    }
}
