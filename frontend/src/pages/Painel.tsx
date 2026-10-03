import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api';
import { BadgeSituacao, Carregando, ListaViolacoes, useCarregar } from '../components/ui';
import { dataCurta, isoDe, moeda, numero, paraData, pct } from '../format';
import type { Escala, ResultadoValidacao, ResumoDia } from '../types';

const segundaDe = (d: Date) => {
  const r = new Date(d);
  r.setDate(r.getDate() - ((r.getDay() + 6) % 7));
  return r;
};

export default function Painel() {
  const [inicio, setInicio] = useState<string | null>(null);

  // Abre na escala vigente ou na próxima; sem escalas (ou se a lista falhar), na semana atual.
  useEffect(() => {
    api.get<Escala[]>('/escalas')
      .catch(() => [] as Escala[])
      .then((escalas) => {
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

  return (
    <div className="stack">
      <div className="cabecalho">
        <div>
          <h1>Painel financeiro da mão de obra</h1>
          <p>Custo da equipe escalada comparado ao limite de cada dia.</p>
        </div>
        <div className="spacer" />
        <div className="row">
          <button onClick={() => mover(-7)} aria-label="Semana anterior">Anterior</button>
          <strong>{inicio && fim ? `${dataCurta(inicio)} a ${dataCurta(fim)}` : '…'}</strong>
          <button onClick={() => mover(7)} aria-label="Próxima semana">Próxima</button>
        </div>
      </div>

      {!dados ? <Carregando erro={erro} /> : <ConteudoPainel r={dados} />}
    </div>
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
