package br.com.coelho.escalas.dominio;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Locale;

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

    @JsonIgnore
    @Column(nullable = false)
    private String senhaHash;

    /** Tokens emitidos antes deste instante deixam de valer: trocar a senha encerra as sessões abertas. */
    @JsonIgnore
    private Instant senhaAlteradaEm;

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

    /** Logins são únicos sem diferenciar maiúsculas de minúsculas nem espaços nas pontas. */
    public static String normalizarLogin(String login) {
        return login.trim().toLowerCase(Locale.ROOT);
    }
}
