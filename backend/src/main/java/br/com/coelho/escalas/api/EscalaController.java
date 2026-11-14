package br.com.coelho.escalas.api;

import br.com.coelho.escalas.dominio.Escala;
import br.com.coelho.escalas.motor.ResultadoValidacao;
import br.com.coelho.escalas.motor.SugestorSubstituicao;
import br.com.coelho.escalas.servico.EscalaPdfService;
import br.com.coelho.escalas.servico.EscalaService;
import br.com.coelho.escalas.servico.EscalaService.*;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/escalas")
public class EscalaController {

    private final EscalaService servico;
    private final EscalaPdfService pdfs;

    public EscalaController(EscalaService servico, EscalaPdfService pdfs) {
        this.servico = servico;
        this.pdfs = pdfs;
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

    /** Escala em PDF com a logo: {@code imprimir=true} abre no navegador (inline); senão, baixa o arquivo. */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean imprimir) {
        EscalaPdfService.Arquivo arquivo = pdfs.gerar(id);
        ContentDisposition disposicao = (imprimir ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(arquivo.nome()).build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposicao.toString())
                .cacheControl(CacheControl.noStore())
                .body(arquivo.conteudo());
    }

    @PutMapping("/{id}/celula")
    public Matriz celula(@PathVariable Long id, @RequestBody CelulaRequest req) {
        return servico.definirCelula(id, req);
    }

    @GetMapping("/{id}/turnos/{turnoId}/substitutos")
    public SugestorSubstituicao.Sugestao substitutos(@PathVariable Long id, @PathVariable Long turnoId) {
        return servico.substitutos(id, turnoId);
    }

    @PostMapping("/{id}/turnos/{turnoId}/substituir")
    public Matriz substituir(@PathVariable Long id, @PathVariable Long turnoId, @RequestBody SubstituicaoRequest req) {
        return servico.substituir(id, turnoId, req);
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
