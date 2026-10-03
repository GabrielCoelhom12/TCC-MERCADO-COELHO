import type { Violacao } from './types';

const CHAVE_TOKEN = 'coelho.token';
const CHAVE_USUARIO = 'coelho.usuario';

/**
 * Sessão por aba: cada aba guarda a sua (sessionStorage), então dois usuários podem usar o sistema lado a lado no mesmo
 * navegador. O localStorage guarda só o último login, para uma aba nova já abrir logada.
 */
function ler(chave: string) {
  const daAba = sessionStorage.getItem(chave);
  if (daAba !== null) return daAba;
  const ultimo = localStorage.getItem(chave);
  if (ultimo !== null) sessionStorage.setItem(chave, ultimo);
  return ultimo;
}

function gravar(chave: string, valor: string) {
  sessionStorage.setItem(chave, valor);
  localStorage.setItem(chave, valor);
}

/** Ao sair, só apaga o "último login" se ele for desta aba (não derruba outra aba com outro usuário). */
function apagar(chave: string) {
  const daAba = sessionStorage.getItem(chave);
  sessionStorage.removeItem(chave);
  if (localStorage.getItem(chave) === daAba) localStorage.removeItem(chave);
}

export class ApiError extends Error {
  constructor(message: string, public status: number, public violacoes: Violacao[] = []) {
    super(message);
  }
}

export const sessao = {
  token: () => ler(CHAVE_TOKEN),
  salvar: (token: string) => gravar(CHAVE_TOKEN, token),
  limpar: () => {
    apagar(CHAVE_TOKEN);
    apagar(CHAVE_USUARIO);
  },
  usuario: () => ler(CHAVE_USUARIO),
  salvarUsuario: (json: string) => gravar(CHAVE_USUARIO, json),
};

async function requisicao<T>(metodo: string, caminho: string, corpo?: unknown): Promise<T> {
  const headers: Record<string, string> = {};
  const token = sessao.token();
  if (token) headers.Authorization = `Bearer ${token}`;
  const arquivo = corpo instanceof FormData;
  if (corpo !== undefined && !arquivo) headers['Content-Type'] = 'application/json';

  const resp = await fetch(`/api${caminho}`, {
    method: metodo,
    headers,
    body: corpo === undefined ? undefined : arquivo ? corpo : JSON.stringify(corpo),
  });

  if (resp.status === 401 && caminho !== '/auth/login') {
    sessao.limpar();
    window.location.href = '/login';
    throw new ApiError('Sessão expirada.', 401);
  }
  if (resp.status === 204) return undefined as T;

  const texto = await resp.text();
  const dados = texto ? JSON.parse(texto) : undefined;
  if (!resp.ok) {
    throw new ApiError(dados?.mensagem ?? `Erro ${resp.status}`, resp.status, dados?.violacoes ?? []);
  }
  return dados as T;
}

export const api = {
  get: <T>(c: string) => requisicao<T>('GET', c),
  post: <T>(c: string, corpo?: unknown) => requisicao<T>('POST', c, corpo ?? {}),
  put: <T>(c: string, corpo: unknown) => requisicao<T>('PUT', c, corpo),
  del: <T = void>(c: string) => requisicao<T>('DELETE', c),
  /** Envio de arquivo (multipart); o navegador define o Content-Type com o boundary. */
  enviar: <T>(c: string, dados: FormData) => requisicao<T>('PUT', c, dados),
};
