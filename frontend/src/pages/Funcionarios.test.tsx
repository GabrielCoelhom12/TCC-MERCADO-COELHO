import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { mockFetch, renderizar } from '../test/util';
import type { Funcionario, Setor } from '../types';
import Funcionarios from './Funcionarios';

const caixa: Setor = { id: 1, nome: 'Frente de Caixa', cor: '#F26A1B', ativo: true, minimoPorDia: {} } as Setor;
const ana: Funcionario = {
  id: 5, nome: 'Ana Souza', matricula: 'C001', cargo: 'Fiscal de caixa', setor: caixa, salarioMensal: 2300,
  cargaHorariaMensal: 220, percentualEncargos: 38, ativo: true, diasDisponiveis: ['MONDAY'], custoHora: 14.43,
  foto: 'data:image/jpeg;base64,AAAA',
};
const bruno: Funcionario = { ...ana, id: 6, nome: 'Bruno Lima', foto: null };

const rotasBase = {
  'GET /api/funcionarios': { corpo: [ana, bruno] },
  'GET /api/setores': { corpo: [caixa] },
  'GET /api/ausencias': { corpo: [] },
};

describe('Funcionarios', () => {
  it('mostra a foto ou a inicial de cada colaborador', async () => {
    mockFetch(rotasBase);
    renderizar(<Funcionarios />);

    const linhaAna = (await screen.findByText('Ana Souza')).closest('tr')!;
    expect(within(linhaAna).getByRole('img', { name: 'Ana Souza' })).toHaveAttribute('src', ana.foto);
    const linhaBruno = screen.getByText('Bruno Lima').closest('tr')!;
    expect(within(linhaBruno).queryByRole('img')).not.toBeInTheDocument();
    expect(within(linhaBruno).getByText('B')).toBeInTheDocument();
  });

  it('cria colaborador e envia a foto escolhida', async () => {
    const fotos: unknown[] = [];
    mockFetch({
      ...rotasBase,
      'POST /api/funcionarios': { status: 201, corpo: { ...bruno, id: 7 } },
      'PUT /api/funcionarios/7/foto': (corpo) => (fotos.push(corpo), { corpo: bruno }),
    });
    renderizar(<Funcionarios />);

    fireEvent.click(await screen.findByText('+ Novo colaborador'));
    const dialogo = screen.getByRole('dialog', { name: 'Novo colaborador' });
    fireEvent.change(within(dialogo).getByLabelText('Nome'), { target: { value: 'Carla' } });
    const foto = new File([new Uint8Array([0x89, 0x50, 0x4e, 0x47])], 'carla.png', { type: 'image/png' });
    fireEvent.change(within(dialogo).getByLabelText('Escolher foto'), { target: { files: [foto] } });
    fireEvent.click(within(dialogo).getByRole('button', { name: 'Salvar' }));

    await waitFor(() => expect(fotos).toHaveLength(1));
    expect((fotos[0] as FormData).get('arquivo')).toBe(foto);
    expect(await screen.findByRole('status')).toHaveTextContent('Colaborador salvo.');
  });

  it('remove a foto ao editar', async () => {
    const fetch = mockFetch({
      ...rotasBase,
      'PUT /api/funcionarios/5': { corpo: ana },
      'DELETE /api/funcionarios/5/foto': { corpo: { ...ana, foto: null } },
    });
    renderizar(<Funcionarios />);

    const linha = (await screen.findByText('Ana Souza')).closest('tr')!;
    fireEvent.click(within(linha).getByText('Editar'));
    fireEvent.click(screen.getByRole('button', { name: 'Remover foto' }));
    fireEvent.click(screen.getByRole('button', { name: 'Salvar' }));

    await waitFor(() =>
      expect(fetch).toHaveBeenCalledWith('/api/funcionarios/5/foto', expect.objectContaining({ method: 'DELETE' })),
    );
  });

  it('não chama a API de foto quando nada mudou', async () => {
    const fetch = mockFetch({ ...rotasBase, 'PUT /api/funcionarios/5': { corpo: ana } });
    renderizar(<Funcionarios />);

    const linha = (await screen.findByText('Ana Souza')).closest('tr')!;
    fireEvent.click(within(linha).getByText('Editar'));
    fireEvent.click(screen.getByRole('button', { name: 'Salvar' }));

    expect(await screen.findByRole('status')).toHaveTextContent('Colaborador salvo.');
    expect(fetch.mock.calls.some(([url]) => String(url).endsWith('/foto'))).toBe(false);
  });
});
