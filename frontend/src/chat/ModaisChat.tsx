import { useState } from 'react';
import { LogOut, Search } from 'lucide-react';
import { api } from '../api';
import Avatar from '../components/Avatar';
import { Modal, mensagemErro, useToast } from '../components/ui';
import type { Contato, ConversaChat } from '../types';
import { useChat } from './ChatProvider';

/** Lista de pessoas com foto, nome e indicador online. */
function Pessoa({ c, online }: { c: Contato; online: boolean }) {
  return (
    <>
      <span className="chat-item-avatar">
        <Avatar nome={c.nome} foto={c.foto} tamanho={34} className="avatar-linha" />
        {online && <span className="ponto-online" aria-hidden />}
      </span>
      <span className="pessoa-nome">{c.nome}</span>
      {online && <span className="texto-online pequeno">online</span>}
    </>
  );
}

export function ModalNovaConversa({ aberto, onFechar }: { aberto: boolean; onFechar: () => void }) {
  const chat = useChat();
  const [aba, setAba] = useState<'privada' | 'grupo'>('privada');
  const [busca, setBusca] = useState('');
  const [nome, setNome] = useState('');
  const [escolhidos, setEscolhidos] = useState<number[]>([]);
  const [salvando, setSalvando] = useState(false);
  const toast = useToast();

  const fechar = () => {
    setAba('privada');
    setBusca('');
    setNome('');
    setEscolhidos([]);
    onFechar();
  };

  const contatos = chat.contatos.filter((c) => c.nome.toLowerCase().includes(busca.trim().toLowerCase()));

  const abrir = (c: ConversaChat) => {
    chat.guardarConversa(c);
    chat.abrirConversa(c.id);
    fechar();
  };

  const conversarCom = async (usuarioId: number) => {
    setSalvando(true);
    try {
      abrir(await api.post<ConversaChat>('/chat/conversas/diretas', { usuarioId }));
    } catch (e) {
      toast('erro', mensagemErro(e));
    } finally {
      setSalvando(false);
    }
  };

  const criarGrupo = async () => {
    setSalvando(true);
    try {
      abrir(await api.post<ConversaChat>('/chat/conversas/grupos', { nome: nome.trim(), participantes: escolhidos }));
    } catch (e) {
      toast('erro', mensagemErro(e));
    } finally {
      setSalvando(false);
    }
  };

  const alternar = (id: number) =>
    setEscolhidos((atual) => (atual.includes(id) ? atual.filter((x) => x !== id) : [...atual, id]));

  return (
    <Modal titulo="Nova conversa" aberto={aberto} onFechar={fechar}
      acoes={aba === 'grupo'
        ? <><button onClick={fechar}>Cancelar</button>
            <button className="primario" disabled={salvando || !nome.trim() || escolhidos.length === 0} onClick={criarGrupo}>Criar grupo</button></>
        : <button onClick={fechar}>Cancelar</button>}>
      <div className="stack">
        <div className="abas" role="tablist">
          <button role="tab" aria-selected={aba === 'privada'} className={aba === 'privada' ? 'ativa' : ''} onClick={() => setAba('privada')}>
            Conversa privada
          </button>
          <button role="tab" aria-selected={aba === 'grupo'} className={aba === 'grupo' ? 'ativa' : ''} onClick={() => setAba('grupo')}>
            Novo grupo
          </button>
        </div>

        {aba === 'grupo' && (
          <label className="campo">Nome do grupo
            <input value={nome} maxLength={80} onChange={(e) => setNome(e.target.value)} placeholder="Ex.: Escala do fim de semana" />
          </label>
        )}

        <label className="chat-busca">
          <Search size={15} strokeWidth={1.75} aria-hidden />
          <input value={busca} onChange={(e) => setBusca(e.target.value)} placeholder="Buscar pessoa" aria-label="Buscar pessoa" />
        </label>

        <div className="lista-pessoas">
          {contatos.length === 0 && <p className="muted">Nenhuma pessoa encontrada.</p>}
          {contatos.map((c) => aba === 'privada'
            ? (
              <button key={c.id} className="pessoa" disabled={salvando} onClick={() => conversarCom(c.id)}>
                <Pessoa c={c} online={chat.online.has(c.id)} />
              </button>
            )
            : (
              <label key={c.id} className={`pessoa ${escolhidos.includes(c.id) ? 'escolhida' : ''}`}>
                <input type="checkbox" aria-label={c.nome} checked={escolhidos.includes(c.id)} onChange={() => alternar(c.id)} />
                <Pessoa c={c} online={chat.online.has(c.id)} />
              </label>
            ))}
        </div>
        {aba === 'grupo' && escolhidos.length > 0 && <p className="ajuda">{escolhidos.length} pessoa(s) selecionada(s).</p>}
      </div>
    </Modal>
  );
}

export function ModalGerenciarGrupo({ conversa, aberto, onFechar }: { conversa: ConversaChat; aberto: boolean; onFechar: () => void }) {
  const chat = useChat();
  const [nome, setNome] = useState(conversa.nome);
  const [novos, setNovos] = useState<number[]>([]);
  const [confirmandoSaida, setConfirmandoSaida] = useState(false);
  const toast = useToast();

  const membros = new Set(conversa.participantes.map((p) => p.id));
  const disponiveis = chat.contatos.filter((c) => !membros.has(c.id));

  const executar = async (acao: () => Promise<ConversaChat | void>, mensagem: string) => {
    try {
      const r = await acao();
      if (r) chat.guardarConversa(r);
      toast('ok', mensagem);
    } catch (e) {
      toast('erro', mensagemErro(e));
    }
  };

  const sair = async () => {
    try {
      await api.del(`/chat/conversas/${conversa.id}/participantes/eu`);
      chat.aplicar({ tipo: 'SAIU', conversaId: conversa.id });
      chat.abrirConversa(null);
      onFechar();
    } catch (e) {
      toast('erro', mensagemErro(e));
    }
  };

  return (
    <Modal titulo="Grupo" aberto={aberto} onFechar={() => { setConfirmandoSaida(false); onFechar(); }}
      acoes={<button onClick={onFechar}>Fechar</button>}>
      <div className="stack">
        <div className="row" style={{ alignItems: 'flex-end', flexWrap: 'nowrap' }}>
          <label className="campo" style={{ flex: 1 }}>Nome do grupo
            <input value={nome} maxLength={80} onChange={(e) => setNome(e.target.value)} />
          </label>
          <button disabled={!nome.trim() || nome.trim() === conversa.nome}
            onClick={() => executar(() => api.put<ConversaChat>(`/chat/conversas/${conversa.id}`, { nome: nome.trim() }), 'Grupo renomeado.')}>
            Renomear
          </button>
        </div>

        <div>
          <h3 className="titulo-secao">{conversa.participantes.length} participantes</h3>
          <div className="lista-pessoas">
            {conversa.participantes.map((p) => (
              <div key={p.id} className="pessoa estatica">
                <Pessoa c={p} online={p.id !== chat.meuId && chat.online.has(p.id)} />
                {p.id === chat.meuId && <span className="badge neutro">Você</span>}
              </div>
            ))}
          </div>
        </div>

        {disponiveis.length > 0 && (
          <div>
            <h3 className="titulo-secao">Adicionar pessoas</h3>
            <div className="lista-pessoas">
              {disponiveis.map((c) => (
                <label key={c.id} className={`pessoa ${novos.includes(c.id) ? 'escolhida' : ''}`}>
                  <input type="checkbox" aria-label={`Adicionar ${c.nome}`} checked={novos.includes(c.id)}
                    onChange={() => setNovos((a) => (a.includes(c.id) ? a.filter((x) => x !== c.id) : [...a, c.id]))} />
                  <Pessoa c={c} online={chat.online.has(c.id)} />
                </label>
              ))}
            </div>
            <button className="primario" style={{ marginTop: 10 }} disabled={novos.length === 0}
              onClick={() => executar(async () => {
                const r = await api.post<ConversaChat>(`/chat/conversas/${conversa.id}/participantes`, { usuarios: novos });
                setNovos([]);
                return r;
              }, 'Pessoas adicionadas ao grupo.')}>
              Adicionar ao grupo
            </button>
          </div>
        )}

        <div className="zona-saida">
          {confirmandoSaida ? (
            <div className="row">
              <span>Sair do grupo? Você deixará de ver as mensagens.</span>
              <div className="spacer" />
              <button onClick={() => setConfirmandoSaida(false)}>Cancelar</button>
              <button className="perigo" onClick={sair}>Sair</button>
            </div>
          ) : (
            <button className="perigo" onClick={() => setConfirmandoSaida(true)}>
              <LogOut size={15} strokeWidth={1.75} aria-hidden /> Sair do grupo
            </button>
          )}
        </div>
      </div>
    </Modal>
  );
}
