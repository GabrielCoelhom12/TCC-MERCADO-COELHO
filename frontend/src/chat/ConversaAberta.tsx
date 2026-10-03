import { Fragment, useCallback, useEffect, useRef, useState, type KeyboardEvent } from 'react';
import { Link } from 'react-router-dom';
import { ArrowLeft, CalendarDays, ImagePlus, Phone, PhoneIncoming, PhoneMissed, Send, Users, X } from 'lucide-react';
import { api } from '../api';
import Avatar from '../components/Avatar';
import { Modal, mensagemErro, useToast } from '../components/ui';
import { dataLonga } from '../format';
import type { ConversaChat, Escala, MensagemChat } from '../types';
import { useChamada } from './ChamadaProvider';
import { useChat } from './ChatProvider';
import { textoChamada } from './estado';
import { useImagemProtegida } from './imagem';
import { chaveDia, hora, rotuloDia } from './tempo';

const PAGINA = 50;
const TIPOS_IMAGEM = ['image/png', 'image/jpeg'];
const LIMITE_IMAGEM = 5 * 1024 * 1024;

/** "45 s", "3 min" ou "3 min 12 s". */
export function duracao(segundos: number | null) {
  if (segundos === null) return '';
  if (segundos < 60) return `${segundos} s`;
  const resto = segundos % 60;
  return `${Math.floor(segundos / 60)} min${resto ? ` ${resto} s` : ''}`;
}

export default function ConversaAberta({ conversa, onVoltar, onGerenciar }: {
  conversa: ConversaChat;
  onVoltar: () => void;
  onGerenciar: () => void;
}) {
  const chat = useChat();
  const { meuId, online, ouvir, aplicar } = chat;
  const chamada = useChamada();
  const [mensagens, setMensagens] = useState<MensagemChat[] | null>(null);
  const [temMais, setTemMais] = useState(false);
  const [ampliada, setAmpliada] = useState<string | null>(null);
  const fim = useRef<HTMLDivElement>(null);
  const lista = useRef<HTMLDivElement>(null);
  const rolarAoFim = useRef(true);
  const toast = useToast();
  const id = conversa.id;

  const marcarLida = useCallback(() => {
    void api.post(`/chat/conversas/${id}/lida`).catch(() => undefined);
  }, [id]);

  const acrescentar = useCallback((m: MensagemChat) => {
    setMensagens((atual) => (atual && !atual.some((x) => x.id === m.id) ? [...atual, m] : atual));
  }, []);

  useEffect(() => {
    let ativo = true;
    api.get<MensagemChat[]>(`/chat/conversas/${id}/mensagens`)
      .then((ms) => {
        if (!ativo) return;
        rolarAoFim.current = true;
        setMensagens(ms);
        setTemMais(ms.length === PAGINA);
        marcarLida();
      })
      .catch((e) => ativo && toast('erro', mensagemErro(e)));
    return () => {
      ativo = false;
    };
  }, [id, marcarLida, toast]);

  // Mensagens que chegam em tempo real nesta conversa.
  useEffect(() => ouvir((e) => {
    if (e.tipo !== 'MENSAGEM' || e.conversaId !== id) return;
    const el = lista.current;
    rolarAoFim.current = !el || el.scrollHeight - el.scrollTop - el.clientHeight < 120 || e.mensagem.autorId === meuId;
    acrescentar(e.mensagem);
    if (e.mensagem.autorId !== meuId) marcarLida();
  }), [id, meuId, ouvir, acrescentar, marcarLida]);

  useEffect(() => {
    if (rolarAoFim.current) fim.current?.scrollIntoView?.({ block: 'end' });
  }, [mensagens]);

  const carregarAnteriores = async () => {
    if (!mensagens?.length) return;
    const el = lista.current;
    const alturaAntes = el?.scrollHeight ?? 0;
    try {
      const anteriores = await api.get<MensagemChat[]>(`/chat/conversas/${id}/mensagens?antesDe=${mensagens[0].id}`);
      rolarAoFim.current = false;
      setMensagens([...anteriores, ...mensagens]);
      setTemMais(anteriores.length === PAGINA);
      // Mantém a posição de leitura depois de inserir acima.
      requestAnimationFrame(() => el && (el.scrollTop += el.scrollHeight - alturaAntes));
    } catch (e) {
      toast('erro', mensagemErro(e));
    }
  };

  const enviada = (m: MensagemChat) => {
    rolarAoFim.current = true;
    acrescentar(m);
    aplicar({ tipo: 'MENSAGEM', conversaId: id, mensagem: m });
  };

  const direta = conversa.tipo === 'DIRETA';
  const outro = direta ? conversa.participantes.find((p) => p.id !== meuId) : undefined;
  const minhaUltima = mensagens ? [...mensagens].reverse().find((m) => m.autorId === meuId) : undefined;
  const lidaPeloOutro = !!(outro && minhaUltima && (conversa.leituras[outro.id] ?? 0) >= minhaUltima.id);
  const onlineNoGrupo = conversa.participantes.filter((p) => p.id !== meuId && online.has(p.id)).length;

  return (
    <>
      <header className="chat-cabecalho">
        <button className="icone voltar" onClick={onVoltar} aria-label="Voltar"><ArrowLeft size={18} /></button>
        {direta
          ? <span className="chat-item-avatar">
              <Avatar nome={conversa.nome} foto={conversa.foto} tamanho={40} className="avatar-linha" />
              {outro && online.has(outro.id) && <span className="ponto-online" aria-hidden />}
            </span>
          : <span className="avatar avatar-grupo" style={{ width: 40, height: 40 }} aria-hidden><Users size={18} strokeWidth={1.75} /></span>}
        <div className="chat-cabecalho-texto">
          <h2>{conversa.nome}</h2>
          {direta
            ? <span className={outro && online.has(outro.id) ? 'texto-online' : 'muted'}>{outro && online.has(outro.id) ? 'Online' : 'Offline'}</span>
            : <button className="link" onClick={onGerenciar}>
                {conversa.participantes.length} participantes{onlineNoGrupo ? ` · ${onlineNoGrupo} online` : ''}
              </button>}
        </div>
        {direta && outro && chamada.disponivel && (
          <button className="icone botao-ligar" onClick={() => void chamada.ligar(id)}
            disabled={!online.has(outro.id) || !!chamada.estado} aria-label="Ligar"
            title={chamada.estado ? 'Você já está em uma chamada' : online.has(outro.id) ? `Ligar para ${outro.nome}` : `${outro.nome} está offline`}>
            <Phone size={18} strokeWidth={1.9} aria-hidden />
          </button>
        )}
      </header>

      <div className="chat-mensagens" ref={lista}>
        {mensagens === null && <p className="muted chat-carregando">Carregando mensagens…</p>}
        {temMais && <button className="carregar-anteriores" onClick={carregarAnteriores}>Carregar mensagens anteriores</button>}
        {mensagens?.length === 0 && <p className="muted chat-carregando">Nenhuma mensagem ainda. Diga olá!</p>}
        {mensagens?.map((m, i) => {
          const anterior = mensagens[i - 1];
          const novoDia = !anterior || chaveDia(anterior.enviadaEm) !== chaveDia(m.enviadaEm);
          const minha = m.autorId === meuId;
          const agrupada = !novoDia && anterior?.autorId === m.autorId;
          const autor = conversa.participantes.find((p) => p.id === m.autorId);
          if (m.chamada) {
            const atendida = m.chamada.resultado === 'ATENDIDA';
            const texto = atendida && m.chamada.duracaoSegundos !== null
              ? `Chamada de voz · ${duracao(m.chamada.duracaoSegundos)}` : textoChamada(m, meuId);
            return (
              <Fragment key={m.id}>
                {novoDia && <div className="separador-dia"><span>{rotuloDia(m.enviadaEm)}</span></div>}
                <div className={`registro-chamada ${atendida ? '' : 'perdida'}`}>
                  {atendida ? <PhoneIncoming size={15} aria-hidden /> : <PhoneMissed size={15} aria-hidden />}
                  <span>{texto}</span>
                  <time>{hora(m.enviadaEm)}</time>
                </div>
              </Fragment>
            );
          }
          return (
            <Fragment key={m.id}>
              {novoDia && <div className="separador-dia"><span>{rotuloDia(m.enviadaEm)}</span></div>}
              <div className={`mensagem ${minha ? 'minha' : ''} ${agrupada ? 'agrupada' : ''}`}>
                {!minha && !direta && (
                  <span className="mensagem-avatar">
                    {!agrupada && <Avatar nome={m.autorNome} foto={autor?.foto} tamanho={28} className="avatar-linha" />}
                  </span>
                )}
                <div className="balao">
                  {!minha && !direta && !agrupada && <span className="mensagem-autor">{m.autorNome}</span>}
                  {m.imagem && <ImagemMensagem url={m.imagem} onAmpliar={setAmpliada} />}
                  {m.escala && (
                    <Link className="mencao-escala" to={`/escalas/${m.escala.id}`}>
                      <CalendarDays size={18} strokeWidth={1.75} aria-hidden />
                      <span><strong>{m.escala.nome}</strong><small>Abrir escala</small></span>
                    </Link>
                  )}
                  {m.texto && <p>{m.texto}</p>}
                  <time>{hora(m.enviadaEm)}</time>
                </div>
              </div>
              {direta && minha && m.id === minhaUltima?.id && (
                <div className={`recibo ${lidaPeloOutro ? 'lida' : ''}`}>{lidaPeloOutro ? 'Lida' : 'Enviada'}</div>
              )}
            </Fragment>
          );
        })}
        <div ref={fim} />
      </div>

      <Compositor conversaId={id} onEnviada={enviada} />

      <Modal titulo="Imagem" aberto={!!ampliada} onFechar={() => setAmpliada(null)}
        acoes={<button onClick={() => setAmpliada(null)}>Fechar</button>}>
        {ampliada && <img className="imagem-ampliada" src={ampliada} alt="Imagem enviada na conversa" />}
      </Modal>
    </>
  );
}

function ImagemMensagem({ url, onAmpliar }: { url: string; onAmpliar: (src: string) => void }) {
  const src = useImagemProtegida(url);
  if (!src) return <div className="imagem-mensagem carregando" aria-label="Carregando imagem" />;
  return (
    <button className="imagem-mensagem" onClick={() => onAmpliar(src)} aria-label="Ampliar imagem">
      <img src={src} alt="Imagem enviada na conversa" />
    </button>
  );
}

function Compositor({ conversaId, onEnviada }: { conversaId: number; onEnviada: (m: MensagemChat) => void }) {
  const [texto, setTexto] = useState('');
  const [anexo, setAnexo] = useState<File | null>(null);
  const [escala, setEscala] = useState<Escala | null>(null);
  const [escalas, setEscalas] = useState<Escala[] | null>(null);
  const [escolhendo, setEscolhendo] = useState(false);
  const [enviando, setEnviando] = useState(false);
  const campo = useRef<HTMLTextAreaElement>(null);
  const toast = useToast();

  const podeEnviar = !enviando && (texto.trim() !== '' || !!anexo || !!escala);

  const abrirEscalas = async () => {
    setEscolhendo((v) => !v);
    if (escalas === null) {
      try {
        setEscalas(await api.get<Escala[]>('/escalas'));
      } catch (e) {
        toast('erro', mensagemErro(e));
      }
    }
  };

  const escolherImagem = (arquivo: File | undefined) => {
    if (!arquivo) return;
    if (!TIPOS_IMAGEM.includes(arquivo.type)) return toast('erro', 'A imagem precisa ser PNG ou JPEG.');
    if (arquivo.size > LIMITE_IMAGEM) return toast('erro', 'A imagem deve ter no máximo 5 MB.');
    setAnexo(arquivo);
  };

  const enviar = async () => {
    if (!podeEnviar) return;
    setEnviando(true);
    const conteudo = texto.trim();
    try {
      if (anexo) {
        const dados = new FormData();
        dados.append('arquivo', anexo);
        if (conteudo) dados.append('texto', conteudo);
        onEnviada(await api.post<MensagemChat>(`/chat/conversas/${conversaId}/imagens`, dados));
        if (escala) {
          onEnviada(await api.post<MensagemChat>(`/chat/conversas/${conversaId}/mensagens`, { texto: null, escalaId: escala.id }));
        }
      } else {
        onEnviada(await api.post<MensagemChat>(`/chat/conversas/${conversaId}/mensagens`,
          { texto: conteudo || null, escalaId: escala?.id ?? null }));
      }
      setTexto('');
      setAnexo(null);
      setEscala(null);
      campo.current?.focus();
    } catch (e) {
      toast('erro', mensagemErro(e));
    } finally {
      setEnviando(false);
    }
  };

  const teclas = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault();
      void enviar();
    }
  };

  return (
    <div className="compositor">
      {(anexo || escala) && (
        <div className="compositor-anexos">
          {anexo && (
            <span className="anexo-chip">
              <ImagePlus size={14} aria-hidden /> <span>{anexo.name}</span>
              <button className="icone" onClick={() => setAnexo(null)} aria-label="Remover imagem"><X size={13} /></button>
            </span>
          )}
          {escala && (
            <span className="anexo-chip">
              <CalendarDays size={14} aria-hidden /> <span>{escala.nome}</span>
              <button className="icone" onClick={() => setEscala(null)} aria-label="Remover escala"><X size={13} /></button>
            </span>
          )}
        </div>
      )}
      {escolhendo && (
        <div className="seletor-escala" role="listbox" aria-label="Escalas">
          {escalas === null && <p className="muted">Carregando…</p>}
          {escalas?.length === 0 && <p className="muted">Nenhuma escala cadastrada.</p>}
          {escalas?.map((e) => (
            <button key={e.id} onClick={() => { setEscala(e); setEscolhendo(false); campo.current?.focus(); }}>
              <strong>{e.nome}</strong>
              <small>{dataLonga(e.dataInicio)} a {dataLonga(e.dataFim)}</small>
            </button>
          ))}
        </div>
      )}
      <div className="compositor-linha">
        <label className="btn icone botao-arquivo" title="Anexar imagem">
          <ImagePlus size={18} strokeWidth={1.75} aria-hidden />
          <input type="file" accept="image/png,image/jpeg" aria-label="Anexar imagem"
            onChange={(e) => { escolherImagem(e.target.files?.[0]); e.target.value = ''; }} />
        </label>
        <button className={`icone ${escolhendo ? 'ativo' : ''}`} onClick={abrirEscalas} aria-label="Mencionar escala" title="Mencionar escala">
          <CalendarDays size={18} strokeWidth={1.75} aria-hidden />
        </button>
        <textarea ref={campo} value={texto} rows={1} aria-label="Mensagem" placeholder="Escreva uma mensagem…"
          maxLength={4000} onChange={(e) => setTexto(e.target.value)} onKeyDown={teclas} />
        <button className="primario icone enviar" onClick={() => void enviar()} disabled={!podeEnviar} aria-label="Enviar" title="Enviar">
          <Send size={17} strokeWidth={1.9} aria-hidden />
        </button>
      </div>
    </div>
  );
}
