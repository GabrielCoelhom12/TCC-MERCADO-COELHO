package br.com.coelho.escalas.dominio;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Bytes de uma imagem enviada no chat (JPEG já normalizado, ver ImagemChat). */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class AnexoImagem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long conversaId;

    @Column(nullable = false)
    private byte[] dados;

    public AnexoImagem(Long conversaId, byte[] dados) {
        this.conversaId = conversaId;
        this.dados = dados;
    }
}
