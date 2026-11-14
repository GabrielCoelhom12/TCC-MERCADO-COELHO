package br.com.coelho.escalas.api;

import br.com.coelho.escalas.dominio.Ausencia;
import br.com.coelho.escalas.dominio.DocumentoAusencia;
import br.com.coelho.escalas.dominio.DocumentoResumo;
import br.com.coelho.escalas.servico.AfastamentoService;
import br.com.coelho.escalas.servico.AfastamentoService.DadosAfastamento;
import br.com.coelho.escalas.servico.CoberturaService.Previa;
import br.com.coelho.escalas.servico.CoberturaService.Registro;
import br.com.coelho.escalas.servico.DocumentoAfastamentoService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import java.time.LocalDate;
import java.util.List;

/** Operação de afastamentos (atestados, licenças e outras ausências que não são férias). */
@RestController
@RequestMapping("/api/afastamentos")
public class AfastamentoController {

    private final AfastamentoService afastamentos;
    private final DocumentoAfastamentoService documentos;

    public AfastamentoController(AfastamentoService afastamentos, DocumentoAfastamentoService documentos) {
        this.afastamentos = afastamentos;
        this.documentos = documentos;
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

    // ------------------------------------------------------------------ documentos (atestado, declaração...)

    @PostMapping("/{id}/documentos")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentoResumo anexar(@PathVariable Long id, @RequestParam("arquivo") MultipartFile arquivo, Authentication auth)
            throws IOException {
        return documentos.anexar(id, arquivo.getOriginalFilename(), arquivo.getBytes(), auth.getName());
    }

    @GetMapping("/{id}/documentos")
    public List<DocumentoResumo> documentos(@PathVariable Long id) {
        return documentos.listar(id);
    }

    /** Conteúdo do documento (só a gerência, ver SecurityConfig), aberto no navegador e nunca guardado em cache. */
    @GetMapping("/documentos/{documentoId}")
    public ResponseEntity<byte[]> abrir(@PathVariable Long documentoId) {
        DocumentoAusencia d = documentos.abrir(documentoId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(d.getTipo()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(d.getNomeArquivo()).build().toString())
                .cacheControl(CacheControl.noStore())
                .body(d.getConteudo());
    }

    @DeleteMapping("/documentos/{documentoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removerDocumento(@PathVariable Long documentoId) {
        documentos.remover(documentoId);
    }
}
