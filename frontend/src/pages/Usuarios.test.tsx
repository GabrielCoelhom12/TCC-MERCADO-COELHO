import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { mockFetch, renderizar } from '../test/util';
import type { Usuario } from '../types';
import Usuarios from './Usuarios';

const gestor: Usuario = { id: 1, login: 'gestor', nome: 'Gerência', perfil: 'ADMIN', ativo: true, foto: null };
const bia: Usuario = { id: 2, login: 'bia', nome: 'Beatriz Lima', perfil: 'SUPERVISOR', ativo: false, foto: 'data:image/jpeg;base64,AAAA' };

describe('Usuarios', () => {
  it('lista usuários com perfil e situação', async () => {
    mockFetch({ 'GET /api/usuarios': { corpo: [gestor, bia] } });
    renderizar(<Usuarios />);

    const linha = (await screen.findByText('Beatriz Lima')).closest('tr')!;
    expect(within(linha).getByText('bia')).toBeInTheDocument();
    expect(within(linha).getByText('Supervisor')).toBeInTheDocument();
    expect(within(linha).getByText('Inativo')).toBeInTheDocument();
    expect(screen.getByText('Administrador')).toBeInTheDocument();
  });

  it('cria usuário com senha obrigatória', async () => {
    const enviados: unknown[] = [];
    mockFetch({
      'GET /api/usuarios': { corpo: [gestor] },
      'POST /api/usuarios': (corpo) => (enviados.push(corpo), { status: 201, corpo: { ...bia, id: 3 } }),
    });
    renderizar(<Usuarios />);

    fireEvent.click(await screen.findByText('Novo usuário'));
    const dialogo = screen.getByRole('dialog', { name: 'Novo usuário' });
    const salvar = within(dialogo).getByRole('button', { name: 'Criar usuário' });

    fireEvent.change(within(dialogo).getByLabelText('Nome'), { target: { value: 'Carla Dias' } });
    fireEvent.change(within(dialogo).getByLabelText('Login'), { target: { value: 'carla' } });
    expect(salvar).toBeDisabled();
    fireEvent.change(within(dialogo).getByLabelText('Senha'), { target: { value: 'senhafraca1' } });
    expect(salvar).toBeDisabled();
    fireEvent.change(within(dialogo).getByLabelText('Senha'), { target: { value: 'Senha@Forte1' } });
    fireEvent.change(within(dialogo).getByLabelText('Perfil'), { target: { value: 'GESTOR' } });
    fireEvent.click(salvar);

    await waitFor(() => expect(enviados).toHaveLength(1));
    expect(enviados[0]).toEqual({ nome: 'Carla Dias', login: 'carla', perfil: 'GESTOR', senha: 'Senha@Forte1' });
    expect(await screen.findByRole('status')).toHaveTextContent('Usuário criado.');
  });

  it('edita mantendo a senha quando o campo fica vazio', async () => {
    const enviados: unknown[] = [];
    mockFetch({
      'GET /api/usuarios': { corpo: [gestor, bia] },
      'PUT /api/usuarios/2': (corpo) => (enviados.push(corpo), { corpo: bia }),
    });
    renderizar(<Usuarios />);

    const linha = (await screen.findByText('Beatriz Lima')).closest('tr')!;
    fireEvent.click(within(linha).getByText('Editar'));
    const dialogo = screen.getByRole('dialog', { name: 'Editar usuário' });
    fireEvent.click(within(dialogo).getByLabelText('Usuário ativo'));
    fireEvent.click(within(dialogo).getByRole('button', { name: 'Salvar' }));

    await waitFor(() => expect(enviados).toHaveLength(1));
    expect(enviados[0]).toEqual({ nome: 'Beatriz Lima', login: 'bia', perfil: 'SUPERVISOR', ativo: true, senha: null });
  });

  it('mostra o erro da API', async () => {
    mockFetch({
      'GET /api/usuarios': { corpo: [gestor] },
      'PUT /api/usuarios/1': { status: 422, corpo: { mensagem: 'É preciso manter pelo menos um administrador ativo.' } },
    });
    renderizar(<Usuarios />);

    fireEvent.click(await screen.findByText('Editar'));
    fireEvent.change(screen.getByLabelText('Perfil'), { target: { value: 'GESTOR' } });
    fireEvent.click(screen.getByRole('button', { name: 'Salvar' }));
    expect(await screen.findByRole('status')).toHaveTextContent('pelo menos um administrador');
  });

  it('mostra a foto do usuário na lista', async () => {
    mockFetch({ 'GET /api/usuarios': { corpo: [gestor, bia] } });
    renderizar(<Usuarios />);
    const linha = (await screen.findByText('Beatriz Lima')).closest('tr')!;
    expect(within(linha).getByRole('img', { name: 'Beatriz Lima' })).toHaveAttribute('src', bia.foto);
  });

  it('envia a foto escolhida depois de criar o usuário', async () => {
    const enviados: unknown[] = [];
    mockFetch({
      'GET /api/usuarios': { corpo: [] },
      'POST /api/usuarios': { status: 201, corpo: { ...gestor, id: 9 } },
      'PUT /api/usuarios/9/foto': (corpo) => (enviados.push(corpo), { corpo: gestor }),
    });
    renderizar(<Usuarios />);

    fireEvent.click(await screen.findByText('Novo usuário'));
    const dialogo = screen.getByRole('dialog', { name: 'Novo usuário' });
    fireEvent.change(within(dialogo).getByLabelText('Nome'), { target: { value: 'Carla' } });
    fireEvent.change(within(dialogo).getByLabelText('Login'), { target: { value: 'carla' } });
    fireEvent.change(within(dialogo).getByLabelText('Senha'), { target: { value: 'Senha@Forte1' } });
    const foto = new File([new Uint8Array([0xff, 0xd8, 0xff])], 'carla.jpg', { type: 'image/jpeg' });
    fireEvent.change(within(dialogo).getByLabelText('Escolher foto'), { target: { files: [foto] } });
    fireEvent.click(within(dialogo).getByRole('button', { name: 'Criar usuário' }));

    await waitFor(() => expect(enviados).toHaveLength(1));
    expect((enviados[0] as FormData).get('arquivo')).toBe(foto);
  });

  it('recusa arquivos que não são PNG ou JPEG', async () => {
    mockFetch({ 'GET /api/usuarios': { corpo: [] } });
    renderizar(<Usuarios />);

    fireEvent.click(await screen.findByText('Novo usuário'));
    const gif = new File(['GIF89a'], 'x.gif', { type: 'image/gif' });
    fireEvent.change(screen.getByLabelText('Escolher foto'), { target: { files: [gif] } });
    expect(await screen.findByRole('status')).toHaveTextContent('PNG ou JPEG');
  });

  it('remove a foto ao salvar a edição', async () => {
    const fetch = mockFetch({
      'GET /api/usuarios': { corpo: [bia] },
      'PUT /api/usuarios/2': { corpo: bia },
      'DELETE /api/usuarios/2/foto': { corpo: { ...bia, foto: null } },
    });
    renderizar(<Usuarios />);

    fireEvent.click(await screen.findByText('Editar'));
    fireEvent.click(screen.getByRole('button', { name: 'Remover foto' }));
    fireEvent.click(screen.getByRole('button', { name: 'Salvar' }));
    await waitFor(() =>
      expect(fetch).toHaveBeenCalledWith('/api/usuarios/2/foto', expect.objectContaining({ method: 'DELETE' })),
    );
  });
});
