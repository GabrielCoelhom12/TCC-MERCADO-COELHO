package br.com.coelho.escalas.api;

import br.com.coelho.escalas.motor.*;
import br.com.coelho.escalas.servico.ContextoService;
import br.com.coelho.escalas.servico.EscalaService;
import br.com.coelho.escalas.servico.RegraNegocioException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Exposição direta do Motor de Cálculo e Otimização Financeira. */
@RestController
@RequestMapping("/api/motor")
public class MotorController {

    public record SimulacaoRequest(LocalDate data, Map<Long, Integer> proposta) {
    }

    private final ContextoService contextos;
    private final MotorFinanceiro motor;
    private final SimuladorCusto simulador;
    private final EscalaService escalas;

    public MotorController(ContextoService contextos, MotorFinanceiro motor, SimuladorCusto simulador, EscalaService escalas) {
        this.contextos = contextos;
        this.motor = motor;
        this.simulador = simulador;
        this.escalas = escalas;
    }

    /** L_min, L_max, teto e F_d de cada dia do período. */
    @GetMapping("/capacidade")
    public List<CapacidadeDia> capacidade(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        if (fim.isBefore(inicio) || ChronoUnit.DAYS.between(inicio, fim) > 92) {
            throw new RegraNegocioException("Período inválido (máximo de 93 dias).");
        }
        ContextoCalculo ctx = contextos.carregar(inicio, fim);
        List<CapacidadeDia> dias = new ArrayList<>();
        for (LocalDate d = inicio; !d.isAfter(fim); d = d.plusDays(1)) {
            dias.add(motor.capacidade(d, ctx));
        }
        return dias;
    }

    @PostMapping("/simular")
    public SimuladorCusto.Simulacao simular(@RequestBody SimulacaoRequest req) {
        if (req.data() == null) {
            throw new RegraNegocioException("Informe a data da simulação.");
        }
        ContextoCalculo ctx = contextos.carregar(req.data(), req.data());
        return simulador.simular(req.data(), req.proposta() == null ? Map.of() : req.proposta(), ctx);
    }

    /** Custo escalado × teto e situação de cada dia, considerando todas as escalas do período. */
    @GetMapping("/painel")
    public ResultadoValidacao painel(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return escalas.painel(inicio, fim);
    }
}
