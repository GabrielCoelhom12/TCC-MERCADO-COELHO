import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { isoDe } from '../format';
import { logar, mockFetch, renderizar } from '../test/util';
import type { Ausencia, CoberturaTurno, Funcionario, MotivoAusencia } from '../types';
import Afastamentos from './Afastamentos';

const somar = (dias: number) => {
  const d = new Date();
  d.setDate(d.getDate() + dias);
  return isoDe(d);
};
const curta = (iso: string) => `${iso.slice(8, 10)}/${iso.slice(5, 7)}`;

const setor = { id: 1, nome: 'Frente de Caixa', cor: '#F26A1B', ativo: true } as unknown as Funcionario['setor'];
const funcionario = (id: number, nome: string): Funcionario => ({
  id, nome, matricula: null, cargo: null, setor, salarioMensal: 2000, cargaHorariaMensal: 220, percentualEncargos: 38,
  ativo: true, diasDisponiveis: [], custoHora: 12, foto: null,
});
const funcionarios = [funcionario(1, 'Ana Souza'), funcionario(2, 'Bruno Lima'), funcionario(3, 'Carla Dias')];

const afastamento = (id: number, funcionarioId: number, motivo: MotivoAusencia, inicio: string, fim: string): Ausencia => ({
  id, funcionarioId, funcionarioNome: funcionarios[funcionarioId - 1].nome, inicio, fim, motivo, observacao: null,
});

const lista = [
  afastamento(1, 1, 'ATESTADO', somar(0), somar(2)),
  afastamento(2, 2, 'LICENCA_MATERNIDADE', somar(-30), somar(89)),
  afastamento(3, 3, 'LUTO', somar(-40), somar(-39)),
];

const cobertura = (turnoId: number, data: string, substituto: [number, string] | null, passado = false): CoberturaTurno => ({
  turnoId, escalaId: 7, escalaNome: 'Semana', data, horaInicio: '07:00:00', horaFim: '15:20:00',
  substitutoId: substituto?.[0] ?? null, substitutoNome: substituto?.[1] ?? null, substitutoSetor: substituto ? 'Frente de Caixa' : null,
  passado,
});

function montar(extra: Parameters<typeof mockFetch>[0] = {}) {
  return mockFetch({
    'GET /api/afastamentos': { corpo: lista },
    'GET /api/funcionarios': { corpo: funcionarios },
    ...extra,
  });
}

describe('Afastamentos', () => {
  it('mostra quem está afastado, com o tipo e a data de volta', async () => {
    montar();
    renderizar(<Afastamentos />);

    const andamento = await screen.findByRole('region', { name: /Em andamento/ });
    const ana = within(andamento).getByText('Ana Souza').closest('tr')!;
    expect(ana).toHaveTextContent('Atestado médico');
    expect(ana).toHaveTextContent(`volta em ${curta(somar(3))}`);
    expect(within(andamento).getByText('Bruno Lima').closest('tr')).toHaveTextContent('Licença-maternidade');
    expect(screen.getByText('Encerrados (1)')).toBeInTheDocument();
    expect(screen.getByText('Afastados hoje').closest('.kpi')).toHaveTextContent('2');
  });

  it('registra um atestado de hoje mostrando antes quem cobre cada turno', async () => {
    const hoje = somar(0);
    const fim = somar(1);
    const coberturas = [cobertura(50, hoje, [3, 'Carla Dias']), cobertura(51, fim, null)];
    const fetch = montar({
      [`GET /api/afastamentos/analise?funcionarioId=2&motivo=ATESTADO&inicio=${hoje}&fim=${fim}`]: {
        corpo: { dias: 2, retorno: somar(2), erros: [], alertas: [], valida: true, coberturas },
      },
      'POST /api/afastamentos': { status: 201, corpo: { ausencia: afastamento(9, 2, 'ATESTADO', hoje, fim), coberturas } },
    });
    renderizar(<Afastamentos />);

    fireEvent.click(await screen.findByRole('button', { name: /Registrar afastamento/ }));
    const dialogo = screen.getByRole('dialog', { name: 'Registrar afastamento' });
    expect(within(dialogo).getByLabelText('Tipo')).toHaveValue('ATESTADO');
    expect(within(dialogo).getByLabelText('Início')).toHaveValue(hoje);
    fireEvent.change(within(dialogo).getByLabelText('Colaborador'), { target: { value: '2' } });
    fireEvent.click(within(dialogo).getByRole('button', { name: '2 dias' }));

    expect(await within(dialogo).findByText(/2 dias · retorno em/)).toBeInTheDocument();
    const plano = within(dialogo).getByRole('list', { name: 'Quem cobre os turnos já escalados' });
    expect(within(plano).getAllByRole('listitem')[0]).toHaveTextContent('Carla Dias');
    expect(within(dialogo).getByText(/Não informe diagnóstico nem CID/)).toBeInTheDocument();

    fireEvent.click(within(dialogo).getByRole('button', { name: 'Salvar' }));

    expect(await screen.findByText('Afastamento de Bruno Lima registrado · 1 turno repassado · 1 turno retirado sem substituto.'))
      .toBeInTheDocument();
    const envio = fetch.mock.calls.find(([url, init]) => url === '/api/afastamentos' && init?.method === 'POST');
    expect(JSON.parse(String(envio?.[1]?.body))).toEqual({ funcionarioId: 2, motivo: 'ATESTADO', inicio: hoje, fim, observacao: '' });
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it('os atalhos de duração mudam conforme o tipo e os alertas da lei aparecem', async () => {
    const inicio = somar(0);
    const fim = somar(89);
    montar({
      [`GET /api/afastamentos/analise?funcionarioId=1&motivo=LICENCA_MATERNIDADE&inicio=${inicio}&fim=${fim}`]: {
        corpo: { dias: 90, retorno: somar(90), erros: [], valida: true, coberturas: [],
          alertas: ['A licença-maternidade é de 120 dias (CLT art. 392); confira as datas.'] },
      },
    });
    renderizar(<Afastamentos />);

    fireEvent.click(await screen.findByRole('button', { name: /Registrar afastamento/ }));
    const dialogo = screen.getByRole('dialog', { name: 'Registrar afastamento' });
    fireEvent.change(within(dialogo).getByLabelText('Tipo'), { target: { value: 'LICENCA_MATERNIDADE' } });
    expect(within(dialogo).getByRole('button', { name: '120 dias' })).toBeInTheDocument();
    expect(within(dialogo).queryByRole('button', { name: '2 dias' })).not.toBeInTheDocument();

    fireEvent.change(within(dialogo).getByLabelText('Colaborador'), { target: { value: '1' } });
    fireEvent.change(within(dialogo).getByLabelText('Fim'), { target: { value: fim } });

    expect(await within(dialogo).findByText(/art\. 392/)).toBeInTheDocument();
    expect(within(dialogo).getByRole('button', { name: 'Salvar' })).toBeEnabled();
  });

  it('turno de dia que já passou aparece como retirado', async () => {
    const ontem = somar(-1);
    montar({
      [`GET /api/afastamentos/analise?funcionarioId=1&motivo=ATESTADO&inicio=${ontem}&fim=${ontem}`]: {
        corpo: { dias: 1, retorno: somar(0), erros: [], alertas: [], valida: true, coberturas: [cobertura(60, ontem, null, true)] },
      },
    });
    renderizar(<Afastamentos />);

    fireEvent.click(await screen.findByRole('button', { name: /Registrar afastamento/ }));
    const dialogo = screen.getByRole('dialog', { name: 'Registrar afastamento' });
    fireEvent.change(within(dialogo).getByLabelText('Colaborador'), { target: { value: '1' } });
    fireEvent.change(within(dialogo).getByLabelText('Início'), { target: { value: ontem } });
    fireEvent.click(within(dialogo).getByRole('button', { name: '1 dia' }));

    expect(await within(dialogo).findByText('Dia já passou: o turno será retirado')).toBeInTheDocument();
  });

  it('o supervisor registra, mas não edita', async () => {
    logar('SUPERVISOR');
    montar();
    renderizar(<Afastamentos />);

    expect(await screen.findByRole('button', { name: /Registrar afastamento/ })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Editar afastamento/ })).not.toBeInTheDocument();
  });

  it('anexa o atestado ao registrar e conta os documentos na lista', async () => {
    const hoje = somar(0);
    const fetch = montar({
      [`GET /api/afastamentos/analise?funcionarioId=2&motivo=ATESTADO&inicio=${hoje}&fim=${hoje}`]: {
        corpo: { dias: 1, retorno: somar(1), erros: [], alertas: [], valida: true, coberturas: [] },
      },
      'POST /api/afastamentos': { status: 201, corpo: { ausencia: afastamento(9, 2, 'ATESTADO', hoje, hoje), coberturas: [] } },
      'POST /api/afastamentos/9/documentos': { status: 201, corpo: { id: 3, ausenciaId: 9, nomeArquivo: 'atestado.pdf' } },
    });
    renderizar(<Afastamentos />);

    fireEvent.click(await screen.findByRole('button', { name: /Registrar afastamento/ }));
    const dialogo = screen.getByRole('dialog', { name: 'Registrar afastamento' });
    fireEvent.change(within(dialogo).getByLabelText('Colaborador'), { target: { value: '2' } });
    fireEvent.click(within(dialogo).getByRole('button', { name: '1 dia' }));
    const arquivo = new File(['%PDF-1.4'], 'atestado.pdf', { type: 'application/pdf' });
    fireEvent.change(within(dialogo).getByLabelText(/Anexar documento/), { target: { files: [arquivo] } });
    expect(within(dialogo).getByText('atestado.pdf')).toBeInTheDocument();
    expect(within(dialogo).getByText(/só a gerência consegue abrir/)).toBeInTheDocument();

    await within(dialogo).findByText(/1 dia · retorno em/);
    fireEvent.click(within(dialogo).getByRole('button', { name: 'Salvar' }));

    expect(await screen.findByText('Afastamento de Bruno Lima registrado · 1 documento anexado.')).toBeInTheDocument();
    const envio = fetch.mock.calls.find(([url]) => url === '/api/afastamentos/9/documentos');
    expect(envio?.[1]?.method).toBe('POST');
    expect((envio?.[1]?.body as FormData).get('arquivo')).toBe(arquivo);
  });

  it('recusa no navegador arquivos grandes ou de outro tipo', async () => {
    montar();
    renderizar(<Afastamentos />);

    fireEvent.click(await screen.findByRole('button', { name: /Registrar afastamento/ }));
    const dialogo = screen.getByRole('dialog', { name: 'Registrar afastamento' });
    const grande = new File([new Uint8Array(5 * 1024 * 1024 + 1)], 'grande.pdf', { type: 'application/pdf' });
    const planilha = new File(['a;b'], 'notas.csv', { type: 'text/csv' });
    fireEvent.change(within(dialogo).getByLabelText(/Anexar documento/), { target: { files: [grande, planilha] } });

    expect(await screen.findByText(/grande.pdf: máximo de 5 MB/)).toBeInTheDocument();
    expect(within(dialogo).queryByText('notas.csv')).not.toBeInTheDocument();
  });

  it('a gerência vê, abre e remove os documentos ao editar', async () => {
    const lista = [{ ...afastamento(1, 1, 'ATESTADO', somar(0), somar(2)), documentos: 1 }];
    const fetch = mockFetch({
      'GET /api/afastamentos': { corpo: lista },
      'GET /api/funcionarios': { corpo: funcionarios },
      [`GET /api/afastamentos/analise?funcionarioId=1&motivo=ATESTADO&inicio=${somar(0)}&fim=${somar(2)}&ignorar=1`]: {
        corpo: { dias: 3, retorno: somar(3), erros: [], alertas: [], valida: true, coberturas: [] },
      },
      'GET /api/afastamentos/1/documentos': {
        corpo: [{ id: 3, ausenciaId: 1, nomeArquivo: 'atestado.pdf', tipo: 'application/pdf', tamanho: 2048,
          enviadoEm: `${somar(0)}T08:30:00`, enviadoPor: 'sup.loja' }],
      },
      'DELETE /api/afastamentos/documentos/3': { status: 204 },
    });
    renderizar(<Afastamentos />);

    expect(await screen.findByLabelText('1 documento anexado')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Editar afastamento de Ana Souza' }));
    const dialogo = screen.getByRole('dialog', { name: 'Editar afastamento' });
    const doc = (await within(dialogo).findByText('atestado.pdf')).closest('li')!;
    expect(doc).toHaveTextContent('enviado por sup.loja');
    expect(within(doc).getByRole('button', { name: 'Abrir atestado.pdf' })).toBeInTheDocument();

    fireEvent.click(within(doc).getByRole('button', { name: 'Remover atestado.pdf' }));

    await waitFor(() => expect(fetch.mock.calls.some(([url, init]) => url === '/api/afastamentos/documentos/3' && init?.method === 'DELETE')).toBe(true));
  });

  it('o gestor edita e remove', async () => {
    const fetch = montar({
      [`GET /api/afastamentos/analise?funcionarioId=1&motivo=ATESTADO&inicio=${somar(0)}&fim=${somar(2)}&ignorar=1`]: {
        corpo: { dias: 3, retorno: somar(3), erros: [], alertas: [], valida: true, coberturas: [] },
      },
      'DELETE /api/afastamentos/1': { status: 204 },
    });
    renderizar(<Afastamentos />);

    fireEvent.click(await screen.findByRole('button', { name: 'Editar afastamento de Ana Souza' }));
    const dialogo = screen.getByRole('dialog', { name: 'Editar afastamento' });
    expect(within(dialogo).getByLabelText('Tipo')).toHaveValue('ATESTADO');
    fireEvent.click(within(dialogo).getByRole('button', { name: 'Remover' }));

    expect(await screen.findByText('Afastamento removido.')).toBeInTheDocument();
    expect(fetch.mock.calls.some(([url, init]) => url === '/api/afastamentos/1' && init?.method === 'DELETE')).toBe(true);
  });
});
