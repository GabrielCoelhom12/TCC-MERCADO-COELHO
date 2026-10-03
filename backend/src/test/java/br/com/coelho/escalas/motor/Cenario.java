package br.com.coelho.escalas.motor;

import br.com.coelho.escalas.dominio.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

/** Construtor de cenários de teste para o motor, sem banco de dados. */
class Cenario {

    final ParametrosOperacionais parametros = new ParametrosOperacionais();
    final List<Setor> setores = new ArrayList<>();
    final List<Funcionario> funcionarios = new ArrayList<>();
    final Map<LocalDate, Feriado> feriados = new HashMap<>();
    final Map<TipoDia, BigDecimal> projecaoPorTipo = new EnumMap<>(TipoDia.class);
    final Map<LocalDate, BigDecimal> projecaoPorData = new HashMap<>();
    final List<Ausencia> ausencias = new ArrayList<>();
    final List<MovimentoDiario> movimentos = new ArrayList<>();
    final List<TurnoModelo> modelos = new ArrayList<>(List.of(ABERTURA, FECHAMENTO, DOMINGO));
    private long ids = 1;

    /** Faixas de horário usadas nos lançamentos de movimento dos testes. */
    static final LocalTime[][] FAIXAS = {
            {LocalTime.of(7, 0), LocalTime.of(10, 0)},
            {LocalTime.of(10, 0), LocalTime.of(13, 0)},
            {LocalTime.of(13, 0), LocalTime.of(16, 0)},
            {LocalTime.of(16, 0), LocalTime.of(19, 0)},
            {LocalTime.of(19, 0), LocalTime.of(22, 0)},
    };

    /** Segunda-feira, 05/10/2026. */
    static final LocalDate SEGUNDA = LocalDate.of(2026, 10, 5);

    static final TurnoModelo ABERTURA = new TurnoModelo("Abertura", "ABE", LocalTime.of(7, 0), LocalTime.of(15, 20), 60, TurnoModelo.Aplicacao.DIAS_UTEIS);
    static final TurnoModelo FECHAMENTO = new TurnoModelo("Fechamento", "FEC", LocalTime.of(13, 40), LocalTime.of(22, 0), 60, TurnoModelo.Aplicacao.DIAS_UTEIS);
    static final TurnoModelo DOMINGO = new TurnoModelo("Domingo", "DOM", LocalTime.of(8, 0), LocalTime.of(13, 45), 15, TurnoModelo.Aplicacao.DOMINGOS_FERIADOS);

    Cenario() {
        parametros.setPercentualTetoFolha(new BigDecimal("6.00"));
        parametros.setJornadaReferenciaHoras(new BigDecimal("8.00"));
        for (TipoDia t : TipoDia.values()) {
            projecaoPorTipo.put(t, new BigDecimal("10000"));
        }
    }

    Setor setor(String nome, int minimoTodosOsDias) {
        Setor s = new Setor(nome, "#000");
        s.setId(ids++);
        for (TipoDia t : TipoDia.values()) {
            s.getMinimoPorDia().put(t, minimoTodosOsDias);
        }
        setores.add(s);
        return s;
    }

    /** Colaborador com salário-hora S_i exato (carga 220h, sem encargos). */
    Funcionario funcionario(String nome, Setor setor, String custoHora) {
        Funcionario f = new Funcionario();
        f.setId(ids++);
        f.setNome(nome);
        f.setSetor(setor);
        f.setCargaHorariaMensal(220);
        f.setSalarioMensal(new BigDecimal(custoHora).multiply(BigDecimal.valueOf(220)));
        f.setPercentualEncargos(BigDecimal.ZERO);
        funcionarios.add(f);
        return f;
    }

    void feriado(LocalDate data) {
        feriados.put(data, new Feriado(data, "Feriado de teste", Feriado.Abrangencia.NACIONAL));
    }

    void ausencia(Funcionario f, LocalDate inicio, LocalDate fim) {
        Ausencia a = new Ausencia();
        a.setFuncionario(f);
        a.setInicio(inicio);
        a.setFim(fim);
        a.setMotivo(Ausencia.Motivo.FERIAS);
        ausencias.add(a);
    }

    static Turno turno(Funcionario f, LocalDate data, String inicio, String fim, int intervalo) {
        return new Turno(f, data, LocalTime.parse(inicio), LocalTime.parse(fim), intervalo, "T");
    }

    /** Fechamento de um dia: clientes de cada faixa (na ordem de FAIXAS) e o total vendido, rateado pelos clientes. */
    MovimentoDiario movimento(LocalDate data, String vendas, int... clientesPorFaixa) {
        BigDecimal total = new BigDecimal(vendas);
        int clientes = Arrays.stream(clientesPorFaixa).sum();
        MovimentoDiario m = new MovimentoDiario(data);
        for (int i = 0; i < clientesPorFaixa.length; i++) {
            BigDecimal parte = clientes == 0 ? BigDecimal.ZERO
                    : total.multiply(BigDecimal.valueOf(clientesPorFaixa[i])).divide(BigDecimal.valueOf(clientes), 2, java.math.RoundingMode.HALF_UP);
            m.getFaixas().add(new MovimentoFaixa(FAIXAS[i][0], FAIXAS[i][1], clientesPorFaixa[i], parte));
        }
        movimentos.add(m);
        return m;
    }

    ContextoCalculo ctx() {
        return new ContextoCalculo(parametros, setores, funcionarios, feriados, projecaoPorTipo, projecaoPorData, ausencias,
                movimentos, modelos);
    }
}
