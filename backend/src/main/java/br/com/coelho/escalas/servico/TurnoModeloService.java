package br.com.coelho.escalas.servico;

import br.com.coelho.escalas.dominio.TurnoModelo;
import br.com.coelho.escalas.repositorio.TurnoModeloRepository;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;

/** Modelos de turno reutilizados na matriz e pelo gerador automático. */
@Service
public class TurnoModeloService {

    private static final int TAMANHO_SIGLA = 3;

    public record DadosTurnoModelo(@NotBlank String nome, String sigla, @NotNull LocalTime horaInicio,
                                   @NotNull LocalTime horaFim, @Min(0) int intervaloMinutos,
                                   TurnoModelo.Aplicacao aplicacao) {
    }

    private final TurnoModeloRepository modelos;

    public TurnoModeloService(TurnoModeloRepository modelos) {
        this.modelos = modelos;
    }

    @Transactional(readOnly = true)
    public List<TurnoModelo> listar() {
        return modelos.findAllByOrderByHoraInicioAsc();
    }

    @Transactional
    public TurnoModelo criar(DadosTurnoModelo dados) {
        return modelos.save(aplicar(new TurnoModelo(), dados));
    }

    @Transactional
    public TurnoModelo atualizar(Long id, DadosTurnoModelo dados) {
        return aplicar(modelos.findById(id).orElseThrow(() -> new NaoEncontradoException("Modelo de turno", id)), dados);
    }

    @Transactional
    public void excluir(Long id) {
        modelos.deleteById(id);
    }

    private TurnoModelo aplicar(TurnoModelo m, DadosTurnoModelo dados) {
        if (dados.horaInicio().equals(dados.horaFim())) {
            throw new RegraNegocioException("Início e fim do turno não podem ser iguais.");
        }
        String nome = dados.nome().trim();
        m.setNome(nome);
        m.setSigla(dados.sigla() == null || dados.sigla().isBlank() ? siglaDe(nome) : dados.sigla().trim());
        m.setHoraInicio(dados.horaInicio());
        m.setHoraFim(dados.horaFim());
        m.setIntervaloMinutos(dados.intervaloMinutos());
        m.setAplicacao(dados.aplicacao() == null ? TurnoModelo.Aplicacao.TODOS : dados.aplicacao());
        if (m.getHoras() <= 0) {
            throw new RegraNegocioException("O intervalo não pode ser maior ou igual à duração do turno.");
        }
        return m;
    }

    /** Sem sigla informada, usa as primeiras letras do nome (ex.: "Abertura" → "ABE"). */
    private static String siglaDe(String nome) {
        return nome.substring(0, Math.min(TAMANHO_SIGLA, nome.length())).toUpperCase();
    }
}
