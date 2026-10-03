package br.com.coelho.escalas.servico;

import br.com.coelho.escalas.dominio.*;
import br.com.coelho.escalas.motor.ContextoCalculo;
import br.com.coelho.escalas.repositorio.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/** Monta o {@link ContextoCalculo} que alimenta o motor a partir do banco. */
@Service
public class ContextoService {

    /** Histórico de movimento considerado pela previsão (20 semanas antes do período). */
    static final int DIAS_DE_HISTORICO = 140;

    private final ParametrosRepository parametros;
    private final SetorRepository setores;
    private final FuncionarioRepository funcionarios;
    private final FeriadoRepository feriados;
    private final ProjecaoReceitaRepository projecoes;
    private final ProjecaoReceitaDataRepository projecoesData;
    private final AusenciaRepository ausencias;
    private final MovimentoDiarioRepository movimentos;
    private final TurnoModeloRepository modelos;

    public ContextoService(ParametrosRepository parametros, SetorRepository setores, FuncionarioRepository funcionarios,
                           FeriadoRepository feriados, ProjecaoReceitaRepository projecoes,
                           ProjecaoReceitaDataRepository projecoesData, AusenciaRepository ausencias,
                           MovimentoDiarioRepository movimentos, TurnoModeloRepository modelos) {
        this.parametros = parametros;
        this.setores = setores;
        this.funcionarios = funcionarios;
        this.feriados = feriados;
        this.projecoes = projecoes;
        this.projecoesData = projecoesData;
        this.ausencias = ausencias;
        this.movimentos = movimentos;
        this.modelos = modelos;
    }

    @Transactional(readOnly = true)
    public ContextoCalculo carregar(LocalDate inicio, LocalDate fim) {
        Map<LocalDate, Feriado> mapaFeriados = new HashMap<>();
        feriados.findByDataBetween(inicio, fim).forEach(f -> mapaFeriados.put(f.getData(), f));

        Map<TipoDia, BigDecimal> porTipo = new EnumMap<>(TipoDia.class);
        projecoes.findAll().forEach(p -> porTipo.put(p.getTipoDia(), p.getValor()));

        Map<LocalDate, BigDecimal> porData = new HashMap<>();
        projecoesData.findByDataBetween(inicio, fim).forEach(p -> porData.put(p.getData(), p.getValor()));

        return new ContextoCalculo(
                parametros.atuais(),
                setores.findAll(),
                funcionarios.findAll(),
                mapaFeriados,
                porTipo,
                porData,
                ausencias.findByFimGreaterThanEqualAndInicioLessThanEqual(inicio, fim),
                movimentos.findByDataBetweenOrderByDataDesc(inicio.minusDays(DIAS_DE_HISTORICO), fim),
                modelos.findAllByOrderByHoraInicioAsc());
    }
}
