package br.com.coelho.escalas.seguranca;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LimiteTentativasTest {

    /** Relógio que o teste avança à mão. */
    private static final class Relogio extends Clock {
        private Instant agora = Instant.parse("2026-11-13T10:00:00Z");

        void avancar(Duration d) {
            agora = agora.plus(d);
        }

        @Override
        public Instant instant() {
            return agora;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }

    private final Relogio relogio = new Relogio();
    private final LimiteTentativas limite = new LimiteTentativas(relogio);

    private void falhar(String login, String ip, int vezes) {
        for (int i = 0; i < vezes; i++) {
            limite.registrarFalha(login, ip);
        }
    }

    @Test
    void bloqueiaOLoginNaqueleIpNaQuintaFalhaPorQuinzeMinutos() {
        falhar("ana", "10.0.0.1", 4);
        assertThat(limite.bloqueio("ana", "10.0.0.1")).isEmpty();

        falhar("ana", "10.0.0.1", 1);
        assertThat(limite.bloqueio("ana", "10.0.0.1")).contains(Duration.ofMinutes(15));
        assertThat(limite.bloqueio("bia", "10.0.0.1")).isEmpty();

        relogio.avancar(Duration.ofMinutes(10));
        assertThat(limite.bloqueio("ana", "10.0.0.1")).contains(Duration.ofMinutes(5));
        relogio.avancar(Duration.ofMinutes(5));
        assertThat(limite.bloqueio("ana", "10.0.0.1")).isEmpty();
    }

    /** Quem erra a senha (ou um atacante) de um IP não tranca a conta para quem está em outro lugar. */
    @Test
    void falhasDeUmIpNaoTrancamAContaEmOutroIp() {
        falhar("gestor", "203.0.113.7", 10);

        assertThat(limite.bloqueio("gestor", "203.0.113.7")).isPresent();
        assertThat(limite.bloqueio("gestor", "10.0.0.2")).isEmpty();
    }

    /** Ataque distribuído: muitas falhas na mesma conta, cada uma de um IP, bloqueiam a conta. */
    @Test
    void muitasFalhasNaContaVindasDeVariosIpsBloqueiamAConta() {
        for (int i = 0; i < LimiteTentativas.MAX_FALHAS_POR_LOGIN; i++) {
            limite.registrarFalha("gestor", "198.51.100." + i);
        }

        assertThat(limite.bloqueio("gestor", "10.0.0.2")).isPresent();
        assertThat(limite.bloqueio("ana", "10.0.0.2")).isEmpty();
    }

    @Test
    void falhasEspalhadasNoTempoNaoAcumulam() {
        falhar("ana", "10.0.0.1", 4);
        relogio.avancar(Duration.ofMinutes(16));
        falhar("ana", "10.0.0.1", 4);
        assertThat(limite.bloqueio("ana", "10.0.0.1")).isEmpty();
    }

    @Test
    void sucessoZeraAsFalhasDoLogin() {
        falhar("ana", "10.0.0.1", 4);
        limite.registrarSucesso("ana", "10.0.0.1");
        falhar("ana", "10.0.0.1", 4);
        assertThat(limite.bloqueio("ana", "10.0.0.1")).isEmpty();
    }

    @Test
    void muitasFalhasDoMesmoIpBloqueiamQualquerLoginVindoDele() {
        for (int i = 0; i < LimiteTentativas.MAX_FALHAS_POR_IP; i++) {
            limite.registrarFalha("login" + i, "10.0.0.9");
        }
        assertThat(limite.bloqueio("outro", "10.0.0.9")).isPresent();
        assertThat(limite.bloqueio("outro", "10.0.0.8")).isEmpty();
    }

    @Test
    void limparEsqueceTudo() {
        falhar("ana", "10.0.0.1", 5);
        limite.limpar();
        assertThat(limite.bloqueio("ana", "10.0.0.1")).isEmpty();
    }
}
