import { useState } from 'react';
import { api } from '../api';
import Avatar from '../components/Avatar';
import SeletorFoto, { aplicarFoto, fotoInicial, type EstadoFoto } from '../components/SeletorFoto';
import { Carregando, Modal, mensagemErro, useCarregar, useToast } from '../components/ui';
import { DIAS_SEMANA, dataLonga, moeda } from '../format';
import type { Ausencia, DiaSemana, Funcionario, MotivoAusencia, Setor } from '../types';

type Form = {
  id?: number;
  nome: string;
  matricula: string;
  cargo: string;
  setorId: number;
  salarioMensal: number;
  cargaHorariaMensal: number;
  percentualEncargos: number;
  ativo: boolean;
  diasDisponiveis: DiaSemana[];
  foto: EstadoFoto;
};

const MOTIVOS: { valor: MotivoAusencia; rotulo: string }[] = [
  { valor: 'FERIAS', rotulo: 'Férias' },
  { valor: 'ATESTADO', rotulo: 'Atestado' },
  { valor: 'FOLGA_ACORDADA', rotulo: 'Folga acordada' },
  { valor: 'OUTRO', rotulo: 'Outro' },
];

export default function Funcionarios() {
  const { dados, erro, recarregar } = useCarregar(() =>
    Promise.all([api.get<Funcionario[]>('/funcionarios'), api.get<Setor[]>('/setores'), api.get<Ausencia[]>('/ausencias')]));
  const [form, setForm] = useState<Form | null>(null);
  const [ausenciasDe, setAusenciasDe] = useState<Funcionario | null>(null);
  const [filtroSetor, setFiltroSetor] = useState<number | 0>(0);
  const toast = useToast();

  if (!dados) return <Carregando erro={erro} />;
  const [funcionarios, setores, ausencias] = dados;
  const visiveis = funcionarios.filter((f) => !filtroSetor || f.setor.id === filtroSetor);

  const novo = () => setForm({
    nome: '', matricula: '', cargo: '', setorId: setores[0]?.id ?? 0, salarioMensal: 1950,
    cargaHorariaMensal: 220, percentualEncargos: 38, ativo: true, diasDisponiveis: DIAS_SEMANA.map((d) => d.dia),
    foto: fotoInicial(),
  });

  const editar = (f: Funcionario) => setForm({
    id: f.id, nome: f.nome, matricula: f.matricula ?? '', cargo: f.cargo ?? '', setorId: f.setor.id,
    salarioMensal: f.salarioMensal, cargaHorariaMensal: f.cargaHorariaMensal, percentualEncargos: f.percentualEncargos,
    ativo: f.ativo, diasDisponiveis: f.diasDisponiveis, foto: fotoInicial(f.foto),
  });

  const salvar = async () => {
    if (!form) return;
    try {
      const { foto, ...dadosColaborador } = form;
      const salvo = form.id
        ? await api.put<Funcionario>(`/funcionarios/${form.id}`, dadosColaborador)
        : await api.post<Funcionario>('/funcionarios', dadosColaborador);
      await aplicarFoto(`/funcionarios/${salvo.id}`, foto);
      setForm(null);
      toast('ok', 'Colaborador salvo.');
      recarregar();
    } catch (e) {
      toast('erro', mensagemErro(e));
    }
  };

  const excluir = async (f: Funcionario) => {
    if (!window.confirm(`Excluir ${f.nome}? Se já houver escalas com este colaborador, ele será apenas desativado.`)) return;
    try {
      const r = await api.del<{ resultado: string }>(`/funcionarios/${f.id}`);
      toast('ok', r.resultado === 'desativado' ? 'Colaborador desativado (possui histórico de escalas).' : 'Colaborador excluído.');
      recarregar();
    } catch (e) {
      toast('erro', mensagemErro(e));
    }
  };

  const custoHoraPrevia = form
    ? (form.salarioMensal / (form.cargaHorariaMensal || 1)) * (1 + form.percentualEncargos / 100)
    : 0;

  return (
    <div className="stack">
      <div className="cabecalho">
        <div>
          <h1>Colaboradores</h1>
          <p>Equipe, custos com encargos e disponibilidade.</p>
        </div>
        <div className="spacer" />
        <select value={filtroSetor} onChange={(e) => setFiltroSetor(Number(e.target.value))}>
          <option value={0}>Todos os setores</option>
          {setores.map((s) => <option key={s.id} value={s.id}>{s.nome}</option>)}
        </select>
        <button className="primario" onClick={novo} disabled={!setores.length}>+ Novo colaborador</button>
      </div>

      <div className="card tabela-wrap">
        <table>
          <thead>
            <tr>
              <th>Nome</th><th>Setor</th><th className="num">Salário</th><th className="num">Encargos</th>
              <th className="num">Custo/hora</th><th>Disponibilidade</th><th>Ausências</th><th></th>
            </tr>
          </thead>
          <tbody>
            {visiveis.map((f) => {
              const aus = ausencias.filter((a) => a.funcionarioId === f.id);
              return (
                <tr key={f.id} style={{ opacity: f.ativo ? 1 : 0.55 }}>
                  <td>
                    <div className="row" style={{ gap: 10, flexWrap: 'nowrap' }}>
                      <Avatar nome={f.nome} foto={f.foto} tamanho={34} className="avatar-linha" />
                      <div>
                        <strong>{f.nome}</strong>{!f.ativo && <span className="badge neutro"> inativo</span>}
                        <div className="small muted">{f.cargo}{f.matricula ? ` · ${f.matricula}` : ''}</div>
                      </div>
                    </div>
                  </td>
                  <td><span className="ponto" style={{ background: f.setor.cor }} /> {f.setor.nome}</td>
                  <td className="num">{moeda(f.salarioMensal)}</td>
                  <td className="num">{f.percentualEncargos}%</td>
                  <td className="num"><strong>{moeda(f.custoHora)}</strong></td>
                  <td className="small">
                    {f.diasDisponiveis.length === 7 ? 'Todos os dias'
                      : DIAS_SEMANA.filter((d) => f.diasDisponiveis.includes(d.dia)).map((d) => d.rotulo).join(', ')}
                  </td>
                  <td>
                    <button className="link" onClick={() => setAusenciasDe(f)}>
                      {aus.length ? `${aus.length} registro(s)` : 'Registrar'}
                    </button>
                  </td>
                  <td className="num">
                    <button onClick={() => editar(f)}>Editar</button>{' '}
                    <button className="perigo" onClick={() => excluir(f)}>Excluir</button>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      <Modal titulo={form?.id ? 'Editar colaborador' : 'Novo colaborador'} aberto={!!form} onFechar={() => setForm(null)}
        acoes={<><button onClick={() => setForm(null)}>Cancelar</button><button className="primario" onClick={salvar}>Salvar</button></>}>
        {form && (
          <div className="stack">
            <SeletorFoto nome={form.nome} foto={form.foto} onMudar={(foto) => setForm({ ...form, foto })} />
            <label className="campo">Nome<input value={form.nome} onChange={(e) => setForm({ ...form, nome: e.target.value })} /></label>
            <div className="form-grid">
              <label className="campo">Matrícula<input value={form.matricula} onChange={(e) => setForm({ ...form, matricula: e.target.value })} /></label>
              <label className="campo">Cargo<input value={form.cargo} onChange={(e) => setForm({ ...form, cargo: e.target.value })} /></label>
              <label className="campo">Setor
                <select value={form.setorId} onChange={(e) => setForm({ ...form, setorId: Number(e.target.value) })}>
                  {setores.map((s) => <option key={s.id} value={s.id}>{s.nome}</option>)}
                </select>
              </label>
            </div>
            <div className="form-grid">
              <label className="campo">Salário mensal (R$)<input type="number" step="0.01" value={form.salarioMensal} onChange={(e) => setForm({ ...form, salarioMensal: Number(e.target.value) })} /></label>
              <label className="campo">Carga mensal (h)<input type="number" value={form.cargaHorariaMensal} onChange={(e) => setForm({ ...form, cargaHorariaMensal: Number(e.target.value) })} /></label>
              <label className="campo">Encargos (%)<input type="number" step="0.01" value={form.percentualEncargos} onChange={(e) => setForm({ ...form, percentualEncargos: Number(e.target.value) })} />
                <span className="ajuda">INSS patronal, FGTS, provisões de férias e 13º…</span></label>
            </div>
            <div className="aviso info">Custo por hora com encargos: <strong>{moeda(custoHoraPrevia)}</strong></div>
            <div className="campo">
              <span className="campo">Dias disponíveis</span>
              <div className="dias-check">
                {DIAS_SEMANA.map((d) => {
                  const marcado = form.diasDisponiveis.includes(d.dia);
                  return (
                    <label key={d.dia} className={marcado ? 'marcado' : ''}>
                      <input type="checkbox" checked={marcado} onChange={() => setForm({
                        ...form,
                        diasDisponiveis: marcado ? form.diasDisponiveis.filter((x) => x !== d.dia) : [...form.diasDisponiveis, d.dia],
                      })} />
                      {d.rotulo}
                    </label>
                  );
                })}
              </div>
            </div>
            <label className="row" style={{ fontWeight: 600 }}>
              <input type="checkbox" checked={form.ativo} onChange={(e) => setForm({ ...form, ativo: e.target.checked })} /> Ativo
            </label>
          </div>
        )}
      </Modal>

      <ModalAusencias funcionario={ausenciasDe} ausencias={ausencias.filter((a) => a.funcionarioId === ausenciasDe?.id)}
        onFechar={() => setAusenciasDe(null)} onMudou={recarregar} />
    </div>
  );
}

function ModalAusencias({ funcionario, ausencias, onFechar, onMudou }: {
  funcionario: Funcionario | null;
  ausencias: Ausencia[];
  onFechar: () => void;
  onMudou: () => void;
}) {
  const [form, setForm] = useState({ inicio: '', fim: '', motivo: 'FERIAS' as MotivoAusencia, observacao: '' });
  const toast = useToast();

  const adicionar = async () => {
    try {
      await api.post(`/funcionarios/${funcionario!.id}/ausencias`, form);
      setForm({ inicio: '', fim: '', motivo: 'FERIAS', observacao: '' });
      onMudou();
    } catch (e) {
      toast('erro', mensagemErro(e));
    }
  };

  const remover = async (id: number) => {
    await api.del(`/ausencias/${id}`);
    onMudou();
  };

  return (
    <Modal titulo={`Ausências — ${funcionario?.nome ?? ''}`} aberto={!!funcionario} onFechar={onFechar}
      acoes={<button onClick={onFechar}>Fechar</button>}>
      <div className="stack">
        {ausencias.length === 0 ? <p className="muted">Nenhuma ausência registrada.</p> : (
          <table>
            <tbody>
              {ausencias.map((a) => (
                <tr key={a.id}>
                  <td>{MOTIVOS.find((m) => m.valor === a.motivo)?.rotulo}</td>
                  <td>{dataLonga(a.inicio)} a {dataLonga(a.fim)}</td>
                  <td className="num"><button className="perigo" onClick={() => remover(a.id)}>Remover</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        <div className="form-grid">
          <label className="campo">Início<input type="date" value={form.inicio} onChange={(e) => setForm({ ...form, inicio: e.target.value })} /></label>
          <label className="campo">Fim<input type="date" value={form.fim} onChange={(e) => setForm({ ...form, fim: e.target.value })} /></label>
          <label className="campo">Motivo
            <select value={form.motivo} onChange={(e) => setForm({ ...form, motivo: e.target.value as MotivoAusencia })}>
              {MOTIVOS.map((m) => <option key={m.valor} value={m.valor}>{m.rotulo}</option>)}
            </select>
          </label>
        </div>
        <button className="primario" disabled={!form.inicio || !form.fim} onClick={adicionar}>+ Registrar ausência</button>
      </div>
    </Modal>
  );
}
