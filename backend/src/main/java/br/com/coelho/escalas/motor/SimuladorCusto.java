package br.com.coelho.escalas.motor;

import br.com.coelho.escalas.dominio.Funcionario;
import br.com.coelho.escalas.dominio.Setor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * Compara uma proposta "empírica" de quadro (quantos operadores por setor o gestor colocaria)
 * com o quadro recomendado pelo motor, evidenciando a economia gerada.
 */
@Component
public class SimuladorCusto {

    public enum Veredito { APROVADA, SUPERDIMENSIONADA, SUBDIMENSIONADA }

    public record LinhaSetor(Long setorId, String setor, int minimo, int proposto, int recomendado,
                             BigDecimal custoOperador, BigDecimal custoProposto, BigDecimal custoRecomendado) {
    }

    public record Simulacao(CapacidadeDia capacidade, List<LinhaSetor> setores,
                            int operadoresPropostos, BigDecimal custoProposto,
                            int operadoresRecomendados, BigDecimal custoRecomendado,
                            BigDecimal economia, Veredito veredito) {
    }

    private final MotorFinanceiro motor;

    public SimuladorCusto(MotorFinanceiro motor) {
        this.motor = motor;
    }

    public Simulacao simular(LocalDate data, Map<Long, Integer> propostaPorSetor, ContextoCalculo ctx) {
        CapacidadeDia cap = motor.capacidade(data, ctx);

        List<Setor> setores = ctx.setoresAtivos();
        Map<Long, BigDecimal> custoOperador = new HashMap<>();
        Map<Long, Integer> recomendado = new LinkedHashMap<>();
        for (Setor s : setores) {
            List<Funcionario> doSetor = ctx.funcionariosAtivos().stream()
                    .filter(f -> f.getSetor().getId().equals(s.getId())).toList();
            BigDecimal custo = doSetor.isEmpty() ? cap.custoMedioOperador()
                    : motor.custoMedioOperador(doSetor, cap.fator(), ctx.parametros());
            custoOperador.put(s.getId(), custo);
            int minimo = cap.minimoPorSetor().getOrDefault(s.getId(), 0);
            recomendado.put(s.getId(), Math.max(minimo, propostaPorSetor.getOrDefault(s.getId(), 0)));
        }

        // Corta o excedente começando pelo operador mais caro, até caber em max(L_min, L_max) e no teto.
        int limite = Math.max(cap.lMin(), cap.lMax());
        while (true) {
            int total = recomendado.values().stream().mapToInt(Integer::intValue).sum();
            BigDecimal custo = custoTotal(recomendado, custoOperador);
            boolean excede = total > limite || (custo.compareTo(cap.tetoFolha()) > 0 && total > cap.lMin());
            if (!excede) {
                break;
            }
            Optional<Setor> corte = setores.stream()
                    .filter(s -> recomendado.get(s.getId()) > cap.minimoPorSetor().getOrDefault(s.getId(), 0))
                    .max(Comparator.comparing(s -> custoOperador.get(s.getId())));
            if (corte.isEmpty()) {
                break;
            }
            recomendado.merge(corte.get().getId(), -1, Integer::sum);
        }

        List<LinhaSetor> linhas = new ArrayList<>();
        int totalProposto = 0;
        int totalRecomendado = 0;
        boolean abaixoMinimo = false;
        for (Setor s : setores) {
            int minimo = cap.minimoPorSetor().getOrDefault(s.getId(), 0);
            int proposto = propostaPorSetor.getOrDefault(s.getId(), 0);
            int rec = recomendado.get(s.getId());
            BigDecimal c = custoOperador.get(s.getId());
            linhas.add(new LinhaSetor(s.getId(), s.getNome(), minimo, proposto, rec, c,
                    c.multiply(BigDecimal.valueOf(proposto)), c.multiply(BigDecimal.valueOf(rec))));
            totalProposto += proposto;
            totalRecomendado += rec;
            abaixoMinimo |= proposto < minimo;
        }
        BigDecimal custoProposto = linhas.stream().map(LinhaSetor::custoProposto).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal custoRecomendado = linhas.stream().map(LinhaSetor::custoRecomendado).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        Veredito veredito;
        if (totalProposto > limite || (custoProposto.compareTo(cap.tetoFolha()) > 0 && totalProposto > cap.lMin())) {
            veredito = Veredito.SUPERDIMENSIONADA;
        } else if (abaixoMinimo) {
            veredito = Veredito.SUBDIMENSIONADA;
        } else {
            veredito = Veredito.APROVADA;
        }

        return new Simulacao(cap, linhas, totalProposto, custoProposto, totalRecomendado, custoRecomendado,
                custoProposto.subtract(custoRecomendado).max(BigDecimal.ZERO), veredito);
    }

    private static BigDecimal custoTotal(Map<Long, Integer> quantidades, Map<Long, BigDecimal> custoOperador) {
        return quantidades.entrySet().stream()
                .map(e -> custoOperador.get(e.getKey()).multiply(BigDecimal.valueOf(e.getValue())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
