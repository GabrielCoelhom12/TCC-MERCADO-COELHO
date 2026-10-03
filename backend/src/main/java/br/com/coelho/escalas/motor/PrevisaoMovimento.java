package br.com.coelho.escalas.motor;

import br.com.coelho.escalas.dominio.MovimentoDiario;
import br.com.coelho.escalas.dominio.MovimentoFaixa;
import br.com.coelho.escalas.dominio.TipoDia;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.function.Predicate;

/**
 * Previsão de movimento (vendas e clientes, por faixa de horário) aprendida dos fechamentos diários.
 *
 * <p>Método explicável, em vez de um modelo opaco:</p>
 * <ol>
 *   <li><b>Base:</b> média ponderada das últimas 8 ocorrências do mesmo dia da semana (a mais recente pesa mais),
 *       com cada ocorrência "limpa" dos efeitos conhecidos (véspera de feriado, início do mês);</li>
 *   <li><b>Efeitos:</b> véspera de feriado e início do mês (dias 1 a 10, pagamento), medidos no próprio histórico
 *       contra dias normais do mesmo dia da semana. A véspera usa +15% enquanto houver poucas vésperas; o início do
 *       mês só é aplicado quando há evidência (4 ou mais dias);</li>
 *   <li><b>Feriados:</b> média dos feriados anteriores ou, sem eles, dos domingos;</li>
 *   <li><b>Faixas:</b> os clientes previstos são distribuídos conforme o perfil horário das mesmas ocorrências.</li>
 * </ol>
 * Cada previsão traz a lista de motivos ({@link PrevisaoDia#explicacao()}) para o gestor entender o número.
 */
public class PrevisaoMovimento {

    public record FaixaPrevista(LocalTime inicio, LocalTime fim, int clientes) {
        @JsonIgnore
        public String rotulo() {
            return inicio + "–" + fim;
        }
    }

    /**
     * @param amostras ocorrências do histórico usadas na base
     */
    public record PrevisaoDia(LocalDate data, BigDecimal vendas, int clientes, List<FaixaPrevista> faixas, int amostras,
                              List<String> explicacao) {
        /** Faixa com mais clientes previstos (a primeira, em empate). */
        @JsonProperty("pico")
        public FaixaPrevista pico() {
            return faixas.stream().max(Comparator.comparingInt(FaixaPrevista::clientes)
                    .thenComparing(FaixaPrevista::inicio, Comparator.reverseOrder())).orElse(null);
        }
    }

    static final int MAX_AMOSTRAS = 8;
    static final int DIA_LIMITE_PAGAMENTO = 10;
    static final BigDecimal EFEITO_VESPERA_PADRAO = new BigDecimal("1.15");
    private static final int MIN_VESPERAS = 2;
    private static final int MIN_DIAS_PAGAMENTO = 4;
    /** Efeitos aprendidos menores que 3% são tratados como ruído. */
    private static final double EFEITO_MINIMO = 0.03;

    private record Medida(double vendas, double clientes) {
        Medida multiplicar(double fator) {
            return new Medida(vendas * fator, clientes * fator);
        }
    }

    private record Efeito(double fator, int amostras, boolean padrao) {
    }

    public PrevisaoDia prever(LocalDate alvo, ContextoCalculo ctx) {
        List<MovimentoDiario> historico = ctx.movimentos().stream()
                .filter(m -> m.getData().isBefore(alvo) && (m.totalClientes() > 0 || m.totalVendas().signum() > 0))
                .sorted(Comparator.comparing(MovimentoDiario::getData).reversed())
                .toList();
        if (historico.isEmpty()) {
            return null;
        }
        Set<LocalDate> feriados = ctx.feriados().keySet();
        Predicate<LocalDate> ehFeriado = feriados::contains;
        Predicate<LocalDate> ehVespera = d -> !ehFeriado.test(d) && feriados.contains(d.plusDays(1));
        Predicate<LocalDate> ehPagamento = d -> d.getDayOfMonth() <= DIA_LIMITE_PAGAMENTO;

        Efeito vespera = efeito(historico, ehVespera, ehFeriado, ehVespera, ehPagamento, MIN_VESPERAS, EFEITO_VESPERA_PADRAO);
        Efeito pagamento = efeito(historico, ehPagamento, ehFeriado, ehVespera, ehPagamento, MIN_DIAS_PAGAMENTO, null);

        List<String> motivos = new ArrayList<>();
        List<MovimentoDiario> amostras;
        Medida base;

        if (ehFeriado.test(alvo)) {
            List<MovimentoDiario> anteriores = historico.stream().filter(m -> ehFeriado.test(m.getData())).limit(4).toList();
            if (!anteriores.isEmpty()) {
                amostras = anteriores;
                base = media(anteriores, m -> 1.0);
                motivos.add("Feriado: média " + (anteriores.size() == 1 ? "do último feriado" : "dos últimos " + anteriores.size() + " feriados")
                        + " (" + Formato.moeda(dinheiro(base.vendas())) + " e " + Math.round(base.clientes()) + " clientes).");
            } else {
                amostras = mesmoDia(historico, DayOfWeek.SUNDAY, ehFeriado);
                if (amostras.isEmpty()) {
                    amostras = historico.stream().limit(MAX_AMOSTRAS).toList();
                }
                base = media(amostras, m -> 1.0);
                motivos.add("Feriado sem histórico de feriados: usada a média dos domingos.");
            }
        } else {
            DayOfWeek dia = alvo.getDayOfWeek();
            amostras = mesmoDia(historico, dia, ehFeriado);
            String plural = plural(dia);
            if (amostras.isEmpty()) {
                amostras = historico.stream().filter(m -> !ehFeriado.test(m.getData())).limit(MAX_AMOSTRAS).toList();
                if (amostras.isEmpty()) {
                    amostras = historico.stream().limit(MAX_AMOSTRAS).toList();
                }
                base = media(amostras, m -> ajuste(m.getData(), ehVespera, ehPagamento, vespera, pagamento));
                motivos.add("Ainda não há " + plural + " no histórico: usada a média geral dos dias recentes.");
            } else {
                base = media(amostras, m -> ajuste(m.getData(), ehVespera, ehPagamento, vespera, pagamento));
                boolean masculino = dia == DayOfWeek.SATURDAY || dia == DayOfWeek.SUNDAY;
                String quais = amostras.size() == 1
                        ? (masculino ? "do último " : "da última ") + singular(dia)
                        : (masculino ? "dos últimos " : "das últimas ") + amostras.size() + " " + plural;
                motivos.add("Média " + quais + ": " + Formato.moeda(dinheiro(base.vendas())) + " e "
                        + Math.round(base.clientes()) + " clientes.");
            }
            if (ehVespera.test(alvo)) {
                base = base.multiplicar(vespera.fator());
                motivos.add("Véspera de feriado: " + percentual(vespera.fator()) + (vespera.padrao()
                        ? " (padrão; ainda há poucas vésperas no histórico)."
                        : " (aprendido de " + vespera.amostras() + " vésperas)."));
            }
            if (ehPagamento.test(alvo) && pagamento != null) {
                base = base.multiplicar(pagamento.fator());
                motivos.add("Início do mês (pagamento): " + percentual(pagamento.fator())
                        + " (aprendido de " + pagamento.amostras() + " dias).");
            }
        }

        int clientes = (int) Math.round(base.clientes());
        List<FaixaPrevista> faixas = distribuir(clientes, amostras);
        PrevisaoDia previsao = new PrevisaoDia(alvo, dinheiro(base.vendas()), clientes, faixas, amostras.size(), motivos);
        FaixaPrevista pico = previsao.pico();
        if (pico != null && pico.clientes() > 0) {
            motivos.add("Pico previsto: " + pico.rotulo() + " (≈ " + pico.clientes() + " clientes).");
        }
        return previsao;
    }

    // ------------------------------------------------------------------ efeitos

    /**
     * Mede um efeito (ex.: véspera) comparando os dias que o têm com a base "normal" do mesmo dia da semana.
     * Retorna o padrão (ou nulo) se houver poucas amostras, e nulo se o efeito medido for desprezível.
     */
    private static Efeito efeito(List<MovimentoDiario> historico, Predicate<LocalDate> temEfeito, Predicate<LocalDate> ehFeriado,
                                 Predicate<LocalDate> ehVespera, Predicate<LocalDate> ehPagamento, int minimo, BigDecimal padrao) {
        Predicate<LocalDate> normal = d -> !ehFeriado.test(d) && !ehVespera.test(d) && !ehPagamento.test(d);
        List<Double> razoes = new ArrayList<>();
        for (MovimentoDiario m : historico) {
            LocalDate d = m.getData();
            if (ehFeriado.test(d) || !temEfeito.test(d)) {
                continue;
            }
            List<MovimentoDiario> normais = historico.stream()
                    .filter(x -> x.getData().getDayOfWeek() == d.getDayOfWeek() && normal.test(x.getData()))
                    .limit(MAX_AMOSTRAS).toList();
            if (normais.isEmpty()) {
                continue;
            }
            double baseNormal = media(normais, x -> 1.0).vendas();
            if (baseNormal > 0) {
                razoes.add(m.totalVendas().doubleValue() / baseNormal);
            }
        }
        if (razoes.size() < minimo) {
            return padrao == null ? null : new Efeito(padrao.doubleValue(), razoes.size(), true);
        }
        Collections.sort(razoes);
        int meio = razoes.size() / 2;
        double mediana = razoes.size() % 2 == 1 ? razoes.get(meio) : (razoes.get(meio - 1) + razoes.get(meio)) / 2;
        return Math.abs(mediana - 1) < EFEITO_MINIMO ? null : new Efeito(mediana, razoes.size(), false);
    }

    /** Fator pelo qual dividir uma ocorrência passada para tirar dela os efeitos conhecidos. */
    private static double ajuste(LocalDate d, Predicate<LocalDate> ehVespera, Predicate<LocalDate> ehPagamento,
                                 Efeito vespera, Efeito pagamento) {
        double fator = 1;
        if (vespera != null && ehVespera.test(d)) {
            fator *= vespera.fator();
        }
        if (pagamento != null && ehPagamento.test(d)) {
            fator *= pagamento.fator();
        }
        return fator;
    }

    // ------------------------------------------------------------------ base e faixas

    private static List<MovimentoDiario> mesmoDia(List<MovimentoDiario> historico, DayOfWeek dia, Predicate<LocalDate> ehFeriado) {
        return historico.stream()
                .filter(m -> m.getData().getDayOfWeek() == dia && !ehFeriado.test(m.getData()))
                .limit(MAX_AMOSTRAS)
                .toList();
    }

    /** Média ponderada pela recência (a primeira da lista é a mais recente e tem o maior peso). */
    private static Medida media(List<MovimentoDiario> amostras, java.util.function.ToDoubleFunction<MovimentoDiario> divisor) {
        double somaPesos = 0;
        double vendas = 0;
        double clientes = 0;
        int n = amostras.size();
        for (int i = 0; i < n; i++) {
            MovimentoDiario m = amostras.get(i);
            double peso = n - i;
            double fator = divisor.applyAsDouble(m);
            vendas += peso * m.totalVendas().doubleValue() / fator;
            clientes += peso * m.totalClientes() / fator;
            somaPesos += peso;
        }
        return new Medida(vendas / somaPesos, clientes / somaPesos);
    }

    /** Distribui os clientes previstos pelas faixas na proporção observada nas amostras (maiores restos). */
    private static List<FaixaPrevista> distribuir(int clientes, List<MovimentoDiario> amostras) {
        Map<String, LocalTime[]> faixas = new TreeMap<>();
        Map<String, Double> peso = new HashMap<>();
        double total = 0;
        for (MovimentoDiario m : amostras) {
            for (MovimentoFaixa f : m.getFaixas()) {
                String chave = f.getInicio() + "-" + f.getFim();
                faixas.putIfAbsent(chave, new LocalTime[]{f.getInicio(), f.getFim()});
                peso.merge(chave, (double) f.getClientes(), Double::sum);
                total += f.getClientes();
            }
        }
        List<String> chaves = new ArrayList<>(faixas.keySet());
        int[] valores = new int[chaves.size()];
        double[] restos = new double[chaves.size()];
        int distribuidos = 0;
        for (int i = 0; i < chaves.size(); i++) {
            double exato = total == 0 ? 0 : clientes * peso.get(chaves.get(i)) / total;
            valores[i] = (int) Math.floor(exato);
            restos[i] = exato - valores[i];
            distribuidos += valores[i];
        }
        Integer[] ordem = new Integer[chaves.size()];
        for (int i = 0; i < ordem.length; i++) {
            ordem[i] = i;
        }
        Arrays.sort(ordem, (a, b) -> Double.compare(restos[b], restos[a]));
        for (int k = 0; total > 0 && distribuidos < clientes; k = (k + 1) % ordem.length) {
            valores[ordem[k]]++;
            distribuidos++;
        }
        List<FaixaPrevista> lista = new ArrayList<>();
        for (int i = 0; i < chaves.size(); i++) {
            LocalTime[] f = faixas.get(chaves.get(i));
            lista.add(new FaixaPrevista(f[0], f[1], valores[i]));
        }
        return lista;
    }

    // ------------------------------------------------------------------ texto

    private static BigDecimal dinheiro(double valor) {
        return BigDecimal.valueOf(valor).setScale(2, RoundingMode.HALF_UP);
    }

    private static String percentual(double fator) {
        long p = Math.round((fator - 1) * 100);
        return (p >= 0 ? "+" : "") + p + "%";
    }

    private static String singular(DayOfWeek dia) {
        return TipoDia.de(dia).getRotulo().toLowerCase();
    }

    private static String plural(DayOfWeek dia) {
        return singular(dia) + "s";
    }
}
