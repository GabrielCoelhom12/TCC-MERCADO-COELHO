import { useState } from 'react';
import { Download, Printer } from 'lucide-react';
import { api } from '../api';
import { mensagemErro, useToast } from './ui';
import type { Escala } from '../types';

/**
 * Botões "Imprimir" e "Baixar PDF" da escala. O PDF (com a logo) é gerado pelo servidor; para imprimir, ele é
 * carregado num quadro invisível e o diálogo de impressão do navegador abre direto.
 */
export default function ExportarEscala({ escala, apenasBaixar }: { escala: Escala; apenasBaixar?: boolean }) {
  const toast = useToast();
  const [ocupado, setOcupado] = useState<'baixar' | 'imprimir' | null>(null);

  const executar = async (acao: 'baixar' | 'imprimir') => {
    setOcupado(acao);
    try {
      const pdf = await api.arquivo(`/escalas/${escala.id}/pdf${acao === 'imprimir' ? '?imprimir=true' : ''}`);
      const url = URL.createObjectURL(pdf);
      if (acao === 'baixar') baixar(url, `escala-${escala.dataInicio}-a-${escala.dataFim}.pdf`);
      else imprimir(url);
    } catch (e) {
      toast('erro', mensagemErro(e));
    } finally {
      setOcupado(null);
    }
  };

  return (
    <>
      {!apenasBaixar && (
        <button onClick={() => executar('imprimir')} disabled={ocupado !== null}>
          <Printer size={16} aria-hidden />{ocupado === 'imprimir' ? 'Preparando…' : 'Imprimir'}
        </button>
      )}
      <button onClick={() => executar('baixar')} disabled={ocupado !== null}>
        <Download size={16} aria-hidden />{ocupado === 'baixar' ? 'Gerando…' : 'Baixar PDF'}
      </button>
    </>
  );
}

function baixar(url: string, nome: string) {
  const link = document.createElement('a');
  link.href = url;
  link.download = nome;
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}

/** Um quadro invisível por vez; se o navegador não deixar imprimir por ele, o PDF abre numa aba nova. */
function imprimir(url: string) {
  document.querySelector('iframe.impressao')?.remove();
  const quadro = document.createElement('iframe');
  quadro.className = 'impressao';
  quadro.title = 'Escala para impressão';
  quadro.setAttribute('aria-hidden', 'true');
  quadro.onload = () => {
    try {
      quadro.contentWindow?.focus();
      quadro.contentWindow?.print();
    } catch {
      window.open(url, '_blank', 'noopener');
    }
  };
  quadro.src = url;
  document.body.appendChild(quadro);
}
