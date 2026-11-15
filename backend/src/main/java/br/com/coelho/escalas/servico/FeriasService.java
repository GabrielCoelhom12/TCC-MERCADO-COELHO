package br.com.coelho.escalas.servico;

import br.com.coelho.escalas.dominio.Ausencia;
import br.com.coelho.escalas.dominio.Feriado;
import br.com.coelho.escalas.dominio.Funcionario;
import br.com.coelho.escalas.motor.AnaliseAusencia;
import br.com.coelho.escalas.motor.RegrasFerias;
import br.com.coelho.escalas.repositorio.AusenciaRepository;
import br.com.coelho.escalas.repositorio.FeriadoRepository;
import br.com.coelho.escalas.repositorio.FuncionarioRepository;
import br.com.coelho.escalas.servico.CoberturaService.CoberturaTurno;
import br.com.coelho.escalas.servico.CoberturaService.Previa;
import br.com.coelho.escalas.servico.CoberturaService.Registro;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Operação de férias: registro e edição validados pelas regras da CLT ({@link RegrasFerias}). Os turnos que o
 * colaborador já tinha no período são repassados a colegas aptos ({@link CoberturaService}).
 */
@Service
public class FeriasService {

    public record DadosFerias(@NotNull Long funcionarioId, @NotNull LocalDate inicio, @NotNull LocalDate fim, String observacao) {
    }

    private final AusenciaRepository ausencias;
    private final FuncionarioRepository funcionarios;
    private final FeriadoRepository feriados;
    private final CoberturaService cobertura;

    public FeriasService(AusenciaRepository ausencias, FuncionarioRepository funcionarios, FeriadoRepository feriados,
                         CoberturaService cobertura) {
        this.ausencias = ausencias;
        this.funcionarios = funcionarios;
        this.feriados = feriados;
        this.cobertura = cobertura;
    }

    @Transactional(readOnly = true)
    public List<Ausencia> listar() {
        return ausencias.findByMotivoOrderByInicioDesc(Ausencia.Motivo.FERIAS);
    }

    /** Prévia sem gravar nada: regras da CLT e quem assumiria cada turno já escalado. */
    @Transactional(readOnly = true)
    public Previa analisar(Long funcionarioId, LocalDate inicio, LocalDate fim, Long ignorarId) {
        Funcionario f = buscarFuncionario(funcionarioId);
        AnaliseAusencia analise = analisar(f, inicio, fim, ignorarId);
        List<CoberturaTurno> coberturas = analise.valida() ? cobertura.planejar(f, inicio, fim) : List.of();
        return Previa.de(analise, coberturas);
    }

    @Transactional
    public Registro registrar(DadosFerias dados) {
        Funcionario f = buscarFuncionario(dados.funcionarioId());
        exigirValida(analisar(f, dados.inicio(), dados.fim(), null));
        Ausencia a = new Ausencia();
        a.setFuncionario(f);
        a.setMotivo(Ausencia.Motivo.FERIAS);
        preencher(a, dados);
        return new Registro(ausencias.save(a), cobertura.repassar(f, Ausencia.Motivo.FERIAS, dados.inicio(), dados.fim()));
    }

    @Transactional
    public Registro atualizar(Long id, DadosFerias dados) {
        Ausencia a = buscar(id);
        Funcionario f = buscarFuncionario(dados.funcionarioId());
        exigirValida(analisar(f, dados.inicio(), dados.fim(), id));
        a.setFuncionario(f);
        preencher(a, dados);
        return new Registro(a, cobertura.repassar(f, Ausencia.Motivo.FERIAS, dados.inicio(), dados.fim()));
    }

    @Transactional
    public void remover(Long id) {
        ausencias.delete(buscar(id));
    }

    // ------------------------------------------------------------------ auxiliares

    private AnaliseAusencia analisar(Funcionario f, LocalDate inicio, LocalDate fim, Long ignorarId) {
        // Os dois dias após o início entram na busca de feriados (CLT art. 134, §3º).
        LocalDate ate = fim.isAfter(inicio.plusDays(2)) ? fim : inicio.plusDays(2);
        Set<LocalDate> datasFeriado = feriados.findByDataBetween(inicio, ate).stream().map(Feriado::getData).collect(Collectors.toSet());
        return RegrasFerias.analisar(f, inicio, fim, ausencias.findByFimGreaterThanEqualAndInicioLessThanEqual(inicio, fim),
                datasFeriado, ignorarId);
    }

    static void exigirValida(AnaliseAusencia analise) {
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
