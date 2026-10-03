package br.com.coelho.escalas.dominio;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;

/** Chamada de voz entre os dois participantes de uma conversa privada. O áudio trafega direto entre os navegadores. */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Chamada {

    public enum Status { TOCANDO, EM_ANDAMENTO, ENCERRADA, RECUSADA, PERDIDA }

    public static final Set<Status> ATIVAS = EnumSet.of(Status.TOCANDO, Status.EM_ANDAMENTO);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Conversa conversa;

    @ManyToOne(optional = false)
    private Usuario chamador;

    @ManyToOne(optional = false)
    private Usuario destinatario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.TOCANDO;

    @Column(nullable = false)
    private LocalDateTime iniciadaEm = LocalDateTime.now();

    private LocalDateTime atendidaEm;

    private LocalDateTime encerradaEm;

    public boolean ativa() {
        return ATIVAS.contains(status);
    }

    public boolean participa(Usuario u) {
        return chamador.getId().equals(u.getId()) || destinatario.getId().equals(u.getId());
    }

    public Usuario outroLado(Usuario u) {
        return chamador.getId().equals(u.getId()) ? destinatario : chamador;
    }

    /** Duração da conversa (do atendimento ao fim), em segundos; nula se não foi atendida. */
    public Integer duracaoSegundos() {
        return atendidaEm == null || encerradaEm == null ? null : (int) Duration.between(atendidaEm, encerradaEm).toSeconds();
    }
}
