import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { api, sessao } from '../api';
import { useAuth } from '../auth';
import type { Contato, ConversaChat, EventoChat } from '../types';
import { conectarChat } from './conexao';
import { aplicarEvento, totalNaoLidas } from './estado';

type Ouvinte = (e: EventoChat) => void;

interface ChatCtx {
  meuId?: number;
  conversas: ConversaChat[];
  contatos: Contato[];
  online: Set<number>;
  naoLidas: number;
  conectado: boolean;
  conversaAberta: number | null;
  abrirConversa: (id: number | null) => void;
  /** Insere/atualiza uma conversa recebida da API (sem esperar o evento do WebSocket). */
  guardarConversa: (c: ConversaChat) => void;
  aplicar: (e: EventoChat) => void;
  ouvir: (ouvinte: Ouvinte) => () => void;
  recarregar: () => Promise<void>;
}

const Ctx = createContext<ChatCtx>(null!);

export const useChat = () => useContext(Ctx);

/** Estado global do chat (lista de conversas, não lidas, quem está online) e a conexão em tempo real. */
export function ChatProvider({ children }: { children: ReactNode }) {
  const { usuario } = useAuth();
  const meuId = usuario?.id;
  const [conversas, setConversas] = useState<ConversaChat[]>([]);
  const [contatos, setContatos] = useState<Contato[]>([]);
  const [online, setOnline] = useState<Set<number>>(new Set());
  const [conectado, setConectado] = useState(false);
  const [conversaAberta, setConversaAberta] = useState<number | null>(null);
  const aberta = useRef<number | null>(null);
  const listaAtual = useRef<ConversaChat[]>([]);
  listaAtual.current = conversas;
  const ouvintes = useRef(new Set<Ouvinte>());

  const recarregar = useCallback(async () => {
    const [cs, us] = await Promise.all([
      api.get<ConversaChat[]>('/chat/conversas'),
      api.get<Contato[]>('/chat/usuarios'),
    ]);
    setConversas(cs);
    setContatos(us);
    setOnline(new Set(us.filter((u) => u.online).map((u) => u.id)));
  }, []);

  const aplicar = useCallback((e: EventoChat) => {
    if (e.tipo === 'MENSAGEM' && !listaAtual.current.some((c) => c.id === e.conversaId)) {
      void recarregar().catch(() => undefined); // primeira mensagem de uma conversa ainda não listada
    }
    setConversas((cs) => aplicarEvento(cs, e, meuId, aberta.current));
    ouvintes.current.forEach((o) => o(e));
  }, [meuId, recarregar]);

  // Funções estáveis: telas as usam em efeitos (ex.: limpar a conversa aberta ao sair).
  const abrirConversa = useCallback((id: number | null) => {
    aberta.current = id;
    setConversaAberta(id);
    if (id !== null) setConversas((cs) => cs.map((c) => (c.id === id ? { ...c, naoLidas: 0 } : c)));
  }, []);

  const guardarConversa = useCallback((c: ConversaChat) => aplicar({ tipo: 'CONVERSA', conversaId: c.id, conversa: c }), [aplicar]);

  const ouvir = useCallback((o: Ouvinte) => {
    ouvintes.current.add(o);
    return () => {
      ouvintes.current.delete(o);
    };
  }, []);

  useEffect(() => {
    void recarregar().catch(() => undefined);
    const token = sessao.token();
    if (!token || import.meta.env.MODE === 'test') return;
    return conectarChat(token, {
      aoEvento: aplicar,
      aoPresenca: (p) => setOnline((atual) => {
        const novo = new Set(atual);
        if (p.online) novo.add(p.usuarioId);
        else novo.delete(p.usuarioId);
        return novo;
      }),
      aoConectar: () => {
        setConectado(true);
        void recarregar().catch(() => undefined);
      },
      aoDesconectar: () => setConectado(false),
    });
  }, [aplicar, recarregar]);

  const valor = useMemo<ChatCtx>(() => ({
    meuId,
    conversas,
    contatos,
    online,
    naoLidas: totalNaoLidas(conversas),
    conectado,
    conversaAberta,
    abrirConversa,
    guardarConversa,
    aplicar,
    ouvir,
    recarregar,
  }), [meuId, conversas, contatos, online, conectado, conversaAberta, abrirConversa, guardarConversa, aplicar, ouvir, recarregar]);

  return <Ctx.Provider value={valor}>{children}</Ctx.Provider>;
}
