package br.com.coelho.escalas.motor;

import br.com.coelho.escalas.dominio.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Fotografia imutável de todos os dados de que o motor precisa. Montada pela camada de serviço
 * a partir do banco; nos testes, montada à mão — o motor não conhece JPA.
 */
public record ContextoCalculo(
        ParametrosOperacionais parametros,
        List<Setor> setores,
        List<Funcionario> funcionarios,
        Map<LocalDate, Feriado> feriados,
        Map<TipoDia, BigDecimal> projecaoPorTipo,
        Map<LocalDate, BigDecimal> projecaoPorData,
        List<Ausencia> ausencias,
        /** Fechamentos diários (histórico de movimento) usados na previsão. */
        List<MovimentoDiario> movimentos,
        /** Modelos de turno, para planejar a cobertura das faixas de movimento. */
        List<TurnoModelo> modelos
) {
    /** Contexto sem histórico de movimento: a demanda e a receita vêm só da configuração manual. */
    public ContextoCalculo(ParametrosOperacionais parametros, List<Setor> setores, List<Funcionario> funcionarios,
                           Map<LocalDate, Feriado> feriados, Map<TipoDia, BigDecimal> projecaoPorTipo,
                           Map<LocalDate, BigDecimal> projecaoPorData, List<Ausencia> ausencias) {
        this(parametros, setores, funcionarios, feriados, projecaoPorTipo, projecaoPorData, ausencias, List.of(), List.of());
    }

    public List<Setor> setoresAtivos() {
        return setores.stream().filter(Setor::isAtivo).toList();
    }

    public List<Funcionario> funcionariosAtivos() {
        return funcionarios.stream().filter(Funcionario::isAtivo).toList();
    }

    public boolean ausente(Funcionario funcionario, LocalDate data) {
        return ausencias.stream().anyMatch(a -> a.getFuncionario().getId().equals(funcionario.getId()) && a.cobre(data));
    }

    public Ausencia ausencia(Funcionario funcionario, LocalDate data) {
        return ausencias.stream()
                .filter(a -> a.getFuncionario().getId().equals(funcionario.getId()) && a.cobre(data))
                .findFirst().orElse(null);
    }
}
