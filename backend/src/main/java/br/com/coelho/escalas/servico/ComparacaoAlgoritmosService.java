package br.com.coelho.escalas.servico;

import br.com.coelho.escalas.dominio.Escala;
import br.com.coelho.escalas.dominio.Funcionario;
import br.com.coelho.escalas.dominio.Turno;
import br.com.coelho.escalas.dominio.TurnoModelo;
import br.com.coelho.escalas.motor.ContextoCalculo;
import br.com.coelho.escalas.motor.GeradorEscala;
import br.com.coelho.escalas.motor.MotorFinanceiro;
import br.com.coelho.escalas.motor.ResultadoValidacao;
import br.com.coelho.escalas.motor.ValidadorEscala;
import br.com.coelho.escalas.motor.otimizacao.OtimizadorEscala;
import br.com.coelho.escalas.repositorio.EscalaRepository;
import br.com.coelho.escalas.repositorio.TurnoModeloRepository;
import br.com.coelho.escalas.repositorio.TurnoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Compara, para o período de uma escala, o gerador guloso e o otimizador (Timefold) gerando do zero com os mesmos
 * dados: tempo, vagas descobertas, custo, erros e alertas do validador e o equilíbrio de horas entre as pessoas.
 * Nada é gravado: a escala continua como está.
 */
@Service
public class ComparacaoAlgoritmosService {

    /** Resultado de um algoritmo; desvio-padrão das horas no período entre os colaboradores ativos (menor = mais justo). */
    public record Metricas(String algoritmo, long milissegundos, int turnos, int vagasDescobertas, BigDecimal custo,
                           long erros, long alertas, BigDecimal desvioPadraoHoras) {
    }

    public record Comparacao(Long escalaId, LocalDate inicio, LocalDate fim, Metricas guloso, Metricas otimizado) {
    }

    private static final int MARGEM_DIAS = 21;

    private final EscalaRepository escalas;
    private final TurnoRepository turnos;
    private final TurnoModeloRepository modelos;
    private final ContextoService contextos;
    private final GeradorEscala gerador;
    private final OtimizadorEscala otimizador;
    private final ValidadorEscala validador;
    private final MotorFinanceiro motor;
    private final LimiteOtimizacoes limiteOtimizacoes;

    public ComparacaoAlgoritmosService(EscalaRepository escalas, TurnoRepository turnos, TurnoModeloRepository modelos,
                                       ContextoService contextos, GeradorEscala gerador, OtimizadorEscala otimizador,
                                       ValidadorEscala validador, MotorFinanceiro motor,
                                       LimiteOtimizacoes limiteOtimizacoes) {
        this.escalas = escalas;
        this.turnos = turnos;
        this.modelos = modelos;
        this.contextos = contextos;
        this.gerador = gerador;
        this.otimizador = otimizador;
        this.validador = validador;
        this.motor = motor;
        this.limiteOtimizacoes = limiteOtimizacoes;
    }

    @Transactional(readOnly = true)
    public Comparacao comparar(Long escalaId) {
        return limiteOtimizacoes.executar(() -> compararSemLimite(escalaId));
    }

    private Comparacao compararSemLimite(Long escalaId) {
        Escala e = escalas.findById(escalaId).orElseThrow(() -> new NaoEncontradoException("Escala", escalaId));
        LocalDate de = e.getDataInicio().minusDays(MARGEM_DIAS);
        LocalDate ate = e.getDataFim().plusDays(MARGEM_DIAS);
        ContextoCalculo ctx = contextos.carregar(de, ate);
        // As duas gerações partem do zero: só os turnos das escalas vizinhas entram como já decididos.
        List<Turno> vizinhos = turnos.findByDataBetween(de, ate).stream()
                .filter(t -> !t.getEscala().getId().equals(escalaId)).toList();
        List<TurnoModelo> mods = modelos.findAllByOrderByHoraInicioAsc();

        Metricas guloso = medir("Guloso", e, vizinhos, ctx,
                () -> gerador.gerar(e.getDataInicio(), e.getDataFim(), vizinhos, mods, ctx));
        Metricas otimizado = medir("Otimizado (Timefold)", e, vizinhos, ctx,
                () -> otimizador.gerar(e.getDataInicio(), e.getDataFim(), vizinhos, mods, ctx));
        return new Comparacao(escalaId, e.getDataInicio(), e.getDataFim(), guloso, otimizado);
    }

    private Metricas medir(String nome, Escala e, List<Turno> vizinhos, ContextoCalculo ctx,
                           Supplier<GeradorEscala.ResultadoGeracao> algoritmo) {
        long inicio = System.nanoTime();
        GeradorEscala.ResultadoGeracao r = algoritmo.get();
        long ms = (System.nanoTime() - inicio) / 1_000_000;

        List<Turno> todos = new ArrayList<>(vizinhos);
        todos.addAll(r.turnos());
        ResultadoValidacao v = validador.validar(e.getDataInicio(), e.getDataFim(), todos, ctx);
        int descobertas = r.deficits().stream().mapToInt(d -> d.necessarios() - d.alocados()).sum();
        return new Metricas(nome, ms, r.turnos().size(), descobertas, custo(r.turnos(), ctx), v.erros(), v.alertas(),
                desvioPadrao(r.turnos(), ctx));
    }

    private BigDecimal custo(List<Turno> gerados, ContextoCalculo ctx) {
        return gerados.stream().collect(Collectors.groupingBy(Turno::getData)).entrySet().stream()
                .map(dia -> motor.custoEscala(dia.getKey(), dia.getValue(), ctx))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** Desvio-padrão das horas escaladas no período, contando também quem ficou sem turno. */
    static BigDecimal desvioPadrao(List<Turno> gerados, ContextoCalculo ctx) {
        Map<Long, Double> horas = gerados.stream().collect(Collectors.groupingBy(t -> t.getFuncionario().getId(),
                Collectors.summingDouble(t -> t.horasTrabalhadas().doubleValue())));
        List<Funcionario> ativos = ctx.funcionariosAtivos();
        if (ativos.isEmpty()) {
            return BigDecimal.ZERO;
        }
        double media = ativos.stream().mapToDouble(f -> horas.getOrDefault(f.getId(), 0.0)).average().orElse(0);
        double variancia = ativos.stream().mapToDouble(f -> Math.pow(horas.getOrDefault(f.getId(), 0.0) - media, 2)).average().orElse(0);
        return BigDecimal.valueOf(Math.sqrt(variancia)).setScale(2, RoundingMode.HALF_UP);
    }
}
