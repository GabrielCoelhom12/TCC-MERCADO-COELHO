import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { isoDe } from '../format';
import { mockFetch, renderizar } from '../test/util';
import type { Escala, Matriz, SugestaoSubstituicao, TurnoDTO } from '../types';
import TrocaColaborador from './TrocaColaborador';

const escala: Escala = {
  id: 7, nome: 'Semana', dataInicio: '2026-11-09', dataFim: '2026-11-15', status: 'RASCUNHO', observacao: null,
  criadaEm: '2026-11-02T06:00:00', aprovadaEm: null, aprovadaPor: null, geradaAutomaticamente: true,
};

const turno: TurnoDTO = {
  id: 10, funcionarioId: 1, data: '2026-11-11', horaInicio: '07:00:00', horaFim: '15:20:00', intervaloMinutos: 60,
  rotulo: 'ABE', horas: 7.33, custo: 150,
};

const sugestao: SugestaoSubstituicao = {
  titular: {
    funcionarioId: 1, nome: 'Ana Souza', setorNome: 'Frente de Caixa', setorCor: '#F26A1B', horasSemana: 14.67, limiteSemanal: 44,
    diasEscalados: ['2026-11-09', '2026-11-11'],
    diasDisponiveis: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'],
  },
  candidatos: [
    {
      funcionarioId: 2, nome: 'Bruno Lima', setorNome: 'Frente de Caixa', setorCor: '#F26A1B', mesmoSetor: true,
      horasSemana: 7.33, horasComTroca: 14.67, custoTurno: 88, apto: true, impedimentos: [], alertas: [],
    },
    {
      funcionarioId: 3, nome: 'Davi Rocha', setorNome: 'Açougue', setorCor: '#2563eb', mesmoSetor: false,
      horasSemana: 40, horasComTroca: 47.33, custoTurno: 95, apto: true, impedimentos: [],
      alertas: ['Semana de 09/11 soma 47h20, acima de 44h semanais.'],
    },
    {
      funcionarioId: 4, nome: 'Carla Dias', setorNome: 'Frente de Caixa', setorCor: '#F26A1B', mesmoSetor: true,
      horasSemana: 0, horasComTroca: 7.33, custoTurno: 80, apto: false,
      impedimentos: ['Escalado durante ausência (ferias de 09/11 a 15/11).'], alertas: [],
    },
  ],
};

const rota = 'GET /api/escalas/7/turnos/10/substitutos';

function abrir(e: Escala = escala, onTrocado = vi.fn(), onFechar = vi.fn(), t: TurnoDTO = turno) {
  renderizar(<TrocaColaborador escala={e} turno={t} onFechar={onFechar} onTrocado={onTrocado} />);
  return { onTrocado, onFechar };
}

describe('TrocaColaborador', () => {
  it('mostra a disponibilidade do colaborador na semana', async () => {
    mockFetch({ [rota]: { corpo: sugestao } });
    abrir();

    const dialogo = await screen.findByRole('dialog', { name: 'Ana Souza' });
    expect(await within(dialogo).findByText('14h40 de 44h00')).toBeInTheDocument();
    expect(within(dialogo).getByText(/Frente de Caixa/)).toBeInTheDocument();
    expect(within(dialogo).getByText(/Qua 11\/11 · 07:00–15:20/)).toBeInTheDocument();

    const semana = within(dialogo).getByRole('list', { name: 'Dias da semana' });
    expect(within(semana).getByTitle('Seg: escalado')).toBeInTheDocument();
    expect(within(semana).getByTitle('Ter: folga')).toBeInTheDocument();
    expect(within(semana).getByTitle('Dom: indisponível')).toBeInTheDocument();
    expect(within(dialogo).getByText('29h20 livres até o limite semanal.')).toBeInTheDocument();
  });

  it('lista quem pode cobrir o turno e explica quem não pode', async () => {
    mockFetch({ [rota]: { corpo: sugestao } });
    abrir();

    fireEvent.click(await screen.findByRole('button', { name: 'Trocar colaborador' }));

    const aptos = screen.getByRole('list', { name: 'Podem cobrir este turno' });
    const itens = within(aptos).getAllByRole('listitem');
    expect(itens).toHaveLength(2);
    expect(itens[0]).toHaveTextContent('Bruno Lima');
    expect(itens[0]).toHaveTextContent('Mesmo setor');
    expect(itens[0]).toHaveTextContent('7h20 → 14h40 na semana');
    expect(itens[1]).toHaveTextContent('acima de 44h semanais');

    expect(screen.getByText('1 colega não pode cobrir')).toBeInTheDocument();
    expect(screen.getByText(/Carla Dias/).closest('li')).toHaveTextContent('Escalado durante ausência');
  });

  it('troca o colaborador e devolve a matriz atualizada', async () => {
    const matriz = { escala } as Matriz;
    const fetch = mockFetch({
      [rota]: { corpo: sugestao },
      'POST /api/escalas/7/turnos/10/substituir': { corpo: matriz },
    });
    const { onTrocado } = abrir();

    fireEvent.click(await screen.findByRole('button', { name: 'Trocar colaborador' }));
    fireEvent.click(screen.getByRole('button', { name: 'Passar turno para Bruno Lima' }));

    await waitFor(() => expect(onTrocado).toHaveBeenCalledWith(matriz));
    const envio = fetch.mock.calls.find(([url]) => url === '/api/escalas/7/turnos/10/substituir');
    expect(JSON.parse(String(envio?.[1]?.body))).toEqual({ funcionarioId: 2 });
    expect(await screen.findByText('Turno de Ana Souza passado para Bruno Lima.')).toBeInTheDocument();
  });

  it('mostra o motivo quando a troca é recusada', async () => {
    mockFetch({
      [rota]: { corpo: sugestao },
      'POST /api/escalas/7/turnos/10/substituir': { status: 422, corpo: { mensagem: 'Alocação bloqueada: o dia ultrapassaria o limite.' } },
    });
    const { onTrocado } = abrir();

    fireEvent.click(await screen.findByRole('button', { name: 'Trocar colaborador' }));
    fireEvent.click(screen.getByRole('button', { name: 'Passar turno para Bruno Lima' }));

    expect(await screen.findByText('Alocação bloqueada: o dia ultrapassaria o limite.')).toBeInTheDocument();
    expect(onTrocado).not.toHaveBeenCalled();
  });

  it('em escala aprovada permite a troca de urgência e avisa que ela fica registrada', async () => {
    const hoje = isoDe(new Date());
    mockFetch({ [rota]: { corpo: sugestao } });
    abrir({ ...escala, status: 'APROVADA' }, vi.fn(), vi.fn(), { ...turno, data: hoje });

    expect(await screen.findByText(/Escala aprovada: a troca vale na hora/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Trocar colaborador' }));
    expect(screen.getByRole('button', { name: 'Passar turno para Bruno Lima' })).toBeInTheDocument();
  });

  it('em escala aprovada não troca turno de dia que já passou', async () => {
    mockFetch({ [rota]: { corpo: sugestao } });
    abrir({ ...escala, status: 'APROVADA' }, vi.fn(), vi.fn(), { ...turno, data: '2020-03-04' });

    expect(await screen.findByText('Este turno já passou e não pode mais ser trocado.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Trocar colaborador' })).not.toBeInTheDocument();
  });
});
