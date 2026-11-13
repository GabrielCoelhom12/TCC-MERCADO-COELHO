package br.com.coelho.escalas.api;

import br.com.coelho.escalas.dominio.ProjecaoReceitaData;
import br.com.coelho.escalas.dominio.TipoDia;
import br.com.coelho.escalas.servico.ProjecaoService;
import br.com.coelho.escalas.servico.ProjecaoService.DadosProjecaoData;
import br.com.coelho.escalas.servico.ProjecaoService.Projecoes;
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

import java.math.BigDecimal;
import java.util.Map;

/** Projeções de faturamento (R_proj) informadas pela administração. */
@RestController
@RequestMapping("/api/projecoes")
public class ProjecaoController {

    private final ProjecaoService projecoes;

    public ProjecaoController(ProjecaoService projecoes) {
        this.projecoes = projecoes;
    }

    @GetMapping
    public Projecoes listar() {
        return projecoes.listar();
    }

    @PutMapping("/padrao")
    public Projecoes salvarPadrao(@RequestBody Map<TipoDia, BigDecimal> valores) {
        return projecoes.salvarPadrao(valores);
    }

    @PostMapping("/datas")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjecaoReceitaData criarData(@Valid @RequestBody DadosProjecaoData req) {
        return projecoes.criarData(req);
    }

    @PutMapping("/datas/{id}")
    public ProjecaoReceitaData atualizarData(@PathVariable Long id, @Valid @RequestBody DadosProjecaoData req) {
        return projecoes.atualizarData(id, req);
    }

    @DeleteMapping("/datas/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluirData(@PathVariable Long id) {
        projecoes.excluirData(id);
    }
}
