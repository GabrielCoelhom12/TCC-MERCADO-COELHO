import { render } from '@testing-library/react';
import type { ReactElement } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { vi } from 'vitest';
import { AuthProvider } from '../auth';
import { ToastProvider } from '../components/ui';

type Resposta = { status?: number; corpo?: unknown };

/**
 * Substitui o fetch global por respostas fixas por "MÉTODO caminho" (ex.: "GET /api/escalas").
 * Rotas não mapeadas devolvem 404.
 */
export function mockFetch(rotas: Record<string, Resposta | ((corpo: unknown) => Resposta)>) {
  const fn = vi.fn(async (url: string, init?: RequestInit) => {
    const chave = `${init?.method ?? 'GET'} ${url}`;
    const rota = rotas[chave];
    const corpo = typeof init?.body === 'string' ? JSON.parse(init.body) : init?.body;
    const r = typeof rota === 'function' ? rota(corpo) : rota;
    const status = r?.status ?? (r ? 200 : 404);
    const texto = r?.corpo === undefined ? '' : JSON.stringify(r.corpo);
    return new Response(status === 204 ? null : texto, { status });
  });
  vi.stubGlobal('fetch', fn);
  return fn;
}

export function logar() {
  localStorage.setItem('coelho.token', 'token-teste');
  localStorage.setItem('coelho.usuario', JSON.stringify({ id: 1, nome: 'Gerência', login: 'gestor', perfil: 'ADMIN' }));
}

export function renderizar(ui: ReactElement, rota = '/') {
  return render(
    <MemoryRouter initialEntries={[rota]}>
      <AuthProvider>
        <ToastProvider>{ui}</ToastProvider>
      </AuthProvider>
    </MemoryRouter>,
  );
}
