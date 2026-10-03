import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { ChatProvider } from '../chat/ChatProvider';
import { logar, mockFetch, renderizar } from '../test/util';
import type { Contato, ConversaChat, MensagemChat } from '../types';
import Mensagens from './Mensagens';

const EU = 1;
const ana: Contato = { id: 7, nome: 'Ana Souza', foto: null, online: true };
const bruno: Contato = { id: 8, nome: 'Bruno Lima', foto: null, online: false };
const eu: Contato = { id: EU, nome: 'Gerência', foto: null, online: true };

const msg = (id: number, autorId: number, texto: string | null, extra: Partial<MensagemChat> = {}): MensagemChat => ({
  id, conversaId: 1, autorId, autorNome: autorId === EU ? 'Gerência' : 'Ana Souza', texto, imagem: null, escala: null, chamada: null,
  enviadaEm: '2026-10-09T08:00:00', ...extra,
});

const comAna: ConversaChat = {
  id: 1, tipo: 'DIRETA', nome: 'Ana Souza', foto: null, participantes: [eu, ana], naoLidas: 2,
  ultimaMensagem: msg(11, 7, 'Bom dia'), leituras: {}, atualizadaEm: '2026-10-09T08:00:00',
};
const padaria: ConversaChat = {
  id: 2, tipo: 'GRUPO', nome: 'Padaria', foto: null, participantes: [eu, ana, bruno], naoLidas: 0,
  ultimaMensagem: { ...msg(9, EU, 'ok'), conversaId: 2 }, leituras: {}, atualizadaEm: '2026-10-08T08:00:00',
};

const base = (extra: Record<string, unknown> = {}) => ({
  'GET /api/chat/conversas': { corpo: [comAna, padaria] },
  'GET /api/chat/usuarios': { corpo: [ana, bruno] },
  'GET /api/chat/conversas/1/mensagens': { corpo: [msg(10, EU, 'Oi, Ana'), msg(11, 7, 'Bom dia')] },
  'POST /api/chat/conversas/1/lida': { status: 204 },
  ...extra,
});

function abrir() {
  logar();
  renderizar(<ChatProvider><Mensagens /></ChatProvider>, '/mensagens');
}

describe('Mensagens', () => {
  it('lista conversas com prévia, não lidas e quem está online', async () => {
    mockFetch(base());
    abrir();

    const itemAna = (await screen.findByRole('button', { name: /Ana Souza/ }));
    expect(within(itemAna).getByText('Bom dia')).toBeInTheDocument();
    expect(within(itemAna).getByLabelText('2 não lidas')).toBeInTheDocument();
    expect(within(itemAna).getByLabelText('Online')).toBeInTheDocument();
    const itemGrupo = screen.getByRole('button', { name: /Padaria/ });
    expect(within(itemGrupo).getByText('Você: ok')).toBeInTheDocument();
  });

  it('abre a conversa, mostra o histórico e marca como lida', async () => {
    const fetch = mockFetch(base());
    abrir();

    fireEvent.click(await screen.findByRole('button', { name: /Ana Souza/ }));
    expect(await screen.findByText('Oi, Ana')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Ana Souza' })).toBeInTheDocument();
    await waitFor(() =>
      expect(fetch).toHaveBeenCalledWith('/api/chat/conversas/1/lida', expect.objectContaining({ method: 'POST' })),
    );
  });

  it('envia com Enter e mostra a mensagem na conversa', async () => {
    const enviados: unknown[] = [];
    mockFetch(base({
      'POST /api/chat/conversas/1/mensagens': (corpo: unknown) => (enviados.push(corpo), { status: 201, corpo: msg(12, EU, 'Pode cobrir sábado?') }),
    }));
    abrir();

    fireEvent.click(await screen.findByRole('button', { name: /Ana Souza/ }));
    const campo = await screen.findByLabelText('Mensagem');
    fireEvent.change(campo, { target: { value: 'Pode cobrir sábado?' } });
    fireEvent.keyDown(campo, { key: 'Enter' });

    await waitFor(() => expect(enviados).toEqual([{ texto: 'Pode cobrir sábado?', escalaId: null }]));
    expect(await screen.findByText('Pode cobrir sábado?')).toBeInTheDocument();
    expect(campo).toHaveValue('');
  });

  it('Shift+Enter quebra a linha sem enviar', async () => {
    const fetch = mockFetch(base());
    abrir();
    fireEvent.click(await screen.findByRole('button', { name: /Ana Souza/ }));
    const campo = await screen.findByLabelText('Mensagem');
    fireEvent.change(campo, { target: { value: 'linha 1' } });
    fireEvent.keyDown(campo, { key: 'Enter', shiftKey: true });
    expect(fetch.mock.calls.some(([, init]) => init?.method === 'POST' && String(init.body).includes('linha 1'))).toBe(false);
  });

  it('indica quando a outra pessoa leu minha última mensagem', async () => {
    mockFetch(base({
      'GET /api/chat/conversas': { corpo: [{ ...comAna, naoLidas: 0, ultimaMensagem: msg(12, EU, 'Tudo certo?'), leituras: { 7: 12 } }] },
      'GET /api/chat/conversas/1/mensagens': { corpo: [msg(11, 7, 'Bom dia'), msg(12, EU, 'Tudo certo?')] },
    }));
    abrir();
    fireEvent.click(await screen.findByRole('button', { name: /Ana Souza/ }));
    expect(await screen.findByText('Lida')).toBeInTheDocument();
  });

  it('mostra a escala mencionada com link para a matriz', async () => {
    mockFetch(base({
      'GET /api/chat/conversas/1/mensagens': { corpo: [msg(11, 7, null, { escala: { id: 3, nome: 'Semana 05/10' } })] },
    }));
    abrir();
    fireEvent.click(await screen.findByRole('button', { name: /Ana Souza/ }));
    const link = await screen.findByRole('link', { name: /Semana 05\/10/ });
    expect(link).toHaveAttribute('href', '/escalas/3');
  });

  it('menciona uma escala ao enviar', async () => {
    const enviados: unknown[] = [];
    mockFetch(base({
      'GET /api/escalas': { corpo: [{ id: 3, nome: 'Semana 05/10', dataInicio: '2026-10-05', dataFim: '2026-10-11', status: 'RASCUNHO' }] },
      'POST /api/chat/conversas/1/mensagens': (corpo: unknown) => (enviados.push(corpo), { status: 201, corpo: msg(12, EU, 'Veja') }),
    }));
    abrir();
    fireEvent.click(await screen.findByRole('button', { name: /Ana Souza/ }));

    fireEvent.click(await screen.findByRole('button', { name: 'Mencionar escala' }));
    fireEvent.click(await screen.findByRole('button', { name: /Semana 05\/10/ }));
    fireEvent.change(screen.getByLabelText('Mensagem'), { target: { value: 'Veja' } });
    fireEvent.click(screen.getByRole('button', { name: 'Enviar' }));

    await waitFor(() => expect(enviados).toEqual([{ texto: 'Veja', escalaId: 3 }]));
  });

  it('anexa uma imagem', async () => {
    const enviados: unknown[] = [];
    mockFetch(base({
      'POST /api/chat/conversas/1/imagens': (corpo: unknown) => (enviados.push(corpo), {
        status: 201, corpo: msg(12, EU, 'Quadro', { imagem: '/api/chat/mensagens/12/imagem' }),
      }),
    }));
    abrir();
    fireEvent.click(await screen.findByRole('button', { name: /Ana Souza/ }));

    const foto = new File([new Uint8Array([0xff, 0xd8, 0xff])], 'quadro.jpg', { type: 'image/jpeg' });
    fireEvent.change(await screen.findByLabelText('Anexar imagem'), { target: { files: [foto] } });
    expect(screen.getByText('quadro.jpg')).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Mensagem'), { target: { value: 'Quadro' } });
    fireEvent.click(screen.getByRole('button', { name: 'Enviar' }));

    await waitFor(() => expect(enviados).toHaveLength(1));
    const dados = enviados[0] as FormData;
    expect(dados.get('arquivo')).toBe(foto);
    expect(dados.get('texto')).toBe('Quadro');
  });

  it('inicia uma conversa privada', async () => {
    const enviados: unknown[] = [];
    mockFetch(base({
      'POST /api/chat/conversas/diretas': (corpo: unknown) => (enviados.push(corpo), {
        corpo: { ...comAna, id: 3, nome: 'Bruno Lima', participantes: [eu, bruno], naoLidas: 0, ultimaMensagem: null },
      }),
      'GET /api/chat/conversas/3/mensagens': { corpo: [] },
      'POST /api/chat/conversas/3/lida': { status: 204 },
    }));
    abrir();

    fireEvent.click(await screen.findByRole('button', { name: 'Nova conversa' }));
    const dialogo = screen.getByRole('dialog', { name: 'Nova conversa' });
    fireEvent.click(within(dialogo).getByRole('button', { name: /Bruno Lima/ }));

    await waitFor(() => expect(enviados).toEqual([{ usuarioId: 8 }]));
    expect(await screen.findByRole('heading', { name: 'Bruno Lima' })).toBeInTheDocument();
  });

  it('cria um grupo com as pessoas escolhidas', async () => {
    const enviados: unknown[] = [];
    mockFetch(base({
      'POST /api/chat/conversas/grupos': (corpo: unknown) => (enviados.push(corpo), {
        status: 201, corpo: { ...padaria, id: 4, nome: 'Fim de semana', ultimaMensagem: null },
      }),
      'GET /api/chat/conversas/4/mensagens': { corpo: [] },
      'POST /api/chat/conversas/4/lida': { status: 204 },
    }));
    abrir();

    fireEvent.click(await screen.findByRole('button', { name: 'Nova conversa' }));
    const dialogo = screen.getByRole('dialog', { name: 'Nova conversa' });
    fireEvent.click(within(dialogo).getByRole('tab', { name: 'Novo grupo' }));
    const criar = within(dialogo).getByRole('button', { name: 'Criar grupo' });
    expect(criar).toBeDisabled();
    fireEvent.change(within(dialogo).getByLabelText('Nome do grupo'), { target: { value: 'Fim de semana' } });
    fireEvent.click(within(dialogo).getByLabelText('Ana Souza'));
    fireEvent.click(within(dialogo).getByLabelText('Bruno Lima'));
    fireEvent.click(criar);

    await waitFor(() => expect(enviados).toEqual([{ nome: 'Fim de semana', participantes: [7, 8] }]));
    expect(await screen.findByRole('heading', { name: 'Fim de semana' })).toBeInTheDocument();
  });

  it('filtra conversas pela busca', async () => {
    mockFetch(base());
    abrir();
    await screen.findByRole('button', { name: /Ana Souza/ });
    fireEvent.change(screen.getByLabelText('Buscar conversa'), { target: { value: 'pada' } });
    expect(screen.queryByRole('button', { name: /Ana Souza/ })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Padaria/ })).toBeInTheDocument();
  });
});
