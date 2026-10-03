import { describe, expect, it } from 'vitest';
import type { ConversaChat, MensagemChat } from '../types';
import { aplicarEvento, previa, totalNaoLidas } from './estado';

const EU = 1;

const conversa = (id: number, extra: Partial<ConversaChat> = {}): ConversaChat => ({
  id, tipo: 'DIRETA', nome: `Conversa ${id}`, foto: null, participantes: [], ultimaMensagem: null,
  naoLidas: 0, leituras: {}, atualizadaEm: `2026-10-0${id}T10:00:00`, ...extra,
});

const mensagem = (id: number, conversaId: number, autorId: number, extra: Partial<MensagemChat> = {}): MensagemChat => ({
  id, conversaId, autorId, autorNome: autorId === EU ? 'Eu' : 'Ana', texto: 'Oi', imagem: null, escala: null, chamada: null,
  enviadaEm: '2026-10-09T08:00:00', ...extra,
});

describe('aplicarEvento', () => {
  it('nova mensagem de outra pessoa sobe a conversa e conta como não lida', () => {
    const lista = [conversa(2), conversa(1)];
    const r = aplicarEvento(lista, { tipo: 'MENSAGEM', conversaId: 1, mensagem: mensagem(50, 1, 7) }, EU, null);

    expect(r.map((c) => c.id)).toEqual([1, 2]);
    expect(r[0].naoLidas).toBe(1);
    expect(r[0].ultimaMensagem?.id).toBe(50);
    expect(r[0].atualizadaEm).toBe('2026-10-09T08:00:00');
  });

  it('mensagem na conversa aberta não conta como não lida', () => {
    const r = aplicarEvento([conversa(1)], { tipo: 'MENSAGEM', conversaId: 1, mensagem: mensagem(50, 1, 7) }, EU, 1);
    expect(r[0].naoLidas).toBe(0);
  });

  it('minha mensagem já fica como lida por mim', () => {
    const r = aplicarEvento([conversa(1, { naoLidas: 3 })], { tipo: 'MENSAGEM', conversaId: 1, mensagem: mensagem(51, 1, EU) }, EU, null);
    expect(r[0].naoLidas).toBe(3);
    expect(r[0].leituras[EU]).toBe(51);
  });

  it('ignora mensagem repetida (ex.: resposta da API e evento do WebSocket)', () => {
    const uma = aplicarEvento([conversa(1)], { tipo: 'MENSAGEM', conversaId: 1, mensagem: mensagem(50, 1, 7) }, EU, null);
    const duas = aplicarEvento(uma, { tipo: 'MENSAGEM', conversaId: 1, mensagem: mensagem(50, 1, 7) }, EU, null);
    expect(duas[0].naoLidas).toBe(1);
  });

  it('conversa nova ou alterada substitui a existente', () => {
    const nova = conversa(3, { nome: 'Grupo novo', tipo: 'GRUPO', atualizadaEm: '2026-10-09T09:00:00' });
    let r = aplicarEvento([conversa(1)], { tipo: 'CONVERSA', conversaId: 3, conversa: nova }, EU, null);
    expect(r.map((c) => c.id)).toEqual([3, 1]);

    r = aplicarEvento(r, { tipo: 'CONVERSA', conversaId: 3, conversa: { ...nova, nome: 'Renomeado' } }, EU, null);
    expect(r).toHaveLength(2);
    expect(r[0].nome).toBe('Renomeado');
  });

  it('remove a conversa da qual saí', () => {
    const r = aplicarEvento([conversa(1), conversa(2)], { tipo: 'SAIU', conversaId: 1 }, EU, null);
    expect(r.map((c) => c.id)).toEqual([2]);
  });

  it('leitura de outra pessoa atualiza o indicador; a minha zera as não lidas', () => {
    let r = aplicarEvento([conversa(1, { naoLidas: 2 })], { tipo: 'LEITURA', conversaId: 1, usuarioId: 7, mensagemId: 40 }, EU, null);
    expect(r[0].leituras[7]).toBe(40);
    expect(r[0].naoLidas).toBe(2);

    r = aplicarEvento(r, { tipo: 'LEITURA', conversaId: 1, usuarioId: EU, mensagemId: 41 }, EU, null);
    expect(r[0].naoLidas).toBe(0);
  });

  it('eventos de chamada não alteram a lista de conversas', () => {
    const lista = [conversa(1)];
    expect(aplicarEvento(lista, { tipo: 'SINAL', chamadaId: 3, sinal: { tipo: 'ice', dados: '{}' } }, EU, null)).toBe(lista);
  });

  it('soma as não lidas de todas as conversas', () => {
    expect(totalNaoLidas([conversa(1, { naoLidas: 2 }), conversa(2, { naoLidas: 3 })])).toBe(5);
  });
});

describe('previa', () => {
  it('resume texto, imagem e escala, indicando quando fui eu', () => {
    expect(previa(mensagem(1, 1, 7, { texto: 'Bom dia' }), EU)).toBe('Bom dia');
    expect(previa(mensagem(1, 1, EU, { texto: 'Bom dia' }), EU)).toBe('Você: Bom dia');
    expect(previa(mensagem(1, 1, 7, { texto: null, imagem: '/x' }), EU)).toBe('Imagem');
    expect(previa(mensagem(1, 1, 7, { texto: null, escala: { id: 3, nome: 'Semana 1' } }), EU)).toBe('Escala: Semana 1');
    expect(previa(null, EU)).toBe('Nenhuma mensagem ainda');
    const chamada = (resultado: 'ATENDIDA' | 'RECUSADA' | 'PERDIDA') => ({ resultado, duracaoSegundos: 30 });
    expect(previa(mensagem(1, 1, 7, { texto: null, chamada: chamada('ATENDIDA') }), EU)).toBe('Chamada de voz');
    expect(previa(mensagem(1, 1, 7, { texto: null, chamada: chamada('PERDIDA') }), EU)).toBe('Chamada perdida');
    expect(previa(mensagem(1, 1, EU, { texto: null, chamada: chamada('PERDIDA') }), EU)).toBe('Você: Chamada não atendida');
    expect(previa(mensagem(1, 1, EU, { texto: null, chamada: chamada('RECUSADA') }), EU)).toBe('Você: Chamada recusada');
  });
});
