package br.com.coelho.escalas.dominio;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Usuario {

    public enum Perfil { ADMIN, GESTOR, SUPERVISOR }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String login;

    @Column(nullable = false)
    private String senhaHash;

    private String nome;

    @Enumerated(EnumType.STRING)
    private Perfil perfil = Perfil.GESTOR;

    /** Usuários desativados não conseguem entrar; o registro é mantido para auditoria (ex.: quem aprovou escalas). */
    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean ativo = true;

    /** Foto de perfil já normalizada (JPEG 256×256, ver FotoPerfil). */
    private byte[] foto;

    public Usuario(String login, String senhaHash, String nome, Perfil perfil) {
        this.login = login;
        this.senhaHash = senhaHash;
        this.nome = nome;
        this.perfil = perfil;
    }
}
