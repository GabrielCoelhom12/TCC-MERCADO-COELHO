package br.com.coelho.escalas.servico;

/** Imagem anexada a uma mensagem: mantém a proporção, limitada a 1600 px no maior lado, regravada como JPEG. */
public final class ImagemChat {

    public static final int LIMITE_BYTES = 5 * 1024 * 1024;
    private static final int MAX_LADO = 1600;

    private ImagemChat() {
    }

    public static byte[] normalizar(byte[] original) {
        return Imagens.paraJpeg(Imagens.reduzir(Imagens.ler(original, LIMITE_BYTES, "A imagem deve ter no máximo 5 MB."), MAX_LADO));
    }
}
