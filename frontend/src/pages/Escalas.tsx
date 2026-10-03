import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../api';
import { Carregando, Modal, mensagemErro, useCarregar, useToast } from '../components/ui';
import { dataLonga, isoDe } from '../format';
import type { Escala, Geracao } from '../types';

const proximaSegunda = () => {
  const d = new Date();
  d.setDate(d.getDate() + (((8 - d.getDay()) % 7) || 7));
  return d;
};

export default function Escalas() {
  const { dados, erro, recarregar } = useCarregar(() => api.get<Escala[]>('/escalas'));
  const [aberto, setAberto] = useState(false);
  const [form, setForm] = useState({ nome: '', dataInicio: '', dataFim: '', observacao: '', gerar: true });
  const [salvando, setSalvando] = useState(false);
  const toast = useToast();
  const navegar = useNavigate();

  const abrir = () => {
    // Sugere a semana seguinte à última escala cadastrada (ou a próxima semana).
    const ultima = dados?.[0];
    const inicio = ultima ? new Date(ultima.dataFim + 'T00:00') : proximaSegunda();
    if (ultima) inicio.setDate(inicio.getDate() + 1);
    const fim = new Date(inicio);
    fim.setDate(fim.getDate() + 6);
    setForm({ nome: '', dataInicio: isoDe(inicio), dataFim: isoDe(fim), observacao: '', gerar: true });
    setAberto(true);
  };

  const criar = async () => {
    setSalvando(true);
    try {
      const e = await api.post<Escala>('/escalas', form);
      if (form.gerar) {
        const g = await api.post<Geracao>(`/escalas/${e.id}/gerar`);
        toast('ok', `Escala gerada: ${g.turnosCriados} turnos${g.deficits.length ? `, ${g.deficits.length} déficit(s)` : ''}.`);
      }
      navegar(`/escalas/${e.id}`);
    } catch (err) {
      toast('erro', mensagemErro(err));
    } finally {
      setSalvando(false);
    }
  };

  const excluir = async (e: Escala) => {
    if (!window.confirm(`Excluir "${e.nome}" e todos os seus turnos?`)) return;
    try {
      await api.del(`/escalas/${e.id}`);
      recarregar();
    } catch (err) {
      toast('erro', mensagemErro(err));
    }
  };

  return (
    <div className="stack">
      <div className="cabecalho">
        <div>
          <h1>Escalas</h1>
          <p>Escalas por período, com validação de custos e regras trabalhistas.</p>
        </div>
        <div className="spacer" />
        <button className="primario" onClick={abrir}>+ Nova escala</button>
      </div>

      {!dados ? <Carregando erro={erro} /> : (
        <div className="card">
          {dados.length === 0 ? <p className="muted">Nenhuma escala cadastrada.</p> : (
            <table>
              <thead>
                <tr><th>Escala</th><th>Período</th><th>Status</th><th></th></tr>
              </thead>
              <tbody>
                {dados.map((e) => (
                  <tr key={e.id}>
                    <td><Link to={`/escalas/${e.id}`}><strong>{e.nome}</strong></Link>
                      {e.observacao && <div className="small muted">{e.observacao}</div>}</td>
                    <td>{dataLonga(e.dataInicio)} a {dataLonga(e.dataFim)}</td>
                    <td>
                      {e.status === 'APROVADA'
                        ? <span className="badge ok">Aprovada{e.aprovadaPor ? ` por ${e.aprovadaPor}` : ''}</span>
                        : <span className="badge laranja">Rascunho</span>}
                      {e.geradaAutomaticamente && <span className="badge neutro" title="Criada pelo sistema a partir da previsão de movimento" style={{ marginLeft: 6 }}>Automática</span>}
                    </td>
                    <td className="num">
                      <Link className="btn" to={`/escalas/${e.id}`}>Abrir matriz</Link>{' '}
                      {e.status === 'RASCUNHO' && <button className="perigo" onClick={() => excluir(e)}>Excluir</button>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}

      <Modal titulo="Nova escala" aberto={aberto} onFechar={() => setAberto(false)}
        acoes={<>
          <button onClick={() => setAberto(false)}>Cancelar</button>
          <button className="primario" disabled={salvando || !form.dataInicio || !form.dataFim} onClick={criar}>
            {salvando ? 'Gerando…' : 'Criar'}
          </button>
        </>}>
        <div className="stack">
          <div className="form-grid">
            <label className="campo">Início<input type="date" value={form.dataInicio} onChange={(e) => setForm({ ...form, dataInicio: e.target.value })} /></label>
            <label className="campo">Fim<input type="date" value={form.dataFim} onChange={(e) => setForm({ ...form, dataFim: e.target.value })} /></label>
          </div>
          <label className="campo">Nome (opcional)<input value={form.nome} onChange={(e) => setForm({ ...form, nome: e.target.value })} placeholder="Ex.: Semana do feriado" /></label>
          <label className="campo">Observação<input value={form.observacao} onChange={(e) => setForm({ ...form, observacao: e.target.value })} /></label>
          <label className="row" style={{ fontWeight: 600 }}>
            <input type="checkbox" checked={form.gerar} onChange={(e) => setForm({ ...form, gerar: e.target.checked })} />
            Gerar sugestão automática
          </label>
        </div>
      </Modal>
    </div>
  );
}
