package br.com.coelho.escalas.dominio;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Conversa do chat: privada entre dois usuários (DIRETA) ou em grupo com nome. */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Conversa {

    public enum Tipo { DIRETA, GRUPO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Tipo tipo;

    /** Somente para grupos; o nome de uma conversa direta é o do outro participante. */
    private String nome;

    /** Conversas diretas: "menorId:maiorId", garante uma única conversa por par de usuários. */
    @Column(unique = true)
    private String chaveDireta;

    @ManyToOne
    private Usuario criadaPor;

    @Column(nullable = false)
    private LocalDateTime criadaEm = LocalDateTime.now();

    /** Momento da última atividade, usado para ordenar a lista de conversas. */
    @Column(nullable = false)
    private LocalDateTime atualizadaEm = LocalDateTime.now();

    public static String chaveDireta(long a, long b) {
        return Math.min(a, b) + ":" + Math.max(a, b);
    }
}
