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

    /** Tamanho padrão da coluna (VARCHAR 255). */
    public static final int OBSERVACAO_MAX = 255;

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

    /** Acrescenta uma nota à observação, a mais recente primeiro; o que não couber na coluna é cortado. */
    public void anotar(String nota) {
        String texto = observacao == null || observacao.isBlank() ? nota : nota + " " + observacao;
        observacao = texto.length() <= OBSERVACAO_MAX ? texto : texto.substring(0, OBSERVACAO_MAX - 1) + "…";
    }
}
