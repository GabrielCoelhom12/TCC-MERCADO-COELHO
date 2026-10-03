import { act, fireEvent, screen, waitFor, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import Mensagens from '../pages/Mensagens';
import { logar, mockFetch, renderizar } from '../test/util';
import type { Chamada, Contato, ConversaChat, EventoChat, MensagemChat, StatusChamada } from '../types';
import { ChamadaProvider } from './ChamadaProvider';
import { ChatProvider, useChat } from './ChatProvider';

// O WebRTC real não existe no jsdom: o módulo de voz é substituído por um dublê controlável.
const voz = vi.hoisted(() => ({
  obterMicrofone: vi.fn(),
  instancias: [] as { h: { aoEstado: (e: string) => void }; iniciar: ReturnType<typeof vi.fn>; receber: ReturnType<typeof vi.fn>; silenciar: ReturnType<typeof vi.fn>; encerrar: ReturnType<typeof vi.fn> }[],
}));
const som = vi.hoisted(() => ({ tocar: vi.fn(), parar: vi.fn() }));
vi.mock('./toque', () => ({ tocar: som.tocar }));

vi.mock('./voz', () => ({
  obterMicrofone: voz.obterMicrofone,
  LigacaoVoz: class {
    iniciar = vi.fn(async () => undefined);
    receber = vi.fn(async () => undefined);
    silenciar = vi.fn();
    encerrar = vi.fn();
    constructor(_ice: unknown, _mic: unknown, public h: { aoEstado: (e: string) => void }) {
      voz.instancias.push(this);
    }
  },
}));

const EU = 1;
const eu: Contato = { id: EU, nome: 'Gerência', foto: null, online: true };
const ana: Contato = { id: 7, nome: 'Ana Souza', foto: null, online: true };
const bruno: Contato = { id: 8, nome: 'Bruno Lima', foto: null, online: false };

const conversa = (id: number, outro: Contato): ConversaChat => ({
  id, tipo: 'DIRETA', nome: outro.nome, foto: null, participantes: [eu, outro], naoLidas: 0, ultimaMensagem: null,
  leituras: {}, atualizadaEm: `2026-10-0${id}T08:00:00`,
});

const chamada = (status: StatusChamada, chamador = eu, destinatario = ana): Chamada => ({
  id: 50, conversaId: 1, chamador, destinatario, status, iniciadaEm: '2026-10-09T08:00:00', atendidaEm: null, encerradaEm: null,
});

const evento = (c: Chamada): EventoChat => ({ tipo: 'CHAMADA', conversaId: c.conversaId, chamada: c });

let chat: ReturnType<typeof useChat>;
function Espiao() {
  chat = useChat();
  return null;
}

function montar(extra: Record<string, unknown> = {}, mensagens: MensagemChat[] = [], sessaoSemId = false) {
  const fetch = mockFetch({
    'GET /api/chat/conversas': { corpo: [conversa(1, ana), conversa(2, bruno)] },
    'GET /api/chat/usuarios': { corpo: [ana, bruno] },
    'GET /api/chat/conversas/1/mensagens': { corpo: mensagens },
    'POST /api/chat/conversas/1/lida': { status: 204 },
    'GET /api/chat/conversas/2/mensagens': { corpo: [] },
    'POST /api/chat/conversas/2/lida': { status: 204 },
    'GET /api/chat/chamadas/config': { corpo: { iceServers: [{ urls: ['stun:x'] }], tempoToqueSegundos: 30 } },
    ...extra,
  });
  logar();
  if (sessaoSemId) {
    // sessão aberta antes do id existir na resposta do login
    localStorage.setItem('coelho.usuario', JSON.stringify({ nome: 'Gerência', login: 'gestor', perfil: 'ADMIN' }));
  }
  renderizar(<ChatProvider><ChamadaProvider><Espiao /><Mensagens /></ChamadaProvider></ChatProvider>, '/mensagens');
  return fetch;
}

const microfone = () => ({ getTracks: () => [{ stop: vi.fn() }] }) as unknown as MediaStream;

describe('Chamada de voz', () => {
  beforeEach(() => {
    voz.instancias.length = 0;
    voz.obterMicrofone.mockReset();
    voz.obterMicrofone.mockResolvedValue(microfone());
    som.parar.mockReset();
    som.tocar.mockReset();
    som.tocar.mockReturnValue(som.parar);
  });

  it('liga para quem está online e pode cancelar enquanto chama', async () => {
    const fetch = montar({
      'POST /api/chat/conversas/1/chamadas': { status: 201, corpo: chamada('TOCANDO') },
      'POST /api/chat/chamadas/50/encerrar': { corpo: chamada('PERDIDA') },
    });
    fireEvent.click(await screen.findByRole('button', { name: /Ana Souza/ }));
    fireEvent.click(await screen.findByRole('button', { name: 'Ligar' }));

    const painel = await screen.findByRole('dialog', { name: 'Chamada de voz' });
    expect(within(painel).getByText('Chamando…')).toBeInTheDocument();
    expect(within(painel).getByText('Ana Souza')).toBeInTheDocument();
    expect(voz.obterMicrofone).toHaveBeenCalled();

    fireEvent.click(within(painel).getByRole('button', { name: 'Cancelar' }));
    await waitFor(() =>
      expect(fetch).toHaveBeenCalledWith('/api/chat/chamadas/50/encerrar', expect.objectContaining({ method: 'POST' })),
    );
  });

  it('não liga para quem está offline', async () => {
    montar();
    fireEvent.click(await screen.findByRole('button', { name: /Bruno Lima/ }));
    expect(await screen.findByRole('button', { name: 'Ligar' })).toBeDisabled();
  });

  it('avisa quando o microfone não é liberado', async () => {
    voz.obterMicrofone.mockRejectedValue(new Error('NotAllowedError'));
    const fetch = montar();
    fireEvent.click(await screen.findByRole('button', { name: /Ana Souza/ }));
    fireEvent.click(await screen.findByRole('button', { name: 'Ligar' }));

    expect(await screen.findByRole('status')).toHaveTextContent('microfone');
    expect(fetch.mock.calls.some(([url]) => String(url).endsWith('/chamadas'))).toBe(false);
  });

  it('quem liga inicia o áudio quando a outra pessoa atende', async () => {
    montar({ 'POST /api/chat/conversas/1/chamadas': { status: 201, corpo: chamada('TOCANDO') } });
    fireEvent.click(await screen.findByRole('button', { name: /Ana Souza/ }));
    fireEvent.click(await screen.findByRole('button', { name: 'Ligar' }));
    await screen.findByText('Chamando…');

    act(() => chat.aplicar(evento({ ...chamada('EM_ANDAMENTO'), atendidaEm: '2026-10-09T08:00:05' })));
    expect(await screen.findByText('Conectando…')).toBeInTheDocument();
    await waitFor(() => expect(voz.instancias[0]?.iniciar).toHaveBeenCalled());

    act(() => voz.instancias[0].h.aoEstado('connected'));
    expect(await screen.findByText('00:00')).toBeInTheDocument();
  });

  it('recebe a chamada, atende, responde à oferta e pode silenciar e desligar', async () => {
    const fetch = montar({
      'POST /api/chat/chamadas/50/atender': { corpo: chamada('EM_ANDAMENTO', ana, eu) },
      'POST /api/chat/chamadas/50/encerrar': { corpo: chamada('ENCERRADA', ana, eu) },
    });
    await screen.findByRole('button', { name: /Ana Souza/ });

    act(() => chat.aplicar(evento(chamada('TOCANDO', ana, eu))));
    const painel = await screen.findByRole('dialog', { name: 'Chamada de voz' });
    expect(within(painel).getByText('Chamada de voz recebida')).toBeInTheDocument();

    fireEvent.click(within(painel).getByRole('button', { name: 'Atender' }));
    await waitFor(() =>
      expect(fetch).toHaveBeenCalledWith('/api/chat/chamadas/50/atender', expect.objectContaining({ method: 'POST' })),
    );
    expect(await screen.findByText('Conectando…')).toBeInTheDocument();

    act(() => chat.aplicar({ tipo: 'SINAL', chamadaId: 50, sinal: { tipo: 'offer', dados: '{"type":"offer"}' } }));
    await waitFor(() => expect(voz.instancias[0]?.receber).toHaveBeenCalledWith({ tipo: 'offer', dados: '{"type":"offer"}' }));
    expect(voz.instancias[0].iniciar).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: 'Silenciar microfone' }));
    expect(voz.instancias[0].silenciar).toHaveBeenCalledWith(true);
    expect(screen.getByRole('button', { name: 'Ativar microfone' })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Desligar' }));
    await waitFor(() =>
      expect(fetch).toHaveBeenCalledWith('/api/chat/chamadas/50/encerrar', expect.objectContaining({ method: 'POST' })),
    );
    expect(await screen.findByText('Chamada encerrada')).toBeInTheDocument();
    expect(voz.instancias[0].encerrar).toHaveBeenCalled();
  });

  it('para de tocar quando a chamada é atendida em outra aba', async () => {
    montar();
    await screen.findByRole('button', { name: /Ana Souza/ });
    act(() => chat.aplicar(evento(chamada('TOCANDO', ana, eu))));
    await screen.findByText('Chamada de voz recebida');
    expect(som.tocar).toHaveBeenCalledWith('recebendo');

    act(() => chat.aplicar(evento(chamada('EM_ANDAMENTO', ana, eu))));
    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Chamada de voz' })).not.toBeInTheDocument());
    expect(som.parar).toHaveBeenCalled();
  });

  it('ao atender, para o toque e mostra "Conectando…" enquanto o navegador pede o microfone', async () => {
    voz.obterMicrofone.mockReturnValue(new Promise(() => undefined)); // permissão ainda não respondida
    montar();
    await screen.findByRole('button', { name: /Ana Souza/ });
    act(() => chat.aplicar(evento(chamada('TOCANDO', ana, eu))));

    fireEvent.click(await screen.findByRole('button', { name: 'Atender' }));
    expect(await screen.findByText('Conectando…')).toBeInTheDocument();
    expect(som.parar).toHaveBeenCalled();
  });

  it('se o microfone for negado ao atender, recusa a chamada', async () => {
    voz.obterMicrofone.mockRejectedValue(new Error('NotAllowedError'));
    const fetch = montar({ 'POST /api/chat/chamadas/50/recusar': { corpo: chamada('RECUSADA', ana, eu) } });
    await screen.findByRole('button', { name: /Ana Souza/ });
    act(() => chat.aplicar(evento(chamada('TOCANDO', ana, eu))));

    fireEvent.click(await screen.findByRole('button', { name: 'Atender' }));
    await waitFor(() =>
      expect(fetch).toHaveBeenCalledWith('/api/chat/chamadas/50/recusar', expect.objectContaining({ method: 'POST' })),
    );
    expect(await screen.findByRole('status')).toHaveTextContent('microfone');
  });

  it('sessão antiga sem id é atualizada ao abrir e a chamada funciona', async () => {
    montar({
      'GET /api/auth/me': { corpo: { id: EU, nome: 'Gerência', login: 'gestor', perfil: 'ADMIN', foto: null } },
      'POST /api/chat/conversas/1/chamadas': { status: 201, corpo: chamada('TOCANDO') },
    }, [], true);
    fireEvent.click(await screen.findByRole('button', { name: /Ana Souza/ }));
    fireEvent.click(await screen.findByRole('button', { name: 'Ligar' }));
    await screen.findByText('Chamando…');

    act(() => chat.aplicar(evento(chamada('EM_ANDAMENTO'))));
    await waitFor(() => expect(voz.instancias[0]?.iniciar).toHaveBeenCalled());
    expect(som.parar).toHaveBeenCalled();
  });

  it('recusa a chamada recebida', async () => {
    const fetch = montar({ 'POST /api/chat/chamadas/50/recusar': { corpo: chamada('RECUSADA', ana, eu) } });
    await screen.findByRole('button', { name: /Ana Souza/ });
    act(() => chat.aplicar(evento(chamada('TOCANDO', ana, eu))));

    fireEvent.click(await screen.findByRole('button', { name: 'Recusar' }));
    await waitFor(() =>
      expect(fetch).toHaveBeenCalledWith('/api/chat/chamadas/50/recusar', expect.objectContaining({ method: 'POST' })),
    );
    expect(voz.obterMicrofone).not.toHaveBeenCalled();
  });

  it('mostra quando a outra pessoa recusa', async () => {
    montar({ 'POST /api/chat/conversas/1/chamadas': { status: 201, corpo: chamada('TOCANDO') } });
    fireEvent.click(await screen.findByRole('button', { name: /Ana Souza/ }));
    fireEvent.click(await screen.findByRole('button', { name: 'Ligar' }));
    await screen.findByText('Chamando…');

    act(() => chat.aplicar(evento(chamada('RECUSADA'))));
    expect(await screen.findByText('Chamada recusada')).toBeInTheDocument();
  });

  it('registra as chamadas no histórico da conversa', async () => {
    const base: MensagemChat = {
      id: 1, conversaId: 1, autorId: 7, autorNome: 'Ana Souza', texto: null, imagem: null, escala: null,
      chamada: null, enviadaEm: '2026-10-09T08:00:00',
    };
    montar({}, [
      { ...base, id: 1, chamada: { resultado: 'ATENDIDA', duracaoSegundos: 192 } },
      { ...base, id: 2, chamada: { resultado: 'PERDIDA', duracaoSegundos: null } },
      { ...base, id: 3, autorId: EU, chamada: { resultado: 'RECUSADA', duracaoSegundos: null } },
    ]);
    fireEvent.click(await screen.findByRole('button', { name: /Ana Souza/ }));

    expect(await screen.findByText('Chamada de voz · 3 min 12 s')).toBeInTheDocument();
    expect(screen.getByText('Chamada perdida')).toBeInTheDocument();
    expect(screen.getByText('Chamada recusada')).toBeInTheDocument();
  });
});
