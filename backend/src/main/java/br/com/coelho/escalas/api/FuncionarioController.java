package br.com.coelho.escalas.api;

import br.com.coelho.escalas.dominio.Ausencia;
import br.com.coelho.escalas.dominio.Funcionario;
import br.com.coelho.escalas.servico.FuncionarioService;
import br.com.coelho.escalas.servico.FuncionarioService.DadosAusencia;
import br.com.coelho.escalas.servico.FuncionarioService.DadosFuncionario;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Colaboradores e ausências (férias, atestados, folgas acordadas). */
@RestController
@RequestMapping("/api")
public class FuncionarioController {

    private final FuncionarioService funcionarios;

    public FuncionarioController(FuncionarioService funcionarios) {
        this.funcionarios = funcionarios;
    }

    @GetMapping("/funcionarios")
    public List<Funcionario> listar() {
        return funcionarios.listar();
    }

    @PostMapping("/funcionarios")
    @ResponseStatus(HttpStatus.CREATED)
    public Funcionario criar(@Valid @RequestBody DadosFuncionario req) {
        return funcionarios.criar(req);
    }

    @PutMapping("/funcionarios/{id}")
    public Funcionario atualizar(@PathVariable Long id, @Valid @RequestBody DadosFuncionario req) {
        return funcionarios.atualizar(id, req);
    }

    /** Responde {@code {"resultado": "excluido"}} ou {@code "desativado"} (colaborador com histórico de escala). */
    @DeleteMapping("/funcionarios/{id}")
    public Map<String, String> excluir(@PathVariable Long id) {
        return Map.of("resultado", funcionarios.excluir(id).name().toLowerCase(Locale.ROOT));
    }

    @PutMapping("/funcionarios/{id}/foto")
    public Funcionario definirFoto(@PathVariable Long id, @RequestParam("arquivo") MultipartFile arquivo) throws IOException {
        return funcionarios.definirFoto(id, arquivo.getBytes());
    }

    @DeleteMapping("/funcionarios/{id}/foto")
    public Funcionario removerFoto(@PathVariable Long id) {
        return funcionarios.removerFoto(id);
    }

    @GetMapping("/funcionarios/{id}/ausencias")
    public List<Ausencia> ausenciasDe(@PathVariable Long id) {
        return funcionarios.ausenciasDe(id);
    }

    @GetMapping("/ausencias")
    public List<Ausencia> ausencias() {
        return funcionarios.ausencias();
    }

    @PostMapping("/funcionarios/{id}/ausencias")
    @ResponseStatus(HttpStatus.CREATED)
    public Ausencia registrarAusencia(@PathVariable Long id, @Valid @RequestBody DadosAusencia req) {
        return funcionarios.registrarAusencia(id, req);
    }

    @DeleteMapping("/ausencias/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removerAusencia(@PathVariable Long id) {
        funcionarios.removerAusencia(id);
    }
}
