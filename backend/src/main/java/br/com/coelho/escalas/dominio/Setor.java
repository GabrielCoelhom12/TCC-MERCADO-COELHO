package br.com.coelho.escalas.dominio;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.EnumMap;
import java.util.Map;

/** Setor operacional da loja (caixa, padaria, açougue, reposição...). */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Setor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, unique = true)
    private String nome;

    /** Cor usada na matriz visual (hex). */
    private String cor;

    private boolean ativo = true;

    /** Demanda logística mínima de operadores por perfil de dia — compõe o L_min(d). */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "setor_demanda_minima", joinColumns = @JoinColumn(name = "setor_id"))
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "tipo_dia")
    @Column(name = "minimo_operadores")
    private Map<TipoDia, Integer> minimoPorDia = new EnumMap<>(TipoDia.class);

    /**
     * Capacidade de atendimento: clientes por hora que uma pessoa do setor atende (ex.: frente de caixa). Quando
     * informada, a demanda do setor acompanha a previsão de movimento; vazia, vale só a demanda mínima fixa.
     */
    private Integer clientesPorColaboradorHora;

    public Setor(String nome, String cor) {
        this.nome = nome;
        this.cor = cor;
    }

    public int minimoPara(TipoDia tipo) {
        Integer valor = minimoPorDia.get(tipo);
        return valor == null ? 0 : valor;
    }
}
