package br.com.coelho.escalas.servico;

import br.com.coelho.escalas.dominio.Ausencia;
import br.com.coelho.escalas.dominio.Funcionario;
import br.com.coelho.escalas.repositorio.AusenciaRepository;
import br.com.coelho.escalas.repositorio.FuncionarioRepository;
import br.com.coelho.escalas.repositorio.SetorRepository;
import br.com.coelho.escalas.repositorio.TurnoRepository;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Colaboradores (dados contratuais, disponibilidade e foto) e os seus períodos de ausência. */
@Service
public class FuncionarioService {

    /** Carga horária mensal de quem trabalha 44 horas semanais (padrão da CLT). */
    private static final int CARGA_HORARIA_MENSAL_PADRAO = 220;

    public record DadosFuncionario(
            @NotBlank String nome,
            String matricula,
            String cargo,
            @NotNull Long setorId,
            @NotNull @DecimalMin("0.01") BigDecimal salarioMensal,
            @Min(1) @Max(300) Integer cargaHorariaMensal,
            @DecimalMin("0") @DecimalMax("200") BigDecimal percentualEncargos,
            Boolean ativo,
            Set<DayOfWeek> diasDisponiveis) {
    }

    public record DadosAusencia(@NotNull LocalDate inicio, @NotNull LocalDate fim, Ausencia.Motivo motivo, String observacao) {
    }

    public enum ResultadoExclusao { EXCLUIDO, DESATIVADO }

    private final FuncionarioRepository funcionarios;
    private final SetorRepository setores;
    private final AusenciaRepository ausencias;
    private final TurnoRepository turnos;
    private final FeriasService ferias;

    public FuncionarioService(FuncionarioRepository funcionarios, SetorRepository setores, AusenciaRepository ausencias,
                              TurnoRepository turnos, FeriasService ferias) {
        this.funcionarios = funcionarios;
        this.setores = setores;
        this.ausencias = ausencias;
        this.turnos = turnos;
        this.ferias = ferias;
    }

    // ------------------------------------------------------------------ colaboradores

    @Transactional(readOnly = true)
    public List<Funcionario> listar() {
        return funcionarios.findAllByOrderBySetorNomeAscNomeAsc();
    }

    @Transactional
    public Funcionario criar(DadosFuncionario dados) {
        return funcionarios.save(aplicar(new Funcionario(), dados));
    }

    @Transactional
    public Funcionario atualizar(Long id, DadosFuncionario dados) {
        return aplicar(buscar(id), dados);
    }

    /** Colaboradores com histórico de escala são apenas desativados, preservando a auditoria. */
    @Transactional
    public ResultadoExclusao excluir(Long id) {
        Funcionario f = buscar(id);
        if (turnos.existsByFuncionario_Id(id)) {
            f.setAtivo(false);
            return ResultadoExclusao.DESATIVADO;
        }
        ausencias.deleteByFuncionario_Id(id);
        funcionarios.delete(f);
        return ResultadoExclusao.EXCLUIDO;
    }

    @Transactional
    public Funcionario definirFoto(Long id, byte[] arquivo) {
        Funcionario f = buscar(id);
        f.setFoto(FotoPerfil.normalizar(arquivo));
        return f;
    }

    @Transactional
    public Funcionario removerFoto(Long id) {
        Funcionario f = buscar(id);
        f.setFoto(null);
        return f;
    }

    // ------------------------------------------------------------------ ausências

    @Transactional(readOnly = true)
    public List<Ausencia> ausencias() {
        return ausencias.findAll();
    }

    @Transactional(readOnly = true)
    public List<Ausencia> ausenciasDe(Long funcionarioId) {
        return ausencias.findByFuncionario_IdOrderByInicioDesc(funcionarioId);
    }

    @Transactional
    public Ausencia registrarAusencia(Long funcionarioId, DadosAusencia dados) {
        if (dados.fim().isBefore(dados.inicio())) {
            throw new RegraNegocioException("O fim da ausência deve ser posterior ao início.");
        }
        if (dados.motivo() == Ausencia.Motivo.FERIAS) {
            return ferias.registrar(new FeriasService.DadosFerias(funcionarioId, dados.inicio(), dados.fim(), dados.observacao())).ferias();
        }
        Ausencia a = new Ausencia();
        a.setFuncionario(buscar(funcionarioId));
        a.setInicio(dados.inicio());
        a.setFim(dados.fim());
        a.setMotivo(dados.motivo() == null ? Ausencia.Motivo.OUTRO : dados.motivo());
        a.setObservacao(dados.observacao());
        return ausencias.save(a);
    }

    @Transactional
    public void removerAusencia(Long id) {
        ausencias.deleteById(id);
    }

    // ------------------------------------------------------------------ auxiliares

    private Funcionario buscar(Long id) {
        return funcionarios.findById(id).orElseThrow(() -> new NaoEncontradoException("Colaborador", id));
    }

    private Funcionario aplicar(Funcionario f, DadosFuncionario dados) {
        f.setNome(dados.nome().trim());
        f.setMatricula(dados.matricula() == null || dados.matricula().isBlank() ? null : dados.matricula().trim());
        f.setCargo(dados.cargo());
        f.setSetor(setores.findById(dados.setorId()).orElseThrow(() -> new NaoEncontradoException("Setor", dados.setorId())));
        f.setSalarioMensal(dados.salarioMensal());
        f.setCargaHorariaMensal(dados.cargaHorariaMensal() == null ? CARGA_HORARIA_MENSAL_PADRAO : dados.cargaHorariaMensal());
        f.setPercentualEncargos(dados.percentualEncargos() == null ? BigDecimal.ZERO : dados.percentualEncargos());
        f.setAtivo(dados.ativo() == null || dados.ativo());
        // Sem dias informados, o colaborador fica disponível a semana inteira.
        Set<DayOfWeek> dias = dados.diasDisponiveis() == null || dados.diasDisponiveis().isEmpty()
                ? EnumSet.allOf(DayOfWeek.class) : EnumSet.copyOf(dados.diasDisponiveis());
        f.getDiasDisponiveis().clear();
        f.getDiasDisponiveis().addAll(dias);
        return f;
    }
}
