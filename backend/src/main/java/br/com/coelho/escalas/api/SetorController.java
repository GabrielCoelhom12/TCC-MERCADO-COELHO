package br.com.coelho.escalas.api;

import br.com.coelho.escalas.dominio.Setor;
import br.com.coelho.escalas.servico.SetorService;
import br.com.coelho.escalas.servico.SetorService.DadosSetor;
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

/** Setores da loja e demanda mínima por perfil de dia. */
@RestController
@RequestMapping("/api/setores")
public class SetorController {

    private final SetorService setores;

    public SetorController(SetorService setores) {
        this.setores = setores;
    }

    @GetMapping
    public List<Setor> listar() {
        return setores.listar();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Setor criar(@Valid @RequestBody DadosSetor req) {
        return setores.criar(req);
    }

    @PutMapping("/{id}")
    public Setor atualizar(@PathVariable Long id, @Valid @RequestBody DadosSetor req) {
        return setores.atualizar(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        setores.excluir(id);
    }
}
