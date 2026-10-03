package br.com.coelho.escalas.dominio;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Projeção de faturamento padrão (R_proj) por perfil de dia. */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class ProjecaoReceita {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private TipoDia tipoDia;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal valor;

    public ProjecaoReceita(TipoDia tipoDia, BigDecimal valor) {
        this.tipoDia = tipoDia;
        this.valor = valor;
    }
}
