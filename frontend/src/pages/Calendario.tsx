import { useEffect, useState } from 'react';
import { api } from '../api';
import { Carregando, mensagemErro, useCarregar, useToast } from '../components/ui';
import { TIPOS_DIA, dataLonga, moeda, paraData } from '../format';
import type { Feriado, Projecoes, TipoDia } from '../types';

const SEMANA = ['Dom', 'Seg', 'Ter', 'Qua', 'Qui', 'Sex', 'Sáb'];

export default function Calendario() {
  const { dados, erro, recarregar } = useCarregar(() => Promise.all([api.get<Projecoes>('/projecoes'), api.get<Feriado[]>('/feriados')]));
  const [padrao, setPadrao] = useState<Record<TipoDia, number> | null>(null);
  const [novaProj, setNovaProj] = useState({ data: '', valor: 0, observacao: '' });
  const [novoFeriado, setNovoFeriado] = useState({ data: '', descricao: '', abrangencia: 'MUNICIPAL', fatorCusto: '' });
  const [ano, setAno] = useState(new Date().getFullYear() + 1);
  const toast = useToast();

  useEffect(() => {
    if (dados) setPadrao({ ...dados[0].padrao });
  }, [dados]);

  if (!dados || !padrao) return <Carregando erro={erro} />;
  const [projecoes, feriados] = dados;
  const hoje = new Date().toISOString().slice(0, 10);

  const executar = async (fn: () => Promise<unknown>, ok?: string) => {
    try {
      await fn();
      if (ok) toast('ok', ok);
      recarregar();
    } catch (e) {
      toast('erro', mensagemErro(e));
    }
  };

  return (
    <div className="stack">
      <div className="cabecalho">
        <div>
          <h1>Feriados e projeção de receita</h1>
          <p>Receita prevista por dia e calendário de feriados.</p>
        </div>
      </div>

      <div className="card">
        <div className="row" style={{ marginBottom: 12 }}>
          <h2>Projeção de faturamento padrão por dia</h2>
          <div className="spacer" />
          <button className="primario" onClick={() => executar(() => api.put('/projecoes/padrao', padrao), 'Projeções salvas.')}>Salvar</button>
        </div>
        <div className="form-grid">
          {TIPOS_DIA.map((t) => (
            <label className="campo" key={t.tipo}>{t.rotulo}
              <input type="number" step="100" value={padrao[t.tipo] ?? 0} onChange={(e) => setPadrao({ ...padrao, [t.tipo]: Number(e.target.value) })} />
              <span className="ajuda">{moeda(padrao[t.tipo])}</span>
            </label>
          ))}
        </div>
      </div>

      <div className="card">
        <h2>Projeções específicas (início de mês, vésperas, promoções)</h2>
        <p className="ajuda">Quando existe, substitui a projeção padrão daquela data.</p>
        <div className="tabela-wrap">
          <table>
            <thead><tr><th>Data</th><th className="num">Receita projetada</th><th>Motivo</th><th></th></tr></thead>
            <tbody>
              {projecoes.especificas.filter((p) => p.data >= hoje).map((p) => (
                <tr key={p.id}>
                  <td>{SEMANA[paraData(p.data).getDay()]} {dataLonga(p.data)}</td>
                  <td className="num">{moeda(p.valor)}</td>
                  <td>{p.observacao}</td>
                  <td className="num"><button className="perigo" onClick={() => executar(() => api.del(`/projecoes/datas/${p.id}`))}>Remover</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div className="row" style={{ marginTop: 12, alignItems: 'flex-end' }}>
          <label className="campo">Data<input type="date" value={novaProj.data} onChange={(e) => setNovaProj({ ...novaProj, data: e.target.value })} /></label>
          <label className="campo">Receita (R$)<input type="number" step="100" value={novaProj.valor} onChange={(e) => setNovaProj({ ...novaProj, valor: Number(e.target.value) })} /></label>
          <label className="campo" style={{ flex: 1 }}>Motivo<input value={novaProj.observacao} onChange={(e) => setNovaProj({ ...novaProj, observacao: e.target.value })} /></label>
          <button className="primario" disabled={!novaProj.data} onClick={() => executar(async () => {
            await api.post('/projecoes/datas', novaProj);
            setNovaProj({ data: '', valor: 0, observacao: '' });
          })}>Adicionar</button>
        </div>
      </div>

      <div className="card">
        <div className="row" style={{ marginBottom: 12 }}>
          <h2>Feriados</h2>
          <div className="spacer" />
          <input type="number" value={ano} onChange={(e) => setAno(Number(e.target.value))} style={{ width: 90 }} aria-label="Ano" />
          <button onClick={() => executar(async () => {
            const r = await api.post<{ importados: number }>(`/feriados/importar/${ano}`);
            toast('ok', `${r.importados} feriado(s) importado(s).`);
          })}>Importar nacionais + Joinville</button>
        </div>
        <div className="tabela-wrap">
          <table>
            <thead><tr><th>Data</th><th>Descrição</th><th>Abrangência</th><th className="num">Acréscimo de custo</th><th></th></tr></thead>
            <tbody>
              {feriados.filter((f) => f.data >= hoje).map((f) => (
                <tr key={f.id}>
                  <td>{SEMANA[paraData(f.data).getDay()]} {dataLonga(f.data)}</td>
                  <td>{f.descricao}</td>
                  <td><span className="badge neutro">{f.abrangencia.toLowerCase()}</span></td>
                  <td className="num">{f.fatorCusto ? `${f.fatorCusto}×` : <span className="muted">padrão</span>}</td>
                  <td className="num"><button className="perigo" onClick={() => executar(() => api.del(`/feriados/${f.id}`))}>Remover</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div className="row" style={{ marginTop: 12, alignItems: 'flex-end' }}>
          <label className="campo">Data<input type="date" value={novoFeriado.data} onChange={(e) => setNovoFeriado({ ...novoFeriado, data: e.target.value })} /></label>
          <label className="campo" style={{ flex: 1 }}>Descrição<input value={novoFeriado.descricao} onChange={(e) => setNovoFeriado({ ...novoFeriado, descricao: e.target.value })} /></label>
          <label className="campo">Abrangência
            <select value={novoFeriado.abrangencia} onChange={(e) => setNovoFeriado({ ...novoFeriado, abrangencia: e.target.value })}>
              <option value="NACIONAL">Nacional</option><option value="ESTADUAL">Estadual</option><option value="MUNICIPAL">Municipal</option>
            </select>
          </label>
          <label className="campo">Acréscimo de custo<input type="number" step="0.1" placeholder="padrão" value={novoFeriado.fatorCusto} onChange={(e) => setNovoFeriado({ ...novoFeriado, fatorCusto: e.target.value })} style={{ width: 90 }} /></label>
          <button className="primario" disabled={!novoFeriado.data || !novoFeriado.descricao} onClick={() => executar(async () => {
            await api.post('/feriados', { ...novoFeriado, fatorCusto: novoFeriado.fatorCusto ? Number(novoFeriado.fatorCusto) : null });
            setNovoFeriado({ data: '', descricao: '', abrangencia: 'MUNICIPAL', fatorCusto: '' });
          })}>Adicionar</button>
        </div>
      </div>
    </div>
  );
}
