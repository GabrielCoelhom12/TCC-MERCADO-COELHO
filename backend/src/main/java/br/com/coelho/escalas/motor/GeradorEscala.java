package br.com.coelho.escalas.motor;

import br.com.coelho.escalas.dominio.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/**
 * Gera uma sugestão de escala enxuta: para cada dia e setor, aloca exatamente as pessoas necessárias
 * (L_min) — nos setores que acompanham o movimento, nos turnos que cobrem os picos previstos —, escolhendo — entre os colaboradores que não violam nenhuma regra trabalhista — quem
 * tem menos horas na semana e, em empate, o menor custo-hora. A gerência ajusta depois na matriz,
 * sob a vigilância do validador.
 */
@Component
public class GeradorEscala {

    public record Deficit(LocalDate data, Long setorId, String setor, int necessarios, int alocados) {
    }

    public record ResultadoGeracao(List<Turno> turnos, List<Deficit> deficits) {
    }

    private final MotorFinanceiro motor;

    public GeradorEscala(MotorFinanceiro motor) {
        this.motor = motor;
    }

    /**
     * @param turnosExistentes turnos de outras escalas (vizinhas) que precisam ser respeitados
     *                         por interjornada, dias consecutivos e domingos seguidos
     */
    public ResultadoGeracao gerar(LocalDate inicio, LocalDate fim, List<Turno> turnosExistentes,
                                  List<TurnoModelo> modelos, ContextoCalculo ctx) {
        if (modelos.isEmpty()) {
            throw new IllegalArgumentException("Cadastre ao menos um modelo de turno antes de gerar a escala.");
        }
        ParametrosOperacionais p = ctx.parametros();
        Map<Long, List<Turno>> agenda = new HashMap<>();
        for (Turno t : turnosExistentes) {
            agenda.computeIfAbsent(t.getFuncionario().getId(), k -> new ArrayList<>()).add(t);
        }

        List<Turno> novos = new ArrayList<>();
        List<Deficit> deficits = new ArrayList<>();

        for (LocalDate d = inicio; !d.isAfter(fim); d = d.plusDays(1)) {
            final LocalDate dia = d;
            TipoDia tipo = motor.tipoDia(dia, ctx);
            List<TurnoModelo> modelosDia = motor.modelosDoDia(dia, modelos, ctx);
            PrevisaoMovimento.PrevisaoDia previsao = motor.previsao(dia, ctx);

            List<Setor> setores = ctx.setoresAtivos().stream().sorted(Comparator.comparing(Setor::getId)).toList();
            for (Setor setor : setores) {
                // Pessoas e turnos que cobrem o movimento previsto (ou só o mínimo fixo, sem histórico).
                PlanejadorDemanda.PlanoSetor plano = motor.planejar(setor, tipo, previsao, modelosDia);
                int necessarios = plano.necessarios();
                if (necessarios <= 0) {
                    continue;
                }
                List<Funcionario> doSetor = ctx.funcionariosAtivos().stream()
                        .filter(f -> f.getSetor().getId().equals(setor.getId()))
                        .toList();
                int alocados = (int) doSetor.stream().filter(f -> trabalhaNoDia(agenda, f, dia)).count();

                for (int k = alocados; k < necessarios; k++) {
                    Turno escolhido = null;
                    List<TurnoModelo> preferencia = preferencia(plano, modelosDia, k);
                    for (int j = 0; j < preferencia.size() && escolhido == null; j++) {
                        TurnoModelo modelo = preferencia.get(j);
                        escolhido = doSetor.stream()
                                .filter(f -> f.disponivelEm(dia.getDayOfWeek()))
                                .filter(f -> !ctx.ausente(f, dia))
                                .filter(f -> !trabalhaNoDia(agenda, f, dia))
                                .map(f -> Turno.de(modelo, f, dia))
                                .filter(t -> podeAlocar(agenda.getOrDefault(t.getFuncionario().getId(), List.of()), t, p))
                                .min(Comparator
                                        .comparing((Turno t) -> horasNaSemana(agenda, t.getFuncionario(), dia))
                                        .thenComparing(t -> t.getFuncionario().getCustoHora())
                                        .thenComparing(t -> t.getFuncionario().getId()))
                                .orElse(null);
                    }
                    if (escolhido == null) {
                        break;
                    }
                    agenda.computeIfAbsent(escolhido.getFuncionario().getId(), x -> new ArrayList<>()).add(escolhido);
                    novos.add(escolhido);
                    alocados++;
                }
                if (alocados < necessarios) {
                    deficits.add(new Deficit(dia, setor.getId(), setor.getNome(), necessarios, alocados));
                }
            }
        }
        return new ResultadoGeracao(novos, deficits);
    }

    /** O turno sugerido para a k-ésima pessoa vem primeiro; os demais modelos servem de alternativa (em rodízio). */
    private static List<TurnoModelo> preferencia(PlanejadorDemanda.PlanoSetor plano, List<TurnoModelo> modelosDia, int k) {
        List<TurnoModelo> ordem = new ArrayList<>();
        if (k < plano.turnosSugeridos().size()) {
            ordem.add(plano.turnosSugeridos().get(k));
        }
        for (int j = 0; j < modelosDia.size(); j++) {
            TurnoModelo m = modelosDia.get((k + j) % modelosDia.size());
            if (!ordem.contains(m)) {
                ordem.add(m);
            }
        }
        return ordem;
    }

    private static boolean trabalhaNoDia(Map<Long, List<Turno>> agenda, Funcionario f, LocalDate d) {
        return agenda.getOrDefault(f.getId(), List.of()).stream().anyMatch(t -> t.getData().equals(d));
    }

    private static BigDecimal horasNaSemana(Map<Long, List<Turno>> agenda, Funcionario f, LocalDate d) {
        LocalDate segunda = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate domingo = segunda.plusDays(6);
        return agenda.getOrDefault(f.getId(), List.of()).stream()
                .filter(t -> !t.getData().isBefore(segunda) && !t.getData().isAfter(domingo))
                .map(Turno::horasTrabalhadas)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Verifica se o novo turno respeita todas as regras trabalhistas frente à agenda do colaborador. */
    static boolean podeAlocar(List<Turno> agendaFuncionario, Turno novo, ParametrosOperacionais p) {
        if (novo.horasTrabalhadas().compareTo(p.getJornadaMaximaDiariaHoras()) > 0) {
            return false;
        }
        long interjornadaMin = p.getInterjornadaMinimaHoras().multiply(BigDecimal.valueOf(60)).longValue();
        for (Turno t : agendaFuncionario) {
            if (t.getData().equals(novo.getData())) {
                return false;
            }
            if (novo.inicio().isBefore(t.fim()) && t.inicio().isBefore(novo.fim())) {
                return false;
            }
            long descanso = t.fim().isAfter(novo.inicio())
                    ? Duration.between(novo.fim(), t.inicio()).toMinutes()
                    : Duration.between(t.fim(), novo.inicio()).toMinutes();
            if (descanso < interjornadaMin) {
                return false;
            }
        }

        Set<LocalDate> dias = new HashSet<>();
        agendaFuncionario.forEach(t -> dias.add(t.getData()));
        LocalDate d = novo.getData();
        if (sequencia(dias, d, 1) > p.getMaxDiasConsecutivos()) {
            return false;
        }
        if (d.getDayOfWeek() == DayOfWeek.SUNDAY && sequencia(dias, d, 7) > p.getMaxDomingosConsecutivos()) {
            return false;
        }

        LocalDate segunda = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        BigDecimal semana = agendaFuncionario.stream()
                .filter(t -> !t.getData().isBefore(segunda) && !t.getData().isAfter(segunda.plusDays(6)))
                .map(Turno::horasTrabalhadas)
                .reduce(novo.horasTrabalhadas(), BigDecimal::add);
        return semana.compareTo(p.getJornadaSemanalHoras()) <= 0;
    }

    /** Tamanho da sequência que contém d (passo em dias), supondo d trabalhado. */
    private static int sequencia(Set<LocalDate> dias, LocalDate d, int passo) {
        int total = 1;
        for (LocalDate x = d.minusDays(passo); dias.contains(x); x = x.minusDays(passo)) {
            total++;
        }
        for (LocalDate x = d.plusDays(passo); dias.contains(x); x = x.plusDays(passo)) {
            total++;
        }
        return total;
    }
}
