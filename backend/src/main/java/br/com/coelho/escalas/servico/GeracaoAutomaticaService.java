package br.com.coelho.escalas.servico;

import br.com.coelho.escalas.dominio.Escala;
import br.com.coelho.escalas.dominio.ParametrosOperacionais;
import br.com.coelho.escalas.repositorio.EscalaRepository;
import br.com.coelho.escalas.repositorio.ParametrosRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * Rascunho automático: garante que as próximas semanas tenham escala, criando e gerando (pela previsão de movimento)
 * as que faltam. Nunca altera escalas existentes — o gestor revisa, ajusta e aprova o rascunho.
 */
@Service
public class GeracaoAutomaticaService {

    private static final Logger log = LoggerFactory.getLogger(GeracaoAutomaticaService.class);

    private final boolean habilitada;
    private final ParametrosRepository parametros;
    private final EscalaRepository escalas;
    private final EscalaService escalaService;

    public GeracaoAutomaticaService(@Value("${app.escala.geracao-automatica.habilitada:true}") boolean habilitada,
                                    ParametrosRepository parametros, EscalaRepository escalas, EscalaService escalaService) {
        this.habilitada = habilitada;
        this.parametros = parametros;
        this.escalas = escalas;
        this.escalaService = escalaService;
    }

    /** Todo dia (por padrão às 6h) e ao iniciar a aplicação. */
    @Scheduled(cron = "${app.escala.geracao-automatica.cron:0 0 6 * * *}")
    @EventListener(ApplicationReadyEvent.class)
    public void verificar() {
        if (habilitada) {
            executar(LocalDate.now());
        }
    }

    /** Cria os rascunhos que faltam a partir da semana seguinte a {@code hoje}; devolve as escalas criadas. */
    public List<Escala> executar(LocalDate hoje) {
        ParametrosOperacionais p = parametros.atuais();
        List<Escala> criadas = new ArrayList<>();
        if (!p.isGeracaoAutomatica()) {
            return criadas;
        }
        LocalDate proximaSegunda = hoje.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        for (int semana = 0; semana < p.getSemanasAntecedencia(); semana++) {
            LocalDate inicio = proximaSegunda.plusWeeks(semana);
            LocalDate fim = inicio.plusDays(6);
            if (escalas.existeSobreposicao(inicio, fim, null)) {
                continue;
            }
            try {
                Escala e = escalaService.criarAutomatica(inicio, fim);
                escalaService.gerar(e.getId());
                criadas.add(e);
                log.info("Rascunho automático criado: escala de {} a {}.", inicio, fim);
            } catch (RuntimeException ex) {
                log.warn("Não foi possível gerar automaticamente a escala de {} a {}: {}", inicio, fim, ex.getMessage());
            }
        }
        return criadas;
    }
}
