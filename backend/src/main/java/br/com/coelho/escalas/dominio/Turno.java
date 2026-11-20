package br.com.coelho.escalas.dominio;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** Alocação de um colaborador em um dia da escala (uma célula da matriz). */
@Entity
@Table(indexes = @Index(columnList = "data"))
@Getter
@Setter
@NoArgsConstructor
public class Turno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Escala escala;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    private Funcionario funcionario;

    /**
     * Setor que o turno cobre. Nulo nos turnos antigos, que valem para o setor principal do colaborador (ver
     * {@link #setorEfetivo()}); diferente do principal quando a pessoa cobre outro setor pelas habilidades.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    private Setor setor;

    @Column(nullable = false)
    private LocalDate data;

    @Column(nullable = false)
    private LocalTime horaInicio;

    /** Se menor ou igual à hora de início, o turno termina no dia seguinte. */
    @Column(nullable = false)
    private LocalTime horaFim;

    private int intervaloMinutos;

    /** Sigla/nome do modelo de origem, para exibição. */
    private String rotulo;

    public Turno(Funcionario funcionario, LocalDate data, LocalTime inicio, LocalTime fim, int intervalo, String rotulo) {
        this.funcionario = funcionario;
        this.data = data;
        this.horaInicio = inicio;
        this.horaFim = fim;
        this.intervaloMinutos = intervalo;
        this.rotulo = rotulo;
    }

    public static Turno de(TurnoModelo modelo, Funcionario funcionario, LocalDate data) {
        return new Turno(funcionario, data, modelo.getHoraInicio(), modelo.getHoraFim(),
                modelo.getIntervaloMinutos(), modelo.getSigla());
    }

    /** Turno do modelo no setor informado (o principal do colaborador ou um das habilidades). */
    public static Turno de(TurnoModelo modelo, Funcionario funcionario, LocalDate data, Setor setor) {
        Turno t = de(modelo, funcionario, data);
        t.setSetor(setor);
        return t;
    }

    /** O setor coberto: o informado no turno ou, se não houver, o principal do colaborador. */
    public Setor setorEfetivo() {
        return setor != null ? setor : funcionario == null ? null : funcionario.getSetor();
    }

    /** Se o turno cobre o setor (comparando pelo id). */
    public boolean doSetor(Long setorId) {
        Setor s = setorEfetivo();
        return s != null && s.getId() != null && s.getId().equals(setorId);
    }

    public LocalDateTime inicio() {
        return data.atTime(horaInicio);
    }

    public LocalDateTime fim() {
        LocalDateTime fim = data.atTime(horaFim);
        return horaFim.isAfter(horaInicio) ? fim : fim.plusDays(1);
    }

    public long duracaoBrutaMinutos() {
        return Duration.between(inicio(), fim()).toMinutes();
    }

    /** H_{i,d}: horas efetivamente trabalhadas (descontado o intervalo intrajornada). */
    public BigDecimal horasTrabalhadas() {
        long minutos = Math.max(0, duracaoBrutaMinutos() - intervaloMinutos);
        return BigDecimal.valueOf(minutos).divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);
    }
}
