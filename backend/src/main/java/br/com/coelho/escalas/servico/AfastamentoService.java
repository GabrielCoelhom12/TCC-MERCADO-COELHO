package br.com.coelho.escalas.servico;

import br.com.coelho.escalas.dominio.Ausencia;
import br.com.coelho.escalas.dominio.Funcionario;
import br.com.coelho.escalas.motor.AnaliseAusencia;
import br.com.coelho.escalas.motor.RegrasAfastamento;
import br.com.coelho.escalas.repositorio.AusenciaRepository;
import br.com.coelho.escalas.repositorio.DocumentoAusenciaRepository;
import br.com.coelho.escalas.repositorio.FuncionarioRepository;
import br.com.coelho.escalas.servico.CoberturaService.CoberturaTurno;
import br.com.coelho.escalas.servico.CoberturaService.Previa;
import br.com.coelho.escalas.servico.CoberturaService.Registro;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Operação de afastamentos (atestado, licenças, afastamento pelo INSS, acidente de trabalho, luto, casamento, folga
 * acordada...). Valida o período ({@link RegrasAfastamento}) e repassa na hora os turnos já escalados
 * ({@link CoberturaService}). Só o tipo é guardado: diagnóstico e CID não entram no sistema (LGPD, dado de saúde).
 */
@Service
public class AfastamentoService {

    public record DadosAfastamento(@NotNull Long funcionarioId, @NotNull Ausencia.Motivo motivo, @NotNull LocalDate inicio,
                                   @NotNull LocalDate fim, @Size(max = 255) String observacao) {
    }

    private final AusenciaRepository ausencias;
    private final FuncionarioRepository funcionarios;
    private final CoberturaService cobertura;
    private final DocumentoAusenciaRepository documentos;

    public AfastamentoService(AusenciaRepository ausencias, FuncionarioRepository funcionarios, CoberturaService cobertura,
                              DocumentoAusenciaRepository documentos) {
        this.ausencias = ausencias;
        this.funcionarios = funcionarios;
        this.cobertura = cobertura;
        this.documentos = documentos;
    }

    /**
     * Todas as ausências, menos férias (que têm operação própria), das mais recentes para as mais antigas, com a
     * quantidade de documentos anexados a cada uma.
     */
    @Transactional(readOnly = true)
    public List<Ausencia> listar() {
        List<Ausencia> lista = ausencias.findByMotivoNotOrderByInicioDesc(Ausencia.Motivo.FERIAS);
        Map<Long, Long> anexos = lista.isEmpty() ? Map.of() : documentos.contarPorAusencia(lista.stream().map(Ausencia::getId).toList())
                .stream().collect(Collectors.toMap(l -> (Long) l[0], l -> (Long) l[1]));
        lista.forEach(a -> a.setDocumentos(anexos.getOrDefault(a.getId(), 0L).intValue()));
        return lista;
    }

    @Transactional(readOnly = true)
    public Previa analisar(Long funcionarioId, Ausencia.Motivo motivo, LocalDate inicio, LocalDate fim, Long ignorarId) {
        Funcionario f = buscarFuncionario(funcionarioId);
        AnaliseAusencia analise = analisar(f, motivo, inicio, fim, ignorarId);
        List<CoberturaTurno> coberturas = analise.valida() ? cobertura.planejar(f, inicio, fim) : List.of();
        return Previa.de(analise, coberturas);
    }

    @Transactional
    public Registro registrar(DadosAfastamento dados) {
        Funcionario f = buscarFuncionario(dados.funcionarioId());
        FeriasService.exigirValida(analisar(f, dados.motivo(), dados.inicio(), dados.fim(), null));
        Ausencia a = new Ausencia();
        a.setFuncionario(f);
        preencher(a, dados);
        return new Registro(ausencias.save(a), cobertura.repassar(f, dados.motivo(), dados.inicio(), dados.fim()));
    }

    @Transactional
    public Registro atualizar(Long id, DadosAfastamento dados) {
        Ausencia a = buscar(id);
        Funcionario f = buscarFuncionario(dados.funcionarioId());
        FeriasService.exigirValida(analisar(f, dados.motivo(), dados.inicio(), dados.fim(), id));
        a.setFuncionario(f);
        preencher(a, dados);
        return new Registro(a, cobertura.repassar(f, dados.motivo(), dados.inicio(), dados.fim()));
    }

    /** Os documentos anexados vão junto (dado de saúde não fica guardado sem o afastamento). */
    @Transactional
    public void remover(Long id) {
        Ausencia a = buscar(id);
        documentos.deleteByAusencia_Id(a.getId());
        ausencias.delete(a);
    }

    private AnaliseAusencia analisar(Funcionario f, Ausencia.Motivo motivo, LocalDate inicio, LocalDate fim, Long ignorarId) {
        return RegrasAfastamento.analisar(f, motivo, inicio, fim,
                ausencias.findByFimGreaterThanEqualAndInicioLessThanEqual(inicio, fim), ignorarId);
    }

    private static void preencher(Ausencia a, DadosAfastamento dados) {
        a.setMotivo(dados.motivo());
        a.setInicio(dados.inicio());
        a.setFim(dados.fim());
        a.setObservacao(dados.observacao() == null || dados.observacao().isBlank() ? null : dados.observacao().trim());
    }

    private Ausencia buscar(Long id) {
        return ausencias.findById(id)
                .filter(a -> a.getMotivo() != Ausencia.Motivo.FERIAS)
                .orElseThrow(() -> new NaoEncontradoException("Afastamento", id));
    }

    private Funcionario buscarFuncionario(Long id) {
        return funcionarios.findById(id).orElseThrow(() -> new NaoEncontradoException("Colaborador", id));
    }
}
