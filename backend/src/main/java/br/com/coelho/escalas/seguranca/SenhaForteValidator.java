package br.com.coelho.escalas.seguranca;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class SenhaForteValidator implements ConstraintValidator<SenhaForte, String> {

    public static final int MINIMO = 8;
    /** O BCrypt ignora o que passa de 72 bytes; 64 caracteres mantém margem para acentos (2 bytes em UTF-8). */
    public static final int MAXIMO = 64;

    private static final Pattern MAIUSCULA = Pattern.compile("\\p{Lu}");
    private static final Pattern MINUSCULA = Pattern.compile("\\p{Ll}");
    private static final Pattern NUMERO = Pattern.compile("\\p{N}");
    private static final Pattern ESPECIAL = Pattern.compile("[^\\p{L}\\p{N}\\s]");

    @Override
    public boolean isValid(String senha, ConstraintValidatorContext ctx) {
        if (senha == null) {
            return true;
        }
        return senha.length() >= MINIMO && senha.length() <= MAXIMO
                && MAIUSCULA.matcher(senha).find()
                && MINUSCULA.matcher(senha).find()
                && NUMERO.matcher(senha).find()
                && ESPECIAL.matcher(senha).find();
    }
}
