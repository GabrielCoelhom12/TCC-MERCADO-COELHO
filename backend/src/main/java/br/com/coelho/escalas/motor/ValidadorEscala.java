package br.com.coelho.escalas.motor;

import br.com.coelho.escalas.dominio.*;
import br.com.coelho.escalas.motor.ResumoDia.ResumoSetor;
import br.com.coelho.escalas.motor.ResumoDia.Situacao;
import br.com.coelho.escalas.motor.Violacao.Regra;
import br.com.coelho.escalas.motor.Violacao.Severidade;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Validador de regras: cruza a escala proposta com as restrições trabalhistas, a disponibilidade
 * individual, a demanda mínima de cada setor e os limites financeiros do motor.
 *
 * <p>Os turnos podem incluir dias fora do período validado (escalas vizinhas), para que regras como
 * interjornada, dias consecutivos e domingos seguidos enxerguem a continuidade; só são reportadas
 * violações ocorridas dentro do período.</p>
 */
@Component
public class ValidadorEscala {

    private final MotorFinanceiro motor;

    public ValidadorEscala(MotorFinanceiro motor) {
        this.motor = motor;
    }

    public ResultadoValidacao validar(LocalDate inicio, LocalDate fim, List<Turno> turnos, ContextoCalculo ctx) {
        List<Violacao> violacoes = new ArrayList<>();

        Map<Long, List<Turno>> porFuncionario = turnos.stream()
                .collect(Collectors.groupingBy(t -> t.getFuncionario().getId(), LinkedHashMap::new, Collectors.toList()));
        for (List<Turno> lista : porFuncionario.values()) {
            List<Turno> ordenados = new ArrayList<>(lista);
            ordenados.sort(Comparator.comparing(Turno::inicio));
            validarFuncionario(ordenados, inicio, fim, ctx, violacoes);
        }

        Map<LocalDate, List<Turno>> porData = turnos.stream().collect(Collectors.groupingBy(Turno::getData));
        List<ResumoDia> dias = new ArrayList<>();
        for (LocalDate d = inicio; !d.isAfter(fim); d = d.plusDays(1)) {
            dias.add(resumirDia(d, porData.getOrDefault(d, List.of()), ctx, violacoes));
        }

        violacoes.sort(Comparator.comparing(Violacao::data)
                .thenComparing(Violacao::severidade)
                .thenComparing(v -> v.funcionarioNome() == null ? "" : v.funcionarioNome()));

        BigDecimal custoTotal = dias.stream().map(ResumoDia::custoEscala).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal tetoTotal = dias.stream().map(ResumoDia::tetoFolha).reduce(BigDecimal.ZERO, BigDecimal::add);
        long erros = violacoes.stream().filter(Violacao::erro).count();
        return new ResultadoValidacao(dias, violacoes, custoTotal, tetoTotal, erros, violacoes.size() - erros);
    }

    // ------------------------------------------------------------------ regras por colaborador

    private void validarFuncionario(List<Turno> turnos, LocalDate inicio, LocalDate fim, ContextoCalculo ctx, List<Violacao> out) {
        Funcionario f = turnos.getFirst().getFuncionario();
        ParametrosOperacionais p = ctx.parametros();
        Periodo periodo = new Periodo(inicio, fim);

        for (Turno t : turnos) {
            LocalDate d = t.getData();
            if (!periodo.contem(d)) {
                continue;
            }
            if (!f.isAtivo()) {
                out.add(erro(Regra.FUNCIONARIO_INATIVO, d, f, "Colaborador inativo está escalado."));
            }
            if (!f.disponivelEm(d.getDayOfWeek())) {
                out.add(erro(Regra.INDISPONIBILIDADE, d, f,
                        "Escalado em " + TipoDia.de(d.getDayOfWeek()).getRotulo().toLowerCase() + ", dia em que declarou indisponibilidade."));
            }
            Ausencia ausencia = ctx.ausencia(f, d);
            if (ausencia != null) {
                out.add(erro(Regra.AUSENCIA, d, f, "Escalado durante ausência (" + ausencia.getMotivo().name().replace('_', ' ').toLowerCase()
                        + " de " + Formato.data(ausencia.getInicio()) + " a " + Formato.data(ausencia.getFim()) + ")."));
            }
            BigDecimal horas = t.horasTrabalhadas();
            if (horas.compareTo(BigDecimal.valueOf(6)) > 0 && t.getIntervaloMinutos() < 60) {
                out.add(erro(Regra.INTERVALO_INTRAJORNADA, d, f, "Jornada de " + Formato.horas(horas)
                        + " exige intervalo mínimo de 1h (CLT art. 71); informado " + t.getIntervaloMinutos() + " min."));
            } else if (horas.compareTo(BigDecimal.valueOf(4)) > 0 && horas.compareTo(BigDecimal.valueOf(6)) <= 0
                    && t.getIntervaloMinutos() < 15) {
                out.add(erro(Regra.INTERVALO_INTRAJORNADA, d, f, "Jornada de " + Formato.horas(horas)
                        + " exige intervalo mínimo de 15 min (CLT art. 71, §1º)."));
            }
        }

        // Sobreposição e interjornada (CLT art. 66)
        for (int i = 1; i < turnos.size(); i++) {
            Turno anterior = turnos.get(i - 1);
            Turno atual = turnos.get(i);
            if (!periodo.contem(atual.getData())) {
                continue;
            }
            if (atual.inicio().isBefore(anterior.fim())) {
                out.add(erro(Regra.SOBREPOSICAO, atual.getData(), f, "Turnos sobrepostos ("
                        + anterior.getHoraInicio() + "–" + anterior.getHoraFim() + " e "
                        + atual.getHoraInicio() + "–" + atual.getHoraFim() + ")."));
            } else if (!atual.getData().equals(anterior.getData())) {
                long minutos = Duration.between(anterior.fim(), atual.inicio()).toMinutes();
                BigDecimal descanso = BigDecimal.valueOf(minutos).divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);
                if (descanso.compareTo(p.getInterjornadaMinimaHoras()) < 0) {
                    out.add(erro(Regra.INTERJORNADA, atual.getData(), f, "Descanso de " + Formato.horas(descanso)
                            + " entre jornadas; mínimo legal " + Formato.horas(p.getInterjornadaMinimaHoras()) + " (CLT art. 66)."));
                }
            }
        }

        // Jornada diária (CLT arts. 58 e 59)
        Map<LocalDate, BigDecimal> horasPorDia = new TreeMap<>();
        for (Turno t : turnos) {
            horasPorDia.merge(t.getData(), t.horasTrabalhadas(), BigDecimal::add);
        }
        horasPorDia.forEach((d, horas) -> {
            if (!periodo.contem(d)) {
                return;
            }
            if (horas.compareTo(p.getJornadaMaximaDiariaHoras()) > 0) {
                out.add(erro(Regra.JORNADA_DIARIA_MAXIMA, d, f, "Jornada de " + Formato.horas(horas)
                        + " excede o máximo diário de " + Formato.horas(p.getJornadaMaximaDiariaHoras()) + " (CLT art. 59)."));
            } else if (horas.compareTo(p.getJornadaNormalDiariaHoras()) > 0) {
                out.add(alerta(Regra.HORA_EXTRA_DIARIA, d, f, "Jornada de " + Formato.horas(horas)
                        + " gera hora extra diária (normal " + Formato.horas(p.getJornadaNormalDiariaHoras()) + ")."));
            }
        });

        // Jornada semanal (segunda a domingo)
        Map<LocalDate, BigDecimal> horasPorSemana = new TreeMap<>();
        horasPorDia.forEach((d, horas) -> horasPorSemana.merge(d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), horas, BigDecimal::add));
        horasPorSemana.forEach((segunda, horas) -> {
            LocalDate domingo = segunda.plusDays(6);
            if (domingo.isBefore(inicio) || segunda.isAfter(fim)) {
                return;
            }
            if (horas.compareTo(p.getJornadaSemanalHoras()) > 0) {
                LocalDate referencia = segunda.isBefore(inicio) ? inicio : segunda;
                out.add(alerta(Regra.HORA_EXTRA_SEMANAL, referencia, f, "Semana de " + Formato.data(segunda) + " soma "
                        + Formato.horas(horas) + ", acima de " + Formato.horas(p.getJornadaSemanalHoras()) + " semanais."));
            }
        });

        // Repouso semanal (CLT art. 67): no máximo N dias seguidos de trabalho
        int sequencia = 0;
        boolean reportado = false;
        LocalDate anterior = null;
        for (LocalDate d : horasPorDia.keySet()) {
            boolean continua = anterior != null && anterior.plusDays(1).equals(d);
            sequencia = continua ? sequencia + 1 : 1;
            if (!continua) {
                reportado = false;
            }
            if (sequencia > p.getMaxDiasConsecutivos() && periodo.contem(d) && !reportado) {
                out.add(erro(Regra.DIAS_CONSECUTIVOS, d, f, sequencia + "º dia seguido de trabalho; repouso semanal obrigatório após "
                        + p.getMaxDiasConsecutivos() + " dias (CLT art. 67)."));
                reportado = true;
            }
            anterior = d;
        }

        // Folga dominical no comércio (Lei 10.101/2000, art. 6º, parágrafo único)
        int domingos = 0;
        reportado = false;
        anterior = null;
        for (LocalDate d : horasPorDia.keySet()) {
            if (d.getDayOfWeek() != DayOfWeek.SUNDAY) {
                continue;
            }
            boolean continua = anterior != null && anterior.plusDays(7).equals(d);
            domingos = continua ? domingos + 1 : 1;
            if (!continua) {
                reportado = false;
            }
            if (domingos > p.getMaxDomingosConsecutivos() && periodo.contem(d) && !reportado) {
                out.add(erro(Regra.DOMINGOS_CONSECUTIVOS, d, f, domingos + "º domingo seguido; o repouso deve coincidir com domingo ao menos 1 vez a cada "
                        + (p.getMaxDomingosConsecutivos() + 1) + " semanas (Lei 10.101/2000, art. 6º)."));
                reportado = true;
            }
            anterior = d;
        }
    }

    // ------------------------------------------------------------------ regras por dia (operacional + financeiro)

    private ResumoDia resumirDia(LocalDate d, List<Turno> turnosDia, ContextoCalculo ctx, List<Violacao> out) {
        CapacidadeDia cap = motor.capacidade(d, ctx);
        ParametrosOperacionais p = ctx.parametros();

        int operadores = (int) turnosDia.stream().map(t -> t.getFuncionario().getId()).distinct().count();
        BigDecimal custo = motor.custoEscala(d, turnosDia, ctx);

        boolean abaixoMinimo = false;
        List<ResumoSetor> setores = new ArrayList<>();
        for (Setor setor : ctx.setoresAtivos()) {
            List<Turno> doSetor = turnosDia.stream()
                    .filter(t -> setor.getId().equals(t.getFuncionario().getSetor().getId()))
                    .toList();
            int ops = (int) doSetor.stream().map(t -> t.getFuncionario().getId()).distinct().count();
            int necessarios = cap.minimoPorSetor().getOrDefault(setor.getId(), 0);
            PlanejadorDemanda.PlanoSetor plano = cap.planos().get(setor.getId());
            int piso = plano == null ? necessarios : plano.minimoManual();
            List<ResumoDia.CoberturaFaixa> cobertura = cobertura(setor, plano, doSetor, d, out);
            setores.add(new ResumoSetor(setor.getId(), setor.getNome(), setor.getCor(), ops, necessarios,
                    motor.custoEscala(d, doSetor, ctx), cobertura));
            // O piso manual é obrigatório; abaixo do previsto pelo movimento é um alerta (o gestor pode ajustar).
            if (ops < piso) {
                abaixoMinimo = true;
                out.add(new Violacao(Severidade.ERRO, Regra.SETOR_ABAIXO_MINIMO, d, null, null, setor.getId(),
                        setor.getNome() + ": " + ops + " de " + piso + " operadores mínimos (subdimensionamento)."));
            } else if (ops < necessarios) {
                out.add(new Violacao(Severidade.ALERTA, Regra.SETOR_ABAIXO_MINIMO, d, null, null, setor.getId(),
                        setor.getNome() + ": " + ops + " de " + necessarios + " pessoas previstas para o movimento do dia."));
            }
        }

        Severidade severidadeFinanceira = p.getModoFinanceiro() == ParametrosOperacionais.ModoFinanceiro.BLOQUEAR
                ? Severidade.ERRO : Severidade.ALERTA;
        boolean acimaLimite = false;

        if (cap.inviavel()) {
            out.add(new Violacao(Severidade.ALERTA, Regra.PROJECAO_INVIAVEL, d, null, null, null,
                    "Receita projetada de " + Formato.moeda(cap.receitaProjetada()) + " comporta só " + cap.lMax()
                            + " operador(es), abaixo do mínimo operacional de " + cap.lMin() + ". Revise a projeção ou a demanda mínima."));
        }
        // O mínimo operacional é sempre permitido; o bloqueio financeiro atua sobre o que excede max(L_min, L_max).
        int limite = Math.max(cap.lMin(), cap.lMax());
        if (operadores > limite) {
            acimaLimite = true;
            int excedente = operadores - limite;
            BigDecimal desperdicio = cap.custoMedioOperador().multiply(BigDecimal.valueOf(excedente));
            out.add(new Violacao(severidadeFinanceira, Regra.OPERADORES_ACIMA_LMAX, d, null, null, null,
                    operadores + " colaboradores escalados; o máximo é " + cap.lMax() + " para a receita prevista de "
                            + Formato.moeda(cap.receitaProjetada()) + ". Excedente de " + excedente + " (≈ "
                            + Formato.moeda(desperdicio) + " de superdimensionamento)."));
        }
        if (custo.compareTo(cap.tetoFolha()) > 0 && operadores > cap.lMin()) {
            acimaLimite = true;
            out.add(new Violacao(severidadeFinanceira, Regra.CUSTO_ACIMA_TETO, d, null, null, null,
                    "Custo da escala " + Formato.moeda(custo) + " ultrapassa o teto de " + Formato.moeda(cap.tetoFolha())
                            + " (" + p.getPercentualTetoFolha().stripTrailingZeros().toPlainString() + "% da receita projetada) em "
                            + Formato.moeda(custo.subtract(cap.tetoFolha())) + "."));
        }

        Situacao situacao;
        if (acimaLimite) {
            situacao = Situacao.ACIMA_LIMITE;
        } else if (operadores == 0 && cap.lMin() == 0) {
            situacao = Situacao.VAZIO;
        } else if (abaixoMinimo) {
            situacao = Situacao.ABAIXO_MINIMO;
        } else if (cap.inviavel()) {
            situacao = Situacao.INVIAVEL;
        } else {
            situacao = Situacao.OK;
        }

        PrevisaoMovimento.PrevisaoDia previsao = cap.previsao();
        PrevisaoMovimento.FaixaPrevista pico = previsao == null ? null : previsao.pico();
        return new ResumoDia(d, cap.tipoDia().getRotulo(), cap.feriado(), cap.fator(), cap.receitaProjetada(), cap.tetoFolha(),
                custo, cap.custoMedioOperador(), operadores, cap.lMin(), cap.lMax(), situacao, setores,
                previsao == null ? null : previsao.clientes(),
                pico == null || pico.clientes() == 0 ? null : pico.rotulo());
    }

    /** Pessoas do setor presentes em cada faixa de movimento × necessárias; alerta para cada pico descoberto. */
    private static List<ResumoDia.CoberturaFaixa> cobertura(Setor setor, PlanejadorDemanda.PlanoSetor plano, List<Turno> doSetor,
                                                           LocalDate d, List<Violacao> out) {
        if (plano == null || plano.faixas().isEmpty()) {
            return List.of();
        }
        List<ResumoDia.CoberturaFaixa> lista = new ArrayList<>();
        for (PlanejadorDemanda.DemandaFaixa f : plano.faixas()) {
            int presentes = (int) doSetor.stream()
                    .filter(t -> PlanejadorDemanda.cobre(t.getHoraInicio(), t.getHoraFim(), f.inicio(), f.fim()))
                    .map(t -> t.getFuncionario().getId()).distinct().count();
            lista.add(new ResumoDia.CoberturaFaixa(f.inicio().toString(), f.fim().toString(), f.clientes(), f.pessoas(), presentes));
            if (presentes < f.pessoas()) {
                out.add(new Violacao(Severidade.ALERTA, Regra.FAIXA_ABAIXO_DEMANDA, d, null, null, setor.getId(),
                        setor.getNome() + " " + f.inicio() + "–" + f.fim() + ": " + presentes + " de " + f.pessoas()
                                + " pessoas para ≈ " + f.clientes() + " clientes previstos (pico descoberto)."));
            }
        }
        return lista;
    }

    // ------------------------------------------------------------------ auxiliares

    private static Violacao erro(Regra regra, LocalDate d, Funcionario f, String msg) {
        return new Violacao(Severidade.ERRO, regra, d, f.getId(), f.getNome(), f.getSetor() == null ? null : f.getSetor().getId(), msg);
    }

    private static Violacao alerta(Regra regra, LocalDate d, Funcionario f, String msg) {
        return new Violacao(Severidade.ALERTA, regra, d, f.getId(), f.getNome(), f.getSetor() == null ? null : f.getSetor().getId(), msg);
    }

    private record Periodo(LocalDate inicio, LocalDate fim) {
        boolean contem(LocalDate d) {
            return !d.isBefore(inicio) && !d.isAfter(fim);
        }
    }
}
