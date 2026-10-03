import { fireEvent, screen, waitFor } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { mockFetch, renderizar } from '../test/util';
import type { FaixaHoraria, Parametros } from '../types';
import ParametrosPage from './Parametros';

const parametros: Parametros = {
  id: 1, fatorDomingo: 1.5, fatorFeriado: 2, percentualTetoFolha: 6, jornadaReferenciaHoras: 7.33, modoFinanceiro: 'BLOQUEAR',
  jornadaNormalDiariaHoras: 8, jornadaMaximaDiariaHoras: 10, jornadaSemanalHoras: 44, interjornadaMinimaHoras: 11,
  maxDiasConsecutivos: 6, maxDomingosConsecutivos: 2, geracaoAutomatica: true, semanasAntecedencia: 1,
};
const faixas: FaixaHoraria[] = [
  { id: 1, inicio: '07:00:00', fim: '13:00:00' },
  { id: 2, inicio: '13:00:00', fim: '22:00:00' },
];

describe('Parâmetros', () => {
  it('configura a geração automática das escalas', async () => {
    const enviados: unknown[] = [];
    mockFetch({
      'GET /api/parametros': { corpo: parametros },
      'GET /api/movimento/faixas': { corpo: faixas },
      'PUT /api/parametros': (corpo) => (enviados.push(corpo), { corpo: parametros }),
    });
    renderizar(<ParametrosPage />);

    fireEvent.click(await screen.findByLabelText('Gerar automaticamente o rascunho das próximas semanas'));
    fireEvent.change(screen.getByLabelText('Semanas de antecedência'), { target: { value: '2' } });
    fireEvent.click(screen.getByRole('button', { name: 'Salvar' }));

    await waitFor(() => expect(enviados).toHaveLength(1));
    expect(enviados[0]).toMatchObject({ geracaoAutomatica: false, semanasAntecedencia: 2 });
  });

  it('edita as faixas de horário do movimento', async () => {
    const enviados: unknown[] = [];
    mockFetch({
      'GET /api/parametros': { corpo: parametros },
      'GET /api/movimento/faixas': { corpo: faixas },
      'PUT /api/movimento/faixas': (corpo) => (enviados.push(corpo), { corpo: faixas }),
    });
    renderizar(<ParametrosPage />);

    fireEvent.click(await screen.findByRole('button', { name: 'Adicionar faixa' }));
    const inicios = screen.getAllByLabelText(/Início da faixa/);
    expect(inicios).toHaveLength(3);
    fireEvent.change(screen.getAllByLabelText(/Fim da faixa/)[1], { target: { value: '18:00' } });
    fireEvent.change(inicios[2], { target: { value: '18:00' } });
    fireEvent.change(screen.getAllByLabelText(/Fim da faixa/)[2], { target: { value: '22:00' } });
    fireEvent.click(screen.getByRole('button', { name: 'Salvar faixas' }));

    await waitFor(() => expect(enviados).toHaveLength(1));
    expect(enviados[0]).toEqual([
      { inicio: '07:00', fim: '13:00' }, { inicio: '13:00', fim: '18:00' }, { inicio: '18:00', fim: '22:00' },
    ]);
  });

  it('remove uma faixa', async () => {
    const enviados: unknown[] = [];
    mockFetch({
      'GET /api/parametros': { corpo: parametros },
      'GET /api/movimento/faixas': { corpo: faixas },
      'PUT /api/movimento/faixas': (corpo) => (enviados.push(corpo), { corpo: faixas }),
    });
    renderizar(<ParametrosPage />);
    fireEvent.click((await screen.findAllByRole('button', { name: /Remover faixa/ }))[1]);
    fireEvent.click(screen.getByRole('button', { name: 'Salvar faixas' }));
    await waitFor(() => expect(enviados).toEqual([[{ inicio: '07:00', fim: '13:00' }]]));
  });
});
