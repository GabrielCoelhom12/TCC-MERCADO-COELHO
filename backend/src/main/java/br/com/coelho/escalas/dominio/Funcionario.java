package br.com.coelho.escalas.dominio;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Set;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Funcionario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    @Column(unique = true)
    private String matricula;

    private String cargo;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    private Setor setor;

    /** Salário bruto mensal (R$). */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal salarioMensal;

    /** Carga horária mensal contratual (220h para 44h semanais). */
    private int cargaHorariaMensal = 220;

    /** Encargos sobre a folha (INSS patronal, FGTS, provisões de férias/13º...), em %. */
    @Column(precision = 6, scale = 2)
    private BigDecimal percentualEncargos = BigDecimal.ZERO;

    private boolean ativo = true;

    /** Disponibilidade individual: dias da semana em que o colaborador pode ser escalado. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "funcionario_disponibilidade", joinColumns = @JoinColumn(name = "funcionario_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "dia_semana")
    private Set<DayOfWeek> diasDisponiveis = EnumSet.allOf(DayOfWeek.class);

    /** Foto já normalizada (JPEG 256×256, ver FotoPerfil). Exposta no JSON como data URL em "foto". */
    @JsonIgnore
    private byte[] foto;

    @JsonProperty("foto")
    public String getFotoDataUrl() {
        return foto == null ? null : "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(foto);
    }

    /**
     * S_i do modelo: custo do salário-hora com encargos.
     * S_i = salárioMensal / cargaHoráriaMensal × (1 + encargos/100)
     */
    public BigDecimal getCustoHora() {
        if (salarioMensal == null || cargaHorariaMensal <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal encargos = percentualEncargos == null ? BigDecimal.ZERO : percentualEncargos;
        BigDecimal multiplicador = BigDecimal.ONE.add(encargos.movePointLeft(2));
        return salarioMensal
                .divide(BigDecimal.valueOf(cargaHorariaMensal), 6, RoundingMode.HALF_UP)
                .multiply(multiplicador)
                .setScale(4, RoundingMode.HALF_UP);
    }

    public boolean disponivelEm(DayOfWeek dia) {
        return diasDisponiveis == null || diasDisponiveis.isEmpty() || diasDisponiveis.contains(dia);
    }
}
