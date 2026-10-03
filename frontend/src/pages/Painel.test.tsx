import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { mockFetch, renderizar } from '../test/util';
import Painel from './Painel';

describe('Painel', () => {
  it('não fica preso em "Carregando" quando a lista de escalas falha', async () => {
    mockFetch({ 'GET /api/escalas': { status: 500, corpo: { mensagem: 'Indisponível' } } });
    renderizar(<Painel />);

    // Cai na semana atual e tenta o painel (não mapeado → 404), exibindo o erro ao usuário.
    expect(await screen.findByText('Erro 404')).toBeInTheDocument();
  });
});
