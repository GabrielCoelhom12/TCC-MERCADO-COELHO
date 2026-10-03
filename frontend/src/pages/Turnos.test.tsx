import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { mockFetch, renderizar } from '../test/util';
import type { TurnoModelo } from '../types';
import Turnos from './Turnos';

const abertura: TurnoModelo = {
  id: 1, nome: 'Abertura', sigla: 'ABE', horaInicio: '07:00:00', horaFim: '15:20:00',
  intervaloMinutos: 60, aplicacao: 'DIAS_UTEIS', horas: 7.33,
};

describe('Turnos', () => {
  it('lista os modelos cadastrados', async () => {
    mockFetch({ 'GET /api/turnos-modelo': { corpo: [abertura] } });
    renderizar(<Turnos />);

    const linha = (await screen.findByText('Abertura')).closest('tr')!;
    expect(within(linha).getByText('07:00 – 15:20')).toBeInTheDocument();
    expect(within(linha).getByText('7h20')).toBeInTheDocument();
    expect(within(linha).getByText('Segunda a sábado')).toBeInTheDocument();
  });

  it('cria um modelo novo pelo formulário', async () => {
    const enviados: unknown[] = [];
    mockFetch({
      'GET /api/turnos-modelo': { corpo: [] },
      'POST /api/turnos-modelo': (corpo) => (enviados.push(corpo), { status: 201, corpo: {} }),
    });
    renderizar(<Turnos />);

    fireEvent.click(await screen.findByText('+ Novo modelo'));
    const dialogo = screen.getByRole('dialog', { name: 'Novo modelo de turno' });
    fireEvent.change(within(dialogo).getByLabelText('Nome'), { target: { value: 'Noturno' } });
    fireEvent.change(within(dialogo).getByLabelText('Sigla'), { target: { value: 'not' } });
    fireEvent.click(within(dialogo).getByText('Salvar'));

    await waitFor(() => expect(enviados).toHaveLength(1));
    expect(enviados[0]).toMatchObject({ nome: 'Noturno', sigla: 'NOT', horaInicio: '07:00' });
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it('mostra o erro da API ao salvar', async () => {
    mockFetch({
      'GET /api/turnos-modelo': { corpo: [abertura] },
      'PUT /api/turnos-modelo/1': { status: 422, corpo: { mensagem: 'Início e fim do turno não podem ser iguais.' } },
    });
    renderizar(<Turnos />);

    fireEvent.click(await screen.findByText('Editar'));
    fireEvent.click(screen.getByText('Salvar'));
    expect(await screen.findByRole('status')).toHaveTextContent('não podem ser iguais');
  });

  it('exclui após confirmação', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const fetch = mockFetch({
      'GET /api/turnos-modelo': { corpo: [abertura] },
      'DELETE /api/turnos-modelo/1': { status: 204 },
    });
    renderizar(<Turnos />);

    fireEvent.click(await screen.findByText('Excluir'));
    await waitFor(() =>
      expect(fetch).toHaveBeenCalledWith('/api/turnos-modelo/1', expect.objectContaining({ method: 'DELETE' })),
    );
  });
});
