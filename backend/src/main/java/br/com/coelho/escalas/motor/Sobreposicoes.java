package br.com.coelho.escalas.motor;

import br.com.coelho.escalas.dominio.Ausencia;
import br.com.coelho.escalas.dominio.Funcionario;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/** Regras comuns a férias e afastamentos: o colaborador não pode ter duas ausências ao mesmo tempo. */
final class Sobreposicoes {

    private Sobreposicoes() {
    }

    /**
     * Erro para cada ausência do próprio colaborador no período; alerta com os colegas do mesmo setor ausentes.
     *
     * @param ausencias ausências que tocam o período, de qualquer colaborador
     * @param ignorarId ausência sendo editada, que não conta como sobreposição
     */
    static void avaliar(Funcionario f, LocalDate inicio, LocalDate fim, List<Ausencia> ausencias, Long ignorarId,
                        List<String> erros, List<String> alertas) {
        List<Ausencia> sobrepostas = ausencias.stream()
                .filter(a -> ignorarId == null || !ignorarId.equals(a.getId()))
                .filter(a -> !a.getInicio().isAfter(fim) && !a.getFim().isBefore(inicio))
                .toList();
        sobrepostas.stream().filter(a -> mesmo(a.getFuncionario(), f)).forEach(a -> erros.add("Já existe "
                + a.getMotivo().getRotulo() + " de " + Formato.data(a.getInicio()) + " a " + Formato.data(a.getFim()) + " nesse período."));

        String colegas = sobrepostas.stream()
                .filter(a -> !mesmo(a.getFuncionario(), f) && a.getFuncionario().getSetor().getId().equals(f.getSetor().getId()))
                .map(a -> a.getFuncionario().getNome() + " (" + a.getMotivo().getRotulo() + " de " + Formato.data(a.getInicio())
                        + " a " + Formato.data(a.getFim()) + ")")
                .collect(Collectors.joining(", "));
        if (!colegas.isEmpty()) {
            alertas.add("Também estarão ausentes em " + f.getSetor().getNome() + ": " + colegas + ".");
        }
    }

    private static boolean mesmo(Funcionario a, Funcionario b) {
        return a.getId().equals(b.getId());
    }
}
