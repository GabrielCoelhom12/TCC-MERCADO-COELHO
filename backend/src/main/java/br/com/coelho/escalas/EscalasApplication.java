package br.com.coelho.escalas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class EscalasApplication {

    /** Fuso da loja (Joinville/SC). */
    static final String FUSO = "America/Sao_Paulo";

    public static void main(String[] args) {
        // O servidor (contêiner) roda em UTC: sem isso, "hoje" viraria amanhã às 21h, a geração das 6h rodaria às 3h
        // e o horário de emissão do PDF sairia 3 horas adiantado.
        TimeZone.setDefault(TimeZone.getTimeZone(FUSO));
        SpringApplication.run(EscalasApplication.class, args);
    }
}
