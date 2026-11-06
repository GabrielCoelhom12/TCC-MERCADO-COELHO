import type { ConversaChat, EventoChat, MensagemChat } from '../types';

const maisRecentesPrimeiro = (a: ConversaChat, b: ConversaChat) => b.atualizadaEm.localeCompare(a.atualizadaEm);

/**
 * Aplica um evento em tempo real à lista de conversas (função pura).
 * @param conversaAberta conversa visível na tela: mensagens nela não contam como não lidas.
 */
export function aplicarEvento(conversas: ConversaChat[], evento: EventoChat, meuId: number | undefined,
                              conversaAberta: number | null): ConversaChat[] {
  switch (evento.tipo) {
    case 'MENSAGEM': {
      const m = evento.mensagem;
      return conversas.map((c) => {
        if (c.id !== m.conversaId || (c.ultimaMensagem && c.ultimaMensagem.id >= m.id)) return c;
        const minha = m.autorId === meuId;
        return {
          ...c,
          ultimaMensagem: m,
          atualizadaEm: m.enviadaEm,
          naoLidas: minha || c.id === conversaAberta ? c.naoLidas : c.naoLidas + 1,
          leituras: minha ? { ...c.leituras, [m.autorId]: m.id } : c.leituras,
        };
      }).sort(maisRecentesPrimeiro);
    }
    case 'CONVERSA':
      return [...conversas.filter((c) => c.id !== evento.conversa.id), evento.conversa].sort(maisRecentesPrimeiro);
    case 'SAIU':
      return conversas.filter((c) => c.id !== evento.conversaId);
    case 'LEITURA':
      return conversas.map((c) => c.id !== evento.conversaId ? c : {
        ...c,
        leituras: { ...c.leituras, [evento.usuarioId]: evento.mensagemId },
        naoLidas: evento.usuarioId === meuId ? 0 : c.naoLidas,
      });
  }
}

export const totalNaoLidas = (conversas: ConversaChat[]) => conversas.reduce((s, c) => s + c.naoLidas, 0);

/** Texto curto da última mensagem para a lista de conversas. */
export function previa(m: MensagemChat | null, meuId: number | undefined) {
  if (!m) return 'Nenhuma mensagem ainda';
  const conteudo = m.texto ?? (m.imagem ? 'Imagem' : m.escala ? `Escala: ${m.escala.nome}` : '');
  return m.autorId === meuId ? `Você: ${conteudo}` : conteudo;
}
