import { useEffect, useRef, useState, type KeyboardEvent } from 'react';
import { Link } from 'react-router-dom';
import { ChevronLeft, ChevronRight, LayoutGrid, Palmtree } from 'lucide-react';
import { api } from '../api';
import { BadgeSituacao, Carregando, ListaViolacoes, useCarregar } from '../components/ui';
import { DIAS_SEMANA, MOTIVOS_AUSENCIA, dataCurta, diaSemanaDe, isoDe, moeda, numero, paraData, pct } from '../format';
import type { Ausencia, Escala, Matriz, ResultadoValidacao, ResumoDia, TurnoDTO } from '../types';
import TrocaColaborador from './TrocaColaborador';

const DIA_MS = 86400000;
const somarDias = (iso: string, dias: number) => isoDe(new Date(paraData(iso).getTime() + dias * DIA_MS));
const segundaDe = (iso: string) => somarDias(iso, -((paraData(iso).getDay() + 6) % 7));
const hora = (h: string) => h.slice(0, 5);
const rotuloDia = (d: string) => DIAS_SEMANA.find((x) => x.dia === diaSemanaDe(d))?.rotulo;
const rotuloMotivo = (motivo: string) => MOTIVOS_AUSENCIA.find((m) => m.valor === motivo)?.rotulo;
const pessoas = (n: number) => (n === 1 ? '1 pessoa' : `${n} pessoas`);

export default function Painel() {
  const hoje = isoDe(new Date());
  const [inicio, setInicio] = useState<string | null>(null);
  const [escalas, setEscalas] = useState<Escala[] | null>(null);
  const [diaEscolhido, setDiaEscolhido] = useState<string | null>(null);
  const [gradeAberta, setGradeAberta] = useState(false);

  // Abre na escala vigente ou na próxima; sem escalas (ou se a lista falhar), na semana atual.
  useEffect(() => {
    api.get<Escala[]>('/escalas')
      .catch(() => [] as Escala[])
      .then((lista) => {
        setEscalas(lista);
        const vigente = lista.find((e) => e.dataInicio <= hoje && e.dataFim >= hoje);
        const proxima = [...lista].filter((e) => e.dataInicio > hoje).sort((a, b) => a.dataInicio.localeCompare(b.dataInicio))[0];
        setInicio((vigente ?? proxima)?.dataInicio ?? segundaDe(hoje));
      });
  }, [hoje]);

  const fim = inicio ? somarDias(inicio, 6) : null;
  const dias = inicio ? Array.from({ length: 7 }, (_, i) => somarDias(inicio, i)) : [];

  // Ao trocar de semana, o dia aberto é hoje (se estiver nela) ou o primeiro.
  useEffect(() => {
    if (inicio && fim) setDiaEscolhido(hoje >= inicio && hoje <= fim ? hoje : inicio);
  }, [inicio, fim, hoje]);

  const { dados, erro, recarregar } = useCarregar(
    () => (inicio ? api.get<ResultadoValidacao>(`/motor/painel?inicio=${inicio}&fim=${fim}`) : new Promise<ResultadoValidacao>(() => {})),
    [inicio],
  );
  const daSemana = inicio && fim ? escalas?.find((e) => e.dataInicio <= fim && e.dataFim >= inicio) : undefined;
  const { dados: matriz, setDados: setMatriz } = useCarregar(
    () => (daSemana ? api.get<Matriz>(`/escalas/${daSemana.id}/matriz`) : Promise.resolve(null)),
    [daSemana?.id],
  );

  const { dados: ferias } = useCarregar(() => api.get<Ausencia[]>('/ferias'), []);

  const mover = (n: number) => setInicio((i) => (i ? somarDias(i, n) : i));
  const semanaAtual = inicio !== null && fim !== null && hoje >= inicio && hoje <= fim;

  return (
    <div className="stack painel">
      <div className="cabecalho">
        <div>
          <h1>Painel</h1>
          <p>Quem trabalha e quanto custa cada dia da semana.</p>
        </div>
        <div className="spacer" />
        {!semanaAtual && inicio && <button onClick={() => setInicio(segundaDe(hoje))}>Esta semana</button>}
        <div className="navegador-semana" role="group" aria-label="Semana exibida">
          <button className="icone" onClick={() => mover(-7)} aria-label="Semana anterior"><ChevronLeft size={18} aria-hidden /></button>
          <strong>{inicio && fim ? `${dataCurta(inicio)} – ${dataCurta(fim)}` : '…'}</strong>
          <button className="icone" onClick={() => mover(7)} aria-label="Próxima semana"><ChevronRight size={18} aria-hidden /></button>
        </div>
      </div>

      {!dados || !diaEscolhido ? <Carregando erro={erro} /> : (
        <>
          <Indicadores r={dados} />

          {ferias && inicio && fim && <DeFerias ferias={ferias} inicio={inicio} fim={fim} matriz={matriz} />}

          <section className="card semana" aria-labelledby="titulo-semana">
            <div className="row cabecalho-semana">
              <h2 id="titulo-semana">Dia a dia</h2>
              {daSemana && (
                <>
                  <span className="muted">{daSemana.nome}</span>
                  {daSemana.status === 'APROVADA'
                    ? <span className="badge ok">Aprovada</span>
                    : <span className="badge laranja">Rascunho</span>}
                </>
              )}
              <div className="spacer" />
              {daSemana && (
                <>
                  <button aria-expanded={gradeAberta} onClick={() => setGradeAberta((a) => !a)}>
                    <LayoutGrid size={16} aria-hidden />{gradeAberta ? 'Ocultar grade da semana' : 'Ver grade da semana'}
                  </button>
                  <Link className="btn" to={`/escalas/${daSemana.id}`}>Abrir matriz</Link>
                </>
              )}
            </div>

            <DiasDaSemana dias={dias} resumos={dados.dias} hoje={hoje} escolhido={diaEscolhido} onEscolher={setDiaEscolhido} />

            <DetalheDoDia
              data={diaEscolhido}
              resumo={dados.dias.find((d) => d.data === diaEscolhido)}
              violacoes={dados.violacoes.filter((v) => v.data === diaEscolhido)}
              escala={daSemana}
              matriz={matriz}
              onTrocado={(m) => {
                setMatriz(m);
                recarregar();
              }}
            />

            {gradeAberta && daSemana && (matriz
              ? <GradeDaSemana escala={daSemana} matriz={matriz} dias={dias} hoje={hoje} />
              : <Carregando />)}
          </section>
        </>
      )}
    </div>
  );
}

// ------------------------------------------------------------------ indicadores da semana

function Indicadores({ r }: { r: ResultadoValidacao }) {
  const receita = r.dias.reduce((s, d) => s + d.receitaProjetada, 0);
  const folga = r.tetoTotal - r.custoTotal;
  const uso = r.tetoTotal > 0 ? Math.min(100, (r.custoTotal / r.tetoTotal) * 100) : 0;
  const acima = r.custoTotal > r.tetoTotal;
  return (
    <div className="grid-kpi">
      <div className="card kpi destaque">
        <div className="rotulo">Custo escalado na semana</div>
        <div className="valor">{moeda(r.custoTotal)}</div>
        <div className="detalhe">{pct(r.custoTotal, receita)} da receita prevista</div>
      </div>
      <div className="card kpi">
        <div className="rotulo">Uso do limite de custo</div>
        <div className="valor" style={{ color: acima ? 'var(--erro)' : undefined }}>{pct(r.custoTotal, r.tetoTotal)}</div>
        <div className="barra-uso" aria-hidden><i className={acima ? 'acima' : ''} style={{ width: `${uso}%` }} /></div>
        <div className="detalhe">limite de {moeda(r.tetoTotal)}</div>
      </div>
      <div className="card kpi">
        <div className="rotulo">{folga >= 0 ? 'Margem preservada' : 'Acima do limite'}</div>
        <div className="valor" style={{ color: folga >= 0 ? 'var(--ok)' : 'var(--erro)' }}>{moeda(Math.abs(folga))}</div>
        <div className="detalhe">sobre {moeda(receita)} de receita prevista</div>
      </div>
      <div className="card kpi">
        <div className="rotulo">Conformidade</div>
        <div className="valor" style={{ color: r.erros > 0 ? 'var(--erro)' : 'var(--ok)' }}>
          {r.erros === 0 ? 'Em dia' : `${r.erros} ${r.erros === 1 ? 'erro' : 'erros'}`}
        </div>
        <div className="detalhe">{r.alertas === 1 ? '1 alerta' : `${r.alertas} alertas`} para revisar</div>
      </div>
    </div>
  );
}

// ------------------------------------------------------------------ de férias na semana

function DeFerias({ ferias, inicio, fim, matriz }: { ferias: Ausencia[]; inicio: string; fim: string; matriz: Matriz | null }) {
  const daSemana = ferias.filter((f) => f.inicio <= fim && f.fim >= inicio).sort((a, b) => a.fim.localeCompare(b.fim));
  const pessoa = (id: number) => matriz?.funcionarios.find((f) => f.id === id);
  return (
    <section className="card de-ferias" aria-labelledby="titulo-ferias">
      <div className="row">
        <Palmtree size={18} className="icone-ferias" aria-hidden />
        <h2 id="titulo-ferias">De férias nesta semana</h2>
        <span className="badge laranja">{daSemana.length}</span>
        <div className="spacer" />
        <Link to="/ferias">Ver férias</Link>
      </div>
      {daSemana.length === 0 ? <p className="muted">Ninguém de férias nesta semana.</p> : (
        <ul className="lista-ferias">
          {daSemana.map((f) => {
            const p = pessoa(f.funcionarioId);
            return (
              <li key={f.id}>
                <span className="membro">
                  <strong>{f.funcionarioNome}</strong>
                  {p && (
                    <span className="small muted">
                      <span className="ponto-setor" style={{ background: p.setorCor }} aria-hidden />{p.setorNome}
                    </span>
                  )}
                </span>
                <span className="small periodo-ferias">
                  {f.inicio > inicio && <>a partir de {dataCurta(f.inicio)} · </>}volta em {dataCurta(somarDias(f.fim, 1))}
                </span>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}

// ------------------------------------------------------------------ seletor de dias

function DiasDaSemana({ dias, resumos, hoje, escolhido, onEscolher }: {
  dias: string[]; resumos: ResumoDia[]; hoje: string; escolhido: string; onEscolher: (d: string) => void;
}) {
  // Em telas estreitas a faixa rola na horizontal: centraliza o dia escolhido, sem rolar a página.
  const faixa = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const aba = document.getElementById(`aba-${escolhido}`);
    const el = faixa.current;
    if (aba && el && el.scrollWidth > el.clientWidth) {
      el.scrollLeft = aba.offsetLeft - el.offsetLeft - (el.clientWidth - aba.offsetWidth) / 2;
    }
  }, [escolhido]);
  const escolher = (d: string) => {
    onEscolher(d);
    document.getElementById(`aba-${d}`)?.focus();
  };
  const teclas = (e: KeyboardEvent, i: number) => {
    const destino = { ArrowRight: i + 1, ArrowLeft: i - 1, Home: 0, End: dias.length - 1 }[e.key];
    if (destino === undefined) return;
    e.preventDefault();
    escolher(dias[(destino + dias.length) % dias.length]);
  };
  return (
    <div className="dias-semana" role="tablist" aria-label="Dias da semana" ref={faixa}>
      {dias.map((d, i) => {
        const r = resumos.find((x) => x.data === d);
        const uso = r && r.tetoFolha > 0 ? Math.min(100, (r.custoEscala / r.tetoFolha) * 100) : 0;
        const ativo = d === escolhido;
        return (
          <button key={d} id={`aba-${d}`} role="tab" type="button" aria-selected={ativo} aria-controls="detalhe-dia"
            tabIndex={ativo ? 0 : -1} className={`dia-aba${ativo ? ' ativa' : ''}${d === hoje ? ' hoje' : ''}`}
            onClick={() => onEscolher(d)} onKeyDown={(e) => teclas(e, i)}>
            <span className="dia-topo">
              <span className="dia-nome">{d === hoje ? 'Hoje' : rotuloDia(d)}</span>
              {r && <BadgeSituacao situacao={r.situacao} curto />}
            </span>
            <span className="dia-data">{dataCurta(d)}</span>
            <span className="dia-equipe">{r ? pessoas(r.operadores) : '—'}</span>
            <span className="mini-barra" aria-hidden><i className={r && r.custoEscala > r.tetoFolha ? 'acima' : ''} style={{ width: `${uso}%` }} /></span>
            {r?.feriado && <span className="dia-feriado">{r.feriado}</span>}
          </button>
        );
      })}
    </div>
  );
}

// ------------------------------------------------------------------ detalhe do dia escolhido

const PERIODOS = [
  { nome: 'Manhã', ate: '12:00' },
  { nome: 'Tarde', ate: '18:00' },
  { nome: 'Noite', ate: '24:00' },
];

function DetalheDoDia({ data, resumo, violacoes, escala, matriz, onTrocado }: {
  data: string; resumo: ResumoDia | undefined; violacoes: ResultadoValidacao['violacoes'];
  escala: Escala | undefined; matriz: Matriz | null; onTrocado: (m: Matriz) => void;
}) {
  const nomeDia = `${rotuloDia(data)} ${dataCurta(data)}`;
  return (
    <div className="detalhe-dia" role="tabpanel" id="detalhe-dia" aria-labelledby={`aba-${data}`}>
      <div className="detalhe-equipe">
        <EquipeDoDia data={data} nomeDia={nomeDia} escala={escala} matriz={matriz} onTrocado={onTrocado} />
      </div>
      <div className="detalhe-custos">
        <h3>Custos do dia</h3>
        {!resumo ? <p className="muted">Sem dados de custo para este dia.</p> : (
          <>
            <dl className="numeros-dia">
              <div><dt>Receita prevista</dt><dd>{moeda(resumo.receitaProjetada)}</dd></div>
              <div><dt>Limite de custo</dt><dd>{moeda(resumo.tetoFolha)}</dd></div>
              <div><dt>Custo escalado</dt><dd>{moeda(resumo.custoEscala)}</dd></div>
              <div><dt>Equipe</dt><dd>{resumo.operadores} <span className="muted">(mín. {resumo.lMin} · máx. {resumo.lMax})</span></dd></div>
            </dl>
            <div className="row" style={{ gap: 8 }}>
              <BadgeSituacao situacao={resumo.situacao} />
              {resumo.fator > 1 && <span className="badge neutro">custo {numero(resumo.fator)}× ({resumo.feriado ?? 'domingo'})</span>}
            </div>
          </>
        )}
        <h3 style={{ marginTop: 16 }}>Alertas do dia</h3>
        <ListaViolacoes violacoes={violacoes} vazio="Nenhum alerta neste dia." />
      </div>
    </div>
  );
}

function EquipeDoDia({ data, nomeDia, escala, matriz, onTrocado }: {
  data: string; nomeDia: string; escala: Escala | undefined; matriz: Matriz | null; onTrocado: (m: Matriz) => void;
}) {
  const [aberto, setAberto] = useState<TurnoDTO | null>(null);
  if (!escala) {
    return (
      <>
        <h3>Equipe · {nomeDia}</h3>
        <p className="muted"><span>Nenhuma escala cobre esta semana.</span> <Link to="/escalas">Ir para Escalas</Link></p>
      </>
    );
  }
  if (!matriz) return <><h3>Equipe · {nomeDia}</h3><Carregando /></>;

  const pessoasPorId = new Map(matriz.funcionarios.map((f) => [f.id, f]));
  const turnos = matriz.turnos
    .filter((t) => t.data === data && pessoasPorId.has(t.funcionarioId))
    .sort((a, b) => a.horaInicio.localeCompare(b.horaInicio)
      || (pessoasPorId.get(a.funcionarioId)?.nome ?? '').localeCompare(pessoasPorId.get(b.funcionarioId)?.nome ?? ''));
  const trabalham = new Set(turnos.map((t) => t.funcionarioId));
  const ausentes = matriz.ausencias.filter((a) => a.inicio <= data && a.fim >= data && !trabalham.has(a.funcionarioId));
  const foraDaEscala = data < escala.dataInicio || data > escala.dataFim;

  const grupos = PERIODOS.map((p, i) => ({
    ...p,
    turnos: turnos.filter((t) => hora(t.horaInicio) < p.ate && (i === 0 || hora(t.horaInicio) >= PERIODOS[i - 1].ate)),
  })).filter((g) => g.turnos.length > 0);

  return (
    <>
      <div className="row" style={{ marginBottom: 8 }}>
        <h3>Equipe · {nomeDia}</h3>
        <div className="spacer" />
        <span className="badge laranja">{pessoas(turnos.length)}</span>
      </div>
      {foraDaEscala && <p className="muted">Este dia está fora do período da escala.</p>}
      {!foraDaEscala && turnos.length === 0 && <p className="muted">Ninguém escalado neste dia.</p>}
      {grupos.map((g) => (
        <div className="periodo" key={g.nome}>
          <div className="periodo-nome">{g.nome}</div>
          <ul className="lista-equipe">
            {g.turnos.map((t) => (
              <PessoaNoTurno key={t.id} turno={t} pessoa={pessoasPorId.get(t.funcionarioId)} onAbrir={() => setAberto(t)} />
            ))}
          </ul>
        </div>
      ))}
      {ausentes.length > 0 && (
        <p className="ausentes small">
          <strong>Ausentes:</strong> {ausentes.map((a) => `${a.funcionarioNome} (${rotuloMotivo(a.motivo)})`).join(', ')}
        </p>
      )}
      {aberto && (
        <TrocaColaborador escala={escala} turno={aberto} onFechar={() => setAberto(null)}
          onTrocado={(m) => {
            setAberto(null);
            onTrocado(m);
          }} />
      )}
    </>
  );
}

function PessoaNoTurno({ turno, pessoa, onAbrir }: {
  turno: TurnoDTO; pessoa: Matriz['funcionarios'][number] | undefined; onAbrir: () => void;
}) {
  return (
    <li>
      <button type="button" className="pessoa-turno" onClick={onAbrir} title="Ver disponibilidade e trocar">
        <span className="horario">{`${hora(turno.horaInicio)}–${hora(turno.horaFim)}`}</span>
        <span className="membro">
          <strong>{pessoa?.nome}</strong>
          <span className="small muted">
            <span className="ponto-setor" style={{ background: pessoa?.setorCor }} aria-hidden />{pessoa?.setorNome}
          </span>
        </span>
        {turno.rotulo && <span className="badge neutro">{turno.rotulo}</span>}
      </button>
    </li>
  );
}

// ------------------------------------------------------------------ grade completa (sob demanda)

function GradeDaSemana({ escala, matriz, dias, hoje }: { escala: Escala; matriz: Matriz; dias: string[]; hoje: string }) {
  const naEscala = (d: string) => d >= escala.dataInicio && d <= escala.dataFim;
  const linhas = [...matriz.funcionarios].sort((a, b) => a.setorNome.localeCompare(b.setorNome) || a.nome.localeCompare(b.nome));
  const turnoDe = (id: number, d: string) => matriz.turnos.find((t) => t.funcionarioId === id && t.data === d);
  const ausenciaDe = (id: number, d: string) => matriz.ausencias.find((a) => a.funcionarioId === id && a.inicio <= d && a.fim >= d);
  const escalados = (d: string) => new Set(matriz.turnos.filter((t) => t.data === d).map((t) => t.funcionarioId)).size;

  return (
    <div className="tabela-wrap grade-semana">
      <table className="escala-semana">
        <thead>
          <tr>
            <th>Colaborador</th>
            {dias.map((d) => (
              <th key={d} className={d === hoje ? 'hoje' : undefined} aria-current={d === hoje ? 'date' : undefined}>
                {d === hoje ? 'Hoje' : rotuloDia(d)} <span className="muted">{dataCurta(d)}</span>
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {linhas.map((f) => (
            <tr key={f.id}>
              <th scope="row">
                <span className="ponto-setor" style={{ background: f.setorCor }} aria-hidden />
                {f.nome}
                <div className="small muted">{f.setorNome}</div>
              </th>
              {dias.map((d) => {
                const turno = turnoDe(f.id, d);
                const ausencia = ausenciaDe(f.id, d);
                const classe = d === hoje ? 'hoje' : undefined;
                if (!naEscala(d)) return <td key={d} className={classe}><span className="muted">—</span></td>;
                if (turno) {
                  return (
                    <td key={d} className={classe}>
                      <strong>{`${hora(turno.horaInicio)}–${hora(turno.horaFim)}`}</strong>
                      {turno.rotulo && <div className="small muted">{turno.rotulo}</div>}
                    </td>
                  );
                }
                if (ausencia) return <td key={d} className={classe}><span className="badge neutro">{rotuloMotivo(ausencia.motivo)}</span></td>;
                return <td key={d} className={classe}><span className="muted">Folga</span></td>;
              })}
            </tr>
          ))}
        </tbody>
        <tfoot>
          <tr>
            <th scope="row">Pessoas escaladas</th>
            {dias.map((d) => <td key={d} className={d === hoje ? 'hoje' : undefined}>{naEscala(d) ? escalados(d) : '—'}</td>)}
          </tr>
        </tfoot>
      </table>
    </div>
  );
}
