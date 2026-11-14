package br.com.coelho.escalas.motor;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;

/** Resultado das regras de uma ausência (férias ou afastamento): erros impedem o registro; alertas só avisam. */
public record AnaliseAusencia(int dias, LocalDate retorno, List<String> erros, List<String> alertas) {

    @JsonProperty("valida")
    public boolean valida() {
        return erros.isEmpty();
    }
}
