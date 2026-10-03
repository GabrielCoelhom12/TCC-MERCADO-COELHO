package br.com.coelho.escalas.api;

import br.com.coelho.escalas.dominio.ProjecaoReceita;
import br.com.coelho.escalas.dominio.ProjecaoReceitaData;
import br.com.coelho.escalas.dominio.TipoDia;
import br.com.coelho.escalas.repositorio.ProjecaoReceitaDataRepository;
import br.com.coelho.escalas.repositorio.ProjecaoReceitaRepository;
import br.com.coelho.escalas.servico.NaoEncontradoException;
import br.com.coelho.escalas.servico.RegraNegocioException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Projeções de faturamento (R_proj) informadas pela administração. */
@RestController
@RequestMapping("/api/projecoes")
public class ProjecaoController {

    public record ProjecaoDataRequest(@NotNull LocalDate data, @NotNull @DecimalMin("0") BigDecimal valor, String observacao) {
    }

    public record Projecoes(Map<TipoDia, BigDecimal> padrao, List<ProjecaoReceitaData> especificas) {
    }

    private final ProjecaoReceitaRepository padrao;
    private final ProjecaoReceitaDataRepository especificas;

    public ProjecaoController(ProjecaoReceitaRepository padrao, ProjecaoReceitaDataRepository especificas) {
        this.padrao = padrao;
        this.especificas = especificas;
    }

    @GetMapping
    public Projecoes listar() {
        Map<TipoDia, BigDecimal> mapa = new EnumMap<>(TipoDia.class);
        for (TipoDia t : TipoDia.values()) {
            mapa.put(t, BigDecimal.ZERO);
        }
        padrao.findAll().forEach(p -> mapa.put(p.getTipoDia(), p.getValor()));
        return new Projecoes(mapa, especificas.findAllByOrderByDataAsc());
    }

    @PutMapping("/padrao")
    @Transactional
    public Projecoes salvarPadrao(@RequestBody Map<TipoDia, BigDecimal> valores) {
        valores.forEach((tipo, valor) -> {
            if (valor == null || valor.signum() < 0) {
                throw new RegraNegocioException("Projeção inválida para " + tipo.getRotulo() + ".");
            }
            ProjecaoReceita p = padrao.findByTipoDia(tipo).orElseGet(() -> new ProjecaoReceita(tipo, valor));
            p.setValor(valor);
            padrao.save(p);
        });
        return listar();
    }

    @PostMapping("/datas")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ProjecaoReceitaData criarData(@Valid @RequestBody ProjecaoDataRequest req) {
        return especificas.save(new ProjecaoReceitaData(req.data(), req.valor(), req.observacao()));
    }

    @PutMapping("/datas/{id}")
    @Transactional
    public ProjecaoReceitaData atualizarData(@PathVariable Long id, @Valid @RequestBody ProjecaoDataRequest req) {
        ProjecaoReceitaData p = especificas.findById(id).orElseThrow(() -> new NaoEncontradoException("Projeção", id));
        p.setData(req.data());
        p.setValor(req.valor());
        p.setObservacao(req.observacao());
        return p;
    }

    @DeleteMapping("/datas/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluirData(@PathVariable Long id) {
        especificas.deleteById(id);
    }
}
