package br.com.coelho.escalas.servico;

import br.com.coelho.escalas.dominio.Ausencia;
import br.com.coelho.escalas.dominio.Escala;
import br.com.coelho.escalas.dominio.Funcionario;
import br.com.coelho.escalas.dominio.Turno;
import br.com.coelho.escalas.motor.AnaliseAusencia;
import br.com.coelho.escalas.motor.ContextoCalculo;
import br.com.coelho.escalas.motor.SugestorSubstituicao;
import br.com.coelho.escalas.motor.SugestorSubstituicao.Cobertura;
import br.com.coelho.escalas.repositorio.FuncionarioRepository;
import br.com.coelho.escalas.repositorio.TurnoRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cobertura dos turnos de quem se ausenta (férias ou afastamento): cada turno que a pessoa já tinha no período passa a
 * um colega apto ({@link SugestorSubstituicao#planejarCobertura}), inclusive em escala aprovada, e a troca fica anotada
 * na observação da escala. Turnos de dias que já passaram são só retirados: a pessoa não trabalhou e não se sabe quem
 * cobriu.
 */
@Service
public class CoberturaService {

    /** Um turno já escalado no período e quem o assume (substituto nulo: o turno é retirado). */
    public record CoberturaTurno(Long turnoId, Long escalaId, String escalaNome, LocalDate data, LocalTime horaInicio,
                                 LocalTime horaFim, Long substitutoId, String substitutoNome, String substitutoSetor,
                                 boolean passado) {
    }

    /** Prévia de uma ausência: regras (dias, retorno, erros, alertas) e quem cobre cada turno, sem gravar nada. */
    public record Previa(int dias, LocalDate retorno, List<String> erros, List<String> alertas, boolean valida,
                         List<CoberturaTurno> coberturas) {

        static Previa de(AnaliseAusencia analise, List<CoberturaTurno> coberturas) {
            return new Previa(analise.dias(), analise.retorno(), analise.erros(), analise.alertas(), analise.valida(), coberturas);
        }
    }

    public record Registro(Ausencia ausencia, List<CoberturaTurno> coberturas) {
    }

    /** Margem em volta do período para as regras de continuidade na escolha dos substitutos. */
    private static final int MARGEM_DIAS = 7;
    /** Tamanho da coluna de observação da escala. */
    private static final int OBSERVACAO_MAX = 255;
    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd/MM");

    private final TurnoRepository turnos;
    private final FuncionarioRepository funcionarios;
    private final ContextoService contextos;
    private final SugestorSubstituicao sugestor;

    public CoberturaService(TurnoRepository turnos, FuncionarioRepository funcionarios, ContextoService contextos,
                            SugestorSubstituicao sugestor) {
        this.turnos = turnos;
        this.funcionarios = funcionarios;
        this.contextos = contextos;
        this.sugestor = sugestor;
    }

    /** Quem cobriria cada turno do colaborador no período (nada é alterado). */
    public List<CoberturaTurno> planejar(Funcionario titular, LocalDate inicio, LocalDate fim) {
        return plano(titular, inicio, fim).stream().map(c -> dto(c, passou(c.turno()))).toList();
    }

    /** Aplica o plano e anota as trocas nas escalas; devolve o que foi feito, na mesma forma da prévia. */
    public List<CoberturaTurno> repassar(Funcionario titular, Ausencia.Motivo motivo, LocalDate inicio, LocalDate fim) {
        List<CoberturaTurno> feitas = new ArrayList<>();
        Map<Long, Escala> escalas = new LinkedHashMap<>();
        Map<Long, List<String>> notas = new LinkedHashMap<>();
        for (Cobertura c : plano(titular, inicio, fim)) {
            Turno t = c.turno();
            boolean passado = passou(t);
            feitas.add(dto(c, passado));
            escalas.putIfAbsent(t.getEscala().getId(), t.getEscala());
            String dia = DIA_MES.format(t.getData()) + ": " + titular.getNome();
            List<String> daEscala = notas.computeIfAbsent(t.getEscala().getId(), k -> new ArrayList<>());
            if (passado) {
                turnos.delete(t);
                daEscala.add(dia + " não trabalhou (" + motivo.getRotulo() + "), turno retirado");
            } else if (c.substituto() == null) {
                turnos.delete(t);
                daEscala.add(dia + " ausente (" + motivo.getRotulo() + "), turno retirado sem substituto");
            } else {
                t.setFuncionario(funcionarios.findById(c.substituto().funcionarioId())
                        .orElseThrow(() -> new NaoEncontradoException("Colaborador", c.substituto().funcionarioId())));
                daEscala.add(dia + " → " + c.substituto().nome() + " (" + motivo.getRotulo() + ")");
            }
        }
        turnos.flush();
        notas.forEach((id, linhas) -> anotar(escalas.get(id), String.join("; ", linhas) + "."));
        return feitas;
    }

    /** Turnos de dias passados não entram no sorteio de substitutos: só os de hoje em diante. */
    private List<Cobertura> plano(Funcionario titular, LocalDate inicio, LocalDate fim) {
        LocalDate de = inicio.minusDays(MARGEM_DIAS);
        LocalDate ate = fim.plusDays(MARGEM_DIAS);
        List<Turno> janela = turnos.findByDataBetween(de, ate);
        List<Turno> doTitular = janela.stream()
                .filter(t -> t.getFuncionario().getId().equals(titular.getId()) && !t.getData().isBefore(inicio) && !t.getData().isAfter(fim))
                .toList();
        List<Turno> futuros = doTitular.stream().filter(t -> !passou(t)).toList();
        List<Cobertura> plano = new ArrayList<>();
        doTitular.stream().filter(CoberturaService::passou).forEach(t -> plano.add(new Cobertura(t, null)));
        if (!futuros.isEmpty()) {
            ContextoCalculo ctx = contextos.carregar(de, ate);
            plano.addAll(sugestor.planejarCobertura(futuros, janela, ctx));
        }
        plano.sort(Comparator.comparing(c -> c.turno().inicio()));
        return plano;
    }

    private static boolean passou(Turno t) {
        return t.getData().isBefore(LocalDate.now());
    }

    /** A anotação mais recente vem primeiro; o que não couber na coluna é cortado. */
    private static void anotar(Escala e, String nota) {
        String texto = e.getObservacao() == null || e.getObservacao().isBlank() ? nota : nota + " " + e.getObservacao();
        e.setObservacao(texto.length() <= OBSERVACAO_MAX ? texto : texto.substring(0, OBSERVACAO_MAX - 1) + "…");
    }

    private static CoberturaTurno dto(Cobertura c, boolean passado) {
        Turno t = c.turno();
        SugestorSubstituicao.Candidato s = c.substituto();
        return new CoberturaTurno(t.getId(), t.getEscala().getId(), t.getEscala().getNome(), t.getData(), t.getHoraInicio(),
                t.getHoraFim(), s == null ? null : s.funcionarioId(), s == null ? null : s.nome(), s == null ? null : s.setorNome(),
                passado);
    }
}
