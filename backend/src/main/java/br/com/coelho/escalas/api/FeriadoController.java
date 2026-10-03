package br.com.coelho.escalas.api;

import br.com.coelho.escalas.dominio.Feriado;
import br.com.coelho.escalas.repositorio.FeriadoRepository;
import br.com.coelho.escalas.servico.CalendarioFeriados;
import br.com.coelho.escalas.servico.NaoEncontradoException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/feriados")
public class FeriadoController {

    public record FeriadoRequest(@NotNull LocalDate data, @NotBlank String descricao, Feriado.Abrangencia abrangencia,
                                 @DecimalMin("1.0") BigDecimal fatorCusto) {
    }

    private final FeriadoRepository feriados;

    public FeriadoController(FeriadoRepository feriados) {
        this.feriados = feriados;
    }

    @GetMapping
    public List<Feriado> listar() {
        return feriados.findAllByOrderByDataAsc();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Feriado criar(@Valid @RequestBody FeriadoRequest req) {
        return feriados.save(aplicar(new Feriado(), req));
    }

    @PutMapping("/{id}")
    @Transactional
    public Feriado atualizar(@PathVariable Long id, @Valid @RequestBody FeriadoRequest req) {
        return aplicar(feriados.findById(id).orElseThrow(() -> new NaoEncontradoException("Feriado", id)), req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        feriados.deleteById(id);
    }

    /** Importa os feriados nacionais e municipais (Joinville) do ano, ignorando datas já cadastradas. */
    @PostMapping("/importar/{ano}")
    @Transactional
    public Map<String, Integer> importar(@PathVariable int ano) {
        int novos = 0;
        for (Feriado f : CalendarioFeriados.doAno(ano)) {
            if (!feriados.existsByData(f.getData())) {
                feriados.save(f);
                novos++;
            }
        }
        return Map.of("importados", novos);
    }

    private Feriado aplicar(Feriado f, FeriadoRequest req) {
        f.setData(req.data());
        f.setDescricao(req.descricao().trim());
        f.setAbrangencia(req.abrangencia() == null ? Feriado.Abrangencia.NACIONAL : req.abrangencia());
        f.setFatorCusto(req.fatorCusto());
        return f;
    }
}
