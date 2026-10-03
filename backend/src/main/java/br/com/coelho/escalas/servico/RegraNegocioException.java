package br.com.coelho.escalas.servico;

import br.com.coelho.escalas.motor.Violacao;

import java.util.List;

/** Operação recusada por regra de negócio (HTTP 422), opcionalmente com as violações que a motivaram. */
public class RegraNegocioException extends RuntimeException {

    private final transient List<Violacao> violacoes;

    public RegraNegocioException(String mensagem) {
        this(mensagem, List.of());
    }

    public RegraNegocioException(String mensagem, List<Violacao> violacoes) {
        super(mensagem);
        this.violacoes = violacoes;
    }

    public List<Violacao> getViolacoes() {
        return violacoes;
    }
}
