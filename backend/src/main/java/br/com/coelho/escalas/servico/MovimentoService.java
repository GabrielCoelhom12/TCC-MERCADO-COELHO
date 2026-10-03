package br.com.coelho.escalas.servico;

import br.com.coelho.escalas.dominio.FaixaHoraria;
import br.com.coelho.escalas.dominio.MovimentoDiario;
import br.com.coelho.escalas.dominio.MovimentoFaixa;
import br.com.coelho.escalas.motor.CapacidadeDia;
import br.com.coelho.escalas.motor.ContextoCalculo;
import br.com.coelho.escalas.motor.MotorFinanceiro;
import br.com.coelho.escalas.motor.PlanejadorDemanda;
import br.com.coelho.escalas.motor.PrevisaoMovimento;
import br.com.coelho.escalas.repositorio.FaixaHorariaRepository;
import br.com.coelho.escalas.repositorio.MovimentoDiarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Fechamento do dia (movimento por faixa de horário) e consulta da previsão que ele alimenta. */
@Service
public class MovimentoService {

    public record Faixa(LocalTime inicio, LocalTime fim) {
    }

    public record FaixaLancada(LocalTime inicio, LocalTime fim, int clientes, BigDecimal vendas) {
    }

    public record Fechamento(LocalDate data, List<FaixaLancada> faixas, int clientes, BigDecimal vendas, String observacao,
                             String registradoPor, LocalDateTime registradoEm) {
    }

    public record DemandaSetor(Long setorId, String nome, String cor, int minimoManual, int necessarios,
                               List<PlanejadorDemanda.DemandaFaixa> faixas, List<String> turnosSugeridos) {
    }

    public record PrevisaoDiaria(LocalDate data, String tipoDia, String feriado, BigDecimal receitaProjetada,
                                 CapacidadeDia.OrigemReceita origemReceita, PrevisaoMovimento.PrevisaoDia previsao,
                                 List<DemandaSetor> setores) {
    }

    static final List<Faixa> FAIXAS_PADRAO = List.of(
            new Faixa(LocalTime.of(7, 0), LocalTime.of(10, 0)),
            new Faixa(LocalTime.of(10, 0), LocalTime.of(13, 0)),
            new Faixa(LocalTime.of(13, 0), LocalTime.of(16, 0)),
            new Faixa(LocalTime.of(16, 0), LocalTime.of(19, 0)),
            new Faixa(LocalTime.of(19, 0), LocalTime.of(22, 0)));
    private static final int MAX_FAIXAS = 12;
    private static final int MAX_DIAS_PREVISAO = 31;

    private final FaixaHorariaRepository faixas;
    private final MovimentoDiarioRepository movimentos;
    private final ContextoService contextos;
    private final MotorFinanceiro motor;

    public MovimentoService(FaixaHorariaRepository faixas, MovimentoDiarioRepository movimentos, ContextoService contextos,
                            MotorFinanceiro motor) {
        this.faixas = faixas;
        this.movimentos = movimentos;
        this.contextos = contextos;
        this.motor = motor;
    }

    // ------------------------------------------------------------------ faixas

    /** Faixas configuradas; na primeira leitura cria as padrão (7h às 22h, de 3 em 3 horas). */
    @Transactional
    public List<FaixaHoraria> faixas() {
        List<FaixaHoraria> lista = faixas.findAllByOrderByInicioAsc();
        if (!lista.isEmpty()) {
            return lista;
        }
        FAIXAS_PADRAO.forEach(f -> faixas.save(new FaixaHoraria(f.inicio(), f.fim())));
        return faixas.findAllByOrderByInicioAsc();
    }

    @Transactional
    public List<FaixaHoraria> substituirFaixas(List<Faixa> novas) {
        validarFaixas(novas, "Informe ao menos uma faixa de horário.");
        if (novas.size() > MAX_FAIXAS) {
            throw new RegraNegocioException("Use no máximo " + MAX_FAIXAS + " faixas de horário.");
        }
        faixas.deleteAllInBatch();
        novas.stream().sorted(Comparator.comparing(Faixa::inicio))
                .forEach(f -> faixas.save(new FaixaHoraria(f.inicio(), f.fim())));
        return faixas.findAllByOrderByInicioAsc();
    }

    // ------------------------------------------------------------------ fechamentos

    @Transactional(readOnly = true)
    public List<Fechamento> fechamentos(LocalDate inicio, LocalDate fim) {
        return movimentos.findByDataBetweenOrderByDataDesc(inicio, fim).stream().map(MovimentoService::dto).toList();
    }

    @Transactional(readOnly = true)
    public Fechamento fechamento(LocalDate data) {
        return movimentos.findByData(data).map(MovimentoService::dto)
                .orElseThrow(() -> new NaoEncontradoException("Fechamento do dia", data));
    }

    /** Lança (ou corrige) o fechamento de um dia: substitui as faixas anteriores. */
    @Transactional
    public Fechamento registrar(LocalDate data, List<FaixaLancada> lancadas, String observacao, String usuario) {
        if (data.isAfter(LocalDate.now())) {
            throw new RegraNegocioException("Não é possível lançar o fechamento de um dia futuro.");
        }
        validarFaixas(lancadas.stream().map(f -> new Faixa(f.inicio(), f.fim())).toList(), "Lance o movimento de ao menos uma faixa.");
        MovimentoDiario m = movimentos.findByData(data).orElseGet(() -> new MovimentoDiario(data));
        m.getFaixas().clear();
        lancadas.stream().sorted(Comparator.comparing(FaixaLancada::inicio))
                .forEach(f -> m.getFaixas().add(new MovimentoFaixa(f.inicio(), f.fim(), f.clientes(), f.vendas())));
        m.setObservacao(observacao == null || observacao.isBlank() ? null : observacao.trim());
        m.setRegistradoPor(usuario);
        m.setRegistradoEm(LocalDateTime.now());
        return dto(movimentos.save(m));
    }

    @Transactional
    public void remover(LocalDate data) {
        movimentos.findByData(data).ifPresent(movimentos::delete);
    }

    // ------------------------------------------------------------------ previsão

    @Transactional(readOnly = true)
    public List<PrevisaoDiaria> previsao(LocalDate inicio, LocalDate fim) {
        if (fim.isBefore(inicio) || ChronoUnit.DAYS.between(inicio, fim) >= MAX_DIAS_PREVISAO) {
            throw new RegraNegocioException("Período inválido (máximo de " + MAX_DIAS_PREVISAO + " dias).");
        }
        ContextoCalculo ctx = contextos.carregar(inicio, fim);
        List<PrevisaoDiaria> dias = new ArrayList<>();
        for (LocalDate d = inicio; !d.isAfter(fim); d = d.plusDays(1)) {
            CapacidadeDia cap = motor.capacidade(d, ctx);
            List<DemandaSetor> setores = ctx.setoresAtivos().stream().map(s -> {
                PlanejadorDemanda.PlanoSetor p = cap.planos().get(s.getId());
                return new DemandaSetor(s.getId(), s.getNome(), s.getCor(), p.minimoManual(), p.necessarios(), p.faixas(),
                        p.turnosSugeridos().stream().map(t -> t.getSigla()).toList());
            }).toList();
            dias.add(new PrevisaoDiaria(d, cap.tipoDia().getRotulo(), cap.feriado(), cap.receitaProjetada(), cap.origemReceita(),
                    cap.previsao(), setores));
        }
        return dias;
    }

    // ------------------------------------------------------------------ auxiliares

    private static void validarFaixas(List<Faixa> lista, String mensagemVazia) {
        if (lista == null || lista.isEmpty()) {
            throw new RegraNegocioException(mensagemVazia);
        }
        List<Faixa> ordenadas = lista.stream().sorted(Comparator.comparing(Faixa::inicio)).toList();
        for (int i = 0; i < ordenadas.size(); i++) {
            Faixa f = ordenadas.get(i);
            if (!f.inicio().isBefore(f.fim())) {
                throw new RegraNegocioException("Faixa inválida: " + f.inicio() + "–" + f.fim() + " (o início deve ser antes do fim).");
            }
            if (i > 0 && f.inicio().isBefore(ordenadas.get(i - 1).fim())) {
                throw new RegraNegocioException("Há faixas sobrepostas (" + ordenadas.get(i - 1).inicio() + "–"
                        + ordenadas.get(i - 1).fim() + " e " + f.inicio() + "–" + f.fim() + ").");
            }
        }
    }

    private static Fechamento dto(MovimentoDiario m) {
        return new Fechamento(m.getData(),
                m.faixasOrdenadas().stream().map(f -> new FaixaLancada(f.getInicio(), f.getFim(), f.getClientes(), f.getVendas())).toList(),
                m.totalClientes(), m.totalVendas(), m.getObservacao(), m.getRegistradoPor(), m.getRegistradoEm());
    }
}
