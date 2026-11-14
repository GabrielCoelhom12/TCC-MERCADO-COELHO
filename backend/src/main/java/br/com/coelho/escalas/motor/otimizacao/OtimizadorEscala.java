package br.com.coelho.escalas.motor.otimizacao;

import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import br.com.coelho.escalas.dominio.Funcionario;
import br.com.coelho.escalas.dominio.Setor;
import br.com.coelho.escalas.dominio.TipoDia;
import br.com.coelho.escalas.dominio.Turno;
import br.com.coelho.escalas.dominio.TurnoModelo;
import br.com.coelho.escalas.motor.ContextoCalculo;
import br.com.coelho.escalas.motor.GeradorEscala;
import br.com.coelho.escalas.motor.MotorFinanceiro;
import br.com.coelho.escalas.motor.PlanejadorDemanda;
import br.com.coelho.escalas.motor.PrevisaoMovimento;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gera a escala com otimização (Timefold Solver): monta as mesmas vagas que o gerador guloso preencheria — a demanda
 * de cada dia e setor, nos turnos que cobrem os picos — e deixa o solver escolher quem ocupa cada uma, olhando a semana
 * inteira de uma vez. Primeiro uma heurística de construção, depois busca local até o tempo limite ou até parar de
 * melhorar. Devolve o mesmo resultado do guloso (turnos + vagas descobertas), para a escala seguir o mesmo caminho.
 */
@Component
public class OtimizadorEscala {

    private final MotorFinanceiro motor;
    private final SolverFactory<PlanoEscala> fabrica;

    @Autowired
    public OtimizadorEscala(MotorFinanceiro motor, @Value("${app.otimizacao.segundos:5}") long segundos) {
        this(motor, Duration.ofSeconds(segundos));
    }

    public OtimizadorEscala(MotorFinanceiro motor, Duration limite) {
        this.motor = motor;
        this.fabrica = SolverFactory.create(new SolverConfig()
                .withSolutionClass(PlanoEscala.class)
                .withEntityClasses(Vaga.class)
                .withConstraintProviderClass(RestricoesEscala.class)
                .withTerminationConfig(new TerminationConfig()
                        .withSpentLimit(limite)
                        .withUnimprovedSpentLimit(limite.dividedBy(4))));
    }

    /**
     * @param turnosExistentes turnos de outras escalas (vizinhas), respeitados nas regras de continuidade
     */
    public GeradorEscala.ResultadoGeracao gerar(LocalDate inicio, LocalDate fim, List<Turno> turnosExistentes,
                                                List<TurnoModelo> modelos, ContextoCalculo ctx) {
        if (modelos.isEmpty()) {
            throw new IllegalArgumentException("Cadastre ao menos um modelo de turno antes de gerar a escala.");
        }
        Regras regras = Regras.de(ctx.parametros());
        List<TurnoFixo> fixos = turnosExistentes.stream().map(t -> TurnoFixo.de(t, regras)).toList();
        Map<String, Integer> necessariosPorDiaSetor = new LinkedHashMap<>();
        List<Vaga> vagas = montarVagas(inicio, fim, turnosExistentes, modelos, ctx, regras, necessariosPorDiaSetor);
        if (vagas.isEmpty()) {
            return new GeradorEscala.ResultadoGeracao(List.of(), List.of());
        }

        PlanoEscala resolvido = fabrica.buildSolver().solve(new PlanoEscala(vagas, fixos));
        return resultado(resolvido.getVagas(), turnosExistentes, necessariosPorDiaSetor, ctx);
    }

    private List<Vaga> montarVagas(LocalDate inicio, LocalDate fim, List<Turno> existentes, List<TurnoModelo> modelos,
                                   ContextoCalculo ctx, Regras regras, Map<String, Integer> necessariosPorDiaSetor) {
        List<Vaga> vagas = new ArrayList<>();
        long id = 0;
        for (LocalDate dia = inicio; !dia.isAfter(fim); dia = dia.plusDays(1)) {
            final LocalDate d = dia;
            TipoDia tipo = motor.tipoDia(d, ctx);
            List<TurnoModelo> modelosDia = motor.modelosDoDia(d, modelos, ctx);
            PrevisaoMovimento.PrevisaoDia previsao = motor.previsao(d, ctx);
            BigDecimal fator = motor.fator(d, ctx);
            for (Setor setor : ctx.setoresAtivos().stream().sorted(Comparator.comparing(Setor::getId)).toList()) {
                PlanejadorDemanda.PlanoSetor plano = motor.planejar(setor, tipo, previsao, modelosDia);
                int necessarios = plano.necessarios();
                if (necessarios <= 0 || modelosDia.isEmpty()) {
                    continue;
                }
                necessariosPorDiaSetor.put(chave(d, setor.getId()), necessarios);
                List<Funcionario> doSetor = ctx.funcionariosAtivos().stream()
                        .filter(f -> f.getSetor().getId().equals(setor.getId())).toList();
                int jaEscalados = (int) existentes.stream()
                        .filter(t -> t.getData().equals(d) && t.getFuncionario().getSetor().getId().equals(setor.getId()))
                        .map(t -> t.getFuncionario().getId()).distinct().count();
                List<Funcionario> candidatos = doSetor.stream()
                        .filter(f -> f.disponivelEm(d.getDayOfWeek()) && !ctx.ausente(f, d))
                        .toList();
                for (int k = jaEscalados; k < necessarios; k++) {
                    TurnoModelo modelo = k < plano.turnosSugeridos().size()
                            ? plano.turnosSugeridos().get(k)
                            : modelosDia.get(k % modelosDia.size());
                    Turno molde = Turno.de(modelo, null, d);
                    int minutos = molde.horasTrabalhadas().multiply(BigDecimal.valueOf(60)).intValue();
                    vagas.add(new Vaga(++id, d, setor.getId(), setor.getNome(), modelo, molde.inicio(), molde.fim(), minutos,
                            fator, candidatos, regras));
                }
            }
        }
        return vagas;
    }

    private static GeradorEscala.ResultadoGeracao resultado(List<Vaga> vagas, List<Turno> existentes,
                                                           Map<String, Integer> necessariosPorDiaSetor, ContextoCalculo ctx) {
        List<Turno> turnos = vagas.stream()
                .filter(v -> v.getFuncionario() != null)
                .map(v -> Turno.de(v.getModelo(), v.getFuncionario(), v.getData()))
                .toList();
        Map<String, String> nomes = new LinkedHashMap<>();
        Map<String, Integer> vazias = new LinkedHashMap<>();
        for (Vaga v : vagas) {
            String chave = chave(v.getData(), v.getSetorId());
            nomes.put(chave, v.getSetorNome());
            if (v.getFuncionario() == null) {
                vazias.merge(chave, 1, Integer::sum);
            }
        }
        List<GeradorEscala.Deficit> deficits = new ArrayList<>();
        vazias.forEach((chave, n) -> {
            String[] partes = chave.split("\\|");
            int necessarios = necessariosPorDiaSetor.get(chave);
            deficits.add(new GeradorEscala.Deficit(LocalDate.parse(partes[0]), Long.valueOf(partes[1]), nomes.get(chave),
                    necessarios, necessarios - n));
        });
        return new GeradorEscala.ResultadoGeracao(turnos, deficits);
    }

    private static String chave(LocalDate d, Long setorId) {
        return d + "|" + setorId;
    }
}
