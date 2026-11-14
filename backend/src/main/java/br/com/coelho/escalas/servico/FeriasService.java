package br.com.coelho.escalas.servico;

import br.com.coelho.escalas.dominio.Ausencia;
import br.com.coelho.escalas.dominio.Escala;
import br.com.coelho.escalas.dominio.Feriado;
import br.com.coelho.escalas.dominio.Funcionario;
import br.com.coelho.escalas.dominio.Turno;
import br.com.coelho.escalas.motor.ContextoCalculo;
import br.com.coelho.escalas.motor.RegrasFerias;
import br.com.coelho.escalas.motor.SugestorSubstituicao;
import br.com.coelho.escalas.motor.SugestorSubstituicao.Cobertura;
import br.com.coelho.escalas.repositorio.AusenciaRepository;
import br.com.coelho.escalas.repositorio.FeriadoRepository;
import br.com.coelho.escalas.repositorio.FuncionarioRepository;
import br.com.coelho.escalas.repositorio.TurnoRepository;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Operação de férias: registro e edição validados pelas regras da CLT ({@link RegrasFerias}). Os turnos que o
 * colaborador já tinha no período são repassados a colegas aptos ({@link SugestorSubstituicao#planejarCobertura}),
 * inclusive em escala aprovada, e cada troca fica anotada na observação da escala.
 */
@Service
public class FeriasService {

    public record DadosFerias(@NotNull Long funcionarioId, @NotNull LocalDate inicio, @NotNull LocalDate fim, String observacao) {
    }

    /** Um turno já escalado no período e quem o assume (substituto nulo: ninguém pode, o turno é retirado). */
    public record CoberturaTurno(Long turnoId, Long escalaId, String escalaNome, LocalDate data, LocalTime horaInicio,
                                 LocalTime horaFim, Long substitutoId, String substitutoNome, String substitutoSetor) {
    }

    public record Previa(int dias, LocalDate retorno, List<String> erros, List<String> alertas, boolean valida,
                         List<CoberturaTurno> coberturas) {
    }

    public record Registro(Ausencia ferias, List<CoberturaTurno> coberturas) {
    }

    /** Margem em volta do período para as regras de continuidade na escolha dos substitutos. */
    private static final int MARGEM_DIAS = 7;
    /** Tamanho da coluna de observação da escala. */
    private static final int OBSERVACAO_MAX = 255;
    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd/MM");

    private final AusenciaRepository ausencias;
    private final FuncionarioRepository funcionarios;
    private final TurnoRepository turnos;
    private final FeriadoRepository feriados;
    private final ContextoService contextos;
    private final SugestorSubstituicao sugestor;

    public FeriasService(AusenciaRepository ausencias, FuncionarioRepository funcionarios, TurnoRepository turnos,
                         FeriadoRepository feriados, ContextoService contextos, SugestorSubstituicao sugestor) {
        this.ausencias = ausencias;
        this.funcionarios = funcionarios;
        this.turnos = turnos;
        this.feriados = feriados;
        this.contextos = contextos;
        this.sugestor = sugestor;
    }

    @Transactional(readOnly = true)
    public List<Ausencia> listar() {
        return ausencias.findByMotivoOrderByInicioDesc(Ausencia.Motivo.FERIAS);
    }

    /** Prévia sem gravar nada: regras da CLT e quem assumiria cada turno já escalado. */
    @Transactional(readOnly = true)
    public Previa analisar(Long funcionarioId, LocalDate inicio, LocalDate fim, Long ignorarId) {
        Funcionario f = buscarFuncionario(funcionarioId);
        RegrasFerias.Analise analise = analisar(f, inicio, fim, ignorarId);
        List<CoberturaTurno> coberturas = analise.valida()
                ? planejar(f, inicio, fim).stream().map(FeriasService::dto).toList()
                : List.of();
        return new Previa(analise.dias(), analise.retorno(), analise.erros(), analise.alertas(), analise.valida(), coberturas);
    }

    @Transactional
    public Registro registrar(DadosFerias dados) {
        Funcionario f = buscarFuncionario(dados.funcionarioId());
        exigirValida(analisar(f, dados.inicio(), dados.fim(), null));
        Ausencia a = new Ausencia();
        a.setFuncionario(f);
        a.setMotivo(Ausencia.Motivo.FERIAS);
        preencher(a, dados);
        return new Registro(ausencias.save(a), repassarTurnos(f, dados.inicio(), dados.fim()));
    }

    @Transactional
    public Registro atualizar(Long id, DadosFerias dados) {
        Ausencia a = buscar(id);
        Funcionario f = buscarFuncionario(dados.funcionarioId());
        exigirValida(analisar(f, dados.inicio(), dados.fim(), id));
        a.setFuncionario(f);
        preencher(a, dados);
        return new Registro(a, repassarTurnos(f, dados.inicio(), dados.fim()));
    }

    @Transactional
    public void remover(Long id) {
        ausencias.delete(buscar(id));
    }

    // ------------------------------------------------------------------ cobertura dos turnos já escalados

    private List<Cobertura> planejar(Funcionario f, LocalDate inicio, LocalDate fim) {
        LocalDate de = inicio.minusDays(MARGEM_DIAS);
        LocalDate ate = fim.plusDays(MARGEM_DIAS);
        List<Turno> janela = turnos.findByDataBetween(de, ate);
        List<Turno> doTitular = janela.stream()
                .filter(t -> t.getFuncionario().getId().equals(f.getId()) && !t.getData().isBefore(inicio) && !t.getData().isAfter(fim))
                .toList();
        if (doTitular.isEmpty()) {
            return List.of();
        }
        ContextoCalculo ctx = contextos.carregar(de, ate);
        return sugestor.planejarCobertura(doTitular, janela, ctx);
    }

    /**
     * Aplica o plano: cada turno passa ao substituto ou, sem ninguém apto, é retirado (o painel passa a apontar o dia
     * descoberto). Vale também para escala aprovada, já que ninguém pode trabalhar de férias; a troca fica anotada.
     */
    private List<CoberturaTurno> repassarTurnos(Funcionario titular, LocalDate inicio, LocalDate fim) {
        List<CoberturaTurno> feitas = new ArrayList<>();
        Map<Long, Escala> escalas = new LinkedHashMap<>();
        Map<Long, List<String>> notas = new LinkedHashMap<>();
        for (Cobertura c : planejar(titular, inicio, fim)) {
            Turno t = c.turno();
            feitas.add(dto(c));
            escalas.putIfAbsent(t.getEscala().getId(), t.getEscala());
            String dia = DIA_MES.format(t.getData()) + ": " + titular.getNome();
            List<String> daEscala = notas.computeIfAbsent(t.getEscala().getId(), k -> new ArrayList<>());
            if (c.substituto() == null) {
                turnos.delete(t);
                daEscala.add(dia + " de férias, turno retirado sem substituto");
            } else {
                t.setFuncionario(buscarFuncionario(c.substituto().funcionarioId()));
                daEscala.add(dia + " → " + c.substituto().nome() + " (férias)");
            }
        }
        turnos.flush();
        notas.forEach((id, linhas) -> anotar(escalas.get(id), String.join("; ", linhas) + "."));
        return feitas;
    }

    /** A anotação mais recente vem primeiro; o que não couber na coluna é cortado. */
    private static void anotar(Escala e, String nota) {
        String texto = e.getObservacao() == null || e.getObservacao().isBlank() ? nota : nota + " " + e.getObservacao();
        e.setObservacao(texto.length() <= OBSERVACAO_MAX ? texto : texto.substring(0, OBSERVACAO_MAX - 1) + "…");
    }

    private static CoberturaTurno dto(Cobertura c) {
        Turno t = c.turno();
        SugestorSubstituicao.Candidato s = c.substituto();
        return new CoberturaTurno(t.getId(), t.getEscala().getId(), t.getEscala().getNome(), t.getData(), t.getHoraInicio(),
                t.getHoraFim(), s == null ? null : s.funcionarioId(), s == null ? null : s.nome(), s == null ? null : s.setorNome());
    }

    // ------------------------------------------------------------------ auxiliares

    private RegrasFerias.Analise analisar(Funcionario f, LocalDate inicio, LocalDate fim, Long ignorarId) {
        // Os dois dias após o início entram na busca de feriados (CLT art. 134, §3º).
        LocalDate ate = fim.isAfter(inicio.plusDays(2)) ? fim : inicio.plusDays(2);
        Set<LocalDate> datasFeriado = feriados.findByDataBetween(inicio, ate).stream().map(Feriado::getData).collect(Collectors.toSet());
        return RegrasFerias.analisar(f, inicio, fim, ausencias.findByFimGreaterThanEqualAndInicioLessThanEqual(inicio, fim),
                datasFeriado, ignorarId);
    }

    private static void exigirValida(RegrasFerias.Analise analise) {
        if (!analise.valida()) {
            throw new RegraNegocioException(String.join(" ", analise.erros()));
        }
    }

    private static void preencher(Ausencia a, DadosFerias dados) {
        a.setInicio(dados.inicio());
        a.setFim(dados.fim());
        a.setObservacao(dados.observacao() == null || dados.observacao().isBlank() ? null : dados.observacao().trim());
    }

    private Ausencia buscar(Long id) {
        return ausencias.findById(id)
                .filter(a -> a.getMotivo() == Ausencia.Motivo.FERIAS)
                .orElseThrow(() -> new NaoEncontradoException("Férias", id));
    }

    private Funcionario buscarFuncionario(Long id) {
        return funcionarios.findById(id).orElseThrow(() -> new NaoEncontradoException("Colaborador", id));
    }
}
