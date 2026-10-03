package br.com.coelho.escalas.dominio;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Feriado {

    public enum Abrangencia { NACIONAL, ESTADUAL, MUNICIPAL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private LocalDate data;

    @Column(nullable = false)
    private String descricao;

    @Enumerated(EnumType.STRING)
    private Abrangencia abrangencia = Abrangencia.NACIONAL;

    /** Fator F_d específico deste feriado. Quando nulo, usa o fator padrão de feriado dos parâmetros. */
    @Column(precision = 5, scale = 2)
    private BigDecimal fatorCusto;

    public Feriado(LocalDate data, String descricao, Abrangencia abrangencia) {
        this.data = data;
        this.descricao = descricao;
        this.abrangencia = abrangencia;
    }
}
