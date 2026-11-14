import { screen, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { mockFetch, renderizar } from '../test/util';
import type { ComparacaoAlgoritmos as Comparacao } from '../types';
import ComparacaoAlgoritmos from './ComparacaoAlgoritmos';

const comparacao: Comparacao = {
  escalaId: 7, inicio: '2026-11-09', fim: '2026-11-15',
  guloso: { algoritmo: 'Guloso', milissegundos: 35, turnos: 76, vagasDescobertas: 2, custo: 7420.5, erros: 0, alertas: 3, desvioPadraoHoras: 9.8 },
  otimizado: { algoritmo: 'Otimizado (Timefold)', milissegundos: 2100, turnos: 78, vagasDescobertas: 0, custo: 7381.79, erros: 0, alertas: 1, desvioPadraoHoras: 4.1 },
};

describe('ComparacaoAlgoritmos', () => {
  it('mostra as métricas lado a lado e destaca o melhor de cada linha', async () => {
    mockFetch({ 'GET /api/escalas/7/comparacao': { corpo: comparacao } });
    renderizar(<ComparacaoAlgoritmos escalaId={7} onFechar={vi.fn()} />);

    const tabela = await screen.findByRole('table');
    const linha = (nome: RegExp) => within(tabela).getByRole('row', { name: nome });

    expect(linha(/Vagas sem ninguém/)).toHaveTextContent('2');
    expect(within(linha(/Vagas sem ninguém/)).getByText('0')).toHaveClass('melhor');
    expect(within(linha(/Custo da escala/)).getByText(/7\.381,79/)).toHaveClass('melhor');
    expect(within(linha(/Equilíbrio de horas/)).getByText('4,1 h')).toHaveClass('melhor');
    expect(within(linha(/Tempo/)).getByText('35 ms')).toHaveClass('melhor');
    expect(within(linha(/Tempo/)).getByText('2,1 s')).toBeInTheDocument();
    expect(within(tabela).getByRole('columnheader', { name: 'Timefold' })).toBeInTheDocument();
    expect(screen.getByText(/R\$\s?38,71 a menos/)).toBeInTheDocument();
  });

  it('avisa enquanto os dois algoritmos rodam', () => {
    mockFetch({});
    renderizar(<ComparacaoAlgoritmos escalaId={7} onFechar={vi.fn()} />);

    expect(screen.getByText(/Gerando as duas escalas/)).toBeInTheDocument();
  });
});
