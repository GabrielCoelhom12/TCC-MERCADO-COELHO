package br.com.coelho.escalas.servico;

import br.com.coelho.escalas.dominio.ProjecaoReceita;
import br.com.coelho.escalas.dominio.ProjecaoReceitaData;
import br.com.coelho.escalas.dominio.TipoDia;
import br.com.coelho.escalas.repositorio.ProjecaoReceitaDataRepository;
import br.com.coelho.escalas.repositorio.ProjecaoReceitaRepository;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Projeções de faturamento (R_proj) informadas pela administração: a padrão de cada perfil de dia e as de datas específicas. */
@Service
public class ProjecaoService {

    public record DadosProjecaoData(@NotNull LocalDate data, @NotNull @DecimalMin("0") BigDecimal valor, String observacao) {
    }

    public record Projecoes(Map<TipoDia, BigDecimal> padrao, List<ProjecaoReceitaData> especificas) {
    }

    private final ProjecaoReceitaRepository padrao;
    private final ProjecaoReceitaDataRepository especificas;

    public ProjecaoService(ProjecaoReceitaRepository padrao, ProjecaoReceitaDataRepository especificas) {
        this.padrao = padrao;
        this.especificas = especificas;
    }

    /** Todo perfil de dia aparece na resposta; os que ainda não têm projeção valem zero. */
    @Transactional(readOnly = true)
    public Projecoes listar() {
        Map<TipoDia, BigDecimal> porTipo = new EnumMap<>(TipoDia.class);
        for (TipoDia tipo : TipoDia.values()) {
            porTipo.put(tipo, BigDecimal.ZERO);
        }
        padrao.findAll().forEach(p -> porTipo.put(p.getTipoDia(), p.getValor()));
        return new Projecoes(porTipo, especificas.findAllByOrderByDataAsc());
    }

    @Transactional
    public Projecoes salvarPadrao(Map<TipoDia, BigDecimal> valores) {
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

    @Transactional
    public ProjecaoReceitaData criarData(DadosProjecaoData dados) {
        return especificas.save(new ProjecaoReceitaData(dados.data(), dados.valor(), dados.observacao()));
    }

    @Transactional
    public ProjecaoReceitaData atualizarData(Long id, DadosProjecaoData dados) {
        ProjecaoReceitaData p = especificas.findById(id).orElseThrow(() -> new NaoEncontradoException("Projeção", id));
        p.setData(dados.data());
        p.setValor(dados.valor());
        p.setObservacao(dados.observacao());
        return p;
    }

    @Transactional
    public void excluirData(Long id) {
        especificas.deleteById(id);
    }
}
