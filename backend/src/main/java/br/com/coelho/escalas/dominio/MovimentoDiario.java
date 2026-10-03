package br.com.coelho.escalas.dominio;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Fechamento do dia: clientes atendidos e vendas por faixa de horário. É a base da previsão de movimento. */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class MovimentoDiario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private LocalDate data;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "movimento_faixa", joinColumns = @JoinColumn(name = "movimento_id"))
    @OrderBy("inicio")
    private List<MovimentoFaixa> faixas = new ArrayList<>();

    private String observacao;

    private String registradoPor;

    private LocalDateTime registradoEm;

    public MovimentoDiario(LocalDate data) {
        this.data = data;
    }

    public int totalClientes() {
        return faixas.stream().mapToInt(MovimentoFaixa::getClientes).sum();
    }

    public BigDecimal totalVendas() {
        return faixas.stream().map(MovimentoFaixa::getVendas).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public List<MovimentoFaixa> faixasOrdenadas() {
        return faixas.stream().sorted(Comparator.comparing(MovimentoFaixa::getInicio)).toList();
    }
}
