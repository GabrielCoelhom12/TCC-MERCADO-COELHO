import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react';
import { ApiError } from '../api';
import type { Situacao, Violacao } from '../types';
import { ROTULO_REGRA, dataCurta } from '../format';

// ---------------------------------------------------------------- toast

type Toast = { tipo: 'ok' | 'erro'; texto: string } | null;
const ToastCtx = createContext<(tipo: 'ok' | 'erro', texto: string) => void>(() => {});

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toast, setToast] = useState<Toast>(null);
  useEffect(() => {
    if (!toast) return;
    const t = setTimeout(() => setToast(null), 4500);
    return () => clearTimeout(t);
  }, [toast]);
  const mostrar = useCallback((tipo: 'ok' | 'erro', texto: string) => setToast({ tipo, texto }), []);
  return (
    <ToastCtx.Provider value={mostrar}>
      {children}
      {toast && <div className={`toast ${toast.tipo}`} role="status">{toast.texto}</div>}
    </ToastCtx.Provider>
  );
}

export const useToast = () => useContext(ToastCtx);

export const mensagemErro = (e: unknown) => (e instanceof ApiError || e instanceof Error ? e.message : 'Erro inesperado.');

// ---------------------------------------------------------------- carregamento

export function useCarregar<T>(carregar: () => Promise<T>, deps: unknown[] = []) {
  const [dados, setDados] = useState<T | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  const [versao, setVersao] = useState(0);
  useEffect(() => {
    let ativo = true;
    carregar()
      .then((d) => ativo && (setDados(d), setErro(null)))
      .catch((e) => ativo && setErro(mensagemErro(e)));
    return () => {
      ativo = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, versao]);
  return { dados, setDados, erro, recarregar: () => setVersao((v) => v + 1) };
}

export function Carregando({ erro }: { erro?: string | null }) {
  return erro ? <div className="aviso erro">{erro}</div> : <p className="muted">Carregando…</p>;
}

/** Faixa exibida nas telas de cadastro quando o perfil só pode consultar. */
export function AvisoConsulta() {
  return <div className="aviso info">Seu perfil permite consultar esta página; as alterações ficam com a gerência.</div>;
}

// ---------------------------------------------------------------- modal

export function Modal({ titulo, aberto, onFechar, children, acoes }: {
  titulo: string;
  aberto: boolean;
  onFechar: () => void;
  children: ReactNode;
  acoes?: ReactNode;
}) {
  useEffect(() => {
    const esc = (e: KeyboardEvent) => e.key === 'Escape' && onFechar();
    window.addEventListener('keydown', esc);
    return () => window.removeEventListener('keydown', esc);
  }, [onFechar]);
  if (!aberto) return null;
  return (
    <div className="modal-fundo" onMouseDown={onFechar}>
      <div className="modal" role="dialog" aria-label={titulo} onMouseDown={(e) => e.stopPropagation()}>
        <h2>{titulo}</h2>
        {children}
        {acoes && <div className="acoes">{acoes}</div>}
      </div>
    </div>
  );
}

// ---------------------------------------------------------------- confirmação

export type OpcoesConfirmacao = {
  titulo: string;
  mensagem: ReactNode;
  /** Texto do botão que confirma (padrão "Confirmar"). */
  confirmar?: string;
  /** Ação destrutiva (excluir, apagar): o botão de confirmar fica em vermelho. */
  perigoso?: boolean;
};

type Pedido = OpcoesConfirmacao & { responder: (sim: boolean) => void };
const ConfirmacaoCtx = createContext<(opcoes: OpcoesConfirmacao) => Promise<boolean>>(async () => false);

/**
 * Confirmações dentro do próprio sistema, no lugar do window.confirm do navegador:
 * `if (!(await confirmar({ titulo, mensagem }))) return;`. Esc, clicar fora ou "Cancelar" respondem "não".
 */
export function ConfirmacaoProvider({ children }: { children: ReactNode }) {
  const [pedido, setPedido] = useState<Pedido | null>(null);
  const confirmar = useCallback(
    (opcoes: OpcoesConfirmacao) => new Promise<boolean>((resolve) => setPedido({ ...opcoes, responder: resolve })),
    [],
  );
  const responder = useCallback((sim: boolean) => {
    setPedido((atual) => {
      atual?.responder(sim);
      return null;
    });
  }, []);
  const cancelar = useCallback(() => responder(false), [responder]);
  return (
    <ConfirmacaoCtx.Provider value={confirmar}>
      {children}
      <Modal titulo={pedido?.titulo ?? ''} aberto={pedido !== null} onFechar={cancelar}
        acoes={<>
          <button onClick={cancelar}>Cancelar</button>
          <button className={pedido?.perigoso ? 'primario perigoso' : 'primario'} autoFocus onClick={() => responder(true)}>
            {pedido?.confirmar ?? 'Confirmar'}
          </button>
        </>}>
        <p className="confirmacao-texto">{pedido?.mensagem}</p>
      </Modal>
    </ConfirmacaoCtx.Provider>
  );
}

export const useConfirmar = () => useContext(ConfirmacaoCtx);

// ---------------------------------------------------------------- status

const SITUACAO: Record<Situacao, { classe: string; texto: string }> = {
  OK: { classe: 'ok', texto: 'Dentro dos limites' },
  ABAIXO_MINIMO: { classe: 'erro', texto: 'Abaixo do mínimo' },
  ACIMA_LIMITE: { classe: 'erro', texto: 'Superdimensionado' },
  INVIAVEL: { classe: 'alerta', texto: 'Projeção inviável' },
  VAZIO: { classe: 'neutro', texto: 'Sem alocação' },
};

export function BadgeSituacao({ situacao, curto }: { situacao: Situacao; curto?: boolean }) {
  const s = SITUACAO[situacao];
  if (curto) return <span className={`ponto-situacao ${s.classe}`} title={s.texto} aria-label={s.texto} />;
  return <span className={`badge ${s.classe}`} title={s.texto}>{s.texto}</span>;
}

export function ListaViolacoes({ violacoes, vazio }: { violacoes: Violacao[]; vazio?: string }) {
  if (violacoes.length === 0) {
    return <div className="aviso ok">{vazio ?? 'Nenhuma violação: escala em conformidade.'}</div>;
  }
  return (
    <div className="violacoes">
      {violacoes.map((v, i) => (
        <div className="violacao" key={i}>
          <span className={`badge ${v.severidade === 'ERRO' ? 'erro' : 'alerta'}`}>
            {v.severidade === 'ERRO' ? 'Erro' : 'Alerta'}
          </span>
          <div>
            <div className="meta">
              {dataCurta(v.data)} · {ROTULO_REGRA[v.regra] ?? v.regra}
              {v.funcionarioNome ? ` · ${v.funcionarioNome}` : ''}
            </div>
            {v.mensagem}
          </div>
        </div>
      ))}
    </div>
  );
}
