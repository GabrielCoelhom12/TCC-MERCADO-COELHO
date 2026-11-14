import { screen, within } from '@testing-library/react';
import { Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import App from '../App';
import { logar, mockFetch, renderizar } from '../test/util';
import type { Escala, Funcionario, Matriz, ResumoDia, Setor, TurnoModelo } from '../types';
import Calendario from './Calendario';
import Escalas from './Escalas';
import Ferias from './Ferias';
import Funcionarios from './Funcionarios';
import MatrizEscala from './MatrizEscala';
import Parametros from './Parametros';
import Setores from './Setores';
import Turnos from './Turnos';

/** O SUPERVISOR consulta tudo, mas não vê salários nem os botões das ações que o servidor recusaria. */

const AVISO = /Seu perfil permite consultar/;

const caixa = { id: 1, nome: 'Frente de Caixa', cor: '#F26A1B', ativo: true, minimoPorDia: {} } as unknown as Setor;
const anaSemSalario: Funcionario = {
  id: 5, nome: 'Ana Souza', matricula: 'C001', cargo: 'Fiscal de caixa', setor: caixa, salarioMensal: null,
  cargaHorariaMensal: 220, percentualEncargos: null, ativo: true, diasDisponiveis: ['MONDAY'], custoHora: null, foto: null,
};
const escala: Escala = {
  id: 7, nome: 'Escala 09/11 a 15/11', dataInicio: '2026-11-09', dataFim: '2026-11-15', status: 'RASCUNHO', observacao: null,
  criadaEm: '2026-11-02T06:00:00', aprovadaEm: null, aprovadaPor: null, geradaAutomaticamente: true,
};
const modelo = { id: 1, nome: 'Abertura', sigla: 'ABE', horaInicio: '07:00:00', horaFim: '15:20:00', intervaloMinutos: 60, aplicacao: 'DIAS_UTEIS' } as TurnoModelo;

const resumo = (data: string): ResumoDia => ({
  data, tipoDia: 'Dia', feriado: null, fator: 1, receitaProjetada: 40000, tetoFolha: 2400, custoEscala: 150,
  custoMedioOperador: 150, operadores: 1, lMin: 1, lMax: 9, situacao: 'OK', setores: [], clientesPrevistos: null, pico: null,
});
const dias = ['09', '10', '11', '12', '13', '14', '15'].map((d) => `2026-11-${d}`);
const matriz: Matriz = {
  escala,
  funcionarios: [{ id: 5, nome: 'Ana Souza', cargo: 'Fiscal', setorId: 1, setorNome: 'Frente de Caixa', setorCor: '#F26A1B',
    custoHora: null, diasDisponiveis: [], ativo: true }],
  turnos: [{ id: 10, funcionarioId: 5, data: '2026-11-09', horaInicio: '07:00:00', horaFim: '15:20:00', intervaloMinutos: 60,
    rotulo: 'ABE', horas: 7.33, custo: null }],
  ausencias: [],
  modelos: [modelo],
  validacao: { dias: dias.map(resumo), violacoes: [], custoTotal: 1050, tetoTotal: 16800, erros: 0, alertas: 0, aprovavel: true },
};

describe('perfil SUPERVISOR', () => {
  it('vê os colaboradores sem salário e sem editar', async () => {
    logar('SUPERVISOR');
    mockFetch({
      'GET /api/funcionarios': { corpo: [anaSemSalario] },
      'GET /api/setores': { corpo: [caixa] },
      'GET /api/ausencias': { corpo: [] },
    });
    renderizar(<Funcionarios />);

    const linha = (await screen.findByText('Ana Souza')).closest('tr')!;
    expect(within(linha).getAllByText('—').length).toBeGreaterThanOrEqual(3);
    expect(within(linha).queryByText(/R\$/)).not.toBeInTheDocument();
    expect(within(linha).queryByRole('button', { name: 'Editar' })).not.toBeInTheDocument();
    expect(within(linha).queryByRole('button', { name: 'Excluir' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Novo colaborador/ })).not.toBeInTheDocument();
    expect(screen.getByText(AVISO)).toBeInTheDocument();
  });

  it('consulta as escalas sem criar nem excluir', async () => {
    logar('SUPERVISOR');
    mockFetch({ 'GET /api/escalas': { corpo: [escala] } });
    renderizar(<Escalas />);

    expect(await screen.findByText('Escala 09/11 a 15/11')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Nova escala/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Excluir' })).not.toBeInTheDocument();
  });

  it('abre a matriz só para leitura, sem custo por pessoa', async () => {
    logar('SUPERVISOR');
    mockFetch({ 'GET /api/escalas/7/matriz': { corpo: matriz } });
    renderizar(<Routes><Route path="/escalas/:id" element={<MatrizEscala />} /></Routes>, '/escalas/7');

    expect(await screen.findByText('Ana Souza')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Gerar automaticamente' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Aprovar escala' })).not.toBeInTheDocument();
    expect(screen.queryByText(/\/h/)).not.toBeInTheDocument();
    expect(screen.getByText(AVISO)).toBeInTheDocument();
  });

  it('consulta as férias sem registrar nem editar', async () => {
    logar('SUPERVISOR');
    mockFetch({
      'GET /api/ferias': { corpo: [{ id: 1, funcionarioId: 5, funcionarioNome: 'Ana Souza', inicio: '2030-01-07', fim: '2030-01-20', motivo: 'FERIAS', observacao: null }] },
      'GET /api/funcionarios': { corpo: [anaSemSalario] },
    });
    renderizar(<Ferias />);

    expect(await screen.findByText('Ana Souza')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Registrar férias/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Editar férias/ })).not.toBeInTheDocument();
  });

  it('vê setores, modelos de turno, feriados e parâmetros sem os botões de alterar', async () => {
    logar('SUPERVISOR');
    mockFetch({
      'GET /api/setores': { corpo: [caixa] },
      'GET /api/turnos-modelo': { corpo: [modelo] },
      'GET /api/projecoes': { corpo: { padrao: {}, especificas: [] } },
      'GET /api/feriados': { corpo: [] },
      'GET /api/parametros': { corpo: {} },
      'GET /api/movimento/faixas': { corpo: [] },
    });

    const { unmount } = renderizar(<Setores />);
    expect(await screen.findByText(AVISO)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Novo setor|Salvar/ })).not.toBeInTheDocument();
    unmount();

    const turnos = renderizar(<Turnos />);
    expect(await screen.findByText(AVISO)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Novo modelo|Editar|Excluir/ })).not.toBeInTheDocument();
    turnos.unmount();

    const calendario = renderizar(<Calendario />);
    expect(await screen.findByText(AVISO)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Salvar|Remover|Adicionar|Importar/ })).not.toBeInTheDocument();
    calendario.unmount();

    renderizar(<Parametros />);
    expect(await screen.findByText(AVISO)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Salvar/ })).not.toBeInTheDocument();
  });

  it('não tem o simulador de custos no menu', async () => {
    logar('SUPERVISOR');
    mockFetch({ 'GET /api/escalas': { corpo: [] }, 'GET /api/chat/conversas': { corpo: [] } });
    renderizar(<App />, '/simulador');

    expect(await screen.findByRole('link', { name: /Painel/ })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /Simulador de custos/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: /Simulador/ })).not.toBeInTheDocument();
  });
});
