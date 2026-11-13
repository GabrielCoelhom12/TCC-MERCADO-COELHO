package br.com.coelho.escalas.servico;

import br.com.coelho.escalas.dominio.Feriado;
import br.com.coelho.escalas.repositorio.FeriadoRepository;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Feriados que mudam o perfil do dia e o fator de custo (F_d) aplicado pelo motor. */
@Service
public class FeriadoService {

    public record DadosFeriado(@NotNull LocalDate data, @NotBlank String descricao, Feriado.Abrangencia abrangencia,
                               @DecimalMin("1.0") BigDecimal fatorCusto) {
    }

    private final FeriadoRepository feriados;

    public FeriadoService(FeriadoRepository feriados) {
        this.feriados = feriados;
    }

    @Transactional(readOnly = true)
    public List<Feriado> listar() {
        return feriados.findAllByOrderByDataAsc();
    }

    @Transactional
    public Feriado criar(DadosFeriado dados) {
        return feriados.save(aplicar(new Feriado(), dados));
    }

    @Transactional
    public Feriado atualizar(Long id, DadosFeriado dados) {
        return aplicar(feriados.findById(id).orElseThrow(() -> new NaoEncontradoException("Feriado", id)), dados);
    }

    @Transactional
    public void excluir(Long id) {
        feriados.deleteById(id);
    }

    /**
     * Importa os feriados nacionais e municipais (Joinville) do ano, ignorando datas já cadastradas.
     *
     * @return quantidade de feriados incluídos
     */
    @Transactional
    public int importar(int ano) {
        int novos = 0;
        for (Feriado f : CalendarioFeriados.doAno(ano)) {
            if (!feriados.existsByData(f.getData())) {
                feriados.save(f);
                novos++;
            }
        }
        return novos;
    }

    private static Feriado aplicar(Feriado f, DadosFeriado dados) {
        f.setData(dados.data());
        f.setDescricao(dados.descricao().trim());
        f.setAbrangencia(dados.abrangencia() == null ? Feriado.Abrangencia.NACIONAL : dados.abrangencia());
        f.setFatorCusto(dados.fatorCusto());
        return f;
    }
}
