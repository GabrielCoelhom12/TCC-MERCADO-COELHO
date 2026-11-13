package br.com.coelho.escalas.servico;

import br.com.coelho.escalas.dominio.Ausencia;
import br.com.coelho.escalas.dominio.Feriado;
import br.com.coelho.escalas.dominio.Funcionario;
import br.com.coelho.escalas.motor.RegrasFerias;
import br.com.coelho.escalas.repositorio.AusenciaRepository;
import br.com.coelho.escalas.repositorio.FeriadoRepository;
import br.com.coelho.escalas.repositorio.FuncionarioRepository;
import br.com.coelho.escalas.repositorio.TurnoRepository;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Operação de férias: registro e edição validados pelas regras da CLT ({@link RegrasFerias}) e análise de impacto. */
@Service
public class FeriasService {

    public record DadosFerias(@NotNull Long funcionarioId, @NotNull LocalDate inicio, @NotNull LocalDate fim, String observacao) {
    }

    private final AusenciaRepository ausencias;
    private final FuncionarioRepository funcionarios;
    private final TurnoRepository turnos;
    private final FeriadoRepository feriados;

    public FeriasService(AusenciaRepository ausencias, FuncionarioRepository funcionarios, TurnoRepository turnos,
                         FeriadoRepository feriados) {
        this.ausencias = ausencias;
        this.funcionarios = funcionarios;
        this.turnos = turnos;
        this.feriados = feriados;
    }

    @Transactional(readOnly = true)
    public List<Ausencia> listar() {
        return ausencias.findByMotivoOrderByInicioDesc(Ausencia.Motivo.FERIAS);
    }

    @Transactional(readOnly = true)
    public RegrasFerias.Analise analisar(Long funcionarioId, LocalDate inicio, LocalDate fim, Long ignorarId) {
        return analisar(buscarFuncionario(funcionarioId), inicio, fim, ignorarId);
    }

    @Transactional
    public Ausencia registrar(DadosFerias dados) {
        Funcionario f = buscarFuncionario(dados.funcionarioId());
        exigirValida(analisar(f, dados.inicio(), dados.fim(), null));
        Ausencia a = new Ausencia();
        a.setFuncionario(f);
        a.setMotivo(Ausencia.Motivo.FERIAS);
        preencher(a, dados);
        return ausencias.save(a);
    }

    @Transactional
    public Ausencia atualizar(Long id, DadosFerias dados) {
        Ausencia a = buscar(id);
        Funcionario f = buscarFuncionario(dados.funcionarioId());
        exigirValida(analisar(f, dados.inicio(), dados.fim(), id));
        a.setFuncionario(f);
        preencher(a, dados);
        return a;
    }

    @Transactional
    public void remover(Long id) {
        ausencias.delete(buscar(id));
    }

    // ------------------------------------------------------------------ auxiliares

    private RegrasFerias.Analise analisar(Funcionario f, LocalDate inicio, LocalDate fim, Long ignorarId) {
        // Os dois dias após o início entram na busca de feriados (CLT art. 134, §3º).
        LocalDate ate = fim.isAfter(inicio.plusDays(2)) ? fim : inicio.plusDays(2);
        Set<LocalDate> datasFeriado = feriados.findByDataBetween(inicio, ate).stream().map(Feriado::getData).collect(Collectors.toSet());
        return RegrasFerias.analisar(f, inicio, fim, ausencias.findByFimGreaterThanEqualAndInicioLessThanEqual(inicio, fim),
                turnos.findByDataBetween(inicio, fim), datasFeriado, ignorarId);
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
