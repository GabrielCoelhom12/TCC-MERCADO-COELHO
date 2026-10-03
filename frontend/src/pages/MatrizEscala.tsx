import { Fragment, useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ApiError, api } from '../api';
import { BadgeSituacao, Carregando, ListaViolacoes, Modal, mensagemErro, useCarregar, useToast } from '../components/ui';
import { dataCurta, dataLonga, diaSemanaDe, hora, horas, isoDe, moeda, paraData, pct } from '../format';
import type { Escala, Geracao, LinhaFuncionario, Matriz, TurnoDTO, TurnoModelo, Violacao } from '../types';

type MenuCelula = { x: number; y: number; funcionario: LinhaFuncionario; data: string } | null;
type Bloqueio = { mensagem: string; violacoes: Violacao[] } | null;
type Filtro = 'TODAS' | 'ERRO' | 'ALERTA';

export default function MatrizEscala() {
  const { id } = useParams();
  const { dados: m, setDados, erro, recarregar } = useCarregar(() => api.get<Matriz>(`/escalas/${id}/matriz`), [id]);
  const [menu, setMenu] = useState<MenuCelula>(null);
  const [personalizado, setPersonalizado] = useState<{ funcionario: LinhaFuncionario; data: string } | null>(null);
  const [bloqueio, setBloqueio] = useState<Bloqueio>(null);
  const [filtro, setFiltro] = useState<Filtro>('TODAS');
  const [ocupado, setOcupado] = useState(false);
  const toast = useToast();

  useEffect(() => {
    const fechar = () => setMenu(null);
    window.addEventListener('scroll', fechar, true);
    return () => window.removeEventListener('scroll', fechar, true);
  }, []);

  const dias = useMemo(() => {
    if (!m) return [];
    const lista: string[] = [];
    for (let d = paraData(m.escala.dataInicio); isoDe(d) <= m.escala.dataFim; d.setDate(d.getDate() + 1)) lista.push(isoDe(d));
    return lista;
  }, [m]);

  const turnoDe = useMemo(() => {
    const mapa = new Map<string, TurnoDTO[]>();
    m?.turnos.forEach((t) => {
      const k = `${t.funcionarioId}|${t.data}`;
      mapa.set(k, [...(mapa.get(k) ?? []), t]);
    });
    return mapa;
  }, [m]);

  const violacoesCelula = useMemo(() => {
    const mapa = new Map<string, Violacao[]>();
    m?.validacao.violacoes.forEach((v) => {
      if (v.funcionarioId == null) return;
      const k = `${v.funcionarioId}|${v.data}`;
      mapa.set(k, [...(mapa.get(k) ?? []), v]);
    });
    return mapa;
  }, [m]);

  const setoresAgrupados = useMemo(() => {
    const grupos = new Map<number, { nome: string; cor: string; funcionarios: LinhaFuncionario[] }>();
    m?.funcionarios.forEach((f) => {
      const g = grupos.get(f.setorId) ?? { nome: f.setorNome, cor: f.setorCor, funcionarios: [] };
      g.funcionarios.push(f);
      grupos.set(f.setorId, g);
    });
    return [...grupos.entries()];
  }, [m]);

  if (!m) return <Carregando erro={erro} />;

  const editavel = m.escala.status === 'RASCUNHO';
  const v = m.validacao;
  const resumoDia = new Map(v.dias.map((d) => [d.data, d]));

  const definir = async (funcionario: LinhaFuncionario, data: string, corpo: Record<string, unknown>) => {
    setMenu(null);
    setOcupado(true);
    try {
      setDados(await api.put<Matriz>(`/escalas/${id}/celula`, { funcionarioId: funcionario.id, data, ...corpo }));
    } catch (e) {
      if (e instanceof ApiError && e.status === 422) setBloqueio({ mensagem: e.message, violacoes: e.violacoes });
      else toast('erro', mensagemErro(e));
    } finally {
      setOcupado(false);
    }
  };

  const acao = async (caminho: string, confirmar?: string) => {
    if (confirmar && !window.confirm(confirmar)) return;
    setOcupado(true);
    try {
      if (caminho === 'gerar') {
        const g = await api.post<Geracao>(`/escalas/${id}/gerar`);
        setDados(g.matriz);
        toast(g.deficits.length ? 'erro' : 'ok', g.deficits.length
          ? `${g.turnosCriados} turnos gerados; ${g.deficits.length} dia/setor sem colaboradores suficientes.`
          : `${g.turnosCriados} turnos gerados, exatamente na demanda mínima.`);
      } else if (caminho === 'limpar') {
        setDados(await api.post<Matriz>(`/escalas/${id}/limpar`));
      } else {
        await api.post<Escala>(`/escalas/${id}/${caminho}`);
        toast('ok', caminho === 'aprovar' ? 'Escala aprovada.' : 'Escala reaberta para edição.');
        recarregar();
      }
    } catch (e) {
      if (e instanceof ApiError && e.violacoes.length) setBloqueio({ mensagem: e.message, violacoes: e.violacoes });
      else toast('erro', mensagemErro(e));
    } finally {
      setOcupado(false);
    }
  };

  const violacoesFiltradas = v.violacoes.filter((x) => filtro === 'TODAS' || x.severidade === filtro);

  return (
    <div className="stack" onClick={() => setMenu(null)}>
      <div className="cabecalho">
        <div>
          <div className="small"><Link to="/escalas">Voltar para escalas</Link></div>
          <h1>{m.escala.nome}</h1>
          <p>
            {dataLonga(m.escala.dataInicio)} a {dataLonga(m.escala.dataFim)} ·{' '}
            {editavel ? <span className="badge laranja">Rascunho</span> : <span className="badge ok">Aprovada</span>}
            {m.escala.geradaAutomaticamente && <> <span className="badge neutro" title="Criada pelo sistema a partir da previsão de movimento">Automática</span></>}
          </p>
        </div>
        <div className="spacer" />
        {editavel ? (
          <div className="row">
            <button disabled={ocupado} onClick={() => acao('gerar', 'Substituir todos os turnos desta escala por uma nova sugestão automática?')}>Gerar automaticamente</button>
            <button disabled={ocupado} onClick={() => acao('limpar', 'Remover todos os turnos desta escala?')}>Limpar</button>
            <button className="primario" disabled={ocupado || !v.aprovavel} onClick={() => acao('aprovar')}
              title={v.aprovavel ? 'Aprovar escala' : 'Corrija os erros para aprovar'}>Aprovar escala</button>
          </div>
        ) : (
          <button disabled={ocupado} onClick={() => acao('reabrir')}>Reabrir para edição</button>
        )}
      </div>

      <div className="grid-kpi">
        <div className="card kpi destaque">
          <div className="rotulo">Custo total</div>
          <div className="valor">{moeda(v.custoTotal)}</div>
          <div className="detalhe">{pct(v.custoTotal, v.tetoTotal)} do limite de {moeda(v.tetoTotal)}</div>
        </div>
        <div className="card kpi">
          <div className="rotulo">Turnos alocados</div>
          <div className="valor">{m.turnos.length}</div>
          <div className="detalhe">{horas(m.turnos.reduce((s, t) => s + t.horas, 0))} de trabalho</div>
        </div>
        <div className="card kpi">
          <div className="rotulo">Erros (bloqueiam aprovação)</div>
          <div className="valor" style={{ color: v.erros ? 'var(--erro)' : 'var(--ok)' }}>{v.erros}</div>
          <div className="detalhe">{v.alertas} alerta(s)</div>
        </div>
        <div className="card kpi">
          <div className="rotulo">Status de aprovação</div>
          <div className="valor">{v.aprovavel ? 'Aprovável' : 'Bloqueada'}</div>
          <div className="detalhe">Custos e regras trabalhistas</div>
        </div>
      </div>

      <div className="matriz-wrap">
        <table className="matriz">
          <thead>
            <tr>
              <th className="col-nome">Colaborador</th>
              {dias.map((d) => {
                const r = resumoDia.get(d)!;
                const especial = r.tipoDia === 'Domingo' || r.feriado;
                return (
                  <th key={d} className={especial ? 'domingo' : ''}>
                    <div>{r.tipoDia.slice(0, 3)} {dataCurta(d)}</div>
                    <div className="small muted" style={{ fontWeight: 500 }}>
                      {r.feriado ?? (r.clientesPrevistos ? `≈ ${r.clientesPrevistos} clientes${r.pico ? ` · pico ${r.pico.replace(/:00/g, 'h')}` : ''}` : '')}
                    </div>
                  </th>
                );
              })}
            </tr>
          </thead>
          <tbody>
            {setoresAgrupados.map(([setorId, g]) => (
              <Fragment key={setorId}>
                <tr className="setor-linha">
                  <td className="col-nome"><span className="ponto" style={{ background: g.cor }} /> {g.nome}</td>
                  {dias.map((d) => {
                    const s = resumoDia.get(d)!.setores.find((x) => x.setorId === setorId);
                    const ok = !s || s.operadores >= s.minimo;
                    return (
                      <td key={d} style={{ textAlign: 'center' }}>
                        {s && <span className={`badge ${ok ? 'neutro' : 'erro'}`} title="operadores / mínimo">{s.operadores}/{s.minimo}</span>}
                      </td>
                    );
                  })}
                </tr>
                {g.funcionarios.map((f) => (
                  <tr key={f.id}>
                    <td className="col-nome">
                      <div style={{ fontWeight: 600 }}>{f.nome}{!f.ativo && <span className="badge neutro"> inativo</span>}</div>
                      <div className="small muted">{f.cargo} · {moeda(f.custoHora)}/h</div>
                    </td>
                    {dias.map((d) => (
                      <Celula key={d} f={f} data={d} cor={g.cor} editavel={editavel && !ocupado}
                        turnos={turnoDe.get(`${f.id}|${d}`) ?? []}
                        violacoes={violacoesCelula.get(`${f.id}|${d}`) ?? []}
                        ausencia={m.ausencias.find((a) => a.funcionarioId === f.id && a.inicio <= d && a.fim >= d)?.motivo}
                        onClick={(e) => {
                          e.stopPropagation();
                          if (!editavel || ocupado) return;
                          const r = (e.currentTarget as HTMLElement).getBoundingClientRect();
                          setMenu({ x: Math.min(r.left, window.innerWidth - 240), y: Math.min(r.bottom + 4, window.innerHeight - 260), funcionario: f, data: d });
                        }} />
                    ))}
                  </tr>
                ))}
              </Fragment>
            ))}
          </tbody>
          <tfoot>
            <tr>
              <td className="col-nome">
                Resumo do dia
              </td>
              {dias.map((d) => {
                const r = resumoDia.get(d)!;
                return (
                  <td key={d}>
                    <div className="dia-sit" title={`Receita prevista ${moeda(r.receitaProjetada)}`}>
                      <BadgeSituacao situacao={r.situacao} curto />
                      <div className="linha"><span>equipe</span><strong>{r.operadores}</strong></div>
                      <div className="linha muted"><span>mín–máx</span><span>{r.lMin}–{r.lMax}</span></div>
                      <div className="linha"><span>custo</span><strong style={{ color: r.custoEscala > r.tetoFolha ? 'var(--erro)' : undefined }}>{moeda(r.custoEscala)}</strong></div>
                      <div className="linha muted"><span>limite</span><span>{moeda(r.tetoFolha)}</span></div>
                    </div>
                  </td>
                );
              })}
            </tr>
          </tfoot>
        </table>
      </div>

      <div className="card">
        <div className="row" style={{ marginBottom: 10 }}>
          <h2>Validação ({v.violacoes.length})</h2>
          <div className="spacer" />
          {(['TODAS', 'ERRO', 'ALERTA'] as Filtro[]).map((f) => (
            <button key={f} className={filtro === f ? 'primario' : ''} onClick={() => setFiltro(f)}>
              {f === 'TODAS' ? 'Todas' : f === 'ERRO' ? `Erros (${v.erros})` : `Alertas (${v.alertas})`}
            </button>
          ))}
        </div>
        <ListaViolacoes violacoes={violacoesFiltradas} />
      </div>

      {menu && (
        <div className="menu-celula" style={{ left: menu.x, top: menu.y }} onClick={(e) => e.stopPropagation()}>
          <div className="titulo">{menu.funcionario.nome} · {dataCurta(menu.data)}</div>
          {ordenarModelos(m.modelos, resumoDia.get(menu.data)!).map((mod) => (
            <button key={mod.id} onClick={() => definir(menu.funcionario, menu.data, { turnoModeloId: mod.id })}>
              <strong>{mod.sigla}</strong>&nbsp;{mod.nome} <span className="muted small">{hora(mod.horaInicio)}–{hora(mod.horaFim)}</span>
            </button>
          ))}
          <button onClick={() => { setPersonalizado({ funcionario: menu.funcionario, data: menu.data }); setMenu(null); }}>Horário personalizado…</button>
          <button onClick={() => definir(menu.funcionario, menu.data, {})}>Folga (remover turno)</button>
        </div>
      )}

      <ModalPersonalizado alvo={personalizado} onFechar={() => setPersonalizado(null)}
        onSalvar={(corpo) => { const p = personalizado!; setPersonalizado(null); definir(p.funcionario, p.data, corpo); }} />

      <Modal titulo="Alocação bloqueada" aberto={!!bloqueio} onFechar={() => setBloqueio(null)}
        acoes={<button className="primario" onClick={() => setBloqueio(null)}>Entendi</button>}>
        <div className="stack">
          <div className="aviso erro">{bloqueio?.mensagem}</div>
          {bloqueio && bloqueio.violacoes.length > 0 && <ListaViolacoes violacoes={bloqueio.violacoes} />}
        </div>
      </Modal>
    </div>
  );
}

function ordenarModelos(modelos: TurnoModelo[], dia: { tipoDia: string; feriado: string | null }) {
  const especial = dia.tipoDia === 'Domingo' || !!dia.feriado;
  const aplicavel = (m: TurnoModelo) => m.aplicacao === 'TODOS' || (m.aplicacao === 'DOMINGOS_FERIADOS') === especial;
  return [...modelos].sort((a, b) => Number(aplicavel(b)) - Number(aplicavel(a)));
}

const ROTULO_AUSENCIA: Record<string, string> = { FERIAS: 'Férias', ATESTADO: 'Atestado', FOLGA_ACORDADA: 'Folga', OUTRO: 'Ausente' };

function Celula({ f, data, cor, turnos, violacoes, ausencia, editavel, onClick }: {
  f: LinhaFuncionario;
  data: string;
  cor: string;
  turnos: TurnoDTO[];
  violacoes: Violacao[];
  ausencia?: string;
  editavel: boolean;
  onClick: (e: React.MouseEvent) => void;
}) {
  const temErro = violacoes.some((v) => v.severidade === 'ERRO');
  const temAlerta = !temErro && violacoes.length > 0;
  const indisponivel = f.diasDisponiveis.length > 0 && !f.diasDisponiveis.includes(diaSemanaDe(data));
  const dica = [
    ...turnos.map((t) => `${hora(t.horaInicio)}–${hora(t.horaFim)} (intervalo ${t.intervaloMinutos} min) · ${horas(t.horas)} · ${moeda(t.custo)}`),
    ...violacoes.map((v) => `${v.severidade === 'ERRO' ? 'Erro' : 'Alerta'}: ${v.mensagem}`),
  ].join('\n');

  return (
    <td className={`celula ${editavel ? '' : 'bloqueada'} ${temErro ? 'com-erro' : ''} ${temAlerta ? 'com-alerta' : ''}`}
      onClick={onClick} title={dica || undefined}>
      {turnos.length > 0 ? turnos.map((t) => (
        <div key={t.id} className="turno" style={{ background: cor }}>
          {t.rotulo}
          <small>{hora(t.horaInicio)}–{hora(t.horaFim)}</small>
        </div>
      )) : ausencia ? (
        <div className="ausente">{ROTULO_AUSENCIA[ausencia]}</div>
      ) : indisponivel ? (
        <span className="indisp">indisponível</span>
      ) : (
        <span className="folga">·</span>
      )}
    </td>
  );
}

function ModalPersonalizado({ alvo, onFechar, onSalvar }: {
  alvo: { funcionario: LinhaFuncionario; data: string } | null;
  onFechar: () => void;
  onSalvar: (corpo: Record<string, unknown>) => void;
}) {
  const [form, setForm] = useState({ horaInicio: '08:00', horaFim: '16:20', intervaloMinutos: 60 });
  return (
    <Modal titulo={alvo ? `Horário de ${alvo.funcionario.nome} em ${dataCurta(alvo.data)}` : ''} aberto={!!alvo} onFechar={onFechar}
      acoes={<>
        <button onClick={onFechar}>Cancelar</button>
        <button className="primario" onClick={() => onSalvar(form)}>Aplicar</button>
      </>}>
      <div className="form-grid">
        <label className="campo">Início<input type="time" value={form.horaInicio} onChange={(e) => setForm({ ...form, horaInicio: e.target.value })} /></label>
        <label className="campo">Fim<input type="time" value={form.horaFim} onChange={(e) => setForm({ ...form, horaFim: e.target.value })} /></label>
        <label className="campo">Intervalo (min)<input type="number" min={0} value={form.intervaloMinutos} onChange={(e) => setForm({ ...form, intervaloMinutos: Number(e.target.value) })} /></label>
      </div>
      <p className="ajuda">Se o fim for menor que o início, o turno termina no dia seguinte.</p>
    </Modal>
  );
}
