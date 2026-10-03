import { useEffect, useMemo, useState } from 'react';
import { MessageSquarePlus, Search, Users } from 'lucide-react';
import { useChat } from '../chat/ChatProvider';
import ConversaAberta from '../chat/ConversaAberta';
import { ModalGerenciarGrupo, ModalNovaConversa } from '../chat/ModaisChat';
import { previa } from '../chat/estado';
import { horarioCurto } from '../chat/tempo';
import Avatar from '../components/Avatar';
import type { ConversaChat } from '../types';

export default function Mensagens() {
  const chat = useChat();
  const [busca, setBusca] = useState('');
  const [novaAberta, setNovaAberta] = useState(false);
  const [gerenciando, setGerenciando] = useState(false);
  const atual = chat.conversas.find((c) => c.id === chat.conversaAberta) ?? null;

  // Ao sair da tela, nenhuma conversa fica "aberta" (novas mensagens voltam a contar como não lidas).
  const { abrirConversa } = chat;
  useEffect(() => () => abrirConversa(null), [abrirConversa]);

  const filtradas = useMemo(() => {
    const termo = busca.trim().toLowerCase();
    return termo ? chat.conversas.filter((c) => c.nome.toLowerCase().includes(termo)) : chat.conversas;
  }, [busca, chat.conversas]);

  return (
    <div className={`chat ${atual ? 'com-conversa' : ''}`}>
      <aside className="chat-lista">
        <div className="chat-lista-topo">
          <h1>Mensagens</h1>
          <button className="primario icone" onClick={() => setNovaAberta(true)} aria-label="Nova conversa" title="Nova conversa">
            <MessageSquarePlus size={18} strokeWidth={1.9} aria-hidden />
          </button>
        </div>
        <label className="chat-busca">
          <Search size={15} strokeWidth={1.75} aria-hidden />
          <input value={busca} onChange={(e) => setBusca(e.target.value)} placeholder="Buscar conversa" aria-label="Buscar conversa" />
        </label>
        <div className="chat-itens">
          {filtradas.length === 0 && (
            <p className="muted chat-vazio-lista">
              {chat.conversas.length === 0 ? 'Nenhuma conversa ainda. Comece uma nova!' : 'Nenhuma conversa encontrada.'}
            </p>
          )}
          {filtradas.map((c) => (
            <ItemConversa key={c.id} conversa={c} ativa={c.id === chat.conversaAberta} meuId={chat.meuId}
              online={chat.online} onAbrir={() => chat.abrirConversa(c.id)} />
          ))}
        </div>
      </aside>

      <section className="chat-conversa">
        {atual ? (
          <ConversaAberta key={atual.id} conversa={atual} onVoltar={() => chat.abrirConversa(null)}
            onGerenciar={() => setGerenciando(true)} />
        ) : (
          <div className="chat-vazio">
            <div className="chat-vazio-icone" aria-hidden><Users size={30} strokeWidth={1.5} /></div>
            <h2>Converse com a equipe</h2>
            <p className="muted">Escolha uma conversa ao lado ou comece uma nova.</p>
            <button className="primario" onClick={() => setNovaAberta(true)}>Começar uma conversa</button>
          </div>
        )}
      </section>

      <ModalNovaConversa aberto={novaAberta} onFechar={() => setNovaAberta(false)} />
      {atual?.tipo === 'GRUPO' && (
        <ModalGerenciarGrupo conversa={atual} aberto={gerenciando} onFechar={() => setGerenciando(false)} />
      )}
    </div>
  );
}

function ItemConversa({ conversa: c, ativa, meuId, online, onAbrir }: {
  conversa: ConversaChat;
  ativa: boolean;
  meuId?: number;
  online: Set<number>;
  onAbrir: () => void;
}) {
  const outro = c.tipo === 'DIRETA' ? c.participantes.find((p) => p.id !== meuId) : undefined;
  return (
    <button className={`chat-item ${ativa ? 'ativo' : ''} ${c.naoLidas ? 'nao-lida' : ''}`} onClick={onAbrir}>
      <span className="chat-item-avatar">
        {c.tipo === 'GRUPO'
          ? <span className="avatar avatar-grupo" style={{ width: 42, height: 42 }} aria-hidden><Users size={19} strokeWidth={1.75} /></span>
          : <Avatar nome={c.nome} foto={c.foto} tamanho={42} className="avatar-linha" />}
        {outro && online.has(outro.id) && <span className="ponto-online" aria-label="Online" role="img" />}
      </span>
      <span className="chat-item-texto">
        <span className="chat-item-linha">
          <strong>{c.nome}</strong>
          {c.ultimaMensagem && <time>{horarioCurto(c.ultimaMensagem.enviadaEm)}</time>}
        </span>
        <span className="chat-item-linha">
          <span className="chat-item-previa">{previa(c.ultimaMensagem, meuId)}</span>
          {c.naoLidas > 0 && <span className="contador" aria-label={`${c.naoLidas} não lidas`}>{c.naoLidas > 99 ? '99+' : c.naoLidas}</span>}
        </span>
      </span>
    </button>
  );
}
