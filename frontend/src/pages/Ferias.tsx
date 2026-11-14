import { useEffect, useState } from 'react';
import { Palmtree, Search } from 'lucide-react';
import { api } from '../api';
import Avatar from '../components/Avatar';
import { Carregando, Modal, mensagemErro, useCarregar, useToast } from '../components/ui';
import { DIAS_SEMANA, dataCurta, dataLonga, diaSemanaDe, hora, isoDe, paraData } from '../format';
import type { Ausencia, Funcionario } from '../types';

/** Turno já escalado no período e quem o assume (sem substituto: o turno é retirado). */
interface CoberturaTurno {
  turnoId: number;
  escalaId: number;
  escalaNome: string;
  data: string;
  horaInicio: string;
  horaFim: string;
  substitutoId: number | null;
  substitutoNome: string | null;
  substitutoSetor: string | null;
}

/** Prévia devolvida por GET /ferias/analise. */
interface Analise {
  dias: number;
  retorno: string | null;
  erros: string[];
  alertas: string[];
  valida: boolean;
  coberturas: CoberturaTurno[];
}

type Form = { id?: number; funcionarioId: number | ''; inicio: string; fim: string; observacao: string };

const DURACOES = [30, 20, 15, 10, 5];
const DIA_MS = 86400000;
const entre = (de: string, ate: string) => Math.round((paraData(ate).getTime() - paraData(de).getTime()) / DIA_MS);
const somarDias = (iso: string, n: number) => isoDe(new Date(paraData(iso).getTime() + n * DIA_MS));
const dias = (n: number) => (n === 1 ? '1 dia' : `${n} dias`);
const rotuloDia = (d: string) => DIAS_SEMANA.find((x) => x.dia === diaSemanaDe(d))?.rotulo;
const turnos = (n: number, um: string, varios: string) => (n === 1 ? `1 turno ${um}` : `${n} turnos ${varios}`);

/** "Férias de Ana registradas · 2 turnos repassados · 1 turno retirado sem substituto." */
function mensagemRegistro(nome: string | undefined, editando: boolean, coberturas: CoberturaTurno[]) {
  const repassados = coberturas.filter((c) => c.substitutoId !== null).length;
  const retirados = coberturas.length - repassados;
  const partes = [editando ? 'Férias atualizadas' : `Férias de ${nome} registradas`];
  if (repassados) partes.push(turnos(repassados, 'repassado', 'repassados'));
  if (retirados) partes.push(turnos(retirados, 'retirado sem substituto', 'retirados sem substituto'));
  return `${partes.join(' · ')}.`;
}

export default function Ferias() {
  const { dados, erro, recarregar } = useCarregar(() =>
    Promise.all([api.get<Ausencia[]>('/ferias'), api.get<Funcionario[]>('/funcionarios')]));
  const [busca, setBusca] = useState('');
  const [form, setForm] = useState<Form | null>(null);

  if (!dados) return <Carregando erro={erro} />;
  const [ferias, funcionarios] = dados;
  const hoje = isoDe(new Date());
  const porId = new Map(funcionarios.map((f) => [f.id, f]));
  const termo = busca.trim().toLowerCase();
  const visiveis = ferias.filter((f) => !termo || f.funcionarioNome.toLowerCase().includes(termo));

  const andamento = visiveis.filter((f) => f.inicio <= hoje && f.fim >= hoje).sort((a, b) => a.fim.localeCompare(b.fim));
  const programadas = visiveis.filter((f) => f.inicio > hoje).sort((a, b) => a.inicio.localeCompare(b.inicio));
  const encerradas = visiveis.filter((f) => f.fim < hoje).sort((a, b) => b.fim.localeCompare(a.fim));

  const editar = (f: Ausencia) =>
    setForm({ id: f.id, funcionarioId: f.funcionarioId, inicio: f.inicio, fim: f.fim, observacao: f.observacao ?? '' });

  return (
    <div className="stack">
      <div className="cabecalho">
        <div>
          <h1>Férias</h1>
          <p>Programe as férias da equipe dentro das regras da CLT e veja o impacto na escala.</p>
        </div>
        <div className="spacer" />
        <label className="busca">
          <Search size={16} aria-hidden />
          <input placeholder="Buscar colaborador" value={busca} onChange={(e) => setBusca(e.target.value)} aria-label="Buscar colaborador" />
        </label>
        <button className="primario" onClick={() => setForm({ funcionarioId: '', inicio: '', fim: '', observacao: '' })}>
          <Palmtree size={16} aria-hidden />Registrar férias
        </button>
      </div>

      <div className="grid-kpi">
        <div className="card kpi destaque">
          <div className="rotulo">De férias hoje</div>
          <div className="valor">{ferias.filter((f) => f.inicio <= hoje && f.fim >= hoje).length}</div>
          <div className="detalhe">colaboradores fora da escala</div>
        </div>
        <div className="card kpi">
          <div className="rotulo">Começam em 30 dias</div>
          <div className="valor">{ferias.filter((f) => f.inicio > hoje && entre(hoje, f.inicio) <= 30).length}</div>
          <div className="detalhe">para considerar nas próximas escalas</div>
        </div>
        <div className="card kpi">
          <div className="rotulo">Voltam em 7 dias</div>
          <div className="valor">{ferias.filter((f) => f.fim >= hoje && entre(hoje, f.fim) < 7).length}</div>
          <div className="detalhe">retornos desta semana</div>
        </div>
      </div>

      <Secao titulo="Em andamento" itens={andamento} porId={porId} hoje={hoje} onEditar={editar}
        vazio="Ninguém de férias hoje." />
      <Secao titulo="Programadas" itens={programadas} porId={porId} hoje={hoje} onEditar={editar}
        vazio="Nenhuma férias programada." />
      {encerradas.length > 0 && (
        <details className="card encerradas">
          <summary>Encerradas ({encerradas.length})</summary>
          <TabelaFerias itens={encerradas} porId={porId} hoje={hoje} onEditar={editar} />
        </details>
      )}

      {form && (
        <ModalFerias form={form} setForm={setForm} funcionarios={funcionarios}
          onSalvo={() => {
            setForm(null);
            recarregar();
          }} />
      )}
    </div>
  );
}

type Lista = { itens: Ausencia[]; porId: Map<number, Funcionario>; hoje: string; onEditar: (f: Ausencia) => void };

function Secao({ titulo, vazio, ...lista }: Lista & { titulo: string; vazio: string }) {
  const id = `ferias-${titulo.toLowerCase().replace(/\s+/g, '-')}`;
  return (
    <section className="card" aria-labelledby={id}>
      <h2 id={id}>{titulo} <span className="muted">({lista.itens.length})</span></h2>
      {lista.itens.length === 0 ? <p className="muted">{vazio}</p> : <TabelaFerias {...lista} />}
    </section>
  );
}

function TabelaFerias({ itens, porId, hoje, onEditar }: Lista) {
  return (
    <div className="tabela-wrap">
      <table className="tabela-ferias">
        <thead>
          <tr><th>Colaborador</th><th>Período</th><th className="num">Duração</th><th>Situação</th><th></th></tr>
        </thead>
        <tbody>
          {itens.map((f) => {
            const pessoa = porId.get(f.funcionarioId);
            return (
              <tr key={f.id}>
                <td>
                  <div className="row" style={{ gap: 10, flexWrap: 'nowrap' }}>
                    <Avatar nome={f.funcionarioNome} foto={pessoa?.foto} tamanho={32} className="avatar-linha" />
                    <div>
                      <strong>{f.funcionarioNome}</strong>
                      {pessoa && (
                        <div className="small muted">
                          <span className="ponto-setor" style={{ background: pessoa.setor.cor }} aria-hidden />{pessoa.setor.nome}
                        </div>
                      )}
                    </div>
                  </div>
                </td>
                <td>{dataCurta(f.inicio)} – {dataLonga(f.fim)}</td>
                <td className="num">{dias(entre(f.inicio, f.fim) + 1)}</td>
                <td><Situacao f={f} hoje={hoje} /></td>
                <td className="num">
                  <button className="link" onClick={() => onEditar(f)} aria-label={`Editar férias de ${f.funcionarioNome}`}>Editar</button>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

function Situacao({ f, hoje }: { f: Ausencia; hoje: string }) {
  if (f.fim < hoje) return <span className="badge neutro">voltou em {dataCurta(somarDias(f.fim, 1))}</span>;
  if (f.inicio <= hoje) return <span className="badge laranja">volta em {dataCurta(somarDias(f.fim, 1))}</span>;
  const faltam = entre(hoje, f.inicio);
  return <span className="badge ok">{faltam === 1 ? 'começa amanhã' : `começa em ${faltam} dias`}</span>;
}

function ModalFerias({ form, setForm, funcionarios, onSalvo }: {
  form: Form;
  setForm: (f: Form | null) => void;
  funcionarios: Funcionario[];
  onSalvo: () => void;
}) {
  const toast = useToast();
  const [analise, setAnalise] = useState<Analise | null>(null);
  const [salvando, setSalvando] = useState(false);
  const completo = form.funcionarioId !== '' && !!form.inicio && !!form.fim;

  // Prévia ao vivo: dias, retorno, regras da CLT e impacto na escala.
  useEffect(() => {
    setAnalise(null);
    if (!completo) return;
    let ativo = true;
    const ignorar = form.id ? `&ignorar=${form.id}` : '';
    api.get<Analise>(`/ferias/analise?funcionarioId=${form.funcionarioId}&inicio=${form.inicio}&fim=${form.fim}${ignorar}`)
      .then((a) => ativo && setAnalise(a))
      .catch((e) => ativo && setAnalise({ dias: 0, retorno: null, erros: [mensagemErro(e)], alertas: [], valida: false, coberturas: [] }));
    return () => {
      ativo = false;
    };
  }, [completo, form.id, form.funcionarioId, form.inicio, form.fim]);

  const nome = funcionarios.find((f) => f.id === form.funcionarioId)?.nome;
  const ativos = funcionarios.filter((f) => f.ativo || f.id === form.funcionarioId);
  const setores = [...new Set(ativos.map((f) => f.setor.nome))].sort();

  const salvar = async () => {
    setSalvando(true);
    try {
      const corpo = { funcionarioId: form.funcionarioId, inicio: form.inicio, fim: form.fim, observacao: form.observacao };
      const registro = form.id
        ? await api.put<{ coberturas: CoberturaTurno[] }>(`/ferias/${form.id}`, corpo)
        : await api.post<{ coberturas: CoberturaTurno[] }>('/ferias', corpo);
      toast('ok', mensagemRegistro(nome, !!form.id, registro.coberturas));
      onSalvo();
    } catch (e) {
      toast('erro', mensagemErro(e));
    } finally {
      setSalvando(false);
    }
  };

  const remover = async () => {
    try {
      await api.del(`/ferias/${form.id}`);
      toast('ok', 'Férias removidas.');
      onSalvo();
    } catch (e) {
      toast('erro', mensagemErro(e));
    }
  };

  return (
    <Modal titulo={form.id ? 'Editar férias' : 'Registrar férias'} aberto onFechar={() => setForm(null)}
      acoes={(
        <>
          {form.id && <button className="perigo" onClick={remover}>Remover</button>}
          <div className="spacer" />
          <button onClick={() => setForm(null)}>Cancelar</button>
          <button className="primario" onClick={salvar} disabled={!analise?.valida || salvando}>Salvar</button>
        </>
      )}>
      <div className="stack">
        <label className="campo">Colaborador
          <select value={form.funcionarioId} onChange={(e) => setForm({ ...form, funcionarioId: e.target.value ? Number(e.target.value) : '' })}>
            <option value="">Selecione…</option>
            {setores.map((s) => (
              <optgroup key={s} label={s}>
                {ativos.filter((f) => f.setor.nome === s).map((f) => <option key={f.id} value={f.id}>{f.nome}</option>)}
              </optgroup>
            ))}
          </select>
        </label>
        <div className="form-grid">
          <label className="campo">Início
            <input type="date" value={form.inicio} onChange={(e) => setForm({ ...form, inicio: e.target.value })} />
          </label>
          <label className="campo">Fim
            <input type="date" value={form.fim} min={form.inicio || undefined} onChange={(e) => setForm({ ...form, fim: e.target.value })} />
          </label>
        </div>
        <div className="duracoes" role="group" aria-label="Duração">
          {DURACOES.map((n) => {
            const fim = form.inicio ? somarDias(form.inicio, n - 1) : '';
            return (
              <button key={n} type="button" disabled={!form.inicio} className={fim && fim === form.fim ? 'ativa' : undefined}
                onClick={() => setForm({ ...form, fim })}>
                {dias(n)}
              </button>
            );
          })}
        </div>
        <label className="campo">Observação
          <input value={form.observacao} onChange={(e) => setForm({ ...form, observacao: e.target.value })} placeholder="Opcional" />
        </label>

        {completo && !analise && <p className="muted small">Verificando o período…</p>}
        {analise && (
          <div className="previa-ferias" aria-live="polite">
            {analise.retorno && <strong>{dias(analise.dias)} · retorno em {dataLonga(analise.retorno)}</strong>}
            {analise.erros.map((e) => <div key={e} className="aviso erro">{e}</div>)}
            {analise.alertas.map((a) => <div key={a} className="aviso alerta">{a}</div>)}
            {analise.valida && analise.alertas.length === 0 && analise.coberturas.length === 0 && (
              <div className="aviso ok">Período dentro das regras e sem conflitos na escala.</div>
            )}
            {analise.coberturas.length > 0 && <PlanoCobertura coberturas={analise.coberturas} />}
          </div>
        )}
      </div>
    </Modal>
  );
}

/** Quem assume cada turno que o colaborador já tinha no período; aplicado ao salvar. */
function PlanoCobertura({ coberturas }: { coberturas: CoberturaTurno[] }) {
  return (
    <div className="cobertura-ferias">
      <strong>Turnos já escalados no período</strong>
      <p className="small muted">Ao salvar, o sistema repassa cada turno para um colega apto (mesmo setor e menos horas primeiro).</p>
      <ul aria-label="Quem cobre os turnos já escalados">
        {coberturas.map((c) => (
          <li key={c.turnoId} className={c.substitutoId === null ? 'sem-substituto' : undefined}>
            <span className="quando">{rotuloDia(c.data)} {dataCurta(c.data)} · {hora(c.horaInicio)}–{hora(c.horaFim)}</span>
            {c.substitutoNome
              ? <span>→ <strong>{c.substitutoNome}</strong> <span className="muted">({c.substitutoSetor})</span></span>
              : <span className="texto-alerta">Ninguém pode cobrir: o turno será retirado</span>}
          </li>
        ))}
      </ul>
    </div>
  );
}
