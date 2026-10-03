import { beforeEach, describe, expect, it, vi } from 'vitest';
import { LigacaoVoz } from './voz';

/** RTCPeerConnection falso: registra as chamadas e permite disparar os eventos do navegador. */
class PcFalso {
  static ultima: PcFalso;
  config: RTCConfiguration;
  remoteDescription: RTCSessionDescriptionInit | null = null;
  localDescription: RTCSessionDescriptionInit | null = null;
  candidatos: RTCIceCandidateInit[] = [];
  fechada = false;
  connectionState = 'new';
  onicecandidate: ((e: { candidate: { toJSON: () => RTCIceCandidateInit } | null }) => void) | null = null;
  ontrack: ((e: { streams: MediaStream[] }) => void) | null = null;
  onconnectionstatechange: (() => void) | null = null;
  addTrack = vi.fn();

  constructor(config: RTCConfiguration) {
    this.config = config;
    PcFalso.ultima = this;
  }

  async createOffer() { return { type: 'offer', sdp: 'sdp-oferta' }; }
  async createAnswer() { return { type: 'answer', sdp: 'sdp-resposta' }; }
  async setLocalDescription(d: RTCSessionDescriptionInit) { this.localDescription = d; }
  async setRemoteDescription(d: RTCSessionDescriptionInit) { this.remoteDescription = d; }
  async addIceCandidate(c: RTCIceCandidateInit) { this.candidatos.push(c); }
  close() { this.fechada = true; }
}

const trilha = () => ({ enabled: true, stop: vi.fn() });

function criar() {
  const faixa = trilha();
  const microfone = { getTracks: () => [faixa], getAudioTracks: () => [faixa] } as unknown as MediaStream;
  const h = { enviarSinal: vi.fn(), aoAudioRemoto: vi.fn(), aoEstado: vi.fn() };
  const ligacao = new LigacaoVoz([{ urls: ['stun:teste'] }], microfone, h);
  return { ligacao, h, faixa, pc: PcFalso.ultima, microfone };
}

describe('LigacaoVoz', () => {
  beforeEach(() => vi.stubGlobal('RTCPeerConnection', PcFalso));

  it('usa os servidores ICE e envia o microfone', () => {
    const { pc, faixa, microfone } = criar();
    expect(pc.config.iceServers).toEqual([{ urls: ['stun:teste'] }]);
    expect(pc.addTrack).toHaveBeenCalledWith(faixa, microfone);
  });

  it('quem liga cria e envia a oferta', async () => {
    const { ligacao, h } = criar();
    await ligacao.iniciar();
    expect(h.enviarSinal).toHaveBeenCalledWith({ tipo: 'offer', dados: JSON.stringify({ type: 'offer', sdp: 'sdp-oferta' }) });
  });

  it('quem atende responde à oferta e aplica candidatos que chegaram antes', async () => {
    const { ligacao, h, pc } = criar();
    await ligacao.receber({ tipo: 'ice', dados: JSON.stringify({ candidate: 'c1' }) });
    expect(pc.candidatos).toEqual([]); // ainda sem descrição remota: fica na fila

    await ligacao.receber({ tipo: 'offer', dados: JSON.stringify({ type: 'offer', sdp: 'x' }) });
    expect(pc.remoteDescription).toEqual({ type: 'offer', sdp: 'x' });
    expect(pc.candidatos).toEqual([{ candidate: 'c1' }]);
    expect(h.enviarSinal).toHaveBeenCalledWith({ tipo: 'answer', dados: JSON.stringify({ type: 'answer', sdp: 'sdp-resposta' }) });

    await ligacao.receber({ tipo: 'ice', dados: JSON.stringify({ candidate: 'c2' }) });
    expect(pc.candidatos).toEqual([{ candidate: 'c1' }, { candidate: 'c2' }]);
  });

  it('quem ligou aplica a resposta', async () => {
    const { ligacao, pc } = criar();
    await ligacao.receber({ tipo: 'answer', dados: JSON.stringify({ type: 'answer', sdp: 'y' }) });
    expect(pc.remoteDescription).toEqual({ type: 'answer', sdp: 'y' });
  });

  it('repassa candidatos locais, áudio remoto e estado da conexão', () => {
    const { h, pc } = criar();
    pc.onicecandidate?.({ candidate: { toJSON: () => ({ candidate: 'local' }) } });
    pc.onicecandidate?.({ candidate: null }); // fim da coleta: nada a enviar
    expect(h.enviarSinal).toHaveBeenCalledTimes(1);
    expect(h.enviarSinal).toHaveBeenCalledWith({ tipo: 'ice', dados: JSON.stringify({ candidate: 'local' }) });

    const remoto = {} as MediaStream;
    pc.ontrack?.({ streams: [remoto] });
    expect(h.aoAudioRemoto).toHaveBeenCalledWith(remoto);

    pc.connectionState = 'connected';
    pc.onconnectionstatechange?.();
    expect(h.aoEstado).toHaveBeenCalledWith('connected');
  });

  it('silencia o microfone e encerra liberando os recursos', () => {
    const { ligacao, faixa, pc } = criar();
    ligacao.silenciar(true);
    expect(faixa.enabled).toBe(false);
    ligacao.silenciar(false);
    expect(faixa.enabled).toBe(true);

    ligacao.encerrar();
    expect(pc.fechada).toBe(true);
    expect(faixa.stop).toHaveBeenCalled();
  });
});
