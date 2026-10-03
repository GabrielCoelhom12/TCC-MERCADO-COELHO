import { useState } from 'react';
import { api } from '../api';
import { Carregando, Modal, mensagemErro, useCarregar, useToast } from '../components/ui';
import { hora, horas } from '../format';
import type { Aplicacao, TurnoModelo } from '../types';

const APLICACAO: Record<Aplicacao, string> = {
  DIAS_UTEIS: 'Segunda a sábado',
  DOMINGOS_FERIADOS: 'Domingos e feriados',
  TODOS: 'Todos os dias',
};

type Form = { id?: number; nome: string; sigla: string; horaInicio: string; horaFim: string; intervaloMinutos: number; aplicacao: Aplicacao };

export default function Turnos() {
  const { dados, erro, recarregar } = useCarregar(() => api.get<TurnoModelo[]>('/turnos-modelo'));
  const [form, setForm] = useState<Form | null>(null);
  const toast = useToast();

  if (!dados) return <Carregando erro={erro} />;

  const salvar = async () => {
    if (!form) return;
    try {
      if (form.id) await api.put(`/turnos-modelo/${form.id}`, form);
      else await api.post('/turnos-modelo', form);
      setForm(null);
      recarregar();
    } catch (e) {
      toast('erro', mensagemErro(e));
    }
  };

  const excluir = async (t: TurnoModelo) => {
    if (!window.confirm(`Excluir o modelo ${t.nome}? Turnos já lançados não são afetados.`)) return;
    await api.del(`/turnos-modelo/${t.id}`);
    recarregar();
  };

  return (
    <div className="stack">
      <div className="cabecalho">
        <div>
          <h1>Modelos de turno</h1>
          <p>Horários padrão usados na montagem das escalas.</p>
        </div>
        <div className="spacer" />
        <button className="primario" onClick={() => setForm({ nome: '', sigla: '', horaInicio: '07:00', horaFim: '15:20', intervaloMinutos: 60, aplicacao: 'DIAS_UTEIS' })}>+ Novo modelo</button>
      </div>
      <div className="card tabela-wrap">
        <table>
          <thead><tr><th>Sigla</th><th>Nome</th><th>Horário</th><th className="num">Intervalo</th><th className="num">Horas trabalhadas</th><th>Aplicação</th><th></th></tr></thead>
          <tbody>
            {dados.map((t) => (
              <tr key={t.id}>
                <td><span className="badge laranja">{t.sigla}</span></td>
                <td>{t.nome}</td>
                <td>{hora(t.horaInicio)} – {hora(t.horaFim)}</td>
                <td className="num">{t.intervaloMinutos} min</td>
                <td className="num">{horas(t.horas)}</td>
                <td>{APLICACAO[t.aplicacao]}</td>
                <td className="num">
                  <button onClick={() => setForm({ ...t, horaInicio: hora(t.horaInicio), horaFim: hora(t.horaFim) })}>Editar</button>{' '}
                  <button className="perigo" onClick={() => excluir(t)}>Excluir</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <Modal titulo={form?.id ? 'Editar modelo' : 'Novo modelo de turno'} aberto={!!form} onFechar={() => setForm(null)}
        acoes={<><button onClick={() => setForm(null)}>Cancelar</button><button className="primario" onClick={salvar}>Salvar</button></>}>
        {form && (
          <div className="form-grid">
            <label className="campo">Nome<input value={form.nome} onChange={(e) => setForm({ ...form, nome: e.target.value })} /></label>
            <label className="campo">Sigla<input value={form.sigla} maxLength={5} onChange={(e) => setForm({ ...form, sigla: e.target.value.toUpperCase() })} /></label>
            <label className="campo">Início<input type="time" value={form.horaInicio} onChange={(e) => setForm({ ...form, horaInicio: e.target.value })} /></label>
            <label className="campo">Fim<input type="time" value={form.horaFim} onChange={(e) => setForm({ ...form, horaFim: e.target.value })} /></label>
            <label className="campo">Intervalo (min)<input type="number" min={0} value={form.intervaloMinutos} onChange={(e) => setForm({ ...form, intervaloMinutos: Number(e.target.value) })} /></label>
            <label className="campo">Aplicação
              <select value={form.aplicacao} onChange={(e) => setForm({ ...form, aplicacao: e.target.value as Aplicacao })}>
                {Object.entries(APLICACAO).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
              </select>
            </label>
          </div>
        )}
      </Modal>
    </div>
  );
}
