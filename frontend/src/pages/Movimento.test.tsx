import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { isoDe } from '../format';
import { mockFetch, renderizar } from '../test/util';
import type { FaixaHoraria, Fechamento, PrevisaoDiaria } from '../types';
import Movimento, { periodoFechamentos, periodoPrevisao } from './Movimento';

const faixas: FaixaHoraria[] = [
  { id: 1, inicio: '07:00:00', fim: '10:00:00' },
  { id: 2, inicio: '16:00:00', fim: '19:00:00' },
];

const hoje = isoDe(new Date());
const ontem = (() => { const d = new Date(); d.setDate(d.getDate() - 1); return isoDe(d); })();

const fechamentoOntem: Fechamento = {
  data: ontem, clientes: 300, vendas: 18000, observacao: null, registradoPor: 'gestor', registradoEm: `${ontem}T22:10:00`,
  faixas: [{ inicio: '07:00:00', fim: '10:00:00', clientes: 100, vendas: 6000 }, { inicio: '16:00:00', fim: '19:00:00', clientes: 200, vendas: 12000 }],
};

const diaPrevisto = (extra: Partial<PrevisaoDiaria> = {}): PrevisaoDiaria => ({
  data: periodoPrevisao().inicio, tipoDia: 'Sexta', feriado: null, receitaProjetada: 48200, origemReceita: 'PREVISAO',
  previsao: {
    data: periodoPrevisao().inicio, vendas: 48200, clientes: 820, amostras: 8,
    faixas: [{ inicio: '07:00:00', fim: '10:00:00', clientes: 220 }, { inicio: '16:00:00', fim: '19:00:00', clientes: 600 }],
    pico: { inicio: '16:00:00', fim: '19:00:00', clientes: 600 },
    explicacao: ['Média das últimas 8 sextas: R$ 46.000,00 e 790 clientes.', 'Início do mês (pagamento): +12% (aprendido de 9 dias).'],
  },
  setores: [
    { setorId: 1, nome: 'Frente de Caixa', cor: '#F26A1B', minimoManual: 4, necessarios: 7, turnosSugeridos: ['FEC', 'FEC'], faixas: [{ inicio: '16:00:00', fim: '19:00:00', clientes: 600, pessoas: 7 }] },
    { setorId: 2, nome: 'Padaria', cor: '#E0A526', minimoManual: 2, necessarios: 2, turnosSugeridos: [], faixas: [] },
  ],
  ...extra,
});

function montar(rotas: Parameters<typeof mockFetch>[0] = {}, previsao: PrevisaoDiaria[] = [diaPrevisto()]) {
  const f = periodoFechamentos();
  const p = periodoPrevisao();
  return mockFetch({
    'GET /api/movimento/faixas': { corpo: faixas },
    [`GET /api/movimento/fechamentos?inicio=${f.inicio}&fim=${f.fim}`]: { corpo: [fechamentoOntem] },
    [`GET /api/movimento/fechamentos/${ontem}`]: { corpo: fechamentoOntem },
    [`GET /api/movimento/previsao?inicio=${p.inicio}&fim=${p.fim}`]: { corpo: previsao },
    ...rotas,
  });
}

describe('Movimento', () => {
  it('lança o fechamento de hoje por faixa de horário', async () => {
    const enviados: unknown[] = [];
    montar({ [`PUT /api/movimento/fechamentos/${hoje}`]: (corpo: unknown) => (enviados.push(corpo), { corpo: fechamentoOntem }) });
    renderizar(<Movimento />);

    expect(await screen.findByLabelText('Data do fechamento')).toHaveValue(hoje);
    fireEvent.change(screen.getByLabelText('Clientes 07:00–10:00'), { target: { value: '120' } });
    fireEvent.change(screen.getByLabelText('Vendas 07:00–10:00'), { target: { value: '7200.50' } });
    fireEvent.change(screen.getByLabelText('Clientes 16:00–19:00'), { target: { value: '300' } });
    fireEvent.change(screen.getByLabelText('Vendas 16:00–19:00'), { target: { value: '18000' } });
    expect(screen.getByText('420 clientes')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Salvar fechamento' }));
    await waitFor(() => expect(enviados).toHaveLength(1));
    expect(enviados[0]).toEqual({
      observacao: '',
      faixas: [
        { inicio: '07:00:00', fim: '10:00:00', clientes: 120, vendas: 7200.5 },
        { inicio: '16:00:00', fim: '19:00:00', clientes: 300, vendas: 18000 },
      ],
    });
    expect(await screen.findByRole('status')).toHaveTextContent('Fechamento salvo');
  });

  it('mostra os últimos dias, destaca os pendentes e abre um lançamento existente', async () => {
    montar();
    renderizar(<Movimento />);

    const lista = await screen.findByRole('list', { name: 'Últimos fechamentos' });
    expect(within(lista).getAllByText('Pendente').length).toBeGreaterThan(0);
    fireEvent.click(within(lista).getByRole('button', { name: /300 clientes/ }));

    expect(await screen.findByLabelText('Data do fechamento')).toHaveValue(ontem);
    await waitFor(() => expect(screen.getByLabelText('Clientes 16:00–19:00')).toHaveValue(200));
    expect(screen.getByText(/Lançado por gestor/)).toBeInTheDocument();
  });

  it('mostra a previsão com o pico, o porquê e as pessoas necessárias', async () => {
    montar();
    renderizar(<Movimento />);

    const cartao = (await screen.findByText('Média das últimas 8 sextas: R$ 46.000,00 e 790 clientes.')).closest('article')!;
    expect(within(cartao).getByText('≈ 820 clientes')).toBeInTheDocument();
    expect(within(cartao).getByText('Pico 16h–19h')).toBeInTheDocument();
    expect(within(cartao).getByText('Previsto pelo histórico')).toBeInTheDocument();
    expect(within(cartao).getByText('Frente de Caixa')).toBeInTheDocument();
    expect(within(cartao).getByText('7 pessoas')).toBeInTheDocument();
    // setores de demanda fixa não aparecem no cartão
    expect(within(cartao).queryByText('Padaria')).not.toBeInTheDocument();
  });

  it('sem histórico, avisa que usa a configuração manual', async () => {
    montar({}, [diaPrevisto({ origemReceita: 'PADRAO', previsao: null, receitaProjetada: 38000 })]);
    renderizar(<Movimento />);
    expect(await screen.findByText(/Sem histórico suficiente/)).toBeInTheDocument();
    expect(screen.getByText('Configuração manual')).toBeInTheDocument();
  });

  it('recusa valores negativos antes de enviar', async () => {
    const fetch = montar();
    renderizar(<Movimento />);
    fireEvent.change(await screen.findByLabelText('Clientes 07:00–10:00'), { target: { value: '-5' } });
    expect(screen.getByRole('button', { name: 'Salvar fechamento' })).toBeDisabled();
    expect(fetch.mock.calls.some(([, init]) => init?.method === 'PUT')).toBe(false);
  });
});
