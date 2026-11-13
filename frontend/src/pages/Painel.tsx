import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api';
import { BadgeSituacao, Carregando, ListaViolacoes, useCarregar } from '../components/ui';
import { DIAS_SEMANA, MOTIVOS_AUSENCIA, dataCurta, diaSemanaDe, isoDe, moeda, numero, paraData, pct } from '../format';
import type { Escala, Matriz, ResultadoValidacao, ResumoDia } from '../types';

const segundaDe = (d: Date) => {
  const r = new Date(d);
  r.setDate(r.getDate() - ((r.getDay() + 6) % 7));
  return r;
};

export default function Painel() {
  const [inicio, setInicio] = useState<string | null>(null);
  const [escalas, setEscalas] = useState<Escala[] | null>(null);

  // Abre na escala vigente ou na próxima; sem escalas (ou se a lista falhar), na semana atual.
  useEffect(() => {
    api.get<Escala[]>('/escalas')
      .catch(() => [] as Escala[])
      .then((escalas) => {
        setEscalas(escalas);
        const hoje = isoDe(new Date());
        const vigente = escalas.find((e) => e.dataInicio <= hoje && e.dataFim >= hoje);
        const proxima = [...escalas].filter((e) => e.dataInicio > hoje).sort((a, b) => a.dataInicio.localeCompare(b.dataInicio))[0];
        setInicio((vigente ?? proxima)?.dataInicio ?? isoDe(segundaDe(new Date())));
      });
  }, []);

  const fim = inicio ? isoDe(new Date(paraData(inicio).getTime() + 6 * 86400000)) : null;
  const { dados, erro } = useCarregar(
    () => (inicio ? api.get<ResultadoValidacao>(`/motor/painel?inicio=${inicio}&fim=${fim}`) : new Promise<ResultadoValidacao>(() => {})),
    [inicio],
  );

  const mover = (dias: number) => setInicio((i) => (i ? isoDe(new Date(paraData(i).getTime() + dias * 86400000)) : i));
  const daSemana = inicio && fim ? escalas?.find((e) => e.dataInicio <= fim && e.dataFim >= inicio) : undefined;

  return (
    <div className="stack">
      <div className="cabecalho">
        <div>
          <h1>Painel de custos da mão de obra</h1>
          <p>Custo da equipe escalada comparado ao limite de cada dia.</p>
        </div>
        <div className="spacer" />
        <div className="row">
          <button onClick={() => mover(-7)} aria-label="Semana anterior">Anterior</button>
          <strong>{inicio && fim ? `${dataCurta(inicio)} a ${dataCurta(fim)}` : '…'}</strong>
          <button onClick={() => mover(7)} aria-label="Próxima semana">Próxima</button>
        </div>
      </div>

      <EquipeDeHoje escalas={escalas} />
      {daSemana && inicio && <EscalaDaSemana escala={daSemana} inicio={inicio} />}
      {!dados ? <Carregando erro={erro} /> : <ConteudoPainel r={dados} />}
    </div>
  );
}

const hora = (h: string) => h.slice(0, 5);
const rotuloDia = (d: string) => DIAS_SEMANA.find((x) => x.dia === diaSemanaDe(d))?.rotulo;
const rotuloMotivo = (motivo: string) => MOTIVOS_AUSENCIA.find((m) => m.valor === motivo)?.rotulo;

/** Equipe do dia de hoje (pela escala que cobre hoje, qualquer que seja a semana exibida), em ordem de entrada. */
function EquipeDeHoje({ escalas }: { escalas: Escala[] | null }) {
  const hoje = isoDe(new Date());
  const vigente = escalas?.find((e) => e.dataInicio <= hoje && e.dataFim >= hoje);
  const { dados: matriz, erro } = useCarregar(
    () => (vigente ? api.get<Matriz>(`/escalas/${vigente.id}/matriz`) : Promise.resolve(null)),
    [vigente?.id],
  );

  const pessoas = matriz ? new Map(matriz.funcionarios.map((f) => [f.id, f])) : new Map();
  const turnos = (matriz?.turnos ?? [])
    .filter((t) => t.data === hoje && pessoas.has(t.funcionarioId))
    .sort((a, b) => a.horaInicio.localeCompare(b.horaInicio) || pessoas.get(a.funcionarioId).nome.localeCompare(pessoas.get(b.funcionarioId).nome));
  const trabalham = new Set(turnos.map((t) => t.funcionarioId));
  const ausentes = (matriz?.ausencias ?? []).filter((a) => a.inicio <= hoje && a.fim >= hoje && !trabalham.has(a.funcionarioId));

  let conteudo;
  if (escalas === null) {
    conteudo = <Carregando />;
  } else if (!vigente) {
    conteudo = <p className="muted"><span>Nenhuma escala cobre o dia de hoje.</span> <Link to="/escalas">Ver escalas</Link></p>;
  } else if (!matriz) {
    conteudo = <Carregando erro={erro} />;
  } else {
    conteudo = (
      <>
        {turnos.length === 0 ? <p className="muted">Ninguém escalado para hoje.</p> : (
          <div className="tabela-wrap">
            <table className="equipe-hoje">
              <thead>
                <tr>
                  <th>Horário</th>
                  <th>Colaborador</th>
                  <th>Setor</th>
                  <th>Turno</th>
                </tr>
              </thead>
              <tbody>
                {turnos.map((t) => {
                  const f = pessoas.get(t.funcionarioId);
                  return (
                    <tr key={t.id}>
                      <td><strong>{`${hora(t.horaInicio)}–${hora(t.horaFim)}`}</strong></td>
                      <td>{f.nome}</td>
                      <td><span className="ponto-setor" style={{ background: f.setorCor }} aria-hidden />{f.setorNome}</td>
                      <td className="muted">{t.rotulo}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
        {ausentes.length > 0 && (
          <p className="small muted" style={{ marginTop: 10 }}>
            <strong>Ausentes hoje:</strong>{' '}
            {ausentes.map((a) => `${a.funcionarioNome} (${rotuloMotivo(a.motivo)})`).join(', ')}
          </p>
        )}
      </>
    );
  }

  return (
    <section className="card" aria-labelledby="titulo-equipe-hoje">
      <div className="row" style={{ marginBottom: 12 }}>
        <h2 id="titulo-equipe-hoje">Quem trabalha hoje</h2>
        <span className="muted">{rotuloDia(hoje)} {dataCurta(hoje)}</span>
        <div className="spacer" />
        {matriz && <span className="badge laranja">{turnos.length === 1 ? '1 pessoa' : `${turnos.length} pessoas`}</span>}
      </div>
      {conteudo}
    </section>
  );
}

/** Quem trabalha em cada dia da semana exibida, com o horário, as folgas e as ausências (consulta; edição na matriz). */
function EscalaDaSemana({ escala, inicio }: { escala: Escala; inicio: string }) {
  const { dados: matriz, erro } = useCarregar(() => api.get<Matriz>(`/escalas/${escala.id}/matriz`), [escala.id]);
  const hoje = isoDe(new Date());
  const dias = Array.from({ length: 7 }, (_, i) => isoDe(new Date(paraData(inicio).getTime() + i * 86400000)));
  const naEscala = (d: string) => d >= escala.dataInicio && d <= escala.dataFim;
  const linhas = matriz
    ? [...matriz.funcionarios].sort((a, b) => a.setorNome.localeCompare(b.setorNome) || a.nome.localeCompare(b.nome))
    : [];
  const turnoDe = (funcionarioId: number, d: string) => matriz?.turnos.find((t) => t.funcionarioId === funcionarioId && t.data === d);
  const ausenciaDe = (funcionarioId: number, d: string) =>
    matriz?.ausencias.find((a) => a.funcionarioId === funcionarioId && a.inicio <= d && a.fim >= d);
  const escalados = (d: string) => new Set(matriz?.turnos.filter((t) => t.data === d).map((t) => t.funcionarioId)).size;

  return (
    <section className="card" aria-labelledby="titulo-escala-semana">
      <div className="row" style={{ marginBottom: 12 }}>
        <h2 id="titulo-escala-semana">Escala da semana</h2>
        <span className="muted">{escala.nome}</span>
        {escala.status === 'APROVADA'
          ? <span className="badge ok">Aprovada</span>
          : <span className="badge laranja">Rascunho</span>}
        <div className="spacer" />
        <Link className="btn" to={`/escalas/${escala.id}`}>Abrir matriz</Link>
      </div>
      {!matriz ? <Carregando erro={erro} /> : (
        <div className="tabela-wrap">
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
                    if (ausencia) {
                      return (
                        <td key={d} className={classe}>
                          <span className="badge neutro">{rotuloMotivo(ausencia.motivo)}</span>
                        </td>
                      );
                    }
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
      )}
    </section>
  );
}

function ConteudoPainel({ r }: { r: ResultadoValidacao }) {
  const receita = r.dias.reduce((s, d) => s + d.receitaProjetada, 0);
  const foraDosLimites = r.dias.filter((d) => d.situacao === 'ACIMA_LIMITE' || d.situacao === 'ABAIXO_MINIMO').length;
  const folga = r.tetoTotal - r.custoTotal;
  const semAlocacao = r.dias.every((d) => d.operadores === 0);

  return (
    <>
      <div className="grid-kpi">
        <div className="card kpi destaque">
          <div className="rotulo">Custo escalado na semana</div>
          <div className="valor">{moeda(r.custoTotal)}</div>
          <div className="detalhe">{pct(r.custoTotal, receita)} da receita projetada</div>
        </div>
        <div className="card kpi">
          <div className="rotulo">Limite de custo da semana</div>
          <div className="valor">{moeda(r.tetoTotal)}</div>
          <div className="detalhe">sobre {moeda(receita)} de receita projetada</div>
        </div>
        <div className="card kpi">
          <div className="rotulo">{folga >= 0 ? 'Margem preservada' : 'Acima do limite'}</div>
          <div className="valor" style={{ color: folga >= 0 ? 'var(--ok)' : 'var(--erro)' }}>{moeda(Math.abs(folga))}</div>
          <div className="detalhe">uso de {pct(r.custoTotal, r.tetoTotal)} do limite</div>
        </div>
        <div className="card kpi">
          <div className="rotulo">Conformidade</div>
          <div className="valor">{foraDosLimites === 0 && r.erros === 0 ? 'OK' : `${r.erros} erro(s)`}</div>
          <div className="detalhe">{foraDosLimites} dia(s) fora do limite · {r.alertas} alerta(s)</div>
        </div>
      </div>

      {semAlocacao && (
        <div className="aviso info">
          Nenhuma escala cobre este período. <Link to="/escalas">Crie uma escala</Link> e use o gerador automático.
        </div>
      )}

      <div className="card">
        <div className="row" style={{ marginBottom: 12 }}>
          <h2>Custo por dia</h2>
          <div className="spacer" />
          <div className="legenda">
            <span><i className="amostra-barra" />Custo</span>
            <span><i className="amostra-teto" />Limite do dia</span>
            <span className="badge erro">acima do limite</span>
          </div>
        </div>
        <GraficoCustoTeto dias={r.dias} />
      </div>

      <div className="card">
        <h2>Resumo por dia</h2>
        <div className="tabela-wrap">
          <table>
            <thead>
              <tr>
                <th>Dia</th>
                <th className="num">Receita prevista</th>
                <th className="num">Acréscimo</th>
                <th className="num">Limite</th>
                <th className="num">Custo</th>
                <th className="num">Equipe</th>
                <th className="num">Mín.</th>
                <th className="num">Máx.</th>
                <th>Situação</th>
              </tr>
            </thead>
            <tbody>
              {r.dias.map((d) => (
                <tr key={d.data}>
                  <td>
                    <strong>{d.tipoDia}</strong> {dataCurta(d.data)}
                    {d.feriado && <div className="small muted">{d.feriado}</div>}
                  </td>
                  <td className="num">{moeda(d.receitaProjetada)}</td>
                  <td className="num">{numero(d.fator)}×</td>
                  <td className="num">{moeda(d.tetoFolha)}</td>
                  <td className="num">{moeda(d.custoEscala)}</td>
                  <td className="num">{d.operadores}</td>
                  <td className="num">{d.lMin}</td>
                  <td className="num">{d.lMax}</td>
                  <td><BadgeSituacao situacao={d.situacao} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      <div className="card">
        <h2>Violações e alertas do período</h2>
        <ListaViolacoes violacoes={r.violacoes} />
      </div>
    </>
  );
}

/** Bullet chart: barra = custo escalado; marcador = teto do dia. Escala comum a todos os dias. */
function GraficoCustoTeto({ dias }: { dias: ResumoDia[] }) {
  const max = Math.max(1, ...dias.map((d) => Math.max(d.custoEscala, d.tetoFolha))) * 1.05;
  return (
    <div className="bullet-lista" role="list">
      {dias.map((d) => {
        const acima = d.custoEscala > d.tetoFolha;
        const dica = `${d.tipoDia} ${dataCurta(d.data)} — custo ${moeda(d.custoEscala)} / teto ${moeda(d.tetoFolha)} (${pct(d.custoEscala, d.tetoFolha)})`;
        return (
          <div className="bullet" key={d.data} role="listitem" title={dica}>
            <div className="legenda-dia">
              <strong>{d.tipoDia}</strong> {dataCurta(d.data)}
              {d.feriado && <div className="small muted">feriado</div>}
            </div>
            <div className="trilho">
              <div className={`barra ${acima ? 'acima' : ''}`} style={{ width: `${(d.custoEscala / max) * 100}%` }} />
              <div className="teto" style={{ left: `${(d.tetoFolha / max) * 100}%` }} />
            </div>
            <div className="valores">
              {acima && <span className="badge erro" style={{ marginRight: 6 }}>acima</span>}
              {moeda(d.custoEscala)} <span className="muted">/ {moeda(d.tetoFolha)}</span>
            </div>
          </div>
        );
      })}
    </div>
  );
}
