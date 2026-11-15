package br.com.coelho.escalas.motor;

import br.com.coelho.escalas.dominio.Ausencia;
import br.com.coelho.escalas.dominio.Funcionario;
import br.com.coelho.escalas.dominio.Setor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static br.com.coelho.escalas.motor.Cenario.SEGUNDA;
import static org.assertj.core.api.Assertions.assertThat;

class RegrasFeriasTest {

    private Cenario c;
    private Setor caixa;
    private Funcionario ana;
    private final List<Ausencia> ausencias = new ArrayList<>();
    private Set<LocalDate> feriados = Set.of();

    @BeforeEach
    void setUp() {
        c = new Cenario();
        caixa = c.setor("Caixa", 0);
        ana = c.funcionario("Ana", caixa, "10");
    }

    private AnaliseAusencia analisar(LocalDate inicio, LocalDate fim) {
        return RegrasFerias.analisar(ana, inicio, fim, ausencias, feriados);
    }

    private Ausencia ausencia(Funcionario f, LocalDate inicio, LocalDate fim, Ausencia.Motivo motivo) {
        Ausencia a = new Ausencia();
        a.setFuncionario(f);
        a.setInicio(inicio);
        a.setFim(fim);
        a.setMotivo(motivo);
        ausencias.add(a);
        return a;
    }

    @Test
    void periodoRegularContaOsDiasEORetorno() {
        var a = analisar(SEGUNDA, SEGUNDA.plusDays(29));

        assertThat(a.dias()).isEqualTo(30);
        assertThat(a.retorno()).isEqualTo(SEGUNDA.plusDays(30));
        assertThat(a.erros()).isEmpty();
        assertThat(a.alertas()).isEmpty();
        assertThat(a.valida()).isTrue();
    }

    @Test
    void fimAntesDoInicioEhRecusado() {
        var a = analisar(SEGUNDA.plusDays(3), SEGUNDA);
        assertThat(a.valida()).isFalse();
        assertThat(a.erros()).singleElement().asString().contains("posterior ao início");
    }

    @Test
    void periodoTemDeTerEntreCincoETrintaDias() {
        assertThat(analisar(SEGUNDA, SEGUNDA.plusDays(3)).erros()).singleElement().asString().contains("ao menos 5 dias");
        assertThat(analisar(SEGUNDA, SEGUNDA.plusDays(4)).erros()).isEmpty();
        assertThat(analisar(SEGUNDA, SEGUNDA.plusDays(30)).erros()).singleElement().asString().contains("30 dias");
    }

    @Test
    void naoPodeComecarNosDoisDiasAntesDoDomingoOuDeFeriado() {
        LocalDate sexta = SEGUNDA.plusDays(4);
        assertThat(analisar(sexta, sexta.plusDays(9)).erros()).singleElement().asString().contains("art. 134, §3º");
        assertThat(analisar(sexta.plusDays(1), sexta.plusDays(10)).erros()).hasSize(1);

        LocalDate quinta = SEGUNDA.plusDays(3);
        assertThat(analisar(quinta, quinta.plusDays(9)).erros()).isEmpty();
        feriados = Set.of(SEGUNDA.plusDays(4)); // feriado na sexta: quarta e quinta ficam vedadas
        assertThat(analisar(quinta, quinta.plusDays(9)).erros()).hasSize(1);
        assertThat(analisar(SEGUNDA.plusDays(2), SEGUNDA.plusDays(11)).erros()).hasSize(1);
    }

    @Test
    void naoPodeSobreporOutraAusenciaDoColaborador() {
        ausencia(ana, SEGUNDA.plusDays(10), SEGUNDA.plusDays(12), Ausencia.Motivo.ATESTADO);
        ausencia(c.funcionario("Bruno", caixa, "10"), SEGUNDA, SEGUNDA.plusDays(20), Ausencia.Motivo.ATESTADO);

        var a = analisar(SEGUNDA, SEGUNDA.plusDays(14));

        assertThat(a.erros()).singleElement().asString().contains("atestado").contains("15/10");
    }

    @Test
    void ignoraAPropriaAusenciaQuandoEditada() {
        Ausencia propria = ausencia(ana, SEGUNDA, SEGUNDA.plusDays(9), Ausencia.Motivo.FERIAS);
        propria.setId(99L);

        var a = RegrasFerias.analisar(ana, SEGUNDA, SEGUNDA.plusDays(14), ausencias, feriados, 99L);

        assertThat(a.erros()).isEmpty();
    }

    @Test
    void colaboradorInativoNaoTiraFerias() {
        ana.setAtivo(false);
        assertThat(analisar(SEGUNDA, SEGUNDA.plusDays(9)).erros()).singleElement().asString().contains("inativo");
    }

    @Test
    void alertaColegasDoSetorAusentesNoMesmoPeriodo() {
        ausencia(c.funcionario("Bruno", caixa, "10"), SEGUNDA.plusDays(5), SEGUNDA.plusDays(20), Ausencia.Motivo.FERIAS);
        ausencia(c.funcionario("Davi", c.setor("Açougue", 0), "10"), SEGUNDA, SEGUNDA.plusDays(20), Ausencia.Motivo.FERIAS);

        var a = analisar(SEGUNDA, SEGUNDA.plusDays(9));

        assertThat(a.valida()).isTrue();
        assertThat(a.alertas()).singleElement().asString().contains("Bruno").doesNotContain("Davi");
    }
}
