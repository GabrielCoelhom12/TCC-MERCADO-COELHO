import { useEffect, useMemo, useState } from 'react';
import { CalendarCheck, CircleAlert, Sparkles } from 'lucide-react';
import { ApiError, api } from '../api';
import { Carregando, mensagemErro, useCarregar, useToast } from '../components/ui';
import { DIAS_SEMANA, dataCurta, diaSemanaDe, isoDe, moeda, numero, paraData } from '../format';
import type { FaixaHoraria, Fechamento, PrevisaoDiaria } from '../types';

const DIAS_RECENTES = 14;
const DIAS_PREVISAO = 14;

const somarDias = (iso: string, dias: number) => {
  const d = paraData(iso);
  d.setDate(d.getDate() + dias);
  return isoDe(d);
};

/** Últimos 14 dias, até hoje. */
export const periodoFechamentos = () => {
  const hoje = isoDe(new Date());
  return { inicio: somarDias(hoje, -(DIAS_RECENTES - 1)), fim: hoje };
};

/** Próximos 14 dias, a partir de amanhã. */
export const periodoPrevisao = () => {
  const amanha = somarDias(isoDe(new Date()), 1);
  return { inicio: amanha, fim: somarDias(amanha, DIAS_PREVISAO - 1) };
};

const hora = (h: string) => h.slice(0, 5);
const horaCurta = (h: string) => `${Number(h.slice(0, 2))}h${h.slice(3, 5) === '00' ? '' : h.slice(3, 5)}`;
const rotuloDia = (iso: string) => `${DIAS_SEMANA.find((d) => d.dia === diaSemanaDe(iso))?.rotulo} ${dataCurta(iso)}`;

type Linha = { inicio: string; fim: string; clientes: string; vendas: string };

export default function Movimento() {
  const periodo = useMemo(periodoFechamentos, []);
  const proximos = useMemo(periodoPrevisao, []);
  const { dados: faixas, erro } = useCarregar(() => api.get<FaixaHoraria[]>('/movimento/faixas'));
  const recentes = useCarregar(() => api.get<Fechamento[]>(`/movimento/fechamentos?inicio=${periodo.inicio}&fim=${periodo.fim}`));
  const previsao = useCarregar(() => api.get<PrevisaoDiaria[]>(`/movimento/previsao?inicio=${proximos.inicio}&fim=${proximos.fim}`));
  const [data, setData] = useState(periodo.fim);

  if (!faixas) return <Carregando erro={erro} />;

  const atualizar = () => {
    recentes.recarregar();
    previsao.recarregar();
  };

  return (
    <div className="stack">
      <div className="cabecalho">
        <div>
          <h1>Movimento e previsão</h1>
          <p>Lance o fechamento de cada dia. O sistema aprende o movimento da loja e prevê os próximos dias.</p>
        </div>
      </div>

      <div className="movimento-topo">
        <FormularioFechamento faixas={faixas} periodo={periodo} data={data} onData={setData} recentes={recentes.dados}
          onSalvo={atualizar} />
        <UltimosDias periodo={periodo} recentes={recentes.dados} onEscolher={setData} />
      </div>

      <section className="card">
        <div className="row" style={{ marginBottom: 14 }}>
          <h2>Previsão dos próximos {DIAS_PREVISAO} dias</h2>
          <span className="spacer" />
          <span className="ajuda">As escalas são geradas com esta previsão; você pode ajustá-las na matriz.</span>
        </div>
        {!previsao.dados ? <Carregando erro={previsao.erro} /> : (
          <div className="previsao-grade">
            {previsao.dados.map((d) => <CartaoPrevisao key={d.data} dia={d} />)}
          </div>
        )}
      </section>
    </div>
  );
}

// ---------------------------------------------------------------- fechamento

function FormularioFechamento({ faixas, periodo, data, onData, recentes, onSalvo }: {
  faixas: FaixaHoraria[];
  periodo: { inicio: string; fim: string };
  data: string;
  onData: (data: string) => void;
  recentes: Fechamento[] | null;
  onSalvo: () => void;
}) {
  const vazio = (): Linha[] => faixas.map((f) => ({ inicio: f.inicio, fim: f.fim, clientes: '', vendas: '' }));
  const [linhas, setLinhas] = useState<Linha[]>(vazio);
  const [observacao, setObservacao] = useState('');
  const [existente, setExistente] = useState<Fechamento | null>(null);
  const [salvando, setSalvando] = useState(false);
  const toast = useToast();

  // Carrega o fechamento já lançado para a data (se houver) ou prepara as faixas configuradas.
  useEffect(() => {
    let ativo = true;
    api.get<Fechamento>(`/movimento/fechamentos/${data}`)
      .then((f) => {
        if (!ativo) return;
        setExistente(f);
        setLinhas(f.faixas.map((x) => ({ inicio: x.inicio, fim: x.fim, clientes: String(x.clientes), vendas: String(x.vendas) })));
        setObservacao(f.observacao ?? '');
      })
      .catch((e) => {
        if (!ativo) return;
        setExistente(null);
        setLinhas(vazio());
        setObservacao('');
        if (!(e instanceof ApiError && e.status === 404)) toast('erro', mensagemErro(e));
      });
    return () => {
      ativo = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [data]);

  const numeros = linhas.map((l) => ({ clientes: Number(l.clientes || 0), vendas: Number(l.vendas || 0) }));
  const invalido = numeros.some((n) => n.clientes < 0 || n.vendas < 0 || !Number.isFinite(n.clientes) || !Number.isFinite(n.vendas))
    || linhas.some((l) => l.clientes !== '' && !Number.isInteger(Number(l.clientes)));
  const totalClientes = numeros.reduce((s, n) => s + n.clientes, 0);
  const totalVendas = numeros.reduce((s, n) => s + n.vendas, 0);

  const salvar = async () => {
    setSalvando(true);
    try {
      await api.put(`/movimento/fechamentos/${data}`, {
        observacao,
        faixas: linhas.map((l, i) => ({ inicio: l.inicio, fim: l.fim, clientes: numeros[i].clientes, vendas: numeros[i].vendas })),
      });
      toast('ok', 'Fechamento salvo. A previsão já considera este dia.');
      onSalvo();
      setExistente({ data } as Fechamento);
    } catch (e) {
      toast('erro', mensagemErro(e));
    } finally {
      setSalvando(false);
    }
  };

  const mudar = (i: number, campo: 'clientes' | 'vendas', valor: string) =>
    setLinhas((ls) => ls.map((l, j) => (j === i ? { ...l, [campo]: valor } : l)));

  const jaLancado = recentes?.some((f) => f.data === data);

  return (
    <section className="card fechamento">
      <div className="row" style={{ marginBottom: 12 }}>
        <h2>Fechamento do dia</h2>
        <span className="spacer" />
        <input type="date" value={data} max={periodo.fim} aria-label="Data do fechamento"
          onChange={(e) => e.target.value && onData(e.target.value)} />
      </div>
      {existente?.registradoPor && (
        <p className="ajuda" style={{ marginTop: -4 }}>
          <CalendarCheck size={13} aria-hidden /> Lançado por {existente.registradoPor}
          {existente.registradoEm ? ` em ${dataCurta(existente.registradoEm.slice(0, 10))} às ${existente.registradoEm.slice(11, 16)}` : ''}.
          Salvar de novo corrige o lançamento.
        </p>
      )}
      {!existente && jaLancado === false && data !== periodo.fim && (
        <p className="ajuda texto-alerta"><CircleAlert size={13} aria-hidden /> Este dia ainda não foi lançado.</p>
      )}
      <table className="tabela-fechamento">
        <thead><tr><th>Faixa</th><th className="num">Clientes</th><th className="num">Vendas (R$)</th></tr></thead>
        <tbody>
          {linhas.map((l, i) => {
            const rotulo = `${hora(l.inicio)}–${hora(l.fim)}`;
            return (
              <tr key={l.inicio}>
                <td>{rotulo}</td>
                <td className="num">
                  <input type="number" min={0} step={1} inputMode="numeric" value={l.clientes} placeholder="0"
                    aria-label={`Clientes ${rotulo}`} onChange={(e) => mudar(i, 'clientes', e.target.value)} />
                </td>
                <td className="num">
                  <input type="number" min={0} step="0.01" inputMode="decimal" value={l.vendas} placeholder="0,00"
                    aria-label={`Vendas ${rotulo}`} onChange={(e) => mudar(i, 'vendas', e.target.value)} />
                </td>
              </tr>
            );
          })}
        </tbody>
        <tfoot>
          <tr>
            <td><strong>Total</strong></td>
            <td className="num"><strong>{numero(totalClientes)} clientes</strong></td>
            <td className="num"><strong>{moeda(totalVendas)}</strong></td>
          </tr>
        </tfoot>
      </table>
      <label className="campo" style={{ marginTop: 12 }}>Observação
        <input value={observacao} maxLength={500} placeholder="Ex.: chuva forte, promoção de carnes…"
          onChange={(e) => setObservacao(e.target.value)} />
      </label>
      <div className="row" style={{ marginTop: 14 }}>
        <span className="spacer" />
        <button className="primario" disabled={salvando || invalido} onClick={salvar}>
          {salvando ? 'Salvando…' : 'Salvar fechamento'}
        </button>
      </div>
    </section>
  );
}

function UltimosDias({ periodo, recentes, onEscolher }: {
  periodo: { inicio: string; fim: string };
  recentes: Fechamento[] | null;
  onEscolher: (data: string) => void;
}) {
  const dias: string[] = [];
  for (let d = periodo.fim; d >= periodo.inicio; d = somarDias(d, -1)) dias.push(d);
  const porData = new Map((recentes ?? []).map((f) => [f.data, f]));

  return (
    <section className="card ultimos-dias">
      <h2>Últimos dias</h2>
      <ul aria-label="Últimos fechamentos">
        {dias.map((d) => {
          const f = porData.get(d);
          return (
            <li key={d}>
              <button className={`dia-fechamento ${f ? '' : 'pendente'}`} onClick={() => onEscolher(d)}>
                <span className="dia-nome">{rotuloDia(d)}</span>
                {f ? (
                  <>
                    <span className="muted">{numero(f.clientes)} clientes</span>
                    <strong>{moeda(f.vendas)}</strong>
                  </>
                ) : (
                  <span className="badge alerta">Pendente</span>
                )}
              </button>
            </li>
          );
        })}
      </ul>
    </section>
  );
}

// ---------------------------------------------------------------- previsão

const ORIGEM: Record<PrevisaoDiaria['origemReceita'], string> = {
  PREVISAO: 'Previsto pelo histórico',
  INFORMADA: 'Projeção informada',
  PADRAO: 'Configuração manual',
};

function CartaoPrevisao({ dia }: { dia: PrevisaoDiaria }) {
  const p = dia.previsao;
  const maximo = p ? Math.max(1, ...p.faixas.map((f) => f.clientes)) : 1;
  const setores = dia.setores.filter((s) => s.faixas.length > 0);

  return (
    <article className={`cartao-previsao ${dia.feriado ? 'feriado' : ''}`}>
      <header>
        <strong>{rotuloDia(dia.data)}</strong>
        {dia.feriado && <span className="badge laranja">{dia.feriado}</span>}
        <span className={`origem origem-${dia.origemReceita.toLowerCase()}`}>
          {dia.origemReceita === 'PREVISAO' && <Sparkles size={12} aria-hidden />}
          {ORIGEM[dia.origemReceita]}
        </span>
      </header>
      <div className="valores-previsao">
        <span className="vendas-prevista">{moeda(dia.receitaProjetada)}</span>
        {p && <span className="muted">≈ {numero(p.clientes)} clientes</span>}
      </div>
      {p ? (
        <>
          <div className="barras-faixas" role="img" aria-label={`Clientes por faixa: ${p.faixas.map((f) => `${hora(f.inicio)} ${f.clientes}`).join(', ')}`}>
            {p.faixas.map((f) => (
              <div key={f.inicio} className={`barra-faixa ${p.pico?.inicio === f.inicio ? 'pico' : ''}`} title={`${hora(f.inicio)}–${hora(f.fim)}: ${f.clientes} clientes`}>
                <span style={{ height: `${Math.max(4, (f.clientes / maximo) * 100)}%` }} />
                <small>{horaCurta(f.inicio)}</small>
              </div>
            ))}
          </div>
          {p.pico && p.pico.clientes > 0 && (
            <span className="badge laranja pico-badge">Pico {horaCurta(p.pico.inicio)}–{horaCurta(p.pico.fim)}</span>
          )}
          <ul className="explicacao">
            {p.explicacao.filter((e) => !e.startsWith('Pico previsto')).map((e) => <li key={e}>{e}</li>)}
          </ul>
        </>
      ) : (
        <p className="ajuda">Sem histórico suficiente: usando a configuração manual de receita e demanda. Lance os fechamentos para o sistema aprender.</p>
      )}
      {setores.length > 0 && (
        <div className="pessoas-setor">
          {setores.map((s) => (
            <div key={s.setorId} className="row" style={{ gap: 8 }}>
              <span className="ponto" style={{ background: s.cor }} />
              <span>{s.nome}</span>
              <span className="spacer" />
              <strong>{s.necessarios} pessoas</strong>
            </div>
          ))}
        </div>
      )}
    </article>
  );
}
