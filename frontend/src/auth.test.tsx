import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { AuthProvider, useAuth } from './auth';
import { mockFetch } from './test/util';

function Quem() {
  const { usuario } = useAuth();
  return <span>{usuario ? `${usuario.id ?? 'sem-id'} ${usuario.nome}` : 'ninguém'}</span>;
}

describe('AuthProvider', () => {
  it('ao abrir, atualiza os dados salvos do usuário (ex.: sessão antiga sem id)', async () => {
    localStorage.setItem('coelho.token', 't');
    localStorage.setItem('coelho.usuario', JSON.stringify({ nome: 'Gerência', login: 'gestor', perfil: 'ADMIN' }));
    mockFetch({ 'GET /api/auth/me': { corpo: { id: 1, nome: 'Gerência Coelho', login: 'gestor', perfil: 'ADMIN', foto: null } } });

    render(<AuthProvider><Quem /></AuthProvider>);
    expect(screen.getByText('sem-id Gerência')).toBeInTheDocument();
    expect(await screen.findByText('1 Gerência Coelho')).toBeInTheDocument();
  });

  it('sem sessão não chama a API', () => {
    const fetch = mockFetch({});
    render(<AuthProvider><Quem /></AuthProvider>);
    expect(screen.getByText('ninguém')).toBeInTheDocument();
    expect(fetch).not.toHaveBeenCalled();
  });
});
