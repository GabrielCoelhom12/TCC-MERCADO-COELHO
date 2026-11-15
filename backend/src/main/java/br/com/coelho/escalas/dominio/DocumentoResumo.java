package br.com.coelho.escalas.dominio;

import java.time.LocalDateTime;

/** Dados de um documento sem o conteúdo: o que as listas mostram a qualquer perfil. */
public record DocumentoResumo(Long id, Long ausenciaId, String nomeArquivo, String tipo, long tamanho,
                              LocalDateTime enviadoEm, String enviadoPor) {
}
