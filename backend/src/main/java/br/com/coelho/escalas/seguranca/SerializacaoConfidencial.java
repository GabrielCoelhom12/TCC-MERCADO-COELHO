package br.com.coelho.escalas.seguranca;

import br.com.coelho.escalas.dominio.Confidencial;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Módulo do Jackson (registrado sozinho pelo Spring Boot) que escreve {@code null} no lugar dos campos
 * {@link Confidencial} quando quem pediu não é ADMIN nem GESTOR. Na dúvida (sem usuário autenticado), oculta.
 */
@Component
public class SerializacaoConfidencial extends SimpleModule {

    static final Set<String> PODEM_VER = Set.of("ROLE_ADMIN", "ROLE_GESTOR");

    public SerializacaoConfidencial() {
        super("SerializacaoConfidencial");
        setSerializerModifier(new BeanSerializerModifier() {
            @Override
            public List<BeanPropertyWriter> changeProperties(SerializationConfig config, BeanDescription bean,
                                                             List<BeanPropertyWriter> propriedades) {
                return propriedades.stream()
                        .map(p -> p.getAnnotation(Confidencial.class) == null ? p : new Oculta(p))
                        .collect(java.util.stream.Collectors.toList());
            }
        });
    }

    static boolean podeVer() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(PODEM_VER::contains);
    }

    /** Mesma propriedade, mas com valor nulo para quem não pode vê-la. */
    static final class Oculta extends BeanPropertyWriter {

        Oculta(BeanPropertyWriter base) {
            super(base);
        }

        @Override
        public void serializeAsField(Object bean, JsonGenerator gen, SerializerProvider prov) throws Exception {
            if (podeVer()) {
                super.serializeAsField(bean, gen, prov);
            } else {
                gen.writeNullField(getName());
            }
        }
    }
}
