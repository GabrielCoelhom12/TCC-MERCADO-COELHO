package br.com.coelho.escalas.dominio;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"conversa_id", "usuario_id"}))
@Getter
@Setter
@NoArgsConstructor
public class ParticipanteConversa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Conversa conversa;

    @ManyToOne(optional = false)
    private Usuario usuario;

    /** Última mensagem que o participante leu (mensagens com id maior contam como não lidas). */
    private Long ultimaMensagemLidaId;

    @Column(nullable = false)
    private LocalDateTime entrouEm = LocalDateTime.now();

    public ParticipanteConversa(Conversa conversa, Usuario usuario) {
        this.conversa = conversa;
        this.usuario = usuario;
    }
}
