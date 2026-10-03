import { useEffect, useState } from 'react';
import { api } from '../api';
import { Carregando, mensagemErro, useCarregar, useToast } from '../components/ui';
import { dataLonga, isoDe, moeda, pct } from '../format';
import type { Funcionario, Setor, Simulacao } from '../types';

const VEREDITO = {
  APROVADA: { classe: 'ok', texto: 'Proposta dentro dos limites' },
  SUPERDIMENSIONADA: { classe: 'erro', texto: 'Equipe acima do limite — seria bloqueada' },
  SUBDIMENSIONADA: { classe: 'erro', texto: 'Equipe abaixo do mínimo' },
} as const;

const ROTULO_DIA: Record<string, string> = {
  SEGUNDA: 'segunda', TERCA: 'terça', QUARTA: 'quarta', QUINTA: 'quinta',
  SEXTA: 'sexta', SABADO: 'sábado', DOMINGO: 'domingo', FERIADO: 'feriado',
};

export default function Simulador() {
  const { dados, erro } = useCarregar(() => Promise.all([api.get<Setor[]>('/setores'), api.get<Funcionario[]>('/funcionarios')]));
  const [data, setData] = useState(() => {
    const d = new Date();
    d.setDate(d.getDate() + ((9 - d.getDay()) % 7 || 7)); // próxima terça: dia de movimento fraco
    return isoDe(d);
  });
  const [proposta, setProposta] = useState<Record<number, number>>({});
  const [resultado, setResultado] = useState<Simulacao | null>(null);
  const toast = useToast();

  // Proposta "empírica" inicial: todos os colaboradores ativos de cada setor trabalhando.
  useEffect(() => {
    if (!dados) return;
    const [setores, funcionarios] = dados;
    const p: Record<number, number> = {};
    setores.filter((s) => s.ativo).forEach((s) => {
      p[s.id] = funcionarios.filter((f) => f.ativo && f.setor.id === s.id).length;
    });
    setProposta(p);
  }, [dados]);

  const simular = async () => {
    try {
      setResultado(await api.post<Simulacao>('/motor/simular', { data, proposta }));
    } catch (e) {
      toast('erro', mensagemErro(e));
    }
  };

  useEffect(() => {
    if (Object.keys(proposta).length) simular();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [proposta, data]);

  if (!dados) return <Carregando erro={erro} />;
  const [setores] = dados;
  const cap = resultado?.capacidade;

  return (
    <div className="stack">
      <div className="cabecalho">
        <div>
          <h1>Simulador de custos</h1>
          <p>Compare a equipe que você planejou com a recomendada e veja a economia.</p>
        </div>
      </div>

      <div className="card">
        <div className="row" style={{ alignItems: 'flex-end' }}>
          <label className="campo">Data simulada<input type="date" value={data} onChange={(e) => setData(e.target.value)} /></label>
          {setores.filter((s) => s.ativo).map((s) => (
            <label className="campo" key={s.id} style={{ width: 140 }}>
              <span><span className="ponto" style={{ background: s.cor }} /> {s.nome}</span>
              <input type="number" min={0} value={proposta[s.id] ?? 0}
                onChange={(e) => setProposta({ ...proposta, [s.id]: Math.max(0, Number(e.target.value)) })} />
            </label>
          ))}
        </div>
      </div>

      {resultado && cap && (
        <>
          <div className="grid-kpi">
            <div className="card kpi">
              <div className="rotulo">Sua proposta</div>
              <div className="valor">{moeda(resultado.custoProposto)}</div>
              <div className="detalhe">{resultado.operadoresPropostos} operadores · {pct(resultado.custoProposto, cap.receitaProjetada)} da receita</div>
            </div>
            <div className="card kpi">
              <div className="rotulo">Recomendado</div>
              <div className="valor">{moeda(resultado.custoRecomendado)}</div>
              <div className="detalhe">{resultado.operadoresRecomendados} operadores · {pct(resultado.custoRecomendado, cap.receitaProjetada)} da receita</div>
            </div>
            <div className="card kpi destaque">
              <div className="rotulo">Economia no dia</div>
              <div className="valor">{moeda(resultado.economia)}</div>
              <div className="detalhe">≈ {moeda(resultado.economia * 4.33)} por mês se repetida semanalmente</div>
            </div>
          </div>

          <div className={`aviso ${VEREDITO[resultado.veredito].classe}`}>
            <strong>{VEREDITO[resultado.veredito].texto}</strong>
          </div>

          <div className="card">
            <h2>Por setor — {ROTULO_DIA[cap.tipoDia]} {dataLonga(cap.data)}{cap.feriado ? ` (${cap.feriado})` : ''}</h2>
            <div className="stack">
              <div className="resumo-linha">
                <div><span>Receita prevista</span><strong>{moeda(cap.receitaProjetada)}</strong></div>
                <div><span>Limite de custo</span><strong>{moeda(cap.tetoFolha)}</strong></div>
                <div><span>Equipe mínima</span><strong>{cap.lMin}</strong></div>
                <div><span>Equipe máxima</span><strong>{cap.lMax}</strong></div>
              </div>
              <div className="tabela-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>Setor</th><th className="num">Mínimo</th><th className="num">Proposto</th><th className="num">Recomendado</th>
                      <th className="num">Custo/operador</th><th className="num">Custo proposto</th><th className="num">Custo recomendado</th>
                    </tr>
                  </thead>
                  <tbody>
                    {resultado.setores.map((l) => (
                      <tr key={l.setorId}>
                        <td>{l.setor}</td>
                        <td className="num">{l.minimo}</td>
                        <td className="num">{l.proposto}</td>
                        <td className="num"><strong>{l.recomendado}</strong></td>
                        <td className="num">{moeda(l.custoOperador)}</td>
                        <td className="num">{moeda(l.custoProposto)}</td>
                        <td className="num">{moeda(l.custoRecomendado)}</td>
                      </tr>
                    ))}
                    <tr>
                      <td><strong>Total</strong></td>
                      <td className="num">{cap.lMin}</td>
                      <td className="num"><strong>{resultado.operadoresPropostos}</strong></td>
                      <td className="num"><strong>{resultado.operadoresRecomendados}</strong></td>
                      <td />
                      <td className="num"><strong>{moeda(resultado.custoProposto)}</strong></td>
                      <td className="num"><strong>{moeda(resultado.custoRecomendado)}</strong></td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
