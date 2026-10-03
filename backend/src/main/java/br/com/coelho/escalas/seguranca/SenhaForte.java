package br.com.coelho.escalas.seguranca;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Política de senha: 8 a 64 caracteres com letra maiúscula, minúscula, número e caractere especial.
 * Nulo é aceito (senha opcional na edição); combine com @NotBlank quando for obrigatória.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = SenhaForteValidator.class)
public @interface SenhaForte {

    String message() default "deve ter de 8 a 64 caracteres, com letra maiúscula, letra minúscula, número e caractere especial";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
