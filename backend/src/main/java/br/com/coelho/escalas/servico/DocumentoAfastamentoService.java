package br.com.coelho.escalas.servico;

import br.com.coelho.escalas.dominio.Ausencia;
import br.com.coelho.escalas.dominio.DocumentoAusencia;
import br.com.coelho.escalas.dominio.DocumentoResumo;
import br.com.coelho.escalas.repositorio.AusenciaRepository;
import br.com.coelho.escalas.repositorio.DocumentoAusenciaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Documentos dos afastamentos (atestado, declaração, certidão...). Aceita PDF, JPG e PNG verificados pelo conteúdo;
 * fotos são regravadas em JPEG, o que descarta metadados como a localização do celular. São dado sensível de saúde:
 * qualquer perfil pode enviar, mas só a gerência abre ou remove (regra em {@code SecurityConfig}).
 */
@Service
public class DocumentoAfastamentoService {

    public static final int LIMITE_BYTES = 5 * 1024 * 1024;
    public static final int MAX_POR_AFASTAMENTO = 10;
    /** Lado máximo das fotos de documento: legível e bem menor que a foto original do celular. */
    private static final int MAX_LADO_FOTO = 2400;
    private static final int MAX_NOME = 120;
    static final String FORMATO_INVALIDO = "Envie o documento em PDF, JPG ou PNG.";

    private final DocumentoAusenciaRepository documentos;
    private final AusenciaRepository ausencias;

    public DocumentoAfastamentoService(DocumentoAusenciaRepository documentos, AusenciaRepository ausencias) {
        this.documentos = documentos;
        this.ausencias = ausencias;
    }

    @Transactional
    public DocumentoResumo anexar(Long afastamentoId, String nomeOriginal, byte[] bytes, String login) {
        Ausencia a = buscarAfastamento(afastamentoId);
        if (bytes == null || bytes.length == 0) {
            throw new RegraNegocioException("Selecione um arquivo.");
        }
        if (bytes.length > LIMITE_BYTES) {
            throw new RegraNegocioException("O documento deve ter no máximo 5 MB.");
        }
        if (documentos.countByAusencia_Id(a.getId()) >= MAX_POR_AFASTAMENTO) {
            throw new RegraNegocioException("Cada afastamento aceita até " + MAX_POR_AFASTAMENTO + " documentos.");
        }

        DocumentoAusencia d = new DocumentoAusencia();
        d.setAusencia(a);
        if (ehPdf(bytes)) {
            d.setTipo("application/pdf");
            d.setNomeArquivo(nome(nomeOriginal, ".pdf"));
            d.setConteudo(bytes);
        } else if (Imagens.ehImagem(bytes)) {
            d.setTipo("image/jpeg");
            d.setNomeArquivo(nome(nomeOriginal, ".jpg"));
            d.setConteudo(Imagens.paraJpeg(Imagens.reduzir(Imagens.ler(bytes, LIMITE_BYTES, "O documento deve ter no máximo 5 MB."),
                    MAX_LADO_FOTO)));
        } else {
            throw new RegraNegocioException(FORMATO_INVALIDO);
        }
        d.setTamanho(d.getConteudo().length);
        d.setEnviadoEm(LocalDateTime.now());
        d.setEnviadoPor(login);
        DocumentoAusencia salvo = documentos.saveAndFlush(d);
        return resumo(salvo);
    }

    @Transactional(readOnly = true)
    public List<DocumentoResumo> listar(Long afastamentoId) {
        return documentos.resumos(buscarAfastamento(afastamentoId).getId());
    }

    @Transactional(readOnly = true)
    public DocumentoAusencia abrir(Long id) {
        return documentos.findById(id).orElseThrow(() -> new NaoEncontradoException("Documento", id));
    }

    @Transactional
    public void remover(Long id) {
        documentos.delete(abrir(id));
    }

    private Ausencia buscarAfastamento(Long id) {
        return ausencias.findById(id)
                .filter(a -> a.getMotivo() != Ausencia.Motivo.FERIAS)
                .orElseThrow(() -> new NaoEncontradoException("Afastamento", id));
    }

    private static boolean ehPdf(byte[] b) {
        return b.length > 5 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F' && b[4] == '-';
    }

    /** Nome original sem caminho nem caracteres de controle, com a extensão do formato guardado. */
    static String nome(String original, String extensao) {
        String base = original == null ? "" : original.replaceAll(".*[/\\\\]", "").replaceAll("\\p{Cntrl}", "").trim();
        int ponto = base.lastIndexOf('.');
        if (ponto > 0) {
            base = base.substring(0, ponto);
        }
        if (base.isBlank()) {
            base = "documento";
        }
        if (base.length() > MAX_NOME) {
            base = base.substring(0, MAX_NOME);
        }
        return base + extensao;
    }

    private static DocumentoResumo resumo(DocumentoAusencia d) {
        return new DocumentoResumo(d.getId(), d.getAusencia().getId(), d.getNomeArquivo(), d.getTipo(), d.getTamanho(),
                d.getEnviadoEm(), d.getEnviadoPor());
    }
}
