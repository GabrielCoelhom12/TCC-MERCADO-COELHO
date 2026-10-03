package br.com.coelho.escalas.api;

import br.com.coelho.escalas.dominio.Setor;
import br.com.coelho.escalas.dominio.TipoDia;
import br.com.coelho.escalas.repositorio.FuncionarioRepository;
import br.com.coelho.escalas.repositorio.SetorRepository;
import br.com.coelho.escalas.servico.NaoEncontradoException;
import br.com.coelho.escalas.servico.RegraNegocioException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/setores")
public class SetorController {

    /** clientesPorColaboradorHora vazio = setor com demanda fixa (não acompanha a previsão de movimento). */
    public record SetorRequest(@NotBlank String nome, String cor, Boolean ativo, Map<TipoDia, Integer> minimoPorDia,
                               @Min(1) @Max(500) Integer clientesPorColaboradorHora) {
    }

    private final SetorRepository setores;
    private final FuncionarioRepository funcionarios;

    public SetorController(SetorRepository setores, FuncionarioRepository funcionarios) {
        this.setores = setores;
        this.funcionarios = funcionarios;
    }

    @GetMapping
    public List<Setor> listar() {
        return setores.findAllByOrderByNomeAsc();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Setor criar(@Valid @RequestBody SetorRequest req) {
        return setores.save(aplicar(new Setor(), req));
    }

    @PutMapping("/{id}")
    @Transactional
    public Setor atualizar(@PathVariable Long id, @Valid @RequestBody SetorRequest req) {
        return aplicar(setores.findById(id).orElseThrow(() -> new NaoEncontradoException("Setor", id)), req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void excluir(@PathVariable Long id) {
        if (funcionarios.existsBySetorId(id)) {
            throw new RegraNegocioException("O setor possui colaboradores. Transfira-os ou desative o setor.");
        }
        setores.deleteById(id);
    }

    private Setor aplicar(Setor s, SetorRequest req) {
        s.setNome(req.nome().trim());
        s.setCor(req.cor() == null || req.cor().isBlank() ? "#F26A1B" : req.cor());
        s.setAtivo(req.ativo() == null || req.ativo());
        s.setClientesPorColaboradorHora(req.clientesPorColaboradorHora());
        if (req.minimoPorDia() != null) {
            req.minimoPorDia().forEach((tipo, minimo) -> {
                if (minimo == null || minimo < 0) {
                    throw new RegraNegocioException("Demanda mínima inválida para " + tipo.getRotulo() + ".");
                }
            });
            s.getMinimoPorDia().clear();
            s.getMinimoPorDia().putAll(req.minimoPorDia());
        }
        return s;
    }
}
