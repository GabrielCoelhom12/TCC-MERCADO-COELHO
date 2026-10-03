package br.com.coelho.escalas.dominio;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

/** Faixa de horário usada no fechamento do dia e na previsão de movimento (ex.: 16h–19h). */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class FaixaHoraria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalTime inicio;

    @Column(nullable = false)
    private LocalTime fim;

    public FaixaHoraria(LocalTime inicio, LocalTime fim) {
        this.inicio = inicio;
        this.fim = fim;
    }
}
