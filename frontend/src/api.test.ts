import { describe, expect, it } from 'vitest';
import { ApiError, api, sessao } from './api';
import { mockFetch } from './test/util';

describe('api', () => {
  it('envia o token e o corpo em JSON', async () => {
    sessao.salvar('abc');
    const fetch = mockFetch({ 'POST /api/setores': { status: 201, corpo: { id: 1 } } });

    await expect(api.post('/setores', { nome: 'Caixa' })).resolves.toEqual({ id: 1 });

    const [, init] = fetch.mock.calls[0];
    expect(init?.headers).toMatchObject({ Authorization: 'Bearer abc', 'Content-Type': 'application/json' });
    expect(init?.body).toBe('{"nome":"Caixa"}');
  });

  it('devolve undefined em 204', async () => {
    mockFetch({ 'DELETE /api/setores/1': { status: 204 } });
    await expect(api.del('/setores/1')).resolves.toBeUndefined();
  });

  it('transforma erros da API em ApiError com as violações', async () => {
    mockFetch({
      'PUT /api/escalas/1/celula': {
        status: 422,
        corpo: { mensagem: 'Bloqueado', violacoes: [{ regra: 'CUSTO_ACIMA_TETO' }] },
      },
    });

    const erro = (await api.put('/escalas/1/celula', {}).catch((e: unknown) => e)) as ApiError;
    expect(erro).toBeInstanceOf(ApiError);
    expect(erro.message).toBe('Bloqueado');
    expect(erro.status).toBe(422);
    expect(erro.violacoes).toHaveLength(1);
  });

  it('usa mensagem genérica quando a resposta não tem corpo', async () => {
    mockFetch({});
    await expect(api.get('/inexistente')).rejects.toThrow('Erro 404');
  });

  it('cada aba mantém a própria sessão mesmo que outra aba entre com outro usuário', () => {
    localStorage.setItem('coelho.token', 'gestor');
    expect(sessao.token()).toBe('gestor');

    localStorage.setItem('coelho.token', 'gabriel'); // login feito em outra aba
    expect(sessao.token()).toBe('gestor');
  });

  it('uma aba nova já abre com o último login', () => {
    localStorage.setItem('coelho.token', 'gabriel');
    expect(sessao.token()).toBe('gabriel');
  });

  it('sair não derruba outra aba que entrou com outro usuário', () => {
    sessao.salvar('gestor');
    localStorage.setItem('coelho.token', 'gabriel');
    sessao.limpar();
    expect(sessao.token()).toBe('gabriel');
    expect(sessionStorage.getItem('coelho.token')).toBe('gabriel');
  });

  it('limpa a sessão quando o token expira', async () => {
    sessao.salvar('vencido');
    mockFetch({ 'GET /api/escalas': { status: 401 } });

    await expect(api.get('/escalas')).rejects.toThrow('Sessão expirada.');
    expect(sessao.token()).toBeNull();
  });
});
