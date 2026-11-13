import { screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { isoDe } from '../format';
import { mockFetch, renderizar } from '../test/util';
import type { Escala, Matriz, ResultadoValidacao } from '../types';
import Painel from './Painel';

const somar = (iso: string, dias: number) => {
  const d = new Date(`${iso}T12:00:00`);
  d.setDate(d.getDate() + dias);
  return isoDe(d);
};
const hoje = isoDe(new Date());
const segunda = somar(hoje, -((new Date(`${hoje}T12:00:00`).getDay() + 6) % 7));
const domingo = somar(segunda, 6);

const escala: Escala = {
  id: 7, nome: 'Escala da semana atual', dataInicio: segunda, dataFim: domingo, status: 'RASCUNHO', observacao: null,
  criadaEm: `${segunda}T06:00:00`, aprovadaEm: null, aprovadaPor: null, geradaAutomaticamente: true,
};

const painelVazio: ResultadoValidacao = { dias: [], violacoes: [], custoTotal: 0, tetoTotal: 0, erros: 0, alertas: 0, aprovavel: true };

const linha = (id: number, nome: string, setorNome: string) => ({
  id, nome, cargo: null, setorId: id, setorNome, setorCor: '#F26A1B', custoHora: 20, diasDisponiveis: [], ativo: true,
});

const matriz: Matriz = {
  escala,
  funcionarios: [linha(1, 'Ana Souza', 'Frente de Caixa'), linha(2, 'Bruno Lima', 'Açougue'), linha(3, 'Carla Dias', 'Padaria')],
  turnos: [{ id: 10, funcionarioId: 1, data: hoje, horaInicio: '07:00:00', horaFim: '15:20:00', intervaloMinutos: 60, rotulo: 'ABE', horas: 7.33, custo: 150 }],
  ausencias: [{ id: 5, funcionarioId: 3, funcionarioNome: 'Carla Dias', inicio: segunda, fim: domingo, motivo: 'FERIAS', observacao: null }],
  modelos: [],
  validacao: painelVazio,
};

function montar(escalas: Escala[]) {
  return mockFetch({
    'GET /api/escalas': { corpo: escalas },
    [`GET /api/motor/painel?inicio=${segunda}&fim=${domingo}`]: { corpo: painelVazio },
    'GET /api/escalas/7/matriz': { corpo: matriz },
  });
}

describe('Painel', () => {
  it('não fica preso em "Carregando" quando a lista de escalas falha', async () => {
    mockFetch({ 'GET /api/escalas': { status: 500, corpo: { mensagem: 'Indisponível' } } });
    renderizar(<Painel />);

    // Cai na semana atual e tenta o painel (não mapeado → 404), exibindo o erro ao usuário.
    expect(await screen.findByText('Erro 404')).toBeInTheDocument();
  });

  it('mostra a escala da semana atual: quem trabalha, em que horário, folgas e ausências', async () => {
    montar([escala]);
    renderizar(<Painel />);

    const secao = await screen.findByRole('region', { name: 'Escala da semana' });
    expect(within(secao).getByText('Escala da semana atual')).toBeInTheDocument();
    expect(within(secao).getByText('Rascunho')).toBeInTheDocument();
    expect(within(secao).getByRole('link', { name: 'Abrir matriz' })).toHaveAttribute('href', '/escalas/7');

    const ana = await within(secao).findByRole('row', { name: /Ana Souza/ });
    expect(within(ana).getByText('07:00–15:20')).toBeInTheDocument();
    expect(within(ana).getAllByText('Folga')).toHaveLength(6);
    expect(within(within(secao).getByRole('row', { name: /Carla Dias/ })).getAllByText('Férias')).toHaveLength(7);

    expect(within(secao).getByRole('columnheader', { current: 'date' })).toHaveTextContent('Hoje');
  });

  it('sem escala na semana, não mostra a grade', async () => {
    montar([]);
    renderizar(<Painel />);

    expect(await screen.findByText(/Nenhuma escala cobre este período/)).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'Escala da semana' })).not.toBeInTheDocument();
  });
});
