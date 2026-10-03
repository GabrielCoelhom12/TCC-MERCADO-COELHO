package br.com.coelho.escalas.dominio;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Duration;
import java.time.LocalTime;

/** Modelo de turno reutilizável na matriz (ex.: Abertura 07:00–15:20). */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class TurnoModelo {

    public enum Aplicacao { DIAS_UTEIS, DOMINGOS_FERIADOS, TODOS }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    /** Sigla curta exibida na célula da matriz. */
    private String sigla;

    @Column(nullable = false)
    private LocalTime horaInicio;

    @Column(nullable = false)
    private LocalTime horaFim;

    private int intervaloMinutos;

    @Enumerated(EnumType.STRING)
    private Aplicacao aplicacao = Aplicacao.TODOS;

    public TurnoModelo(String nome, String sigla, LocalTime inicio, LocalTime fim, int intervalo, Aplicacao aplicacao) {
        this.nome = nome;
        this.sigla = sigla;
        this.horaInicio = inicio;
        this.horaFim = fim;
        this.intervaloMinutos = intervalo;
        this.aplicacao = aplicacao;
    }

    public double getHoras() {
        long bruto = Duration.between(horaInicio, horaFim).toMinutes();
        if (bruto <= 0) {
            bruto += 24 * 60;
        }
        return (bruto - intervaloMinutos) / 60.0;
    }

    public boolean aplicavel(boolean domingoOuFeriado) {
        return switch (aplicacao) {
            case TODOS -> true;
            case DIAS_UTEIS -> !domingoOuFeriado;
            case DOMINGOS_FERIADOS -> domingoOuFeriado;
        };
    }
}
