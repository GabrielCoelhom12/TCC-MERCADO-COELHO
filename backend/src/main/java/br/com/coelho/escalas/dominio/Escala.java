package br.com.coelho.escalas.dominio;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Escala {

    public enum Status { RASCUNHO, APROVADA }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false)
    private LocalDate dataInicio;

    @Column(nullable = false)
    private LocalDate dataFim;

    @Enumerated(EnumType.STRING)
    private Status status = Status.RASCUNHO;

    private String observacao;

    /** Criada pelo sistema (rascunho semanal automático), e não por um gestor. */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean geradaAutomaticamente;

    private LocalDateTime criadaEm = LocalDateTime.now();

    private LocalDateTime aprovadaEm;

    private String aprovadaPor;

    public boolean editavel() {
        return status == Status.RASCUNHO;
    }

    public boolean contem(LocalDate data) {
        return !data.isBefore(dataInicio) && !data.isAfter(dataFim);
    }
}
