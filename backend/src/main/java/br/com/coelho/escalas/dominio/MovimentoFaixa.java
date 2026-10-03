package br.com.coelho.escalas.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * Movimento de uma faixa de horário no fechamento do dia. Guarda os horários da faixa (e não uma referência à
 * configuração) para o histórico continuar válido se as faixas forem alteradas.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class MovimentoFaixa {

    @Column(nullable = false)
    private LocalTime inicio;

    @Column(nullable = false)
    private LocalTime fim;

    @Column(nullable = false)
    private int clientes;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal vendas = BigDecimal.ZERO;

    public MovimentoFaixa(LocalTime inicio, LocalTime fim, int clientes, BigDecimal vendas) {
        this.inicio = inicio;
        this.fim = fim;
        this.clientes = clientes;
        this.vendas = vendas;
    }
}
