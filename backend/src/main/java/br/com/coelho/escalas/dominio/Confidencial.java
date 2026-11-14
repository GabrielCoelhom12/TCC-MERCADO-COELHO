package br.com.coelho.escalas.dominio;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Dado de remuneração individual (salário, encargos, custo por hora ou por turno de uma pessoa). Só os perfis ADMIN e
 * GESTOR o recebem; para os demais, a API o envia como {@code null} (LGPD: necessidade e minimização). A regra fica em
 * {@code seguranca.SerializacaoConfidencial}, para o domínio não depender da segurança.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface Confidencial {
}
