import { fireEvent, screen, waitFor } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { mockFetch, renderizar } from '../test/util';
import type { Escala } from '../types';
import Escalas from './Escalas';

const escala: Escala = {
  id: 7, nome: 'Semana 1', dataInicio: '2026-10-05', dataFim: '2026-10-11', status: 'APROVADA',
  observacao: 'Gerada pelo motor', criadaEm: '2026-10-01T10:00:00', aprovadaEm: null, aprovadaPor: 'gestor',
  geradaAutomaticamente: false,
};

describe('Escalas', () => {
  it('mostra mensagem quando não há escalas', async () => {
    mockFetch({ 'GET /api/escalas': { corpo: [] } });
    renderizar(<Escalas />);
    expect(await screen.findByText('Nenhuma escala cadastrada.')).toBeInTheDocument();
  });

  it('lista escalas com período e status', async () => {
    mockFetch({ 'GET /api/escalas': { corpo: [escala, { ...escala, id: 8, nome: 'Semana 2', status: 'RASCUNHO' }] } });
    renderizar(<Escalas />);

    expect(await screen.findByText('Semana 1')).toBeInTheDocument();
    expect(screen.getAllByText('05/10/2026 a 11/10/2026')).toHaveLength(2);
    expect(screen.getByText('Aprovada por gestor')).toBeInTheDocument();
    expect(screen.getByText('Rascunho')).toBeInTheDocument();
    expect(screen.queryByText('Automática')).not.toBeInTheDocument();
    // Só rascunhos podem ser excluídos.
    expect(screen.getAllByText('Excluir')).toHaveLength(1);
  });

  it('identifica o rascunho criado automaticamente pela previsão', async () => {
    mockFetch({ 'GET /api/escalas': { corpo: [{ ...escala, status: 'RASCUNHO', geradaAutomaticamente: true }] } });
    renderizar(<Escalas />);
    expect(await screen.findByText('Automática')).toBeInTheDocument();
  });

  it('sugere a semana seguinte à última escala e cria com geração automática', async () => {
    const enviados: unknown[] = [];
    mockFetch({
      'GET /api/escalas': { corpo: [escala] },
      'POST /api/escalas': (corpo) => (enviados.push(corpo), { status: 201, corpo: { ...escala, id: 9 } }),
      'POST /api/escalas/9/gerar': { corpo: { turnosCriados: 40, deficits: [] } },
    });
    renderizar(<Escalas />);

    fireEvent.click(await screen.findByText('+ Nova escala'));
    expect(screen.getByLabelText('Início')).toHaveValue('2026-10-12');
    expect(screen.getByLabelText('Fim')).toHaveValue('2026-10-18');

    fireEvent.click(screen.getByText('Criar'));
    await waitFor(() => expect(enviados).toHaveLength(1));
    expect(await screen.findByRole('status')).toHaveTextContent('Escala gerada: 40 turnos.');
  });
});
