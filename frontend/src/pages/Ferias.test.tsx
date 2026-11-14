import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { isoDe } from '../format';
import { mockFetch, renderizar } from '../test/util';
import type { Ausencia, Funcionario } from '../types';
import Ferias from './Ferias';

const somar = (dias: number) => {
  const d = new Date();
  d.setDate(d.getDate() + dias);
  return isoDe(d);
};

const setor = (id: number, nome: string) => ({ id, nome, cor: '#F26A1B', ativo: true }) as Funcionario['setor'];

const funcionario = (id: number, nome: string, setorNome: string, ativo = true): Funcionario => ({
  id, nome, matricula: null, cargo: null, setor: setor(id, setorNome), salarioMensal: 2000, cargaHorariaMensal: 220,
  percentualEncargos: 38, ativo, diasDisponiveis: [], custoHora: 12, foto: null,
});

const funcionarios = [
  funcionario(1, 'Ana Souza', 'Frente de Caixa'),
  funcionario(2, 'Bruno Lima', 'Açougue'),
  funcionario(3, 'Carla Dias', 'Padaria'),
  funcionario(4, 'Edu Inativo', 'Padaria', false),
];

const ferias = (id: number, funcionarioId: number, inicio: string, fim: string): Ausencia => ({
  id, funcionarioId, funcionarioNome: funcionarios[funcionarioId - 1].nome, inicio, fim, motivo: 'FERIAS', observacao: null,
});

const lista = [
  ferias(2, 2, somar(20), somar(34)),
  ferias(1, 1, somar(-3), somar(10)),
  ferias(3, 3, somar(-60), somar(-46)),
];

const cobertura = (turnoId: number, data: string, substituto: [number, string, string] | null) => ({
  turnoId, escalaId: 7, escalaNome: 'Escala 03/03 a 09/03', data, horaInicio: '07:00:00', horaFim: '15:20:00',
  substitutoId: substituto?.[0] ?? null, substitutoNome: substituto?.[1] ?? null, substitutoSetor: substituto?.[2] ?? null,
});
const coberturas = [cobertura(50, '2031-03-04', [3, 'Carla Dias', 'Padaria']), cobertura(51, '2031-03-05', null)];

const NOVO_INICIO = '2031-03-03';
const NOVO_FIM = '2031-03-17';
const analiseNova = `GET /api/ferias/analise?funcionarioId=2&inicio=${NOVO_INICIO}&fim=${NOVO_FIM}`;

function montar(extra: Parameters<typeof mockFetch>[0] = {}) {
  return mockFetch({
    'GET /api/ferias': { corpo: lista },
    'GET /api/funcionarios': { corpo: funcionarios },
    ...extra,
  });
}

async function preencherNovo() {
  fireEvent.click(await screen.findByRole('button', { name: /Registrar férias/ }));
  const dialogo = screen.getByRole('dialog', { name: 'Registrar férias' });
  fireEvent.change(within(dialogo).getByLabelText('Colaborador'), { target: { value: '2' } });
  fireEvent.change(within(dialogo).getByLabelText('Início'), { target: { value: NOVO_INICIO } });
  fireEvent.click(within(dialogo).getByRole('button', { name: '15 dias' }));
  return dialogo;
}

describe('Férias', () => {
  it('separa as férias em andamento, programadas e encerradas', async () => {
    montar();
    renderizar(<Ferias />);

    const andamento = await screen.findByRole('region', { name: /Em andamento/ });
    expect(within(andamento).getByText('Ana Souza')).toBeInTheDocument();
    expect(within(andamento).getByText('14 dias')).toBeInTheDocument();
    expect(within(andamento).getByText(`volta em ${somar(11).slice(8, 10)}/${somar(11).slice(5, 7)}`)).toBeInTheDocument();

    const programadas = screen.getByRole('region', { name: /Programadas/ });
    expect(within(programadas).getByText('Bruno Lima')).toBeInTheDocument();
    expect(within(programadas).getByText('começa em 20 dias')).toBeInTheDocument();

    expect(screen.getByText('Encerradas (1)')).toBeInTheDocument();
    expect(screen.getByText('De férias hoje').closest('.kpi')).toHaveTextContent('1');
    expect(screen.getByText('Começam em 30 dias').closest('.kpi')).toHaveTextContent('1');
  });

  it('filtra pelo nome do colaborador', async () => {
    montar();
    renderizar(<Ferias />);

    fireEvent.change(await screen.findByPlaceholderText('Buscar colaborador'), { target: { value: 'bru' } });

    expect(screen.getByText('Bruno Lima')).toBeInTheDocument();
    expect(screen.queryByText('Ana Souza')).not.toBeInTheDocument();
  });

  it('registra férias mostrando antes quem cobre cada turno já escalado', async () => {
    const fetch = montar({
      [analiseNova]: {
        corpo: { dias: 15, retorno: '2031-03-18', erros: [], alertas: [], valida: true, coberturas },
      },
      'POST /api/ferias': { status: 201, corpo: { ferias: ferias(9, 2, NOVO_INICIO, NOVO_FIM), coberturas } },
    });
    renderizar(<Ferias />);

    const dialogo = await preencherNovo();
    expect(within(dialogo).getByLabelText('Fim')).toHaveValue(NOVO_FIM);
    expect(await within(dialogo).findByText('15 dias · retorno em 18/03/2031')).toBeInTheDocument();
    const plano = within(dialogo).getByRole('list', { name: 'Quem cobre os turnos já escalados' });
    const itens = within(plano).getAllByRole('listitem');
    expect(itens[0]).toHaveTextContent('Ter 04/03 · 07:00–15:20');
    expect(itens[0]).toHaveTextContent('Carla Dias');
    expect(itens[1]).toHaveTextContent('Ninguém pode cobrir: o turno será retirado');
    // Inativos não aparecem para seleção.
    expect(within(dialogo).queryByRole('option', { name: /Edu Inativo/ })).not.toBeInTheDocument();

    fireEvent.click(within(dialogo).getByRole('button', { name: 'Salvar' }));

    expect(await screen.findByText('Férias de Bruno Lima registradas · 1 turno repassado · 1 turno retirado sem substituto.'))
      .toBeInTheDocument();
    const envio = fetch.mock.calls.find(([url, init]) => url === '/api/ferias' && init?.method === 'POST');
    expect(JSON.parse(String(envio?.[1]?.body))).toEqual({ funcionarioId: 2, inicio: NOVO_INICIO, fim: NOVO_FIM, observacao: '' });
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(fetch.mock.calls.filter(([url, init]) => url === '/api/ferias' && init?.method === 'GET')).toHaveLength(2);
  });

  it('não deixa salvar quando o período fere as regras', async () => {
    montar({
      [analiseNova]: {
        corpo: {
          dias: 15, retorno: '2031-03-18', erros: ['As férias não podem começar nos dois dias que antecedem feriado.'], alertas: [],
          valida: false, coberturas: [],
        },
      },
    });
    renderizar(<Ferias />);

    const dialogo = await preencherNovo();

    expect(await within(dialogo).findByText(/não podem começar/)).toBeInTheDocument();
    expect(within(dialogo).getByRole('button', { name: 'Salvar' })).toBeDisabled();
  });

  it('edita e remove férias', async () => {
    const fetch = montar({
      [`GET /api/ferias/analise?funcionarioId=1&inicio=${somar(-3)}&fim=${somar(10)}&ignorar=1`]: {
        corpo: { dias: 14, retorno: somar(11), erros: [], alertas: [], valida: true, coberturas: [] },
      },
      'DELETE /api/ferias/1': { status: 204 },
    });
    renderizar(<Ferias />);

    fireEvent.click(await screen.findByRole('button', { name: 'Editar férias de Ana Souza' }));
    const dialogo = screen.getByRole('dialog', { name: 'Editar férias' });
    expect(within(dialogo).getByLabelText('Colaborador')).toHaveValue('1');
    expect(within(dialogo).getByLabelText('Início')).toHaveValue(somar(-3));
    expect(await within(dialogo).findByText(/14 dias · retorno em/)).toBeInTheDocument();

    fireEvent.click(within(dialogo).getByRole('button', { name: 'Remover' }));

    expect(await screen.findByText('Férias removidas.')).toBeInTheDocument();
    expect(fetch.mock.calls.some(([url, init]) => url === '/api/ferias/1' && init?.method === 'DELETE')).toBe(true);
  });
});
