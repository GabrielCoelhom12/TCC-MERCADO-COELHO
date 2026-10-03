package br.com.coelho.escalas.servico;

import java.awt.image.BufferedImage;
import java.util.Base64;

/** Foto de perfil (usuários e colaboradores): recortada ao centro e regravada como JPEG 256×256. */
public final class FotoPerfil {

    public static final int LIMITE_BYTES = 2 * 1024 * 1024;
    private static final int LADO = 256;

    private FotoPerfil() {
    }

    public static byte[] normalizar(byte[] original) {
        BufferedImage img = Imagens.ler(original, LIMITE_BYTES, "A foto deve ter no máximo 2 MB.");
        int lado = Math.min(img.getWidth(), img.getHeight());
        int x = (img.getWidth() - lado) / 2;
        int y = (img.getHeight() - lado) / 2;
        return Imagens.paraJpeg(Imagens.redesenhar(img, x, y, lado, lado, LADO, LADO));
    }

    public static String dataUrl(byte[] jpeg) {
        return jpeg == null ? null : "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(jpeg);
    }
}
