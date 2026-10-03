import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { api } from '../api';
import { mensagemErro, useToast } from '../components/ui';
import type { Chamada, Contato, EventoChat } from '../types';
import { useChat } from './ChatProvider';
import PainelChamada from './PainelChamada';
import { tocar } from './toque';
import { LigacaoVoz, obterMicrofone, type SinalVoz } from './voz';

export type FaseChamada = 'chamando' | 'recebendo' | 'conectando' | 'em-andamento' | 'encerrada';

export interface EstadoChamada {
  chamada: Chamada;
  fase: FaseChamada;
  outro: Contato;
  mudo: boolean;
  /** Momento (Date.now) em que o áudio conectou, para o cronômetro. */
  conectadaEm?: number;
  mensagem?: string;
}

type Configuracao = { iceServers: RTCIceServer[]; tempoToqueSegundos: number };

interface ChamadaCtx {
  /** Falso fora do provider (ex.: testes de outras telas): a interface de chamada não é exibida. */
  disponivel: boolean;
  estado: EstadoChamada | null;
  ligar: (conversaId: number) => Promise<void>;
  atender: () => Promise<void>;
  recusar: () => Promise<void>;
  desligar: () => Promise<void>;
  alternarMudo: () => void;
}

const nada = async () => undefined;
const Ctx = createContext<ChamadaCtx>({
  disponivel: false, estado: null, ligar: nada, atender: nada, recusar: nada, desligar: nada, alternarMudo: () => undefined,
});

export const useChamada = () => useContext(Ctx);

const ERRO_MICROFONE = 'Não foi possível usar o microfone. Verifique a permissão do navegador.';

/** Chamadas de voz um a um: estado da chamada, conexão WebRTC, sons e o painel flutuante visível em todo o sistema. */
export function ChamadaProvider({ children }: { children: ReactNode }) {
  const { meuId, ouvir } = useChat();
  const toast = useToast();
  const [estado, setEstado] = useState<EstadoChamada | null>(null);
  const atual = useRef<EstadoChamada | null>(null);
  const microfone = useRef<MediaStream | null>(null);
  const ligacao = useRef<LigacaoVoz | null>(null);
  const sinaisPendentes = useRef<SinalVoz[]>([]);
  const config = useRef<Configuracao | null>(null);
  const pararSom = useRef<(() => void) | null>(null);
  const tempoToque = useRef<number | undefined>(undefined);
  const audio = useRef<HTMLAudioElement>(null);

  const definir = useCallback((e: EstadoChamada | null) => {
    atual.current = e;
    setEstado(e);
  }, []);

  const atualizar = useCallback((parcial: Partial<EstadoChamada>) => {
    if (atual.current) definir({ ...atual.current, ...parcial });
  }, [definir]);

  const som = useCallback((tipo: 'recebendo' | 'chamando' | null) => {
    pararSom.current?.();
    pararSom.current = tipo ? tocar(tipo) : null;
  }, []);

  const obterConfig = useCallback(async () => {
    config.current ??= await api.get<Configuracao>('/chat/chamadas/config');
    return config.current;
  }, []);

  const liberar = useCallback(() => {
    som(null);
    window.clearTimeout(tempoToque.current);
    ligacao.current?.encerrar();
    ligacao.current = null;
    sinaisPendentes.current = [];
    microfone.current?.getTracks().forEach((t) => t.stop());
    microfone.current = null;
    if (audio.current) audio.current.srcObject = null;
  }, [som]);

  const finalizar = useCallback((c: Chamada) => {
    liberar();
    const souChamador = c.chamador.id === meuId;
    const mensagem = c.status === 'RECUSADA' ? 'Chamada recusada'
      : c.status === 'PERDIDA' ? (souChamador ? 'Sem resposta' : 'Chamada perdida')
        : 'Chamada encerrada';
    const outro = atual.current?.outro ?? (souChamador ? c.destinatario : c.chamador);
    definir({ chamada: c, outro, fase: 'encerrada', mudo: false, mensagem });
    window.setTimeout(() => {
      if (atual.current?.fase === 'encerrada' && atual.current.chamada.id === c.id) definir(null);
    }, 2500);
  }, [definir, liberar, meuId]);

  const desligar = useCallback(async () => {
    const a = atual.current;
    if (!a || a.fase === 'encerrada') return;
    try {
      finalizar(await api.post<Chamada>(`/chat/chamadas/${a.chamada.id}/encerrar`));
    } catch {
      liberar();
      definir(null);
    }
  }, [definir, finalizar, liberar]);

  const criarLigacao = useCallback((cfg: Configuracao, chamadaId: number) => {
    const nova = new LigacaoVoz(cfg.iceServers, microfone.current!, {
      enviarSinal: (s) => void api.post(`/chat/chamadas/${chamadaId}/sinal`, s).catch(() => undefined),
      aoAudioRemoto: (s) => {
        if (!audio.current) return;
        audio.current.srcObject = s;
        try {
          void audio.current.play()?.catch(() => undefined);
        } catch {
          /* reprodução automática bloqueada: o elemento tem autoplay */
        }
      },
      aoEstado: (e) => {
        if (e === 'connected' && atual.current?.fase === 'conectando') {
          atualizar({ fase: 'em-andamento', conectadaEm: Date.now() });
        } else if (e === 'failed') {
          toast('erro', 'Não foi possível conectar o áudio.');
          void desligar();
        }
      },
    });
    ligacao.current = nova;
    for (const s of sinaisPendentes.current.splice(0)) void nova.receber(s);
    return nova;
  }, [atualizar, desligar, toast]);

  // Eventos em tempo real (via ChatProvider): convite, atendimento, fim da chamada e sinalização WebRTC.
  const tratar = useRef<(e: EventoChat) => void>(() => undefined);
  tratar.current = (e) => {
    const a = atual.current;
    if (e.tipo === 'CHAMADA') {
      const c = e.chamada;
      if (c.status === 'TOCANDO') {
        if (!a && c.destinatario.id === meuId) {
          definir({ chamada: c, fase: 'recebendo', outro: c.chamador, mudo: false });
          som('recebendo');
        }
        return;
      }
      if (!a || a.chamada.id !== c.id) return;
      if (c.status === 'EM_ANDAMENTO') {
        if (a.fase === 'recebendo') {
          // Atendida em outra aba/dispositivo do mesmo usuário: esta aba para de tocar.
          liberar();
          definir(null);
        } else if (a.fase === 'chamando') {
          som(null);
          window.clearTimeout(tempoToque.current);
          atualizar({ chamada: c, fase: 'conectando' });
          void obterConfig().then((cfg) => criarLigacao(cfg, c.id).iniciar()).catch(() => void desligar());
        }
        return;
      }
      if (a.fase !== 'encerrada') finalizar(c);
    } else if (e.tipo === 'SINAL' && a && a.chamada.id === e.chamadaId) {
      if (ligacao.current) void ligacao.current.receber(e.sinal);
      else sinaisPendentes.current.push(e.sinal);
    }
  };

  useEffect(() => ouvir((e) => tratar.current(e)), [ouvir]);
  useEffect(() => () => liberar(), [liberar]);

  const ligar = useCallback(async (conversaId: number) => {
    if (atual.current) {
      toast('erro', 'Você já está em uma chamada.');
      return;
    }
    try {
      microfone.current = await obterMicrofone();
    } catch {
      toast('erro', ERRO_MICROFONE);
      return;
    }
    try {
      const cfg = await obterConfig();
      const c = await api.post<Chamada>(`/chat/conversas/${conversaId}/chamadas`);
      definir({ chamada: c, fase: 'chamando', outro: c.destinatario, mudo: false });
      som('chamando');
      tempoToque.current = window.setTimeout(() => void desligar(), cfg.tempoToqueSegundos * 1000);
    } catch (e) {
      liberar();
      toast('erro', mensagemErro(e));
    }
  }, [definir, desligar, liberar, obterConfig, som, toast]);

  const recusarChamada = useCallback(async (chamadaId: number) => {
    som(null);
    try {
      finalizar(await api.post<Chamada>(`/chat/chamadas/${chamadaId}/recusar`));
    } catch {
      liberar();
      definir(null);
    }
  }, [definir, finalizar, liberar, som]);

  const recusar = useCallback(async () => {
    const a = atual.current;
    if (a?.fase === 'recebendo') await recusarChamada(a.chamada.id);
  }, [recusarChamada]);

  const atender = useCallback(async () => {
    const a = atual.current;
    if (!a || a.fase !== 'recebendo') return;
    // Resposta imediata ao clique, mesmo enquanto o navegador pede permissão do microfone.
    som(null);
    atualizar({ fase: 'conectando' });
    try {
      microfone.current = await obterMicrofone();
    } catch {
      toast('erro', ERRO_MICROFONE);
      void recusarChamada(a.chamada.id);
      return;
    }
    try {
      // A conexão já fica pronta antes de avisar o servidor, para receber a oferta de quem ligou.
      criarLigacao(await obterConfig(), a.chamada.id);
      await api.post<Chamada>(`/chat/chamadas/${a.chamada.id}/atender`);
    } catch (e) {
      toast('erro', mensagemErro(e));
      liberar();
      definir(null);
    }
  }, [atualizar, criarLigacao, definir, liberar, obterConfig, recusarChamada, som, toast]);

  const alternarMudo = useCallback(() => {
    const a = atual.current;
    if (!a) return;
    const mudo = !a.mudo;
    if (ligacao.current) ligacao.current.silenciar(mudo);
    else microfone.current?.getAudioTracks().forEach((t) => (t.enabled = !mudo));
    atualizar({ mudo });
  }, [atualizar]);

  return (
    <Ctx.Provider value={{ disponivel: true, estado, ligar, atender, recusar, desligar, alternarMudo }}>
      {children}
      {estado && <PainelChamada estado={estado} />}
      <audio ref={audio} autoPlay hidden />
    </Ctx.Provider>
  );
}
