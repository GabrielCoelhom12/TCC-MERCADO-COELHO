package br.com.coelho.escalas.dominio;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** Período em que o colaborador não pode ser escalado (férias, atestado, folga acordada...). */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Ausencia {

    public enum Motivo { FERIAS, ATESTADO, FOLGA_ACORDADA, OUTRO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    private Funcionario funcionario;

    @Column(nullable = false)
    private LocalDate inicio;

    @Column(nullable = false)
    private LocalDate fim;

    @Enumerated(EnumType.STRING)
    private Motivo motivo = Motivo.OUTRO;

    private String observacao;

    public Long getFuncionarioId() {
        return funcionario == null ? null : funcionario.getId();
    }

    public String getFuncionarioNome() {
        return funcionario == null ? null : funcionario.getNome();
    }

    public boolean cobre(LocalDate data) {
        return !data.isBefore(inicio) && !data.isAfter(fim);
    }
}
