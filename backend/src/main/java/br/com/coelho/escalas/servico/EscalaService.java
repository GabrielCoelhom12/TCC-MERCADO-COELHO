package br.com.coelho.escalas.servico;

import br.com.coelho.escalas.dominio.*;
import br.com.coelho.escalas.motor.*;
import br.com.coelho.escalas.repositorio.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Matriz Dinâmica de Geração e Validação de Escala (3º pilar): orquestra motor, validador e gerador. */
@Service
public class EscalaService {

    /** Janela extra carregada antes/depois do período para regras de continuidade (domingos, interjornada...). */
    private static final int MARGEM_DIAS = 21;
    private static final int MAX_DIAS_ESCALA = 62;

    public record EscalaRequest(String nome, LocalDate dataInicio, LocalDate dataFim, String observacao) {
    }

    /**
     * Define o conteúdo de uma célula (colaborador × dia). Sem modelo e sem horários = folga.
     */
    public record CelulaRequest(Long funcionarioId, LocalDate data, Long turnoModeloId,
                                LocalTime horaInicio, LocalTime horaFim, Integer intervaloMinutos) {
    }

    public record TurnoDTO(Long id, Long funcionarioId, LocalDate data, LocalTime horaInicio, LocalTime horaFim,
                           int intervaloMinutos, String rotulo, BigDecimal horas, BigDecimal custo) {
    }

    public record LinhaFuncionario(Long id, String nome, String cargo, Long setorId, String setorNome, String setorCor,
                                   BigDecimal custoHora, Set<DayOfWeek> diasDisponiveis, boolean ativo) {
    }

    public record Matriz(Escala escala, List<LinhaFuncionario> funcionarios, List<TurnoDTO> turnos,
                         List<Ausencia> ausencias, List<TurnoModelo> modelos, ResultadoValidacao validacao) {
    }

    public record Geracao(int turnosCriados, List<GeradorEscala.Deficit> deficits, Matriz matriz) {
    }

    private final EscalaRepository escalas;
    private final TurnoRepository turnos;
    private final FuncionarioRepository funcionarios;
    private final TurnoModeloRepository modelos;
    private final ContextoService contextos;
    private final MotorFinanceiro motor;
    private final ValidadorEscala validador;
    private final GeradorEscala gerador;

    public EscalaService(EscalaRepository escalas, TurnoRepository turnos, FuncionarioRepository funcionarios,
                         TurnoModeloRepository modelos, ContextoService contextos, MotorFinanceiro motor,
                         ValidadorEscala validador, GeradorEscala gerador) {
        this.escalas = escalas;
        this.turnos = turnos;
        this.funcionarios = funcionarios;
        this.modelos = modelos;
        this.contextos = contextos;
        this.motor = motor;
        this.validador = validador;
        this.gerador = gerador;
    }

    // ------------------------------------------------------------------ CRUD

    @Transactional(readOnly = true)
    public List<Escala> listar() {
        return escalas.findAllByOrderByDataInicioDesc();
    }

    @Transactional
    public Escala criar(EscalaRequest req) {
        validarPeriodo(req, null);
        Escala e = new Escala();
        e.setNome(req.nome() == null || req.nome().isBlank() ? nomePadrao(req.dataInicio(), req.dataFim()) : req.nome().trim());
        e.setDataInicio(req.dataInicio());
        e.setDataFim(req.dataFim());
        e.setObservacao(req.observacao());
        return escalas.save(e);
    }

    /** Rascunho criado pelo sistema (geração automática semanal). */
    @Transactional
    public Escala criarAutomatica(LocalDate inicio, LocalDate fim) {
        Escala e = criar(new EscalaRequest(null, inicio, fim,
                "Rascunho criado automaticamente a partir da previsão de movimento. Revise, ajuste e aprove."));
        e.setGeradaAutomaticamente(true);
        return e;
    }

    @Transactional
    public Escala atualizar(Long id, EscalaRequest req) {
        Escala e = buscar(id);
        if (req.nome() != null && !req.nome().isBlank()) {
            e.setNome(req.nome().trim());
        }
        e.setObservacao(req.observacao());
        return e;
    }

    @Transactional
    public void excluir(Long id) {
        Escala e = buscar(id);
        exigirEditavel(e);
        turnos.deleteByEscalaId(id);
        escalas.delete(e);
    }

    // ------------------------------------------------------------------ matriz

    @Transactional(readOnly = true)
    public Matriz matriz(Long id) {
        Escala e = buscar(id);
        ContextoCalculo ctx = contextoDa(e);
        List<Turno> janela = turnos.findByDataBetween(e.getDataInicio().minusDays(MARGEM_DIAS), e.getDataFim().plusDays(MARGEM_DIAS));
        ResultadoValidacao validacao = validador.validar(e.getDataInicio(), e.getDataFim(), janela, ctx);

        List<LinhaFuncionario> linhas = funcionarios.findAllByOrderBySetorNomeAscNomeAsc().stream()
                .filter(f -> f.isAtivo() || janela.stream().anyMatch(t -> t.getFuncionario().getId().equals(f.getId())))
                .map(f -> new LinhaFuncionario(f.getId(), f.getNome(), f.getCargo(), f.getSetor().getId(), f.getSetor().getNome(),
                        f.getSetor().getCor(), f.getCustoHora(), f.getDiasDisponiveis(), f.isAtivo()))
                .toList();

        List<TurnoDTO> dtos = janela.stream()
                .filter(t -> e.contem(t.getData()))
                .sorted(Comparator.comparing(Turno::getData).thenComparing(Turno::getHoraInicio))
                .map(t -> new TurnoDTO(t.getId(), t.getFuncionario().getId(), t.getData(), t.getHoraInicio(), t.getHoraFim(),
                        t.getIntervaloMinutos(), t.getRotulo(), t.horasTrabalhadas(), motor.custoTurno(t, ctx)))
                .toList();

        return new Matriz(e, linhas, dtos, ctx.ausencias(), modelos.findAllByOrderByHoraInicioAsc(), validacao);
    }

    @Transactional
    public Matriz definirCelula(Long escalaId, CelulaRequest req) {
        Escala e = buscar(escalaId);
        exigirEditavel(e);
        if (req.data() == null || !e.contem(req.data())) {
            throw new RegraNegocioException("A data informada está fora do período da escala.");
        }
        Funcionario f = funcionarios.findById(req.funcionarioId())
                .orElseThrow(() -> new NaoEncontradoException("Colaborador", req.funcionarioId()));

        ContextoCalculo ctx = contextoDa(e);
        DiaSnapshot antes = snapshot(req.data(), ctx);

        turnos.deleteAll(turnos.findByEscalaIdAndFuncionarioIdAndData(escalaId, f.getId(), req.data()));
        turnos.flush();

        Turno novo = montarTurno(req, f);
        if (novo != null) {
            novo.setEscala(e);
            turnos.saveAndFlush(novo);
            aplicarBloqueioFinanceiro(req.data(), antes, ctx);
        }
        return matriz(escalaId);
    }

    /**
     * Bloqueio financeiro (modo BLOQUEAR): recusa a alocação que leva o dia acima de L_max ou do teto da folha,
     * exceto enquanto o dia não atingiu o mínimo operacional. Alterações que reduzem custo são sempre aceitas.
     */
    private void aplicarBloqueioFinanceiro(LocalDate data, DiaSnapshot antes, ContextoCalculo ctx) {
        if (ctx.parametros().getModoFinanceiro() != ParametrosOperacionais.ModoFinanceiro.BLOQUEAR) {
            return;
        }
        DiaSnapshot depois = snapshot(data, ctx);
        if (depois.custo().compareTo(antes.custo()) <= 0 && depois.operadores() <= antes.operadores()) {
            return;
        }
        CapacidadeDia cap = motor.capacidade(data, ctx);
        boolean acimaLmax = depois.operadores() > Math.max(cap.lMin(), cap.lMax());
        boolean acimaTeto = depois.custo().compareTo(cap.tetoFolha()) > 0 && depois.operadores() > cap.lMin();
        if (acimaLmax || acimaTeto) {
            List<Violacao> motivos = validador.validar(data, data, turnos.findByDataBetween(data, data), ctx).violacoes().stream()
                    .filter(Violacao::financeira).toList();
            throw new RegraNegocioException("Alocação bloqueada: o dia ultrapassaria "
                    + (acimaLmax ? "o máximo de " + cap.lMax() + " colaboradores" : "o limite de custo do dia")
                    + ". Para permitir, escolha \"Apenas alertar a gestão\" em Parâmetros.", motivos);
        }
    }

    private record DiaSnapshot(int operadores, BigDecimal custo) {
    }

    private DiaSnapshot snapshot(LocalDate data, ContextoCalculo ctx) {
        List<Turno> doDia = turnos.findByDataBetween(data, data);
        int ops = (int) doDia.stream().map(t -> t.getFuncionario().getId()).distinct().count();
        return new DiaSnapshot(ops, motor.custoEscala(data, doDia, ctx));
    }

    private Turno montarTurno(CelulaRequest req, Funcionario f) {
        if (req.turnoModeloId() != null) {
            TurnoModelo m = modelos.findById(req.turnoModeloId())
                    .orElseThrow(() -> new NaoEncontradoException("Modelo de turno", req.turnoModeloId()));
            return Turno.de(m, f, req.data());
        }
        if (req.horaInicio() != null && req.horaFim() != null) {
            if (req.horaInicio().equals(req.horaFim())) {
                throw new RegraNegocioException("Hora de início e fim não podem ser iguais.");
            }
            int intervalo = req.intervaloMinutos() == null ? 0 : req.intervaloMinutos();
            return new Turno(f, req.data(), req.horaInicio(), req.horaFim(), intervalo, "Pers.");
        }
        return null; // folga
    }

    // ------------------------------------------------------------------ geração, validação e aprovação

    @Transactional
    public Geracao gerar(Long id) {
        Escala e = buscar(id);
        exigirEditavel(e);
        turnos.deleteByEscalaId(id);
        turnos.flush();

        ContextoCalculo ctx = contextoDa(e);
        List<Turno> vizinhos = turnos.findByDataBetween(e.getDataInicio().minusDays(MARGEM_DIAS), e.getDataFim().plusDays(MARGEM_DIAS));
        GeradorEscala.ResultadoGeracao resultado = gerador.gerar(e.getDataInicio(), e.getDataFim(), vizinhos,
                modelos.findAllByOrderByHoraInicioAsc(), ctx);
        resultado.turnos().forEach(t -> t.setEscala(e));
        turnos.saveAll(resultado.turnos());
        turnos.flush();
        return new Geracao(resultado.turnos().size(), resultado.deficits(), matriz(id));
    }

    @Transactional
    public Matriz limpar(Long id) {
        Escala e = buscar(id);
        exigirEditavel(e);
        turnos.deleteByEscalaId(id);
        turnos.flush();
        return matriz(id);
    }

    @Transactional(readOnly = true)
    public ResultadoValidacao validar(Long id) {
        return matriz(id).validacao();
    }

    @Transactional
    public Escala aprovar(Long id, String usuario) {
        Escala e = buscar(id);
        exigirEditavel(e);
        ResultadoValidacao v = validar(id);
        if (!v.aprovavel()) {
            throw new RegraNegocioException("A escala possui " + v.erros() + " erro(s) e não pode ser aprovada.",
                    v.violacoes().stream().filter(Violacao::erro).toList());
        }
        e.setStatus(Escala.Status.APROVADA);
        e.setAprovadaEm(LocalDateTime.now());
        e.setAprovadaPor(usuario);
        return e;
    }

    @Transactional
    public Escala reabrir(Long id) {
        Escala e = buscar(id);
        e.setStatus(Escala.Status.RASCUNHO);
        e.setAprovadaEm(null);
        e.setAprovadaPor(null);
        return e;
    }

    /** Painel consolidado de qualquer período (todas as escalas que o cobrem). */
    @Transactional(readOnly = true)
    public ResultadoValidacao painel(LocalDate inicio, LocalDate fim) {
        if (fim.isBefore(inicio) || ChronoUnit.DAYS.between(inicio, fim) > MAX_DIAS_ESCALA) {
            throw new RegraNegocioException("Período inválido para o painel.");
        }
        ContextoCalculo ctx = contextos.carregar(inicio.minusDays(MARGEM_DIAS), fim.plusDays(MARGEM_DIAS));
        List<Turno> janela = turnos.findByDataBetween(inicio.minusDays(MARGEM_DIAS), fim.plusDays(MARGEM_DIAS));
        return validador.validar(inicio, fim, janela, ctx);
    }

    // ------------------------------------------------------------------ auxiliares

    private ContextoCalculo contextoDa(Escala e) {
        return contextos.carregar(e.getDataInicio().minusDays(MARGEM_DIAS), e.getDataFim().plusDays(MARGEM_DIAS));
    }

    private Escala buscar(Long id) {
        return escalas.findById(id).orElseThrow(() -> new NaoEncontradoException("Escala", id));
    }

    private static void exigirEditavel(Escala e) {
        if (!e.editavel()) {
            throw new RegraNegocioException("A escala está aprovada. Reabra-a para editar.");
        }
    }

    private void validarPeriodo(EscalaRequest req, Long ignorarId) {
        if (req.dataInicio() == null || req.dataFim() == null) {
            throw new RegraNegocioException("Informe data de início e fim.");
        }
        if (req.dataFim().isBefore(req.dataInicio())) {
            throw new RegraNegocioException("A data final deve ser posterior à inicial.");
        }
        if (ChronoUnit.DAYS.between(req.dataInicio(), req.dataFim()) >= MAX_DIAS_ESCALA) {
            throw new RegraNegocioException("A escala pode ter no máximo " + MAX_DIAS_ESCALA + " dias.");
        }
        if (escalas.existeSobreposicao(req.dataInicio(), req.dataFim(), ignorarId)) {
            throw new RegraNegocioException("Já existe uma escala cobrindo parte deste período.");
        }
    }

    private static String nomePadrao(LocalDate inicio, LocalDate fim) {
        var fmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM");
        return "Escala " + fmt.format(inicio) + " a " + fmt.format(fim);
    }
}
