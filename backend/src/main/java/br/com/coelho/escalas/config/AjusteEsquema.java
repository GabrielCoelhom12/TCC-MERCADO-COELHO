package br.com.coelho.escalas.config;

import br.com.coelho.escalas.dominio.Ausencia;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Ajustes de esquema que o {@code ddl-auto: update} do Hibernate não faz. Ele cria, junto com a tabela, uma restrição
 * CHECK com os valores do enum, mas nunca a atualiza: num banco criado antes dos novos tipos de ausência, gravar uma
 * licença-maternidade falharia. A cada início, a restrição é recriada com os valores atuais do enum.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AjusteEsquema implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    public AjusteEsquema(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        String valores = Arrays.stream(Ausencia.Motivo.values())
                .map(m -> "'" + m.name() + "'")
                .collect(Collectors.joining(", "));
        jdbc.execute("alter table ausencia drop constraint if exists ausencia_motivo_check");
        jdbc.execute("alter table ausencia add constraint ausencia_motivo_check check (motivo in (" + valores + "))");
    }
}
