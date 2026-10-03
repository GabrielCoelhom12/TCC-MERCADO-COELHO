package br.com.coelho.escalas.api;

import br.com.coelho.escalas.dominio.Escala;
import br.com.coelho.escalas.motor.ResultadoValidacao;
import br.com.coelho.escalas.servico.EscalaService;
import br.com.coelho.escalas.servico.EscalaService.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/escalas")
public class EscalaController {

    private final EscalaService servico;

    public EscalaController(EscalaService servico) {
        this.servico = servico;
    }

    @GetMapping
    public List<Escala> listar() {
        return servico.listar();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Escala criar(@RequestBody EscalaRequest req) {
        return servico.criar(req);
    }

    @PutMapping("/{id}")
    public Escala atualizar(@PathVariable Long id, @RequestBody EscalaRequest req) {
        return servico.atualizar(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        servico.excluir(id);
    }

    @GetMapping("/{id}/matriz")
    public Matriz matriz(@PathVariable Long id) {
        return servico.matriz(id);
    }

    @PutMapping("/{id}/celula")
    public Matriz celula(@PathVariable Long id, @RequestBody CelulaRequest req) {
        return servico.definirCelula(id, req);
    }

    @PostMapping("/{id}/gerar")
    public Geracao gerar(@PathVariable Long id) {
        return servico.gerar(id);
    }

    @PostMapping("/{id}/limpar")
    public Matriz limpar(@PathVariable Long id) {
        return servico.limpar(id);
    }

    @GetMapping("/{id}/validacao")
    public ResultadoValidacao validar(@PathVariable Long id) {
        return servico.validar(id);
    }

    @PostMapping("/{id}/aprovar")
    public Escala aprovar(@PathVariable Long id, Authentication auth) {
        return servico.aprovar(id, auth.getName());
    }

    @PostMapping("/{id}/reabrir")
    public Escala reabrir(@PathVariable Long id) {
        return servico.reabrir(id);
    }
}
