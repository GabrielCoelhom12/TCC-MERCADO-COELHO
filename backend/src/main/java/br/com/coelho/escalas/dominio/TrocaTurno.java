package br.com.coelho.escalas.dominio;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Registro de uma troca de colaborador em um turno (trilha de auditoria). Guarda o dia e o horário em vez de apontar
 * para o turno, porque o turno pode ser apagado depois (escala gerada de novo ou limpa) e a troca deve continuar no
 * histórico. Some junto com a escala, que só pode ser excluída enquanto não está aprovada.
 */
@Entity
@Table(indexes = {@Index(columnList = "escala_id"), @Index(columnList = "data")})
@Getter
@NoArgsConstructor
public class TrocaTurno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Escala escala;

    @Column(nullable = false)
    private LocalDate data;

    @Column(nullable = false)
    private LocalTime horaInicio;

    @Column(nullable = false)
    private LocalTime horaFim;

    /** Quem estava escalado e saiu do turno. */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Funcionario titular;

    /** Quem entrou no lugar. */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Funcionario substituto;

    /** Troca de urgência: feita com a escala já aprovada, sem reabri-la. */
    private boolean urgencia;

    @Column(nullable = false)
    private LocalDateTime registradaEm;

    /** Login de quem fez a troca. */
    @Column(nullable = false, length = 100)
    private String registradaPor;

    public TrocaTurno(Turno turno, Funcionario titular, Funcionario substituto, boolean urgencia, String registradaPor) {
        this.escala = turno.getEscala();
        this.data = turno.getData();
        this.horaInicio = turno.getHoraInicio();
        this.horaFim = turno.getHoraFim();
        this.titular = titular;
        this.substituto = substituto;
        this.urgencia = urgencia;
        this.registradaEm = LocalDateTime.now();
        this.registradaPor = registradaPor;
    }
}
