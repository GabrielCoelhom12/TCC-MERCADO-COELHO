package br.com.coelho.escalas.api;

import br.com.coelho.escalas.dominio.TurnoModelo;
import br.com.coelho.escalas.servico.TurnoModeloService;
import br.com.coelho.escalas.servico.TurnoModeloService.DadosTurnoModelo;
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

/** Modelos de turno (ex.: Abertura 07:00–15:20). */
@RestController
@RequestMapping("/api/turnos-modelo")
public class TurnoModeloController {

    private final TurnoModeloService modelos;

    public TurnoModeloController(TurnoModeloService modelos) {
        this.modelos = modelos;
    }

    @GetMapping
    public List<TurnoModelo> listar() {
        return modelos.listar();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TurnoModelo criar(@Valid @RequestBody DadosTurnoModelo req) {
        return modelos.criar(req);
    }

    @PutMapping("/{id}")
    public TurnoModelo atualizar(@PathVariable Long id, @Valid @RequestBody DadosTurnoModelo req) {
        return modelos.atualizar(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        modelos.excluir(id);
    }
}
