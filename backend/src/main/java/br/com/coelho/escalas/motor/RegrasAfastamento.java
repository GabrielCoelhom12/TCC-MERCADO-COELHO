package br.com.coelho.escalas.motor;

import br.com.coelho.escalas.dominio.Ausencia;
import br.com.coelho.escalas.dominio.Ausencia.Motivo;
import br.com.coelho.escalas.dominio.Funcionario;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Regras para registrar um afastamento (atestado, licenças, afastamento pelo INSS, acidente de trabalho, luto,
 * casamento...). Diferente das férias, pode começar em qualquer dia e ser lançado depois do fato (o atestado costuma
 * chegar no dia seguinte). Os alertas lembram o que a lei garante a cada tipo, sem impedir o registro.
 */
public final class RegrasAfastamento {

    /** Teto de um registro: afastamentos maiores são lançados em novos períodos. */
    public static final int MAXIMO_DIAS = 730;
    /** Lei 8.213/1991, art. 60: a empresa paga os primeiros 15 dias; a partir do 16º, o INSS. */
    public static final int DIAS_PAGOS_PELA_EMPRESA = 15;
    /** CLT art. 392. */
    public static final int DIAS_LICENCA_MATERNIDADE = 120;
    /** CLT art. 473, I e II. */
    public static final int DIAS_LUTO = 2;
    public static final int DIAS_CASAMENTO = 3;

    private RegrasAfastamento() {
    }

    /**
     * @param ausencias ausências que tocam o período, de qualquer colaborador
     * @param ignorarId afastamento sendo editado, que não conta como sobreposição
     */
    public static AnaliseAusencia analisar(Funcionario f, Motivo motivo, LocalDate inicio, LocalDate fim,
                                           List<Ausencia> ausencias, Long ignorarId) {
        List<String> erros = new ArrayList<>();
        List<String> alertas = new ArrayList<>();
        if (motivo == null) {
            erros.add("Informe o tipo de afastamento.");
        } else if (motivo == Motivo.FERIAS) {
            erros.add("Férias são registradas na operação de Férias, que aplica as regras próprias da CLT.");
        }
        if (!f.isAtivo()) {
            erros.add(f.getNome() + " está inativo.");
        }
        if (fim.isBefore(inicio)) {
            erros.add("O fim do afastamento deve ser igual ou posterior ao início.");
            return new AnaliseAusencia(0, null, erros, alertas);
        }
        int dias = (int) ChronoUnit.DAYS.between(inicio, fim) + 1;
        if (dias > MAXIMO_DIAS) {
            erros.add("Registre afastamentos de até " + MAXIMO_DIAS + " dias por vez; se continuar, lance um novo período depois.");
        }
        if (motivo != null) {
            alertaDoTipo(motivo, dias).ifPresent(alertas::add);
        }
        Sobreposicoes.avaliar(f, inicio, fim, ausencias, ignorarId, erros, alertas);
        return new AnaliseAusencia(dias, fim.plusDays(1), erros, alertas);
    }

    private static Optional<String> alertaDoTipo(Motivo motivo, int dias) {
        String alerta = switch (motivo) {
            case ATESTADO -> dias > DIAS_PAGOS_PELA_EMPRESA
                    ? "Atestado acima de " + DIAS_PAGOS_PELA_EMPRESA + " dias: a partir do 16º dia o afastamento é pago pelo INSS "
                    + "(Lei 8.213/1991, art. 60); encaminhe o colaborador à perícia."
                    : null;
            case LICENCA_MATERNIDADE -> dias < DIAS_LICENCA_MATERNIDADE
                    ? "A licença-maternidade é de " + DIAS_LICENCA_MATERNIDADE + " dias (CLT art. 392); confira as datas."
                    : null;
            case LUTO -> dias > DIAS_LUTO
                    ? "A CLT garante " + DIAS_LUTO + " dias seguidos por falecimento de familiar (art. 473, I); os demais são acordo com a empresa."
                    : null;
            case CASAMENTO -> dias > DIAS_CASAMENTO
                    ? "A CLT garante " + DIAS_CASAMENTO + " dias seguidos por casamento (art. 473, II); os demais são acordo com a empresa."
                    : null;
            default -> null;
        };
        return Optional.ofNullable(alerta);
    }
}
