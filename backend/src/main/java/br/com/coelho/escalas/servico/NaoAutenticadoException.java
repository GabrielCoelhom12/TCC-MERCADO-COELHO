package br.com.coelho.escalas.servico;

/** Credenciais ausentes, inválidas ou de uma conta desativada (HTTP 401). */
public class NaoAutenticadoException extends RuntimeException {

    public NaoAutenticadoException(String mensagem) {
        super(mensagem);
    }
}
