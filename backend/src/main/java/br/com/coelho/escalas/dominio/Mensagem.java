package br.com.coelho.escalas.dominio;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(indexes = @Index(name = "idx_mensagem_conversa", columnList = "conversa_id, id"))
@Getter
@Setter
@NoArgsConstructor
public class Mensagem {

    public static final int MAX_TEXTO = 4000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Conversa conversa;

    @ManyToOne(optional = false)
    private Usuario autor;

    @Column(length = MAX_TEXTO)
    private String texto;

    /** Imagem anexada (tabela separada para não carregar os bytes junto com o histórico). */
    private Long imagemId;

    /** Escala mencionada; o nome é guardado para a mensagem continuar legível se a escala for excluída. */
    private Long escalaId;

    private String escalaNome;

    /** Mensagem automática de chamada de voz: resultado (ATENDIDA, RECUSADA, PERDIDA) e duração. */
    private String chamadaResultado;

    private Integer chamadaDuracaoSegundos;

    @Column(nullable = false)
    private LocalDateTime enviadaEm = LocalDateTime.now();
}
