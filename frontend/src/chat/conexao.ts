import { Client } from '@stomp/stompjs';
import type { EventoChat } from '../types';

export type Presenca = { usuarioId: number; online: boolean };

/**
 * Conexão STOMP com o servidor (/ws), autenticada com o mesmo token da API. Reconecta sozinha; a cada (re)conexão
 * chama `aoConectar` para o estado ser recarregado (eventos perdidos enquanto offline).
 */
export function conectarChat(token: string, h: {
  aoEvento: (e: EventoChat) => void;
  aoPresenca: (p: Presenca) => void;
  aoConectar: () => void;
  aoDesconectar: () => void;
}) {
  const protocolo = window.location.protocol === 'https:' ? 'wss' : 'ws';
  const cliente = new Client({
    brokerURL: `${protocolo}://${window.location.host}/ws`,
    connectHeaders: { Authorization: `Bearer ${token}` },
    reconnectDelay: 5000,
    onConnect: () => {
      cliente.subscribe('/user/queue/chat', (frame) => h.aoEvento(JSON.parse(frame.body)));
      cliente.subscribe('/topic/presenca', (frame) => h.aoPresenca(JSON.parse(frame.body)));
      h.aoConectar();
    },
    onWebSocketClose: () => h.aoDesconectar(),
  });
  cliente.activate();
  return () => {
    void cliente.deactivate();
  };
}
