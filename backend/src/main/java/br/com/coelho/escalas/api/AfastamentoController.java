package br.com.coelho.escalas.api;

import br.com.coelho.escalas.dominio.Ausencia;
import br.com.coelho.escalas.servico.AfastamentoService;
import br.com.coelho.escalas.servico.AfastamentoService.DadosAfastamento;
import br.com.coelho.escalas.servico.CoberturaService.Previa;
import br.com.coelho.escalas.servico.CoberturaService.Registro;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** Operação de afastamentos (atestados, licenças e outras ausências que não são férias). */
@RestController
@RequestMapping("/api/afastamentos")
public class AfastamentoController {

    private final AfastamentoService afastamentos;

    public AfastamentoController(AfastamentoService afastamentos) {
        this.afastamentos = afastamentos;
    }

    @GetMapping
    public List<Ausencia> listar() {
        return afastamentos.listar();
    }

    /** Prévia do período: dias, retorno, erros, alertas e quem cobre os turnos já escalados. */
    @GetMapping("/analise")
    public Previa analisar(@RequestParam Long funcionarioId, @RequestParam Ausencia.Motivo motivo,
                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
                           @RequestParam(required = false) Long ignorar) {
        return afastamentos.analisar(funcionarioId, motivo, inicio, fim, ignorar);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Registro registrar(@Valid @RequestBody DadosAfastamento dados) {
        return afastamentos.registrar(dados);
    }

    @PutMapping("/{id}")
    public Registro atualizar(@PathVariable Long id, @Valid @RequestBody DadosAfastamento dados) {
        return afastamentos.atualizar(id, dados);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remover(@PathVariable Long id) {
        afastamentos.remover(id);
    }
}
