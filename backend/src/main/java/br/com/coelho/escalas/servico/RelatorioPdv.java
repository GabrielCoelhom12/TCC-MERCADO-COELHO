package br.com.coelho.escalas.servico;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Leitor do relatório de vendas diárias exportado pelo PDV (CSV separado por ";"):
 * {@code Data;Venda Bruta;Venda Cancelada;Valor Desconto;Venda Líquida;Qtde. Cupom;Cupom Médio;Qtde. Cancelado}, com a
 * data como "dd/MM/yyyy" (seguida ou não de " - DIA"). As colunas são achadas pelo nome, em qualquer ordem; aceita
 * números com ponto ou vírgula decimal e arquivos em UTF-8 ou Windows-1252. A linha de totais (sem data) é ignorada.
 */
public final class RelatorioPdv {

    /** Um dia do relatório: venda líquida e clientes atendidos (cupons emitidos menos os cancelados). */
    public record DiaPdv(LocalDate data, BigDecimal vendaLiquida, int clientes) {
    }

    /** Linhas lidas e as que não puderam ser aproveitadas, com o motivo. */
    public record Leitura(List<DiaPdv> dias, List<String> avisos) {
    }

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    /** Marca de ordem de bytes que o Excel grava no início de arquivos UTF-8. */
    private static final String BOM = String.valueOf((char) 0xFEFF);

    private RelatorioPdv() {
    }

    public static Leitura ler(byte[] arquivo) {
        String texto = decodificar(arquivo);
        String[] linhas = texto.split("\\r?\\n");
        int cabecalho = -1;
        int colData = -1;
        int colLiquida = -1;
        int colCupons = -1;
        int colCancelados = -1;
        for (int i = 0; i < linhas.length && cabecalho < 0; i++) {
            String[] c = linhas[i].split(";", -1);
            for (int j = 0; j < c.length; j++) {
                String nome = normalizar(c[j]);
                if (nome.equals("data")) {
                    colData = j;
                } else if (nome.equals("venda liquida")) {
                    colLiquida = j;
                } else if (nome.equals("qtde. cupom") || nome.equals("qtde cupom")) {
                    colCupons = j;
                } else if (nome.equals("qtde. cancelado") || nome.equals("qtde cancelado")) {
                    colCancelados = j;
                }
            }
            if (colData >= 0 && colLiquida >= 0 && colCupons >= 0) {
                cabecalho = i;
            } else {
                colData = colLiquida = colCupons = colCancelados = -1;
            }
        }
        if (cabecalho < 0) {
            throw new RegraNegocioException("Arquivo não reconhecido: esperado o relatório de vendas do PDV com as colunas "
                    + "\"Data\", \"Venda Líquida\" e \"Qtde. Cupom\", separadas por \";\".");
        }

        List<DiaPdv> dias = new ArrayList<>();
        List<String> avisos = new ArrayList<>();
        for (int i = cabecalho + 1; i < linhas.length; i++) {
            String[] c = linhas[i].split(";", -1);
            String campoData = campo(c, colData);
            if (campoData.isBlank()) {
                continue; // linha em branco ou de totais
            }
            int numeroLinha = i + 1;
            try {
                LocalDate data = LocalDate.parse(campoData.length() >= 10 ? campoData.substring(0, 10) : campoData, DATA);
                BigDecimal liquida = numero(campo(c, colLiquida)).setScale(2, RoundingMode.HALF_UP);
                int cupons = numero(campo(c, colCupons)).intValue();
                int cancelados = colCancelados < 0 || campo(c, colCancelados).isBlank() ? 0 : numero(campo(c, colCancelados)).intValue();
                dias.add(new DiaPdv(data, liquida, Math.max(0, cupons - cancelados)));
            } catch (DateTimeParseException | NumberFormatException e) {
                avisos.add("Linha " + numeroLinha + " ignorada: valor inválido (" + linhas[i].trim() + ").");
            }
        }
        return new Leitura(dias, avisos);
    }

    private static String campo(String[] c, int i) {
        return i < c.length ? c[i].trim() : "";
    }

    /** "1234.56", "1.234,56" ou "1234,56". */
    static BigDecimal numero(String valor) {
        String v = valor.trim().replace("R$", "").replace(" ", "");
        if (v.contains(",")) {
            v = v.replace(".", "").replace(",", ".");
        }
        return new BigDecimal(v);
    }

    private static String normalizar(String s) {
        return Normalizer.normalize(s.trim().replace(BOM, ""), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    /** UTF-8 quando válido; senão Windows-1252 (padrão dos sistemas de PDV no Windows). */
    private static String decodificar(byte[] arquivo) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(arquivo)).toString();
        } catch (CharacterCodingException e) {
            return new String(arquivo, java.nio.charset.Charset.forName("windows-1252"));
        }
    }
}
