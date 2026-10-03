package br.com.coelho.escalas.api;

import br.com.coelho.escalas.motor.Violacao;
import br.com.coelho.escalas.servico.NaoEncontradoException;
import br.com.coelho.escalas.servico.RegraNegocioException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class TratadorErros {

    public record Erro(String mensagem, List<Violacao> violacoes) {
        Erro(String mensagem) {
            this(mensagem, List.of());
        }
    }

    @ExceptionHandler(RegraNegocioException.class)
    ResponseEntity<Erro> regra(RegraNegocioException e) {
        return ResponseEntity.unprocessableEntity().body(new Erro(e.getMessage(), e.getViolacoes()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Erro> argumento(IllegalArgumentException e) {
        return ResponseEntity.unprocessableEntity().body(new Erro(e.getMessage()));
    }

    @ExceptionHandler(NaoEncontradoException.class)
    ResponseEntity<Erro> naoEncontrado(NaoEncontradoException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new Erro(e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Erro> validacao(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(new Erro("Dados inválidos — " + msg));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Erro> integridade(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new Erro("Registro duplicado ou em uso por outros cadastros."));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<Erro> arquivoGrande(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(new Erro("Arquivo grande demais (máximo de 5 MB)."));
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Erro> status(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(new Erro(e.getReason()));
    }
}
