package br.com.coelho.escalas.api;

import br.com.coelho.escalas.dominio.Ausencia;
import br.com.coelho.escalas.servico.FeriasService;
import br.com.coelho.escalas.servico.FeriasService.DadosFerias;
import br.com.coelho.escalas.servico.CoberturaService.Previa;
import br.com.coelho.escalas.servico.CoberturaService.Registro;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** Operação de férias (ausências do tipo FERIAS), com as regras da CLT. */
@RestController
@RequestMapping("/api/ferias")
public class FeriasController {

    private final FeriasService ferias;

    public FeriasController(FeriasService ferias) {
        this.ferias = ferias;
    }

    @GetMapping
    public List<Ausencia> listar() {
        return ferias.listar();
    }

    /** Prévia do período: dias, retorno, erros que impedem o registro, alertas e quem cobre os turnos já escalados. */
    @GetMapping("/analise")
    public Previa analisar(@RequestParam Long funcionarioId,
                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
                           @RequestParam(required = false) Long ignorar) {
        return ferias.analisar(funcionarioId, inicio, fim, ignorar);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Registro registrar(@Valid @RequestBody DadosFerias dados) {
        return ferias.registrar(dados);
    }

    @PutMapping("/{id}")
    public Registro atualizar(@PathVariable Long id, @Valid @RequestBody DadosFerias dados) {
        return ferias.atualizar(id, dados);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remover(@PathVariable Long id) {
        ferias.remover(id);
    }
}
