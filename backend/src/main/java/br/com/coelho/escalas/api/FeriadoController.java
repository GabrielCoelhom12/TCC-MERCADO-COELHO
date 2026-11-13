package br.com.coelho.escalas.api;

import br.com.coelho.escalas.dominio.Feriado;
import br.com.coelho.escalas.servico.FeriadoService;
import br.com.coelho.escalas.servico.FeriadoService.DadosFeriado;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Calendário de feriados. */
@RestController
@RequestMapping("/api/feriados")
public class FeriadoController {

    private final FeriadoService feriados;

    public FeriadoController(FeriadoService feriados) {
        this.feriados = feriados;
    }

    @GetMapping
    public List<Feriado> listar() {
        return feriados.listar();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Feriado criar(@Valid @RequestBody DadosFeriado req) {
        return feriados.criar(req);
    }

    @PutMapping("/{id}")
    public Feriado atualizar(@PathVariable Long id, @Valid @RequestBody DadosFeriado req) {
        return feriados.atualizar(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        feriados.excluir(id);
    }

    /** Importa os feriados nacionais e municipais (Joinville) do ano, ignorando datas já cadastradas. */
    @PostMapping("/importar/{ano}")
    public Map<String, Integer> importar(@PathVariable int ano) {
        return Map.of("importados", feriados.importar(ano));
    }
}
