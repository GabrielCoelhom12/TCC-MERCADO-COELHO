import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { api, sessao } from './api';

interface Usuario {
  id?: number;
  nome: string;
  login: string;
  perfil: string;
  foto?: string | null;
}

interface AuthCtx {
  usuario: Usuario | null;
  entrar: (login: string, senha: string) => Promise<void>;
  sair: () => void;
  /** Recarrega nome/foto do usuário logado (ex.: depois de editar o próprio cadastro). */
  atualizar: () => Promise<void>;
}

const Ctx = createContext<AuthCtx>(null!);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [usuario, setUsuario] = useState<Usuario | null>(() => {
    const salvo = sessao.usuario();
    return salvo && sessao.token() ? JSON.parse(salvo) : null;
  });

  const guardar = (r: Usuario) => {
    const u = { id: r.id, nome: r.nome, login: r.login, perfil: r.perfil, foto: r.foto ?? null };
    sessao.salvarUsuario(JSON.stringify(u));
    setUsuario(u);
  };

  // Ao abrir, sincroniza os dados salvos com o servidor (nome/foto alterados, sessões antigas sem id).
  useEffect(() => {
    if (sessao.token()) void api.get<Usuario>('/auth/me').then(guardar).catch(() => undefined);
  }, []);

  const entrar = async (login: string, senha: string) => {
    const r = await api.post<Usuario & { token: string }>('/auth/login', { login, senha });
    sessao.salvar(r.token);
    guardar(r);
  };

  const atualizar = async () => guardar(await api.get<Usuario>('/auth/me'));

  const sair = () => {
    sessao.limpar();
    setUsuario(null);
  };

  return <Ctx.Provider value={{ usuario, entrar, sair, atualizar }}>{children}</Ctx.Provider>;
}

export const useAuth = () => useContext(Ctx);
