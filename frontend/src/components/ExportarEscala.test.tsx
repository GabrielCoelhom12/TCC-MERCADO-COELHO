import { fireEvent, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { mockFetch, renderizar } from '../test/util';
import type { Escala } from '../types';
import ExportarEscala from './ExportarEscala';

const escala: Escala = {
  id: 7, nome: 'Escala 09/11 a 15/11', dataInicio: '2026-11-09', dataFim: '2026-11-15', status: 'APROVADA', observacao: null,
  criadaEm: '2026-11-02T06:00:00', aprovadaEm: null, aprovadaPor: null, geradaAutomaticamente: true,
};

function preparar() {
  const url = vi.fn(() => 'blob:escala');
  vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: url, revokeObjectURL: vi.fn() }));
  const clique = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
  return { url, clique };
}

describe('ExportarEscala', () => {
  it('baixa o PDF da escala com o nome do período', async () => {
    const fetch = mockFetch({ 'GET /api/escalas/7/pdf': { corpo: 'PDF' } });
    const { clique } = preparar();
    renderizar(<ExportarEscala escala={escala} />);

    fireEvent.click(screen.getByRole('button', { name: /Baixar PDF/ }));

    await waitFor(() => expect(clique).toHaveBeenCalled());
    const link = clique.mock.contexts[0] as HTMLAnchorElement;
    expect(link.download).toBe('escala-2026-11-09-a-2026-11-15.pdf');
    expect(link.href).toBe('blob:escala');
    expect(fetch.mock.calls[0][1]?.headers).toBeDefined();
  });

  it('imprimir carrega o PDF num quadro invisível', async () => {
    mockFetch({ 'GET /api/escalas/7/pdf?imprimir=true': { corpo: 'PDF' } });
    preparar();
    renderizar(<ExportarEscala escala={escala} />);

    fireEvent.click(screen.getByRole('button', { name: /Imprimir/ }));

    await waitFor(() => expect(document.querySelector('iframe.impressao')).not.toBeNull());
    expect(document.querySelector('iframe.impressao')).toHaveAttribute('src', 'blob:escala');
  });

  it('avisa quando o PDF não pode ser gerado', async () => {
    mockFetch({ 'GET /api/escalas/7/pdf': { status: 404, corpo: { mensagem: 'Escala não encontrada (id 7).' } } });
    preparar();
    renderizar(<ExportarEscala escala={escala} />);

    fireEvent.click(screen.getByRole('button', { name: /Baixar PDF/ }));

    expect(await screen.findByText('Escala não encontrada (id 7).')).toBeInTheDocument();
  });
});
