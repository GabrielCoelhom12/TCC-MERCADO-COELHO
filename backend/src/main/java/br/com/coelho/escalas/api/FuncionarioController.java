package br.com.coelho.escalas.api;

import br.com.coelho.escalas.dominio.Ausencia;
import br.com.coelho.escalas.dominio.Funcionario;
import br.com.coelho.escalas.repositorio.AusenciaRepository;
import br.com.coelho.escalas.repositorio.FuncionarioRepository;
import br.com.coelho.escalas.repositorio.SetorRepository;
import br.com.coelho.escalas.repositorio.TurnoRepository;
import br.com.coelho.escalas.servico.FotoPerfil;
import br.com.coelho.escalas.servico.NaoEncontradoException;
import br.com.coelho.escalas.servico.RegraNegocioException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api")
public class FuncionarioController {

    public record FuncionarioRequest(
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

    public record AusenciaRequest(@NotNull LocalDate inicio, @NotNull LocalDate fim, Ausencia.Motivo motivo, String observacao) {
    }

    private final FuncionarioRepository funcionarios;
    private final SetorRepository setores;
    private final AusenciaRepository ausencias;
    private final TurnoRepository turnos;

    public FuncionarioController(FuncionarioRepository funcionarios, SetorRepository setores,
                                 AusenciaRepository ausencias, TurnoRepository turnos) {
        this.funcionarios = funcionarios;
        this.setores = setores;
        this.ausencias = ausencias;
        this.turnos = turnos;
    }

    @GetMapping("/funcionarios")
    public List<Funcionario> listar() {
        return funcionarios.findAllByOrderBySetorNomeAscNomeAsc();
    }

    @PostMapping("/funcionarios")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Funcionario criar(@Valid @RequestBody FuncionarioRequest req) {
        return funcionarios.save(aplicar(new Funcionario(), req));
    }

    @PutMapping("/funcionarios/{id}")
    @Transactional
    public Funcionario atualizar(@PathVariable Long id, @Valid @RequestBody FuncionarioRequest req) {
        return aplicar(buscar(id), req);
    }

    /** Colaboradores com histórico de escala são apenas desativados, preservando a auditoria. */
    @DeleteMapping("/funcionarios/{id}")
    @Transactional
    public Map<String, String> excluir(@PathVariable Long id) {
        Funcionario f = buscar(id);
        if (turnos.existsByFuncionario_Id(id)) {
            f.setAtivo(false);
            return Map.of("resultado", "desativado");
        }
        ausencias.deleteByFuncionario_Id(id);
        funcionarios.delete(f);
        return Map.of("resultado", "excluido");
    }

    @PutMapping("/funcionarios/{id}/foto")
    @Transactional
    public Funcionario definirFoto(@PathVariable Long id, @RequestParam("arquivo") MultipartFile arquivo) throws IOException {
        Funcionario f = buscar(id);
        f.setFoto(FotoPerfil.normalizar(arquivo.getBytes()));
        return f;
    }

    @DeleteMapping("/funcionarios/{id}/foto")
    @Transactional
    public Funcionario removerFoto(@PathVariable Long id) {
        Funcionario f = buscar(id);
        f.setFoto(null);
        return f;
    }

    @GetMapping("/funcionarios/{id}/ausencias")
    public List<Ausencia> ausenciasDe(@PathVariable Long id) {
        return ausencias.findByFuncionario_IdOrderByInicioDesc(id);
    }

    @GetMapping("/ausencias")
    public List<Ausencia> todasAusencias() {
        return ausencias.findAll();
    }

    @PostMapping("/funcionarios/{id}/ausencias")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Ausencia registrarAusencia(@PathVariable Long id, @Valid @RequestBody AusenciaRequest req) {
        if (req.fim().isBefore(req.inicio())) {
            throw new RegraNegocioException("O fim da ausência deve ser posterior ao início.");
        }
        Ausencia a = new Ausencia();
        a.setFuncionario(buscar(id));
        a.setInicio(req.inicio());
        a.setFim(req.fim());
        a.setMotivo(req.motivo() == null ? Ausencia.Motivo.OUTRO : req.motivo());
        a.setObservacao(req.observacao());
        return ausencias.save(a);
    }

    @DeleteMapping("/ausencias/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removerAusencia(@PathVariable Long id) {
        ausencias.deleteById(id);
    }

    private Funcionario buscar(Long id) {
        return funcionarios.findById(id).orElseThrow(() -> new NaoEncontradoException("Colaborador", id));
    }

    private Funcionario aplicar(Funcionario f, FuncionarioRequest req) {
        f.setNome(req.nome().trim());
        f.setMatricula(req.matricula() == null || req.matricula().isBlank() ? null : req.matricula().trim());
        f.setCargo(req.cargo());
        f.setSetor(setores.findById(req.setorId()).orElseThrow(() -> new NaoEncontradoException("Setor", req.setorId())));
        f.setSalarioMensal(req.salarioMensal());
        f.setCargaHorariaMensal(req.cargaHorariaMensal() == null ? 220 : req.cargaHorariaMensal());
        f.setPercentualEncargos(req.percentualEncargos() == null ? BigDecimal.ZERO : req.percentualEncargos());
        f.setAtivo(req.ativo() == null || req.ativo());
        Set<DayOfWeek> dias = req.diasDisponiveis() == null || req.diasDisponiveis().isEmpty()
                ? EnumSet.allOf(DayOfWeek.class) : EnumSet.copyOf(req.diasDisponiveis());
        f.getDiasDisponiveis().clear();
        f.getDiasDisponiveis().addAll(dias);
        return f;
    }
}
