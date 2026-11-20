package br.com.coelho.escalas.servico;

import br.com.coelho.escalas.dominio.FaixaHoraria;
import br.com.coelho.escalas.dominio.MovimentoDiario;
import br.com.coelho.escalas.dominio.MovimentoFaixa;
import br.com.coelho.escalas.repositorio.MovimentoDiarioRepository;
import br.com.coelho.escalas.servico.RelatorioPdv.DiaPdv;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * Importa o relatório de vendas diárias do PDV como fechamentos do dia, para a previsão aprender com o movimento real
 * sem que o gestor digite faixa por faixa. O relatório só traz o total do dia; ele é distribuído pelas faixas de
 * horário da loja conforme:
 * <ol>
 *   <li>o fechamento já lançado naquele dia (o gestor digitou as faixas: vale a divisão dele);</li>
 *   <li>senão, a divisão média dos fechamentos lançados à mão nos mesmos dias da semana (dia útil, sábado, domingo);</li>
 *   <li>senão, um perfil horário padrão de supermercado (pico no fim da tarde; domingo só de manhã).</li>
 * </ol>
 * Importar de novo o mesmo período atualiza os dias, sem duplicar.
 */
@Service
public class ImportacaoPdvService {

    /** Observação gravada nos dias importados (identifica que a divisão por faixa foi estimada). */
    public static final String OBSERVACAO_IMPORTADO = "Importado do relatório do PDV";

    public record ResultadoImportacao(int criados, int atualizados, int ignorados, LocalDate primeiroDia, LocalDate ultimoDia,
                                      List<String> avisos) {
    }

    static final int MAX_BYTES = 2 * 1024 * 1024;
    static final int MAX_DIAS = 1100;
    /** Fechamentos lançados à mão usados para estimar a divisão por faixa de cada tipo de dia. */
    private static final int AMOSTRAS_PERFIL = 8;

    private final MovimentoService movimento;
    private final MovimentoDiarioRepository movimentos;

    public ImportacaoPdvService(MovimentoService movimento, MovimentoDiarioRepository movimentos) {
        this.movimento = movimento;
        this.movimentos = movimentos;
    }

    @Transactional
    public ResultadoImportacao importar(byte[] arquivo, String usuario) {
        if (arquivo == null || arquivo.length == 0) {
            throw new RegraNegocioException("Envie o arquivo do relatório de vendas.");
        }
        if (arquivo.length > MAX_BYTES) {
            throw new RegraNegocioException("Arquivo muito grande (máximo de 2 MB).");
        }
        RelatorioPdv.Leitura leitura = RelatorioPdv.ler(arquivo);
        List<String> avisos = new ArrayList<>(leitura.avisos());
        LocalDate hoje = LocalDate.now();

        TreeMap<LocalDate, DiaPdv> porData = new TreeMap<>();
        int ignorados = leitura.avisos().size();
        int semVenda = 0;
        int futuros = 0;
        for (DiaPdv d : leitura.dias()) {
            if (d.data().isAfter(hoje)) {
                futuros++;
            } else if (d.vendaLiquida().signum() <= 0 || d.clientes() <= 0) {
                semVenda++; // testes do PDV, dia só com cancelamentos
            } else {
                porData.put(d.data(), d); // se a data se repetir, vale a última linha
            }
        }
        ignorados += semVenda + futuros;
        if (semVenda > 0) {
            avisos.add(semVenda + (semVenda == 1 ? " dia sem venda líquida foi ignorado" : " dias sem venda líquida foram ignorados")
                    + " (testes do PDV ou só cancelamentos).");
        }
        if (futuros > 0) {
            avisos.add(futuros + (futuros == 1 ? " dia futuro foi ignorado." : " dias futuros foram ignorados."));
        }
        if (porData.isEmpty()) {
            throw new RegraNegocioException("Nenhum dia com venda foi encontrado no arquivo.");
        }
        if (porData.size() > MAX_DIAS) {
            throw new RegraNegocioException("Envie no máximo " + MAX_DIAS + " dias por arquivo (cerca de 3 anos).");
        }

        List<FaixaHoraria> faixas = movimento.faixas();
        LocalDate primeiro = porData.firstKey();
        LocalDate ultimo = porData.lastKey();
        Map<LocalDate, MovimentoDiario> existentes = new HashMap<>();
        movimentos.findByDataBetweenOrderByDataDesc(primeiro, ultimo).forEach(m -> existentes.put(m.getData(), m));
        Map<TipoDeDia, double[]> perfis = perfisLancados(faixas, ultimo);

        int criados = 0;
        int atualizados = 0;
        for (DiaPdv d : porData.values()) {
            MovimentoDiario m = existentes.get(d.data());
            double[] pesos = m != null && lancadoAMao(m) ? pesosDe(m, faixas) : null;
            if (pesos == null) {
                pesos = perfis.getOrDefault(TipoDeDia.de(d.data()), perfilPadrao(faixas, d.data()));
            }
            if (m == null) {
                m = new MovimentoDiario(d.data());
                m.setObservacao(OBSERVACAO_IMPORTADO);
                criados++;
            } else {
                atualizados++;
                if (m.getObservacao() == null || m.getObservacao().isBlank()) {
                    m.setObservacao(OBSERVACAO_IMPORTADO);
                }
            }
            distribuir(m, faixas, pesos, d);
            m.setRegistradoPor(usuario);
            m.setRegistradoEm(LocalDateTime.now());
            movimentos.save(m);
        }
        return new ResultadoImportacao(criados, atualizados, ignorados, primeiro, ultimo, avisos);
    }

    // ------------------------------------------------------------------ distribuição por faixa

    private enum TipoDeDia {
        DIA_UTIL, SABADO, DOMINGO;

        static TipoDeDia de(LocalDate d) {
            return d.getDayOfWeek() == DayOfWeek.SATURDAY ? SABADO : d.getDayOfWeek() == DayOfWeek.SUNDAY ? DOMINGO : DIA_UTIL;
        }
    }

    private static boolean lancadoAMao(MovimentoDiario m) {
        return !OBSERVACAO_IMPORTADO.equals(m.getObservacao()) && m.totalClientes() > 0;
    }

    /** Divisão média (por clientes) dos últimos fechamentos lançados à mão de cada tipo de dia. */
    private Map<TipoDeDia, double[]> perfisLancados(List<FaixaHoraria> faixas, LocalDate ate) {
        Map<TipoDeDia, List<double[]>> amostras = new EnumMap<>(TipoDeDia.class);
        for (MovimentoDiario m : movimentos.findByDataBetweenOrderByDataDesc(ate.minusDays(400), ate)) {
            if (!lancadoAMao(m)) {
                continue;
            }
            double[] p = pesosDe(m, faixas);
            List<double[]> lista = amostras.computeIfAbsent(TipoDeDia.de(m.getData()), k -> new ArrayList<>());
            if (p != null && lista.size() < AMOSTRAS_PERFIL) {
                lista.add(p);
            }
        }
        Map<TipoDeDia, double[]> perfis = new EnumMap<>(TipoDeDia.class);
        amostras.forEach((tipo, lista) -> {
            if (!lista.isEmpty()) {
                double[] media = new double[faixas.size()];
                lista.forEach(p -> {
                    for (int i = 0; i < p.length; i++) {
                        media[i] += p[i] / lista.size();
                    }
                });
                perfis.put(tipo, media);
            }
        });
        return perfis;
    }

    /** Participação de cada faixa da loja nos clientes do fechamento (nulo se as faixas não batem ou não há clientes). */
    private static double[] pesosDe(MovimentoDiario m, List<FaixaHoraria> faixas) {
        double total = m.totalClientes();
        if (total <= 0) {
            return null;
        }
        double[] pesos = new double[faixas.size()];
        double soma = 0;
        for (int i = 0; i < faixas.size(); i++) {
            FaixaHoraria f = faixas.get(i);
            int clientes = m.getFaixas().stream().filter(x -> x.getInicio().equals(f.getInicio())).mapToInt(MovimentoFaixa::getClientes).sum();
            pesos[i] = clientes / total;
            soma += pesos[i];
        }
        return soma > 0.95 ? pesos : null; // faixas configuradas mudaram: a divisão antiga não serve
    }

    /**
     * Perfil horário padrão de supermercado (participação por hora): nos dias úteis o pico é no fim da tarde; no sábado,
     * de manhã e no começo da tarde; no domingo a loja abre só de manhã.
     */
    static double[] perfilPadrao(List<FaixaHoraria> faixas, LocalDate data) {
        double[] pesos = new double[faixas.size()];
        for (int i = 0; i < faixas.size(); i++) {
            FaixaHoraria f = faixas.get(i);
            long minutos = Duration.between(f.getInicio(), f.getFim()).toMinutes();
            if (minutos <= 0) {
                minutos += 24 * 60;
            }
            for (long t = 0; t < minutos; t += 30) {
                pesos[i] += pesoPorHora(f.getInicio().plusMinutes(t), TipoDeDia.de(data)) / 2;
            }
        }
        double soma = Arrays.stream(pesos).sum();
        if (soma <= 0) {
            Arrays.fill(pesos, 1.0 / faixas.size());
            return pesos;
        }
        for (int i = 0; i < pesos.length; i++) {
            pesos[i] /= soma;
        }
        return pesos;
    }

    private static double pesoPorHora(LocalTime h, TipoDeDia tipo) {
        int hora = h.getHour();
        double[] blocos = switch (tipo) { // 7–10, 10–13, 13–16, 16–19, 19–22
            case DIA_UTIL -> new double[]{0.12, 0.20, 0.18, 0.30, 0.20};
            case SABADO -> new double[]{0.15, 0.28, 0.25, 0.22, 0.10};
            case DOMINGO -> new double[]{0.45, 0.55, 0.0, 0.0, 0.0};
        };
        if (hora < 7 || hora >= 22) {
            return 0.0;
        }
        return blocos[(hora - 7) / 3] / 3;
    }

    /** Reparte vendas e clientes do dia pelas faixas: vendas proporcionais, clientes pelos maiores restos. */
    private static void distribuir(MovimentoDiario m, List<FaixaHoraria> faixas, double[] pesos, DiaPdv dia) {
        m.getFaixas().clear();
        int n = faixas.size();
        int[] clientes = new int[n];
        double[] restos = new double[n];
        int distribuidos = 0;
        for (int i = 0; i < n; i++) {
            double exato = dia.clientes() * pesos[i];
            clientes[i] = (int) Math.floor(exato);
            restos[i] = exato - clientes[i];
            distribuidos += clientes[i];
        }
        Integer[] ordem = new Integer[n];
        for (int i = 0; i < n; i++) {
            ordem[i] = i;
        }
        Arrays.sort(ordem, (a, b) -> Double.compare(restos[b], restos[a]));
        for (int k = 0; k < dia.clientes() - distribuidos; k++) {
            clientes[ordem[k % n]]++;
        }

        BigDecimal restante = dia.vendaLiquida();
        int ultimaComPeso = -1;
        for (int i = 0; i < n; i++) {
            if (pesos[i] > 0) {
                ultimaComPeso = i;
            }
        }
        for (int i = 0; i < n; i++) {
            BigDecimal vendas = i == ultimaComPeso ? restante
                    : dia.vendaLiquida().multiply(BigDecimal.valueOf(pesos[i])).setScale(2, RoundingMode.HALF_UP);
            restante = restante.subtract(i == ultimaComPeso ? BigDecimal.ZERO : vendas);
            FaixaHoraria f = faixas.get(i);
            m.getFaixas().add(new MovimentoFaixa(f.getInicio(), f.getFim(), clientes[i], vendas.max(BigDecimal.ZERO)));
        }
    }
}
