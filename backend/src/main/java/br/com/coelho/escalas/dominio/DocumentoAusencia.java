package br.com.coelho.escalas.dominio;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/**
 * Documento anexado a um afastamento (atestado, declaração, certidão...). Pode trazer diagnóstico ou CID, por isso é
 * dado sensível de saúde (LGPD): só a gerência abre, e ele some junto com o afastamento.
 */
@Entity
@Table(indexes = @Index(columnList = "ausencia_id"))
@Getter
@Setter
@NoArgsConstructor
public class DocumentoAusencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Ausencia ausencia;

    @Column(nullable = false, length = 150)
    private String nomeArquivo;

    /** application/pdf ou image/jpeg (imagens são regravadas em JPEG). */
    @Column(nullable = false, length = 40)
    private String tipo;

    private long tamanho;

    @JsonIgnore
    @Column(nullable = false)
    private byte[] conteudo;

    @Column(nullable = false)
    private LocalDateTime enviadoEm;

    /** Login de quem enviou (trilha de auditoria). */
    @Column(nullable = false, length = 100)
    private String enviadoPor;
}
