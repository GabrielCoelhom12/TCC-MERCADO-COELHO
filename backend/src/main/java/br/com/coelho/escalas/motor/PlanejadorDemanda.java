package br.com.coelho.escalas.motor;

import br.com.coelho.escalas.dominio.Setor;
import br.com.coelho.escalas.dominio.TurnoModelo;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Transforma a previsão de clientes por faixa em pessoas necessárias por setor e escolhe a combinação de turnos que
 * cobre todas as faixas: pessoas(faixa) = ⌈clientes / (capacidade por hora × horas da faixa)⌉. A escolha é gulosa —
 * a cada passo, o modelo de turno que atende mais faixas ainda descobertas (em empate, o mais curto) —, simples de
 * explicar e próxima do ótimo para os poucos modelos de turno de uma loja.
 */
public class PlanejadorDemanda {

    public record DemandaFaixa(LocalTime inicio, LocalTime fim, int clientes, int pessoas) {
    }

    /**
     * @param minimoManual    demanda mínima fixa do setor (piso)
     * @param faixas          pessoas necessárias por faixa (vazio se o setor não acompanha o movimento)
     * @param turnosSugeridos turnos que cobrem as faixas
     * @param necessarios     pessoas no dia: o maior entre o piso e a quantidade de turnos sugeridos
     */
    public record PlanoSetor(Long setorId, int minimoManual, List<DemandaFaixa> faixas, List<TurnoModelo> turnosSugeridos,
                             int necessarios) {
    }

    private static final int MAX_TURNOS = 100;

    public PlanoSetor planejar(Setor setor, int minimoManual, PrevisaoMovimento.PrevisaoDia previsao, List<TurnoModelo> modelos) {
        Integer capacidade = setor.getClientesPorColaboradorHora();
        if (capacidade == null || capacidade <= 0 || previsao == null || modelos.isEmpty()) {
            return new PlanoSetor(setor.getId(), minimoManual, List.of(), List.of(), minimoManual);
        }

        List<DemandaFaixa> faixas = previsao.faixas().stream().map(f -> {
            double horas = minutos(f.inicio(), f.fim()) / 60.0;
            int pessoas = f.clientes() <= 0 || horas <= 0 ? 0 : (int) Math.ceil(f.clientes() / (capacidade * horas) - 1e-9);
            return new DemandaFaixa(f.inicio(), f.fim(), f.clientes(), pessoas);
        }).toList();

        int[] faltam = faixas.stream().mapToInt(DemandaFaixa::pessoas).toArray();
        List<TurnoModelo> ordenados = modelos.stream()
                .sorted(Comparator.comparingDouble(TurnoModelo::getHoras).thenComparing(TurnoModelo::getSigla))
                .toList();
        List<TurnoModelo> sugeridos = new ArrayList<>();
        while (sugeridos.size() < MAX_TURNOS) {
            TurnoModelo melhor = null;
            int melhorGanho = 0;
            for (TurnoModelo m : ordenados) {
                int ganho = 0;
                for (int i = 0; i < faixas.size(); i++) {
                    if (faltam[i] > 0 && cobre(m.getHoraInicio(), m.getHoraFim(), faixas.get(i).inicio(), faixas.get(i).fim())) {
                        ganho++;
                    }
                }
                if (ganho > melhorGanho) {
                    melhor = m;
                    melhorGanho = ganho;
                }
            }
            if (melhor == null) {
                break; // nada a cobrir ou nenhuma faixa restante é coberta por algum modelo
            }
            sugeridos.add(melhor);
            for (int i = 0; i < faixas.size(); i++) {
                if (faltam[i] > 0 && cobre(melhor.getHoraInicio(), melhor.getHoraFim(), faixas.get(i).inicio(), faixas.get(i).fim())) {
                    faltam[i]--;
                }
            }
        }
        return new PlanoSetor(setor.getId(), minimoManual, faixas, sugeridos, Math.max(minimoManual, sugeridos.size()));
    }

    /** Um turno cobre a faixa quando está presente em pelo menos metade dela (turnos que viram a noite inclusos). */
    public static boolean cobre(LocalTime inicioTurno, LocalTime fimTurno, LocalTime inicioFaixa, LocalTime fimFaixa) {
        int ti = inicioTurno.toSecondOfDay() / 60;
        int tf = fimTurno.toSecondOfDay() / 60;
        if (tf <= ti) {
            tf += 24 * 60;
        }
        int fi = inicioFaixa.toSecondOfDay() / 60;
        int ff = fimFaixa.toSecondOfDay() / 60;
        if (ff <= fi) {
            ff += 24 * 60;
        }
        int sobreposicao = Math.max(0, Math.min(tf, ff) - Math.max(ti, fi));
        return sobreposicao * 2 >= ff - fi;
    }

    private static long minutos(LocalTime inicio, LocalTime fim) {
        long m = Duration.between(inicio, fim).toMinutes();
        return m <= 0 ? m + 24 * 60 : m;
    }
}
