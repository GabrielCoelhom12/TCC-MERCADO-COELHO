package br.com.coelho.escalas.api;

import br.com.coelho.escalas.dominio.ParametrosOperacionais;
import br.com.coelho.escalas.servico.ParametrosService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Parâmetros operacionais do motor (financeiros e trabalhistas). */
@RestController
@RequestMapping("/api/parametros")
public class ParametrosController {

    private final ParametrosService parametros;

    public ParametrosController(ParametrosService parametros) {
        this.parametros = parametros;
    }

    @GetMapping
    public ParametrosOperacionais obter() {
        return parametros.obter();
    }

    @PutMapping
    public ParametrosOperacionais salvar(@RequestBody ParametrosOperacionais req) {
        return parametros.salvar(req);
    }
}
