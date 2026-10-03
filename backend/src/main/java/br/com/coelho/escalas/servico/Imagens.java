package br.com.coelho.escalas.servico;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Leitura segura de imagens enviadas pelos usuários: aceita apenas PNG/JPEG reais (verificados pela assinatura do
 * arquivo), limita bytes e pixels e sempre regrava em JPEG — o que descarta metadados e conteúdo embutido.
 */
final class Imagens {

    static final String FORMATO_INVALIDO = "O arquivo precisa ser uma imagem PNG ou JPEG.";
    /** Evita "bombas de descompressão": imagens pequenas em bytes, mas gigantes em pixels. */
    private static final int MAX_DIMENSAO = 8000;

    private Imagens() {
    }

    static BufferedImage ler(byte[] bytes, int limiteBytes, String mensagemLimite) {
        if (bytes == null || bytes.length == 0) {
            throw new RegraNegocioException("Selecione uma imagem.");
        }
        if (bytes.length > limiteBytes) {
            throw new RegraNegocioException(mensagemLimite);
        }
        if (!ehPng(bytes) && !ehJpeg(bytes)) {
            throw new RegraNegocioException(FORMATO_INVALIDO);
        }
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> leitores = ImageIO.getImageReaders(in);
            if (!leitores.hasNext()) {
                throw new RegraNegocioException(FORMATO_INVALIDO);
            }
            ImageReader leitor = leitores.next();
            try {
                leitor.setInput(in, true, true);
                if (leitor.getWidth(0) > MAX_DIMENSAO || leitor.getHeight(0) > MAX_DIMENSAO) {
                    throw new RegraNegocioException("A imagem é grande demais (máximo de " + MAX_DIMENSAO + " pixels de lado).");
                }
                return leitor.read(0);
            } finally {
                leitor.dispose();
            }
        } catch (RegraNegocioException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            // Arquivo corrompido ou malformado: o decodificador falha de formas variadas.
            throw new RegraNegocioException(FORMATO_INVALIDO);
        }
    }

    /** Desenha a região (x, y, largura, altura) da origem numa imagem RGB de destino, com fundo branco. */
    static BufferedImage redesenhar(BufferedImage origem, int x, int y, int largura, int altura, int destinoL, int destinoA) {
        BufferedImage saida = new BufferedImage(destinoL, destinoA, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = saida.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setColor(Color.WHITE); // fundo para PNG com transparência
        g.fillRect(0, 0, destinoL, destinoA);
        g.drawImage(origem, 0, 0, destinoL, destinoA, x, y, x + largura, y + altura, null);
        g.dispose();
        return saida;
    }

    /** Reduz mantendo a proporção para que o maior lado tenha no máximo {@code maxLado} pixels. */
    static BufferedImage reduzir(BufferedImage img, int maxLado) {
        double escala = Math.min(1.0, (double) maxLado / Math.max(img.getWidth(), img.getHeight()));
        int largura = Math.max(1, (int) Math.round(img.getWidth() * escala));
        int altura = Math.max(1, (int) Math.round(img.getHeight() * escala));
        return redesenhar(img, 0, 0, img.getWidth(), img.getHeight(), largura, altura);
    }

    static byte[] paraJpeg(BufferedImage img) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "jpg", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao gravar a imagem.", e);
        }
    }

    private static boolean ehPng(byte[] b) {
        return b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
    }

    private static boolean ehJpeg(byte[] b) {
        return b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
    }
}
