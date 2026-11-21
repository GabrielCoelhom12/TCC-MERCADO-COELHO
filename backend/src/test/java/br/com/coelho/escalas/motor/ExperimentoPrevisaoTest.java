package br.com.coelho.escalas.motor;

import br.com.coelho.escalas.dominio.MovimentoDiario;
import br.com.coelho.escalas.servico.CalendarioFeriados;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Experimento (não roda no build): mede o erro da previsão contra um histórico real de vendas diárias, refazendo a
 * previsão de cada dia só com o que veio antes dele. Os dados reais da loja são confidenciais e <b>não ficam no
 * repositório</b> (o .gitignore bloqueia planilhas); o arquivo é passado na linha de comando:
 *
 * <pre>mvn test -Dtest=ExperimentoPrevisaoTest -Dvendas=C:/caminho/vendas.csv [-Ddesde=2025-01-01]</pre>
 *
 * Formato esperado (exportação do PDV, separada por ";"): {@code Data;Venda Bruta;Venda Cancelada;Valor Desconto;
 * Venda Líquida;Qtde. Cupom;Cupom Médio;Qtde. Cancelado}, com a data como "dd/MM/yyyy - DIA". Dias sem venda líquida
 * (testes do PDV, loja fechada) e a linha de totais são ignorados. Só o erro é impresso, nunca os valores.
 * Ver docs/experimento-previsao.md.
 */
@EnabledIfSystemProperty(named = "vendas", matches = ".+")
class ExperimentoPrevisaoTest {

    /** Janela de histórico usada pelo sistema (ContextoService.DIAS_DE_HISTORICO). */
    private static final int JANELA = 400;
    /** Perfil horário hipotético (o arquivo só traz o total do dia; o erro medido é o do total). */
    private static final double[] DIA_UTIL = {.12, .20, .18, .30, .20};
    private static final double[] SABADO = {.15, .28, .25, .22, .10};
    private static final double[] DOMINGO = {.45, .55, 0, 0, 0};

    @Test
    void erroDaPrevisaoContraOHistoricoReal() throws Exception {
        Cenario base = new Cenario();
        DateTimeFormatter formato = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        for (String linha : Files.readAllLines(Path.of(System.getProperty("vendas")))) {
            String[] c = linha.split(";");
            if (c.length < 8 || c[0].length() < 10 || !Character.isDigit(c[0].charAt(0))) {
                continue; // cabeçalho e totais
            }
            double liquida = Double.parseDouble(c[4]);
            if (liquida <= 0 || Double.parseDouble(c[5]) <= Double.parseDouble(c[7])) {
                continue; // dia sem venda (testes do PDV, cancelamentos)
            }
            LocalDate data = LocalDate.parse(c[0].substring(0, 10), formato);
            int clientes = (int) (Double.parseDouble(c[5]) - Double.parseDouble(c[7])); // cupons válidos
            double[] perfil = data.getDayOfWeek() == DayOfWeek.SUNDAY ? DOMINGO
                    : data.getDayOfWeek() == DayOfWeek.SATURDAY ? SABADO : DIA_UTIL;
            int[] porFaixa = Arrays.stream(perfil).mapToInt(p -> (int) Math.round(clientes * p)).toArray();
            base.movimento(data, String.format(Locale.ROOT, "%.2f", liquida), porFaixa);
        }
        assertThat(base.movimentos).as("nenhum dia válido no arquivo").isNotEmpty();
        int primeiroAno = base.movimentos.stream().mapToInt(m -> m.getData().getYear()).min().orElseThrow();
        int ultimoAno = base.movimentos.stream().mapToInt(m -> m.getData().getYear()).max().orElseThrow();
        for (int ano = primeiroAno; ano <= ultimoAno + 1; ano++) {
            CalendarioFeriados.doAno(ano).forEach(f -> base.feriados.put(f.getData(), f));
        }
        LocalDate desde = LocalDate.parse(System.getProperty("desde", primeiroAno + 1 + "-01-01"));

        PrevisaoMovimento previsao = new PrevisaoMovimento();
        Map<String, double[]> grupos = new TreeMap<>(); // grupo → {soma |erro|, soma erro, dias}
        for (MovimentoDiario m : base.movimentos) {
            LocalDate d = m.getData();
            if (d.isBefore(desde)) {
                continue;
            }
            Cenario c = new Cenario();
            c.feriados.putAll(base.feriados);
            base.movimentos.stream().filter(x -> !x.getData().isBefore(d.minusDays(JANELA)) && !x.getData().isAfter(d))
                    .forEach(c.movimentos::add);
            PrevisaoMovimento.PrevisaoDia p = previsao.prever(d, c.ctx());
            if (p == null) {
                continue;
            }
            double real = m.totalVendas().doubleValue();
            double erro = (p.vendas().doubleValue() - real) / real;
            somar(grupos, "1. todos os dias", erro);
            if (base.feriados.containsKey(d)) {
                somar(grupos, "2. feriados", erro);
            }
            CalendarioSazonal.em(d).filter(x -> !base.feriados.containsKey(d)).ifPresent(x -> {
                somar(grupos, "3. datas comemorativas", erro);
                somar(grupos, "4. " + x.nome(), erro);
            });
        }
        System.out.println("Erro da previsão (MAPE = erro médio absoluto; viés = erro médio com sinal), desde " + desde);
        grupos.forEach((nome, v) -> System.out.printf(Locale.ROOT, "  %-40s dias=%4.0f  MAPE=%5.1f%%  viés=%+5.1f%%%n",
                nome, v[2], 100 * v[0] / v[2], 100 * v[1] / v[2]));
    }

    private static void somar(Map<String, double[]> grupos, String grupo, double erro) {
        double[] v = grupos.computeIfAbsent(grupo, k -> new double[3]);
        v[0] += Math.abs(erro);
        v[1] += erro;
        v[2]++;
    }
}
