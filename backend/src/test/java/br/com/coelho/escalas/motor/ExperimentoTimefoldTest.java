package br.com.coelho.escalas.motor;

import br.com.coelho.escalas.dominio.Funcionario;
import br.com.coelho.escalas.dominio.Setor;
import br.com.coelho.escalas.dominio.Turno;
import br.com.coelho.escalas.motor.otimizacao.OtimizadorEscala;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Experimento reproduzível (docs/experimento-otimizacao.md): guloso × Timefold em 30 cenários sorteados com pouca gente,
 * restrições de disponibilidade e férias. Fica fora do build normal (leva ~90 s); para rodar:
 * {@code mvn test -Dtest=ExperimentoTimefoldTest -Dexperimento=true}.
 */
@EnabledIfSystemProperty(named = "experimento", matches = "true")
class ExperimentoTimefoldTest {

    @Test
    void comparaEmCenariosApertados() {
        MotorFinanceiro motor = new MotorFinanceiro();
        OtimizadorEscala otimizador = new OtimizadorEscala(motor, Duration.ofSeconds(3));
        GeradorEscala guloso = new GeradorEscala(motor);
        ValidadorEscala validador = new ValidadorEscala(motor);
        Random r = new Random(2026); // semente fixa: os mesmos 30 cenários a cada execução
        int vazG = 0, vazO = 0, ganhouO = 0, ganhouG = 0, empate = 0;
        double custoG = 0, custoO = 0;
        System.out.println("cenario;vagas_vazias_guloso;vagas_vazias_timefold;custo_guloso;custo_timefold;erros_guloso;erros_timefold");
        for (int n = 1; n <= 30; n++) {
            Cenario c = new Cenario();
            Setor caixa = c.setor("Caixa", 2 + r.nextInt(2));
            Setor acougue = c.setor("Acougue", 1);
            for (int i = 0; i < 4 + r.nextInt(2); i++) {
                Funcionario f = c.funcionario("C" + i, caixa, String.valueOf(10 + r.nextInt(6)));
                restringir(f, r);
            }
            for (int i = 0; i < 2; i++) {
                Funcionario f = c.funcionario("A" + i, acougue, String.valueOf(12 + r.nextInt(4)));
                restringir(f, r);
            }
            if (r.nextBoolean()) {
                Funcionario ferias = c.funcionarios.get(r.nextInt(c.funcionarios.size()));
                c.ausencia(ferias, Cenario.SEGUNDA.plusDays(r.nextInt(4)), Cenario.SEGUNDA.plusDays(4 + r.nextInt(3)));
            }
            LocalDate ini = Cenario.SEGUNDA;
            LocalDate fim = ini.plusDays(6);
            var g = guloso.gerar(ini, fim, List.of(), c.modelos, c.ctx());
            var o = otimizador.gerar(ini, fim, List.of(), c.modelos, c.ctx());
            int vg = g.deficits().stream().mapToInt(d -> d.necessarios() - d.alocados()).sum();
            int vo = o.deficits().stream().mapToInt(d -> d.necessarios() - d.alocados()).sum();
            double cg = custo(motor, g.turnos(), c), co = custo(motor, o.turnos(), c);
            long eg = validador.validar(ini, fim, g.turnos(), c.ctx()).violacoes().stream().filter(v -> v.erro() && v.funcionarioId() != null).count();
            long eo = validador.validar(ini, fim, o.turnos(), c.ctx()).violacoes().stream().filter(v -> v.erro() && v.funcionarioId() != null).count();
            System.out.printf("%d;%d;%d;%.2f;%.2f;%d;%d%n", n, vg, vo, cg, co, eg, eo);
            vazG += vg;
            vazO += vo;
            custoG += cg;
            custoO += co;
            if (vo < vg) ganhouO++;
            else if (vg < vo) ganhouG++;
            else empate++;
        }
        System.out.printf("TOTAL vagas vazias guloso=%d timefold=%d | timefold cobriu mais em %d, guloso em %d, empate %d | custo medio guloso=%.2f timefold=%.2f%n",
                vazG, vazO, ganhouO, ganhouG, empate, custoG / 30, custoO / 30);
    }

    private static void restringir(Funcionario f, Random r) {
        EnumSet<DayOfWeek> dias = EnumSet.allOf(DayOfWeek.class);
        for (int k = 0; k < r.nextInt(3); k++) {
            dias.remove(DayOfWeek.of(1 + r.nextInt(7)));
        }
        f.setDiasDisponiveis(dias);
    }

    private static double custo(MotorFinanceiro motor, List<Turno> turnos, Cenario c) {
        return turnos.stream().collect(Collectors.groupingBy(Turno::getData)).entrySet().stream()
                .mapToDouble(e -> motor.custoEscala(e.getKey(), e.getValue(), c.ctx()).doubleValue()).sum();
    }
}
