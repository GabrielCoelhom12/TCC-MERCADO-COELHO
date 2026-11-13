import { fireEvent, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { isoDe } from '../format';
import { mockFetch, renderizar } from '../test/util';
import type { Escala, Matriz, ResultadoValidacao, ResumoDia } from '../types';
import Painel from './Painel';

const somar = (iso: string, dias: number) => {
  const d = new Date(`${iso}T12:00:00`);
  d.setDate(d.getDate() + dias);
  return isoDe(d);
};
const hoje = isoDe(new Date());
const segunda = somar(hoje, -((new Date(`${hoje}T12:00:00`).getDay() + 6) % 7));
const domingo = somar(segunda, 6);
/** Um dia da semana atual que não é hoje. */
const outroDia = hoje === segunda ? somar(segunda, 1) : segunda;

const escala: Escala = {
  id: 7, nome: 'Escala da semana atual', dataInicio: segunda, dataFim: domingo, status: 'RASCUNHO', observacao: null,
  criadaEm: `${segunda}T06:00:00`, aprovadaEm: null, aprovadaPor: null, geradaAutomaticamente: true,
};

const resumo = (data: string, extra: Partial<ResumoDia> = {}): ResumoDia => ({
  data, tipoDia: 'Dia', feriado: null, fator: 1, receitaProjetada: 40000, tetoFolha: 2400, custoEscala: 1500,
  custoMedioOperador: 150, operadores: 10, lMin: 8, lMax: 16, situacao: 'OK', setores: [], clientesPrevistos: null, pico: null,
  ...extra,
});

const painel: ResultadoValidacao = {
  dias: Array.from({ length: 7 }, (_, i) => somar(segunda, i)).map((d) =>
    resumo(d, d === outroDia ? { custoEscala: 1234, operadores: 0, situacao: 'ABAIXO_MINIMO' } : {})),
  violacoes: [
    { severidade: 'ERRO', regra: 'SETOR_ABAIXO_MINIMO', data: outroDia, funcionarioId: null, funcionarioNome: null, setorId: 1, mensagem: 'Frente de Caixa: 0 de 3 operadores mínimos.' },
    { severidade: 'ALERTA', regra: 'HORA_EXTRA_DIARIA', data: hoje, funcionarioId: 1, funcionarioNome: 'Ana Souza', setorId: 1, mensagem: 'Jornada com hora extra hoje.' },
  ],
  custoTotal: 10234, tetoTotal: 16800, erros: 1, alertas: 1, aprovavel: false,
};

const linha = (id: number, nome: string, setorNome: string) => ({
  id, nome, cargo: null, setorId: id, setorNome, setorCor: '#F26A1B', custoHora: 20, diasDisponiveis: [], ativo: true,
});

const matriz: Matriz = {
  escala,
  funcionarios: [linha(1, 'Ana Souza', 'Frente de Caixa'), linha(2, 'Bruno Lima', 'Açougue'), linha(3, 'Carla Dias', 'Padaria')],
  turnos: [
    { id: 11, funcionarioId: 2, data: hoje, horaInicio: '13:40:00', horaFim: '22:00:00', intervaloMinutos: 60, rotulo: 'FEC', horas: 7.33, custo: 150 },
    { id: 10, funcionarioId: 1, data: hoje, horaInicio: '07:00:00', horaFim: '15:20:00', intervaloMinutos: 60, rotulo: 'ABE', horas: 7.33, custo: 150 },
  ],
  ausencias: [{ id: 5, funcionarioId: 3, funcionarioNome: 'Carla Dias', inicio: segunda, fim: domingo, motivo: 'FERIAS', observacao: null }],
  modelos: [],
  validacao: painel,
};

function montar(escalas: Escala[]) {
  return mockFetch({
    'GET /api/escalas': { corpo: escalas },
    [`GET /api/motor/painel?inicio=${segunda}&fim=${domingo}`]: { corpo: painel },
    'GET /api/escalas/7/matriz': { corpo: matriz },
  });
}

const detalheDoDia = () => screen.findByRole('tabpanel');

describe('Painel', () => {
  it('não fica preso em "Carregando" quando a lista de escalas falha', async () => {
    mockFetch({ 'GET /api/escalas': { status: 500, corpo: { mensagem: 'Indisponível' } } });
    renderizar(<Painel />);

    // Cai na semana atual e tenta o painel (não mapeado → 404), exibindo o erro ao usuário.
    expect(await screen.findByText('Erro 404')).toBeInTheDocument();
  });

  it('mostra os dias da semana e abre no de hoje, com a equipe por horário de entrada e os ausentes', async () => {
    montar([escala]);
    renderizar(<Painel />);

    const dias = await screen.findAllByRole('tab');
    expect(dias).toHaveLength(7);
    const aba = screen.getByRole('tab', { selected: true });
    expect(aba).toHaveTextContent('Hoje');

    const detalhe = await detalheDoDia();
    expect(await within(detalhe).findByText('2 pessoas')).toBeInTheDocument();
    const nomes = within(detalhe).getAllByRole('listitem').map((i) => i.textContent);
    expect(nomes[0]).toContain('Ana Souza');
    expect(nomes[0]).toContain('07:00–15:20');
    expect(nomes[1]).toContain('Bruno Lima');
    expect(within(detalhe).getByText(/Ausentes/).closest('p')).toHaveTextContent('Carla Dias (Férias)');
    expect(within(detalhe).getByText('Jornada com hora extra hoje.')).toBeInTheDocument();
    expect(within(detalhe).queryByText(/0 de 3 operadores/)).not.toBeInTheDocument();
  });

  it('ao escolher outro dia, mostra a equipe, os custos e os alertas só dele', async () => {
    montar([escala]);
    renderizar(<Painel />);

    const abas = await screen.findAllByRole('tab');
    const indice = Array.from({ length: 7 }, (_, i) => somar(segunda, i)).indexOf(outroDia);
    fireEvent.click(abas[indice]);

    expect(abas[indice]).toHaveAttribute('aria-selected', 'true');
    const detalhe = await detalheDoDia();
    expect(await within(detalhe).findByText('Ninguém escalado neste dia.')).toBeInTheDocument();
    expect(within(detalhe).getByText(/1\.234,00/)).toBeInTheDocument();
    expect(within(detalhe).getByText('Frente de Caixa: 0 de 3 operadores mínimos.')).toBeInTheDocument();
    expect(within(detalhe).queryByText('Jornada com hora extra hoje.')).not.toBeInTheDocument();
  });

  it('as setas do teclado trocam o dia', async () => {
    montar([escala]);
    renderizar(<Painel />);

    const selecionada = await screen.findByRole('tab', { selected: true });
    const abas = screen.getAllByRole('tab');
    const atual = abas.indexOf(selecionada);
    const proxima = atual === 6 ? 5 : atual + 1;
    fireEvent.keyDown(selecionada, { key: atual === 6 ? 'ArrowLeft' : 'ArrowRight' });

    expect(abas[proxima]).toHaveAttribute('aria-selected', 'true');
  });

  it('a grade da semana fica recolhida até ser pedida', async () => {
    montar([escala]);
    renderizar(<Painel />);

    const botao = await screen.findByRole('button', { name: 'Ver grade da semana' });
    expect(botao).toHaveAttribute('aria-expanded', 'false');
    expect(screen.queryByRole('table')).not.toBeInTheDocument();

    fireEvent.click(botao);

    const grade = await screen.findByRole('table');
    const ana = await within(grade).findByRole('row', { name: /Ana Souza/ });
    expect(within(ana).getByText('07:00–15:20')).toBeInTheDocument();
    expect(within(ana).getAllByText('Folga')).toHaveLength(6);
    expect(within(within(grade).getByRole('row', { name: /Carla Dias/ })).getAllByText('Férias')).toHaveLength(7);
    expect(within(grade).getByRole('columnheader', { current: 'date' })).toHaveTextContent('Hoje');
    expect(screen.getByRole('link', { name: 'Abrir matriz' })).toHaveAttribute('href', '/escalas/7');
  });

  it('sem escala na semana, avisa e continua mostrando os custos de cada dia', async () => {
    montar([]);
    renderizar(<Painel />);

    expect(await screen.findByText('Nenhuma escala cobre esta semana.')).toBeInTheDocument();
    expect(await screen.findAllByRole('tab')).toHaveLength(7);
    expect(screen.queryByRole('button', { name: 'Ver grade da semana' })).not.toBeInTheDocument();
  });
});
