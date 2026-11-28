import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { mockFetch, renderizar } from '../test/util';
import type { Escala, Geracao, Matriz, ResumoDia, TurnoModelo } from '../types';
import MatrizEscala from './MatrizEscala';

const escala: Escala = {
  id: 7, nome: 'Escala 09/11 a 15/11', dataInicio: '2026-11-09', dataFim: '2026-11-15', status: 'RASCUNHO', observacao: null,
  criadaEm: '2026-11-02T06:00:00', aprovadaEm: null, aprovadaPor: null, geradaAutomaticamente: true,
};
const modelo = { id: 1, nome: 'Abertura', sigla: 'ABE', horaInicio: '07:00:00', horaFim: '15:20:00', intervaloMinutos: 60, aplicacao: 'DIAS_UTEIS' } as TurnoModelo;

const resumo = (data: string): ResumoDia => ({
  data, tipoDia: 'Dia', feriado: null, fator: 1, receitaProjetada: 40000, tetoFolha: 2400, custoEscala: 150,
  custoMedioOperador: 150, operadores: 1, lMin: 1, lMax: 9, situacao: 'OK', setores: [], clientesPrevistos: null, pico: null,
});
const dias = ['09', '10', '11', '12', '13', '14', '15'].map((d) => `2026-11-${d}`);
const matriz: Matriz = {
  escala,
  funcionarios: [{ id: 5, nome: 'Ana Souza', cargo: 'Fiscal', setorId: 1, setorNome: 'Frente de Caixa', setorCor: '#F26A1B',
    custoHora: 14.43, diasDisponiveis: [], ativo: true }],
  turnos: [{ id: 10, funcionarioId: 5, data: '2026-11-09', horaInicio: '07:00:00', horaFim: '15:20:00', intervaloMinutos: 60,
    rotulo: 'ABE', horas: 7.33, custo: 105.8 }],
  ausencias: [],
  modelos: [modelo],
  validacao: { dias: dias.map(resumo), violacoes: [], custoTotal: 1050, tetoTotal: 16800, erros: 0, alertas: 0, aprovavel: true },
};

describe('MatrizEscala', () => {
  it('otimiza a escala com o Timefold e mostra o resultado', async () => {
    const otimizada: Geracao = { turnosCriados: 1, deficits: [], matriz };
    const fetch = mockFetch({
      'GET /api/escalas/7/matriz': { corpo: matriz },
      'POST /api/escalas/7/otimizar': { corpo: otimizada },
    });
    renderizar(<Routes><Route path="/escalas/:id" element={<MatrizEscala />} /></Routes>, '/escalas/7');

    fireEvent.click(await screen.findByRole('button', { name: /Otimizar com Timefold/ }));
    const dialogo = await screen.findByRole('dialog', { name: 'Otimizar com Timefold' });
    expect(dialogo).toHaveTextContent('mais inteligente');
    expect(dialogo).toHaveTextContent('um pouco mais cara');
    expect(fetch.mock.calls.some(([url]) => url === '/api/escalas/7/otimizar')).toBe(false);
    fireEvent.click(within(dialogo).getByRole('button', { name: 'Otimizar' }));

    expect(await screen.findByText('1 turnos gerados pela otimização (Timefold), cobrindo toda a demanda.')).toBeInTheDocument();
    expect(fetch.mock.calls.some(([url, init]) => url === '/api/escalas/7/otimizar' && init?.method === 'POST')).toBe(true);
    expect(screen.getByRole('button', { name: /Gerar \(rápido\)/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Comparar algoritmos/ })).toBeInTheDocument();
  });

  it('mostra o turno coberto em outro setor e deixa escolher o setor da habilidade', async () => {
    const versatil = { id: 6, nome: 'Juliana Costa', cargo: 'Repositora', setorId: 2, setorNome: 'Reposição', setorCor: '#8C5A2B',
      custoHora: 12, diasDisponiveis: [], ativo: true, habilidades: [1] };
    const enviados: Record<string, unknown>[] = [];
    const comHabilidade: Matriz = {
      ...matriz,
      funcionarios: [...matriz.funcionarios, versatil],
      turnos: [...matriz.turnos, { ...matriz.turnos[0], id: 11, funcionarioId: 6, setorId: 1 }],
    };
    mockFetch({
      'GET /api/escalas/7/matriz': { corpo: comHabilidade },
      'PUT /api/escalas/7/celula': (corpo) => (enviados.push(corpo as Record<string, unknown>), { corpo: comHabilidade }),
    });
    renderizar(<Routes><Route path="/escalas/:id" element={<MatrizEscala />} /></Routes>, '/escalas/7');

    const linha = (await screen.findByText('Juliana Costa')).closest('tr')!;
    expect(within(linha).getByText('↪ Frente de Caixa')).toBeInTheDocument();

    fireEvent.click(linha.querySelectorAll('td.celula')[1]); // terça, sem turno
    const setores = screen.getByRole('group', { name: 'Setor do turno' });
    expect(within(setores).getByRole('button', { name: /Reposição/ })).toHaveAttribute('aria-pressed', 'true');
    fireEvent.click(within(setores).getByRole('button', { name: /Frente de Caixa/ }));
    fireEvent.click(screen.getByRole('button', { name: /ABE/ }));

    await waitFor(() => expect(enviados).toHaveLength(1));
    expect(enviados[0]).toMatchObject({ funcionarioId: 6, data: '2026-11-10', setorId: 1 });
  });
});
