package br.com.coelho.escalas.servico;

import br.com.coelho.escalas.motor.CapacidadeDia;
import br.com.coelho.escalas.motor.ContextoCalculo;
import br.com.coelho.escalas.motor.MotorFinanceiro;
import br.com.coelho.escalas.motor.SimuladorCusto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Consultas diretas ao Motor de Cálculo e Otimização Financeira, com os dados atuais do banco. */
@Service
public class MotorService {

    private static final int MAX_DIAS_CAPACIDADE = 93;
    /** Teto por setor na simulação: acima disso não é um quadro real e o corte, pessoa a pessoa, ficaria caro. */
    static final int MAX_PESSOAS_POR_SETOR = 500;

    private final ContextoService contextos;
    private final MotorFinanceiro motor;
    private final SimuladorCusto simulador;

    public MotorService(ContextoService contextos, MotorFinanceiro motor, SimuladorCusto simulador) {
        this.contextos = contextos;
        this.motor = motor;
        this.simulador = simulador;
    }

    /** L_min, L_max, teto e F_d de cada dia do período. */
    @Transactional(readOnly = true)
    public List<CapacidadeDia> capacidade(LocalDate inicio, LocalDate fim) {
        if (fim.isBefore(inicio) || ChronoUnit.DAYS.between(inicio, fim) >= MAX_DIAS_CAPACIDADE) {
            throw new RegraNegocioException("Período inválido (máximo de " + MAX_DIAS_CAPACIDADE + " dias).");
        }
        ContextoCalculo ctx = contextos.carregar(inicio, fim);
        List<CapacidadeDia> dias = new ArrayList<>();
        for (LocalDate d = inicio; !d.isAfter(fim); d = d.plusDays(1)) {
            dias.add(motor.capacidade(d, ctx));
        }
        return dias;
    }

    /**
     * Compara um quadro proposto para o dia com o recomendado pelo motor.
     *
     * @param proposta operadores por setor (id do setor → quantidade); nula equivale a nenhum operador
     */
    @Transactional(readOnly = true)
    public SimuladorCusto.Simulacao simular(LocalDate data, Map<Long, Integer> proposta) {
        if (data == null) {
            throw new RegraNegocioException("Informe a data da simulação.");
        }
        if (proposta != null && proposta.values().stream().anyMatch(n -> n == null || n < 0 || n > MAX_PESSOAS_POR_SETOR)) {
            throw new RegraNegocioException("A quantidade de pessoas por setor deve ficar entre 0 e " + MAX_PESSOAS_POR_SETOR + ".");
        }
        ContextoCalculo ctx = contextos.carregar(data, data);
        return simulador.simular(data, proposta == null ? Map.of() : proposta, ctx);
    }
}
