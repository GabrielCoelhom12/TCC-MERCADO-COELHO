package br.com.coelho.escalas.servico;

import br.com.coelho.escalas.dominio.Ausencia;
import br.com.coelho.escalas.dominio.Escala;
import br.com.coelho.escalas.dominio.TurnoModelo;
import br.com.coelho.escalas.motor.ResumoDia;
import br.com.coelho.escalas.servico.EscalaService.LinhaFuncionario;
import br.com.coelho.escalas.servico.EscalaService.Matriz;
import br.com.coelho.escalas.servico.EscalaService.TurnoDTO;
import org.openpdf.text.Chunk;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.Image;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.ColumnText;
import org.openpdf.text.pdf.PdfContentByte;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfPageEventHelper;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Escala em PDF (A4 deitado) para imprimir e afixar ou para baixar: logo do Supermercado Coelho, uma tabela por semana
 * com os colaboradores agrupados por setor, a legenda dos turnos e espaço para a assinatura da gerência.
 *
 * <p>Sem custos nem salários. Das ausências, só "Férias" e "Folga" aparecem pelo nome; atestados e licenças saem como
 * "Ausente", porque a escala impressa fica à vista de todos (LGPD: dado de saúde).
 */
@Service
public class EscalaPdfService {

    public record Arquivo(String nome, byte[] conteudo) {
    }

    private static final Color LARANJA = new Color(0xF2, 0x6A, 0x1B);
    private static final Color TINTA = new Color(0x1D, 0x1E, 0x19);
    private static final Color CINZA = new Color(0x8B, 0x8C, 0x85);
    private static final Color BORDA = new Color(0xE8, 0xE6, 0xE1);
    private static final Color FUNDO = new Color(0xF7, 0xF6, 0xF3);
    private static final Color FUNDO_LARANJA = new Color(0xFF, 0xF4, 0xED);

    private static final Font TITULO = fonte(16, Font.BOLD, TINTA);
    private static final Font ROTULO = fonte(8, Font.BOLD, LARANJA);
    private static final Font TEXTO = fonte(9, Font.NORMAL, TINTA);
    private static final Font PEQUENO = fonte(7, Font.NORMAL, CINZA);
    private static final Font NEGRITO = fonte(8.5f, Font.BOLD, TINTA);
    private static final Font CABECALHO = fonte(8.5f, Font.BOLD, Color.WHITE);
    private static final Font CABECALHO_DATA = fonte(7.5f, Font.NORMAL, new Color(0xD8, 0xD6, 0xD0));
    private static final Font FOLGA = fonte(8, Font.ITALIC, CINZA);

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final Locale PT_BR = Locale.of("pt", "BR");

    private final EscalaService escalas;

    public EscalaPdfService(EscalaService escalas) {
        this.escalas = escalas;
    }

    @Transactional(readOnly = true)
    public Arquivo gerar(Long escalaId) {
        Matriz m = escalas.matriz(escalaId);
        Escala e = m.escala();
        String nome = "escala-" + e.getDataInicio() + "-a-" + e.getDataFim() + ".pdf";
        return new Arquivo(nome, desenhar(m, LocalDateTime.now()));
    }

    // ------------------------------------------------------------------ documento

    private static byte[] desenhar(Matriz m, LocalDateTime emitidaEm) {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate(), 28, 28, 26, 34);
        PdfWriter escritor = PdfWriter.getInstance(doc, saida);
        escritor.setPageEvent(new Rodape());
        doc.addTitle("Escala de trabalho — " + m.escala().getNome());
        doc.addAuthor("Supermercado Coelho");
        doc.addCreator("Escalas · Supermercado Coelho");
        doc.open();

        List<List<LocalDate>> semanas = semanas(m.escala());
        for (int i = 0; i < semanas.size(); i++) {
            if (i > 0) {
                doc.newPage();
            }
            doc.add(cabecalho(m.escala(), semanas.get(i), emitidaEm));
            doc.add(grade(m, semanas.get(i)));
        }
        doc.add(legenda(m));
        doc.add(assinatura());
        doc.close();
        return saida.toByteArray();
    }

    /** A escala pode ter até 62 dias: uma tabela (e uma página) por semana de 7 dias a partir do início. */
    private static List<List<LocalDate>> semanas(Escala e) {
        List<List<LocalDate>> semanas = new ArrayList<>();
        for (LocalDate d = e.getDataInicio(); !d.isAfter(e.getDataFim()); d = d.plusDays(7)) {
            List<LocalDate> semana = new ArrayList<>();
            for (int i = 0; i < 7 && !d.plusDays(i).isAfter(e.getDataFim()); i++) {
                semana.add(d.plusDays(i));
            }
            semanas.add(semana);
        }
        return semanas;
    }

    private static PdfPTable cabecalho(Escala e, List<LocalDate> semana, LocalDateTime emitidaEm) {
        PdfPTable t = new PdfPTable(new float[]{1.1f, 3f, 1.6f});
        t.setWidthPercentage(100);
        t.setSpacingAfter(10);

        PdfPCell logo = semBorda(new PdfPCell(logo(), false));
        logo.setVerticalAlignment(Element.ALIGN_MIDDLE);
        logo.setPaddingRight(14);
        t.addCell(logo);

        PdfPCell titulo = semBorda(new PdfPCell());
        titulo.setVerticalAlignment(Element.ALIGN_MIDDLE);
        titulo.addElement(new Paragraph("ESCALA DE TRABALHO", ROTULO));
        titulo.addElement(new Paragraph(e.getNome(), TITULO));
        Paragraph periodo = new Paragraph(DATA.format(semana.getFirst()) + " a " + DATA.format(semana.getLast()), TEXTO);
        periodo.setSpacingBefore(2);
        titulo.addElement(periodo);
        t.addCell(titulo);

        PdfPCell situacao = semBorda(new PdfPCell());
        situacao.setVerticalAlignment(Element.ALIGN_MIDDLE);
        Paragraph status = new Paragraph(e.getStatus() == Escala.Status.APROVADA
                ? "Aprovada" + (e.getAprovadaPor() == null ? "" : " por " + e.getAprovadaPor())
                + (e.getAprovadaEm() == null ? "" : " em " + DATA.format(e.getAprovadaEm()))
                : "Rascunho — sujeita a alterações", e.getStatus() == Escala.Status.APROVADA ? NEGRITO : fonte(8.5f, Font.BOLD, LARANJA));
        status.setAlignment(Element.ALIGN_RIGHT);
        situacao.addElement(status);
        Paragraph emitida = new Paragraph("Emitida em " + DATA.format(emitidaEm) + " às " + HORA.format(emitidaEm), PEQUENO);
        emitida.setAlignment(Element.ALIGN_RIGHT);
        situacao.addElement(emitida);
        t.addCell(situacao);

        // Linha laranja da marca abaixo do cabeçalho.
        PdfPCell linha = new PdfPCell();
        linha.setColspan(3);
        linha.setBorder(Rectangle.BOTTOM);
        linha.setBorderColorBottom(LARANJA);
        linha.setBorderWidthBottom(2f);
        linha.setFixedHeight(6);
        t.addCell(linha);
        return t;
    }

    private static Image logo() {
        try (InputStream in = EscalaPdfService.class.getResourceAsStream("/relatorio/logo-coelho.png")) {
            Image img = Image.getInstance(Objects.requireNonNull(in, "logo ausente").readAllBytes());
            img.scaleToFit(118, 58);
            return img;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    // ------------------------------------------------------------------ grade colaborador × dia

    private static PdfPTable grade(Matriz m, List<LocalDate> semana) {
        float[] larguras = new float[semana.size() + 1];
        larguras[0] = 2.5f;
        Arrays.fill(larguras, 1, larguras.length, 1f);
        PdfPTable t = new PdfPTable(larguras);
        t.setWidthPercentage(100);
        t.setHeaderRows(1);
        t.setSplitLate(false);

        Map<LocalDate, ResumoDia> resumos = m.validacao().dias().stream()
                .collect(Collectors.toMap(ResumoDia::data, r -> r, (a, b) -> a));
        t.addCell(celulaCabecalho(new Phrase("Colaborador", CABECALHO), Element.ALIGN_LEFT, TINTA));
        for (LocalDate d : semana) {
            ResumoDia r = resumos.get(d);
            boolean feriado = r != null && r.feriado() != null;
            Phrase p = new Phrase();
            p.add(new Chunk(diaDaSemana(d) + "\n", CABECALHO));
            p.add(new Chunk(feriado ? DIA_MES.format(d) + " · feriado" : DIA_MES.format(d), CABECALHO_DATA));
            t.addCell(celulaCabecalho(p, Element.ALIGN_CENTER, feriado ? LARANJA : TINTA));
        }

        Map<String, List<LinhaFuncionario>> porSetor = m.funcionarios().stream()
                .collect(Collectors.groupingBy(LinhaFuncionario::setorNome, TreeMap::new, Collectors.toList()));
        Map<String, List<TurnoDTO>> turnos = m.turnos().stream()
                .collect(Collectors.groupingBy(tu -> tu.funcionarioId() + "|" + tu.data()));
        int linha = 0;
        for (Map.Entry<String, List<LinhaFuncionario>> setor : porSetor.entrySet()) {
            t.addCell(celulaSetor(setor.getKey(), setor.getValue().getFirst().setorCor(), semana.size() + 1));
            for (LinhaFuncionario f : setor.getValue().stream().sorted(Comparator.comparing(LinhaFuncionario::nome)).toList()) {
                Color fundo = linha++ % 2 == 0 ? Color.WHITE : FUNDO;
                t.addCell(celulaNome(f, fundo));
                for (LocalDate d : semana) {
                    t.addCell(celulaDia(turnos.getOrDefault(f.id() + "|" + d, List.of()), ausencia(m.ausencias(), f.id(), d),
                            m.escala().contem(d), fundo));
                }
            }
        }

        PdfPCell total = celula(new Phrase("Pessoas escaladas", NEGRITO), Element.ALIGN_LEFT, FUNDO_LARANJA);
        total.setBorderWidthTop(1.2f);
        total.setBorderColorTop(LARANJA);
        t.addCell(total);
        for (LocalDate d : semana) {
            long pessoas = m.turnos().stream().filter(tu -> tu.data().equals(d)).map(TurnoDTO::funcionarioId).distinct().count();
            PdfPCell c = celula(new Phrase(String.valueOf(pessoas), NEGRITO), Element.ALIGN_CENTER, FUNDO_LARANJA);
            c.setBorderWidthTop(1.2f);
            c.setBorderColorTop(LARANJA);
            t.addCell(c);
        }
        return t;
    }

    private static PdfPCell celulaDia(List<TurnoDTO> turnos, Ausencia ausencia, boolean naEscala, Color fundo) {
        if (!naEscala) {
            return celula(new Phrase("—", FOLGA), Element.ALIGN_CENTER, fundo);
        }
        if (!turnos.isEmpty()) {
            Phrase p = new Phrase();
            for (int i = 0; i < turnos.size(); i++) {
                TurnoDTO tu = turnos.get(i);
                p.add(new Chunk((i > 0 ? "\n" : "") + HORA.format(tu.horaInicio()) + "–" + HORA.format(tu.horaFim()), NEGRITO));
                if (tu.rotulo() != null && !tu.rotulo().isBlank()) {
                    p.add(new Chunk("\n" + tu.rotulo(), PEQUENO));
                }
            }
            return celula(p, Element.ALIGN_CENTER, fundo);
        }
        if (ausencia != null) {
            return celula(new Phrase(rotuloPublico(ausencia.getMotivo()), FOLGA), Element.ALIGN_CENTER, FUNDO);
        }
        return celula(new Phrase("Folga", FOLGA), Element.ALIGN_CENTER, fundo);
    }

    /** Na escala impressa, ninguém precisa saber por que o colega está fora: só férias e folga aparecem pelo nome. */
    private static String rotuloPublico(Ausencia.Motivo motivo) {
        return switch (motivo) {
            case FERIAS -> "Férias";
            case FOLGA_ACORDADA -> "Folga";
            default -> "Ausente";
        };
    }

    private static Ausencia ausencia(List<Ausencia> ausencias, Long funcionarioId, LocalDate d) {
        return ausencias.stream().filter(a -> a.getFuncionarioId().equals(funcionarioId) && a.cobre(d)).findFirst().orElse(null);
    }

    private static PdfPCell celulaNome(LinhaFuncionario f, Color fundo) {
        Phrase p = new Phrase();
        p.add(new Chunk(f.nome(), NEGRITO));
        if (f.cargo() != null && !f.cargo().isBlank()) {
            p.add(new Chunk("\n" + f.cargo(), PEQUENO));
        }
        PdfPCell c = celula(p, Element.ALIGN_LEFT, fundo);
        c.setPaddingLeft(6);
        return c;
    }

    private static PdfPCell celulaSetor(String setor, String corHex, int colunas) {
        Color cor = cor(corHex);
        PdfPCell c = new PdfPCell(new Phrase(setor.toUpperCase(PT_BR), fonte(8, Font.BOLD, TINTA)));
        c.setColspan(colunas);
        c.setBackgroundColor(clarear(cor));
        c.setBorder(Rectangle.LEFT | Rectangle.BOTTOM);
        c.setBorderColorLeft(cor);
        c.setBorderWidthLeft(4f);
        c.setBorderColorBottom(BORDA);
        c.setPadding(4);
        c.setPaddingLeft(6);
        return c;
    }

    private static PdfPCell celulaCabecalho(Phrase p, int alinhamento, Color fundo) {
        PdfPCell c = new PdfPCell(p);
        c.setHorizontalAlignment(alinhamento);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setBackgroundColor(fundo);
        c.setBorderColor(fundo);
        c.setPadding(5);
        c.setPaddingLeft(6);
        return c;
    }

    private static PdfPCell celula(Phrase p, int alinhamento, Color fundo) {
        PdfPCell c = new PdfPCell(p);
        c.setHorizontalAlignment(alinhamento);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setBackgroundColor(fundo);
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(BORDA);
        c.setPaddingTop(4);
        c.setPaddingBottom(5);
        return c;
    }

    // ------------------------------------------------------------------ legenda e assinatura

    /** Só os modelos de turno usados na escala, com horário e intervalo. */
    private static Paragraph legenda(Matriz m) {
        Set<String> usados = m.turnos().stream().map(TurnoDTO::rotulo).filter(Objects::nonNull).collect(Collectors.toSet());
        Paragraph p = new Paragraph();
        p.setSpacingBefore(10);
        p.add(new Chunk("Turnos   ", fonte(8, Font.BOLD, TINTA)));
        List<TurnoModelo> modelos = m.modelos().stream().filter(mod -> usados.contains(mod.getSigla())).toList();
        for (int i = 0; i < modelos.size(); i++) {
            TurnoModelo mod = modelos.get(i);
            p.add(new Chunk(mod.getSigla() + " ", fonte(8, Font.BOLD, LARANJA)));
            p.add(new Chunk(mod.getNome() + ", " + HORA.format(mod.getHoraInicio()) + "–" + HORA.format(mod.getHoraFim())
                    + " (intervalo de " + mod.getIntervaloMinutos() + " min)" + (i < modelos.size() - 1 ? "     " : ""), PEQUENO));
        }
        if (usados.contains("Pers.")) {
            p.add(new Chunk("     Pers. ", fonte(8, Font.BOLD, LARANJA)));
            p.add(new Chunk("horário personalizado", PEQUENO));
        }
        return p;
    }

    private static PdfPTable assinatura() {
        PdfPTable t = new PdfPTable(new float[]{3f, 1.4f});
        t.setWidthPercentage(100);
        t.setSpacingBefore(26);
        t.addCell(semBorda(new PdfPCell(new Phrase(""))));
        PdfPCell linha = new PdfPCell(new Phrase("Gerência — Supermercado Coelho", PEQUENO));
        linha.setBorder(Rectangle.TOP);
        linha.setBorderColor(CINZA);
        linha.setHorizontalAlignment(Element.ALIGN_CENTER);
        linha.setPaddingTop(4);
        t.addCell(linha);
        return t;
    }

    /** Rodapé de cada página: identificação e número da página. */
    private static final class Rodape extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            Phrase esquerda = new Phrase("Supermercado Coelho · Escala gerada pelo sistema Escalas", PEQUENO);
            Phrase direita = new Phrase("Página " + writer.getPageNumber(), PEQUENO);
            float y = document.bottom() - 18;
            ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, esquerda, document.left(), y, 0);
            ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT, direita, document.right(), y, 0);
        }
    }

    // ------------------------------------------------------------------ auxiliares

    private static String diaDaSemana(LocalDate d) {
        String nome = d.getDayOfWeek().getDisplayName(TextStyle.SHORT, PT_BR).replace(".", "");
        return d.getDayOfWeek() == DayOfWeek.SATURDAY ? "Sáb" : Character.toUpperCase(nome.charAt(0)) + nome.substring(1);
    }

    private static PdfPCell semBorda(PdfPCell c) {
        c.setBorder(Rectangle.NO_BORDER);
        return c;
    }

    private static Font fonte(float tamanho, int estilo, Color cor) {
        return new Font(Font.HELVETICA, tamanho, estilo, cor);
    }

    private static Color cor(String hex) {
        try {
            return Color.decode(hex);
        } catch (RuntimeException e) {
            return LARANJA;
        }
    }

    /** A cor do setor bem clara, para o fundo da faixa do setor. */
    private static Color clarear(Color c) {
        return new Color(c.getRed() + (255 - c.getRed()) * 85 / 100, c.getGreen() + (255 - c.getGreen()) * 85 / 100,
                c.getBlue() + (255 - c.getBlue()) * 85 / 100);
    }
}
