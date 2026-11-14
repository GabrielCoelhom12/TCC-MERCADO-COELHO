package br.com.coelho.escalas.motor;

import br.com.coelho.escalas.dominio.Ausencia;
import br.com.coelho.escalas.dominio.Funcionario;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Regras para registrar um período de férias. Erros impedem o registro; alertas mostram o impacto na operação
 * (colegas do setor ausentes ao mesmo tempo). Os turnos já escalados são repassados por
 * {@link SugestorSubstituicao#planejarCobertura}.
 *
 * <p>O repouso semanal de referência é o domingo (Lei 10.101/2000, art. 6º, para o comércio).</p>
 */
public final class RegrasFerias {

    /** CLT art. 134, §1º: nenhum dos períodos pode ser inferior a 5 dias corridos. */
    public static final int MINIMO_DIAS = 5;
    /** CLT art. 130: até 30 dias corridos por período aquisitivo. */
    public static final int MAXIMO_DIAS = 30;

    public record Analise(int dias, LocalDate retorno, List<String> erros, List<String> alertas) {
        @JsonProperty("valida")
        public boolean valida() {
            return erros.isEmpty();
        }
    }

    private RegrasFerias() {
    }

    public static Analise analisar(Funcionario f, LocalDate inicio, LocalDate fim, List<Ausencia> ausencias,
                                   Set<LocalDate> feriados) {
        return analisar(f, inicio, fim, ausencias, feriados, null);
    }

    /**
     * @param ausencias ausências que tocam o período, de qualquer colaborador
     * @param feriados  feriados do período e dos dois dias seguintes ao início
     * @param ignorarId ausência sendo editada, que não conta como sobreposição
     */
    public static Analise analisar(Funcionario f, LocalDate inicio, LocalDate fim, List<Ausencia> ausencias,
                                   Set<LocalDate> feriados, Long ignorarId) {
        List<String> erros = new ArrayList<>();
        List<String> alertas = new ArrayList<>();
        int dias = (int) ChronoUnit.DAYS.between(inicio, fim) + 1;

        if (!f.isAtivo()) {
            erros.add(f.getNome() + " está inativo.");
        }
        if (fim.isBefore(inicio)) {
            erros.add("O fim das férias deve ser igual ou posterior ao início.");
            return new Analise(0, null, erros, alertas);
        }
        if (dias < MINIMO_DIAS) {
            erros.add("Cada período de férias deve ter ao menos " + MINIMO_DIAS + " dias corridos (CLT art. 134, §1º).");
        }
        if (dias > MAXIMO_DIAS) {
            erros.add("Um período de férias não pode passar de " + MAXIMO_DIAS + " dias (CLT art. 130).");
        }
        for (int i = 1; i <= 2; i++) {
            LocalDate d = inicio.plusDays(i);
            if (d.getDayOfWeek() == DayOfWeek.SUNDAY || feriados.contains(d)) {
                erros.add("As férias não podem começar nos dois dias que antecedem feriado ou o repouso semanal ("
                        + Formato.data(d) + "; CLT art. 134, §3º).");
                break;
            }
        }

        List<Ausencia> sobrepostas = ausencias.stream()
                .filter(a -> ignorarId == null || !ignorarId.equals(a.getId()))
                .filter(a -> !a.getInicio().isAfter(fim) && !a.getFim().isBefore(inicio))
                .toList();
        sobrepostas.stream().filter(a -> mesmo(a.getFuncionario(), f)).forEach(a -> erros.add("Já existe "
                + rotulo(a.getMotivo()) + " de " + Formato.data(a.getInicio()) + " a " + Formato.data(a.getFim()) + " nesse período."));

        String colegas = sobrepostas.stream()
                .filter(a -> !mesmo(a.getFuncionario(), f) && a.getFuncionario().getSetor().getId().equals(f.getSetor().getId()))
                .map(a -> a.getFuncionario().getNome() + " (" + rotulo(a.getMotivo()) + " de " + Formato.data(a.getInicio())
                        + " a " + Formato.data(a.getFim()) + ")")
                .collect(Collectors.joining(", "));
        if (!colegas.isEmpty()) {
            alertas.add("Também estarão ausentes em " + f.getSetor().getNome() + ": " + colegas + ".");
        }
        return new Analise(dias, fim.plusDays(1), erros, alertas);
    }

    private static boolean mesmo(Funcionario a, Funcionario b) {
        return a.getId().equals(b.getId());
    }

    private static String rotulo(Ausencia.Motivo motivo) {
        return switch (motivo) {
            case FERIAS -> "férias";
            case ATESTADO -> "atestado";
            case FOLGA_ACORDADA -> "folga acordada";
            case OUTRO -> "ausência";
        };
    }
}
