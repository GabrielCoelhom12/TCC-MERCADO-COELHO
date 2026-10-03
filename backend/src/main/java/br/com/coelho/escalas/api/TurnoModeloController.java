package br.com.coelho.escalas.api;

import br.com.coelho.escalas.dominio.TurnoModelo;
import br.com.coelho.escalas.repositorio.TurnoModeloRepository;
import br.com.coelho.escalas.servico.NaoEncontradoException;
import br.com.coelho.escalas.servico.RegraNegocioException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/turnos-modelo")
public class TurnoModeloController {

    public record TurnoModeloRequest(@NotBlank String nome, String sigla, @NotNull LocalTime horaInicio,
                                     @NotNull LocalTime horaFim, @Min(0) int intervaloMinutos,
                                     TurnoModelo.Aplicacao aplicacao) {
    }

    private final TurnoModeloRepository modelos;

    public TurnoModeloController(TurnoModeloRepository modelos) {
        this.modelos = modelos;
    }

    @GetMapping
    public List<TurnoModelo> listar() {
        return modelos.findAllByOrderByHoraInicioAsc();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public TurnoModelo criar(@Valid @RequestBody TurnoModeloRequest req) {
        return modelos.save(aplicar(new TurnoModelo(), req));
    }

    @PutMapping("/{id}")
    @Transactional
    public TurnoModelo atualizar(@PathVariable Long id, @Valid @RequestBody TurnoModeloRequest req) {
        return aplicar(modelos.findById(id).orElseThrow(() -> new NaoEncontradoException("Modelo de turno", id)), req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        modelos.deleteById(id);
    }

    private TurnoModelo aplicar(TurnoModelo m, TurnoModeloRequest req) {
        if (req.horaInicio().equals(req.horaFim())) {
            throw new RegraNegocioException("Início e fim do turno não podem ser iguais.");
        }
        m.setNome(req.nome().trim());
        m.setSigla(req.sigla() == null || req.sigla().isBlank() ? req.nome().substring(0, Math.min(3, req.nome().length())).toUpperCase() : req.sigla().trim());
        m.setHoraInicio(req.horaInicio());
        m.setHoraFim(req.horaFim());
        m.setIntervaloMinutos(req.intervaloMinutos());
        m.setAplicacao(req.aplicacao() == null ? TurnoModelo.Aplicacao.TODOS : req.aplicacao());
        if (m.getHoras() <= 0) {
            throw new RegraNegocioException("O intervalo não pode ser maior ou igual à duração do turno.");
        }
        return m;
    }
}
