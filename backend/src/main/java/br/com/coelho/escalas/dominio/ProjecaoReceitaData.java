package br.com.coelho.escalas.dominio;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Projeção pontual para uma data (início de mês, véspera de feriado, promoções...). Sobrepõe a padrão. */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class ProjecaoReceitaData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private LocalDate data;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal valor;

    private String observacao;

    public ProjecaoReceitaData(LocalDate data, BigDecimal valor, String observacao) {
        this.data = data;
        this.valor = valor;
        this.observacao = observacao;
    }
}
