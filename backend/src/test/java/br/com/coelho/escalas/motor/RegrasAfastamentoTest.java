package br.com.coelho.escalas.motor;

import br.com.coelho.escalas.dominio.Ausencia;
import br.com.coelho.escalas.dominio.Ausencia.Motivo;
import br.com.coelho.escalas.dominio.Funcionario;
import br.com.coelho.escalas.dominio.Setor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static br.com.coelho.escalas.motor.Cenario.SEGUNDA;
import static org.assertj.core.api.Assertions.assertThat;

class RegrasAfastamentoTest {

    private Cenario c;
    private Setor caixa;
    private Funcionario ana;
    private final List<Ausencia> ausencias = new ArrayList<>();

    @BeforeEach
    void setUp() {
        c = new Cenario();
        caixa = c.setor("Caixa", 0);
        ana = c.funcionario("Ana", caixa, "10");
    }

    private AnaliseAusencia analisar(Motivo motivo, LocalDate inicio, LocalDate fim) {
        return RegrasAfastamento.analisar(ana, motivo, inicio, fim, ausencias, null);
    }

    private void ausencia(Funcionario f, LocalDate inicio, LocalDate fim, Motivo motivo) {
        Ausencia a = new Ausencia();
        a.setFuncionario(f);
        a.setInicio(inicio);
        a.setFim(fim);
        a.setMotivo(motivo);
        ausencias.add(a);
    }

    @Test
    void atestadoDeUmDiaEmQualquerDiaDaSemana() {
        LocalDate sexta = SEGUNDA.plusDays(4);
        var a = analisar(Motivo.ATESTADO, sexta, sexta);

        assertThat(a.valida()).isTrue();
        assertThat(a.dias()).isEqualTo(1);
        assertThat(a.retorno()).isEqualTo(sexta.plusDays(1));
        assertThat(a.alertas()).isEmpty();
    }

    @Test
    void atestadoAcimaDeQuinzeDiasLembraDoInss() {
        assertThat(analisar(Motivo.ATESTADO, SEGUNDA, SEGUNDA.plusDays(14)).alertas()).isEmpty();
        assertThat(analisar(Motivo.ATESTADO, SEGUNDA, SEGUNDA.plusDays(15)).alertas())
                .singleElement().asString().contains("INSS").contains("art. 60");
    }

    @Test
    void licencaMaternidadeMenorQue120DiasGeraAlerta() {
        assertThat(analisar(Motivo.LICENCA_MATERNIDADE, SEGUNDA, SEGUNDA.plusDays(119)).alertas()).isEmpty();
        assertThat(analisar(Motivo.LICENCA_MATERNIDADE, SEGUNDA, SEGUNDA.plusDays(89)).alertas())
                .singleElement().asString().contains("120 dias").contains("art. 392");
    }

    @Test
    void lutoECasamentoAlemDoQueACltGarante() {
        assertThat(analisar(Motivo.LUTO, SEGUNDA, SEGUNDA.plusDays(1)).alertas()).isEmpty();
        assertThat(analisar(Motivo.LUTO, SEGUNDA, SEGUNDA.plusDays(4)).alertas()).singleElement().asString().contains("art. 473, I");
        assertThat(analisar(Motivo.CASAMENTO, SEGUNDA, SEGUNDA.plusDays(2)).alertas()).isEmpty();
        assertThat(analisar(Motivo.CASAMENTO, SEGUNDA, SEGUNDA.plusDays(6)).alertas()).singleElement().asString().contains("art. 473, II");
    }

    @Test
    void feriasTemOperacaoPropria() {
        assertThat(analisar(Motivo.FERIAS, SEGUNDA, SEGUNDA.plusDays(9)).erros())
                .singleElement().asString().contains("operação de Férias");
        assertThat(analisar(null, SEGUNDA, SEGUNDA).erros()).singleElement().asString().contains("tipo de afastamento");
    }

    @Test
    void periodoInvalidoOuLongoDemais() {
        assertThat(analisar(Motivo.ATESTADO, SEGUNDA.plusDays(2), SEGUNDA).erros())
                .singleElement().asString().contains("posterior ao início");
        assertThat(analisar(Motivo.AFASTAMENTO_INSS, SEGUNDA, SEGUNDA.plusDays(729)).valida()).isTrue();
        assertThat(analisar(Motivo.AFASTAMENTO_INSS, SEGUNDA, SEGUNDA.plusDays(730)).erros())
                .singleElement().asString().contains("730 dias");
    }

    @Test
    void naoSobrepoeOutraAusenciaEAvisaColegasDoSetor() {
        ausencia(ana, SEGUNDA.plusDays(3), SEGUNDA.plusDays(12), Motivo.FERIAS);
        ausencia(c.funcionario("Bruno", caixa, "10"), SEGUNDA, SEGUNDA.plusDays(2), Motivo.ATESTADO);

        var sobreposta = analisar(Motivo.ATESTADO, SEGUNDA, SEGUNDA.plusDays(4));
        assertThat(sobreposta.erros()).singleElement().asString().contains("Já existe férias");

        var livre = analisar(Motivo.ATESTADO, SEGUNDA, SEGUNDA.plusDays(1));
        assertThat(livre.valida()).isTrue();
        assertThat(livre.alertas()).singleElement().asString().contains("Bruno").contains("atestado");
    }

    @Test
    void colaboradorInativo() {
        ana.setAtivo(false);
        assertThat(analisar(Motivo.ATESTADO, SEGUNDA, SEGUNDA).erros()).singleElement().asString().contains("inativo");
    }
}
