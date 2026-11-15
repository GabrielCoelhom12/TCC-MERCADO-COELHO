package br.com.coelho.escalas.api;

import br.com.coelho.escalas.motor.CapacidadeDia;
import br.com.coelho.escalas.motor.ResultadoValidacao;
import br.com.coelho.escalas.motor.SimuladorCusto;
import br.com.coelho.escalas.servico.EscalaService;
import br.com.coelho.escalas.servico.MotorService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Exposição direta do Motor de Cálculo e Otimização Financeira. */
@RestController
@RequestMapping("/api/motor")
public class MotorController {

    public record SimulacaoRequest(LocalDate data, Map<Long, Integer> proposta) {
    }

    private final MotorService motor;
    private final EscalaService escalas;

    public MotorController(MotorService motor, EscalaService escalas) {
        this.motor = motor;
        this.escalas = escalas;
    }

    /** L_min, L_max, teto e F_d de cada dia do período. */
    @GetMapping("/capacidade")
    public List<CapacidadeDia> capacidade(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return motor.capacidade(inicio, fim);
    }

    @PostMapping("/simular")
    public SimuladorCusto.Simulacao simular(@RequestBody SimulacaoRequest req) {
        return motor.simular(req.data(), req.proposta());
    }

    /** Custo escalado × teto e situação de cada dia, considerando todas as escalas do período. */
    @GetMapping("/painel")
    public ResultadoValidacao painel(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return escalas.painel(inicio, fim);
    }
}
