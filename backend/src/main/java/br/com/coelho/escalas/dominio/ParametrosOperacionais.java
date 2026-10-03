package br.com.coelho.escalas.dominio;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Parâmetros únicos (registro id = 1) que regem o motor: fatores de calendário, teto financeiro
 * e regras trabalhistas (CLT / convenção coletiva). Todos configuráveis pela gerência.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class ParametrosOperacionais {

    public enum ModoFinanceiro {
        /** Impede a alocação que ultrapassa L_max / teto da folha do dia. */
        BLOQUEAR,
        /** Permite, mas emite alerta crítico. */
        ALERTAR
    }

    public static final long ID_UNICO = 1L;

    @Id
    private Long id = ID_UNICO;

    // ---- Financeiro ----
    /** F_d para domingos. */
    @Column(precision = 5, scale = 2)
    private BigDecimal fatorDomingo = new BigDecimal("1.50");

    /** F_d padrão para feriados. */
    @Column(precision = 5, scale = 2)
    private BigDecimal fatorFeriado = new BigDecimal("2.00");

    /** Percentual da receita projetada do dia que pode ser comprometido com a folha escalada (capacidade de pagamento). */
    @Column(precision = 5, scale = 2)
    private BigDecimal percentualTetoFolha = new BigDecimal("6.00");

    /** Jornada de referência (h) usada para estimar o custo médio de um operador no cálculo de L_max. */
    @Column(precision = 5, scale = 2)
    private BigDecimal jornadaReferenciaHoras = new BigDecimal("7.33");

    @Enumerated(EnumType.STRING)
    private ModoFinanceiro modoFinanceiro = ModoFinanceiro.BLOQUEAR;

    // ---- Geração automática ----
    /** Cria sozinho, como rascunho, a escala das próximas semanas a partir da previsão de movimento. */
    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean geracaoAutomatica = true;

    /** Quantas semanas à frente devem ter escala (1 = a próxima semana). */
    @Column(nullable = false, columnDefinition = "integer default 1")
    private int semanasAntecedencia = 1;

    // ---- Trabalhista ----
    /** Jornada normal diária (CLT art. 58). Acima disso, alerta de hora extra. */
    @Column(precision = 5, scale = 2)
    private BigDecimal jornadaNormalDiariaHoras = new BigDecimal("8.00");

    /** Jornada máxima diária incluindo extras (CLT art. 59: 8h + 2h). */
    @Column(precision = 5, scale = 2)
    private BigDecimal jornadaMaximaDiariaHoras = new BigDecimal("10.00");

    /** Jornada semanal (CLT art. 58 / CF art. 7º XIII). Acima disso, alerta de hora extra. */
    @Column(precision = 5, scale = 2)
    private BigDecimal jornadaSemanalHoras = new BigDecimal("44.00");

    /** Descanso mínimo entre jornadas (CLT art. 66). */
    @Column(precision = 5, scale = 2)
    private BigDecimal interjornadaMinimaHoras = new BigDecimal("11.00");

    /** Máximo de dias seguidos trabalhados antes do repouso semanal (CLT art. 67). */
    private int maxDiasConsecutivos = 6;

    /** Domingos seguidos permitidos no comércio (Lei 10.101/2000, art. 6º, par. único: folga em domingo a cada 3 semanas). */
    private int maxDomingosConsecutivos = 2;
}
