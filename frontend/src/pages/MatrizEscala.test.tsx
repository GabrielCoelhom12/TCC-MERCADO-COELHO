import { fireEvent, screen } from '@testing-library/react';
import { Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
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
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const otimizada: Geracao = { turnosCriados: 1, deficits: [], matriz };
    const fetch = mockFetch({
      'GET /api/escalas/7/matriz': { corpo: matriz },
      'POST /api/escalas/7/otimizar': { corpo: otimizada },
    });
    renderizar(<Routes><Route path="/escalas/:id" element={<MatrizEscala />} /></Routes>, '/escalas/7');

    fireEvent.click(await screen.findByRole('button', { name: /Otimizar com Timefold/ }));

    expect(await screen.findByText('1 turnos gerados pela otimização (Timefold), cobrindo toda a demanda.')).toBeInTheDocument();
    expect(fetch.mock.calls.some(([url, init]) => url === '/api/escalas/7/otimizar' && init?.method === 'POST')).toBe(true);
    expect(screen.getByRole('button', { name: /Gerar \(rápido\)/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Comparar algoritmos/ })).toBeInTheDocument();
  });
});
