import { fireEvent, screen, waitFor } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { mockFetch, renderizar } from '../test/util';
import type { Setor } from '../types';
import Setores from './Setores';

const caixa: Setor = { id: 1, nome: 'Frente de Caixa', cor: '#F26A1B', ativo: true, minimoPorDia: { SEGUNDA: 4 }, clientesPorColaboradorHora: null };

describe('Setores', () => {
  it('configura a capacidade de atendimento para o setor acompanhar o movimento', async () => {
    const enviados: unknown[] = [];
    mockFetch({
      'GET /api/setores': { corpo: [caixa] },
      'PUT /api/setores/1': (corpo) => (enviados.push(corpo), { corpo: caixa }),
    });
    renderizar(<Setores />);

    const campo = await screen.findByLabelText('Clientes por hora por pessoa — Frente de Caixa');
    expect(campo).toHaveValue(null);
    fireEvent.change(campo, { target: { value: '30' } });
    fireEvent.click(screen.getByRole('button', { name: 'Salvar alterações' }));

    await waitFor(() => expect(enviados).toHaveLength(1));
    expect(enviados[0]).toMatchObject({ clientesPorColaboradorHora: 30 });
  });

  it('apagar a capacidade volta o setor para a demanda fixa', async () => {
    const enviados: unknown[] = [];
    mockFetch({
      'GET /api/setores': { corpo: [{ ...caixa, clientesPorColaboradorHora: 30 }] },
      'PUT /api/setores/1': (corpo) => (enviados.push(corpo), { corpo: caixa }),
    });
    renderizar(<Setores />);

    fireEvent.change(await screen.findByLabelText('Clientes por hora por pessoa — Frente de Caixa'), { target: { value: '' } });
    fireEvent.click(screen.getByRole('button', { name: 'Salvar alterações' }));
    await waitFor(() => expect(enviados).toHaveLength(1));
    expect(enviados[0]).toMatchObject({ clientesPorColaboradorHora: null });
  });
});
