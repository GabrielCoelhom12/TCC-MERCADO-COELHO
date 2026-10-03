import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import App from './App';
import { logar, mockFetch, renderizar } from './test/util';

describe('App', () => {
  it('sem sessão, qualquer rota leva ao login', () => {
    renderizar(<App />, '/escalas');
    expect(screen.getByRole('heading', { name: 'Escalas de trabalho' })).toBeInTheDocument();
  });

  it('faz login, guarda a sessão e abre o sistema', async () => {
    mockFetch({
      'POST /api/auth/login': (corpo) =>
        (corpo as { senha: string }).senha === 'certa'
          ? { corpo: { token: 't1', nome: 'Gerência', login: 'gestor', perfil: 'ADMIN' } }
          : { status: 401, corpo: { mensagem: 'Usuário ou senha inválidos.' } },
      'GET /api/escalas': { corpo: [] },
    });
    renderizar(<App />, '/turnos');

    const entrar = screen.getByRole('button', { name: 'Entrar' });
    expect(entrar).toBeDisabled();

    fireEvent.change(screen.getByLabelText('Usuário'), { target: { value: 'gestor' } });
    fireEvent.change(screen.getByLabelText('Senha'), { target: { value: 'errada' } });
    fireEvent.click(entrar);
    expect(await screen.findByText('Usuário ou senha inválidos.')).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('Senha'), { target: { value: 'certa' } });
    fireEvent.click(entrar);

    expect(await screen.findByText('Gerência')).toBeInTheDocument();
    expect(localStorage.getItem('coelho.token')).toBe('t1');
  });

  it('só administradores veem a gestão de usuários', async () => {
    localStorage.setItem('coelho.token', 't');
    localStorage.setItem('coelho.usuario', JSON.stringify({ nome: 'Bia', login: 'bia', perfil: 'GESTOR' }));
    mockFetch({ 'GET /api/turnos-modelo': { corpo: [] }, 'GET /api/escalas': { corpo: [] } });
    renderizar(<App />, '/usuarios');

    // Rota protegida redireciona para o painel e o item não aparece no menu.
    expect(await screen.findByRole('heading', { name: /Painel financeiro/ })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /Usuários/ })).not.toBeInTheDocument();
  });

  it('administrador acessa a gestão de usuários', async () => {
    logar();
    mockFetch({ 'GET /api/usuarios': { corpo: [] } });
    renderizar(<App />, '/usuarios');

    expect(await screen.findByRole('heading', { name: 'Usuários' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Usuários/ })).toHaveAttribute('href', '/usuarios');
  });

  it('mostra no menu o total de mensagens não lidas', async () => {
    logar();
    const conversa = (id: number, naoLidas: number) => ({
      id, tipo: 'DIRETA', nome: `C${id}`, foto: null, participantes: [], ultimaMensagem: null, naoLidas, leituras: {},
      atualizadaEm: '2026-10-09T08:00:00',
    });
    mockFetch({
      'GET /api/turnos-modelo': { corpo: [] },
      'GET /api/chat/conversas': { corpo: [conversa(1, 2), conversa(2, 3)] },
      'GET /api/chat/usuarios': { corpo: [] },
    });
    renderizar(<App />, '/turnos');

    const link = screen.getByRole('link', { name: /Mensagens/ });
    expect(await within(link).findByLabelText('5 mensagens não lidas')).toHaveTextContent('5');
  });

  it('com sessão, mostra o menu e permite sair', async () => {
    logar();
    mockFetch({ 'GET /api/turnos-modelo': { corpo: [] } });
    renderizar(<App />, '/turnos');

    expect(await screen.findByRole('heading', { name: 'Modelos de turno' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Colaboradores/ })).toHaveAttribute('href', '/funcionarios');

    fireEvent.click(screen.getByRole('button', { name: 'Sair' }));
    await waitFor(() => expect(screen.getByRole('heading', { name: 'Escalas de trabalho' })).toBeInTheDocument());
    expect(localStorage.getItem('coelho.token')).toBeNull();
  });
});
