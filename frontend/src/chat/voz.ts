import type { TipoSinal } from '../types';

export type SinalVoz = { tipo: TipoSinal; dados: string };

/** Pede o microfone com o tratamento de áudio do navegador (eco, ruído e volume). Só funciona em HTTPS ou localhost. */
export function obterMicrofone() {
  return navigator.mediaDevices.getUserMedia({
    audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true },
    video: false,
  });
}

/**
 * Conexão de áudio WebRTC entre dois navegadores. A sinalização (offer/answer/candidatos ICE) sai por `enviarSinal`
 * e chega por `receber`; o servidor apenas a repassa.
 */
export class LigacaoVoz {
  private readonly pc: RTCPeerConnection;
  /** Candidatos que chegam antes da descrição remota precisam esperar. */
  private pendentes: RTCIceCandidateInit[] = [];

  constructor(iceServers: RTCIceServer[], private readonly microfone: MediaStream, private readonly h: {
    enviarSinal: (s: SinalVoz) => void;
    aoAudioRemoto: (s: MediaStream) => void;
    aoEstado: (e: RTCPeerConnectionState) => void;
  }) {
    this.pc = new RTCPeerConnection({ iceServers });
    microfone.getTracks().forEach((t) => this.pc.addTrack(t, microfone));
    this.pc.onicecandidate = (e) => {
      if (e.candidate) h.enviarSinal({ tipo: 'ice', dados: JSON.stringify(e.candidate.toJSON()) });
    };
    this.pc.ontrack = (e) => h.aoAudioRemoto(e.streams[0]);
    this.pc.onconnectionstatechange = () => h.aoEstado(this.pc.connectionState);
  }

  /** Lado de quem ligou: cria a oferta. */
  async iniciar() {
    const oferta = await this.pc.createOffer();
    await this.pc.setLocalDescription(oferta);
    this.h.enviarSinal({ tipo: 'offer', dados: JSON.stringify({ type: oferta.type, sdp: oferta.sdp }) });
  }

  async receber(sinal: SinalVoz) {
    const dados = JSON.parse(sinal.dados);
    if (sinal.tipo === 'ice') {
      if (this.pc.remoteDescription) await this.pc.addIceCandidate(dados);
      else this.pendentes.push(dados);
      return;
    }
    await this.pc.setRemoteDescription(dados);
    for (const c of this.pendentes.splice(0)) await this.pc.addIceCandidate(c);
    if (sinal.tipo === 'offer') {
      const resposta = await this.pc.createAnswer();
      await this.pc.setLocalDescription(resposta);
      this.h.enviarSinal({ tipo: 'answer', dados: JSON.stringify({ type: resposta.type, sdp: resposta.sdp }) });
    }
  }

  silenciar(mudo: boolean) {
    this.microfone.getAudioTracks().forEach((t) => (t.enabled = !mudo));
  }

  encerrar() {
    this.pc.close();
    this.microfone.getTracks().forEach((t) => t.stop());
  }
}
