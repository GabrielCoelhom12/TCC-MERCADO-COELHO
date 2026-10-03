package br.com.coelho.escalas.motor;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

public record ResultadoValidacao(
        List<ResumoDia> dias,
        List<Violacao> violacoes,
        BigDecimal custoTotal,
        BigDecimal tetoTotal,
        long erros,
        long alertas
) {
    /** A "flag de sucesso" do artigo: só aprova sem nenhum ERRO. */
    @JsonProperty("aprovavel")
    public boolean aprovavel() {
        return erros == 0;
    }
}
