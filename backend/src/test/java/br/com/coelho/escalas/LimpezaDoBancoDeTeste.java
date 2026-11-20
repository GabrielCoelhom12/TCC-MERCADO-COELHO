package br.com.coelho.escalas;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Nos testes, o banco é apagado e recriado pelas migrações do Flyway a cada execução: os testes de integração rodam
 * sobre exatamente o esquema que a produção recebe (e uma migração quebrada derruba o build).
 */
@Configuration
@Profile("test")
class LimpezaDoBancoDeTeste {

    @Bean
    FlywayMigrationStrategy limparEMigrar() {
        return flyway -> {
            flyway.clean();
            flyway.migrate();
        };
    }
}
