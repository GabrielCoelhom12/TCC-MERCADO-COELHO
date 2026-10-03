package br.com.coelho.escalas.api;

import br.com.coelho.escalas.dominio.FaixaHoraria;
import br.com.coelho.escalas.servico.MovimentoService;
import br.com.coelho.escalas.servico.MovimentoService.Fechamento;
import br.com.coelho.escalas.servico.MovimentoService.PrevisaoDiaria;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Movimento da loja: faixas de horário, fechamento do dia e previsão. */
@RestController
@RequestMapping("/api/movimento")
public class MovimentoController {

    public record FaixaRequest(@NotNull LocalTime inicio, @NotNull LocalTime fim) {
    }

    public record FaixaLancadaRequest(@NotNull LocalTime inicio, @NotNull LocalTime fim, @Min(0) int clientes,
                                      @NotNull @DecimalMin("0") BigDecimal vendas) {
    }

    public record FechamentoRequest(@NotNull List<@Valid FaixaLancadaRequest> faixas, @Size(max = 500) String observacao) {
    }

    private final MovimentoService movimento;

    public MovimentoController(MovimentoService movimento) {
        this.movimento = movimento;
    }

    @GetMapping("/faixas")
    public List<FaixaHoraria> faixas() {
        return movimento.faixas();
    }

    @PutMapping("/faixas")
    public List<FaixaHoraria> substituirFaixas(@Valid @RequestBody List<@Valid FaixaRequest> faixas) {
        return movimento.substituirFaixas(faixas.stream().map(f -> new MovimentoService.Faixa(f.inicio(), f.fim())).toList());
    }

    @GetMapping("/fechamentos")
    public List<Fechamento> fechamentos(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return movimento.fechamentos(inicio, fim);
    }

    @GetMapping("/fechamentos/{data}")
    public Fechamento fechamento(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return movimento.fechamento(data);
    }

    @PutMapping("/fechamentos/{data}")
    public Fechamento registrar(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
                                @Valid @RequestBody FechamentoRequest req, Authentication auth) {
        return movimento.registrar(data, req.faixas().stream()
                .map(f -> new MovimentoService.FaixaLancada(f.inicio(), f.fim(), f.clientes(), f.vendas())).toList(),
                req.observacao(), auth.getName());
    }

    @DeleteMapping("/fechamentos/{data}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remover(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        movimento.remover(data);
    }

    @GetMapping("/previsao")
    public List<PrevisaoDiaria> previsao(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
                                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return movimento.previsao(inicio, fim);
    }
}
