import { useEffect, useState } from 'react';
import { FileText, Paperclip, Search, Stethoscope, X } from 'lucide-react';
import { api } from '../api';
import { usePermissoes } from '../auth';
import { PlanoCobertura, diasEntre, resumoRegistro, rotuloDias, somarDias } from '../components/Ausencias';
import Avatar from '../components/Avatar';
import { Carregando, Modal, mensagemErro, useCarregar, useToast } from '../components/ui';
import { MOTIVOS_AUSENCIA, dataCurta, dataLonga, isoDe } from '../format';
import type { Ausencia, DocumentoResumo, Funcionario, MotivoAusencia, PreviaAusencia, RegistroAusencia } from '../types';

/** Tipos desta operação (férias têm operação própria). */
const TIPOS = MOTIVOS_AUSENCIA.filter((m) => m.valor !== 'FERIAS');
const rotuloTipo = (m: MotivoAusencia) => MOTIVOS_AUSENCIA.find((x) => x.valor === m)?.rotulo ?? m;

/** Atalhos de duração de cada tipo: o que a lei garante ou o que é mais comum. */
const DURACOES: Partial<Record<MotivoAusencia, number[]>> = {
  ATESTADO: [1, 2, 3, 5, 15],
  LICENCA_MATERNIDADE: [120, 180],
  LICENCA_PATERNIDADE: [5, 20],
  LUTO: [2],
  CASAMENTO: [3],
};

type Form = { id?: number; funcionarioId: number | ''; motivo: MotivoAusencia; inicio: string; fim: string; observacao: string };

export default function Afastamentos() {
  const { dados, erro, recarregar } = useCarregar(() =>
    Promise.all([api.get<Ausencia[]>('/afastamentos'), api.get<Funcionario[]>('/funcionarios')]));
  const [busca, setBusca] = useState('');
  const [form, setForm] = useState<Form | null>(null);
  const { podeEditar } = usePermissoes();

  if (!dados) return <Carregando erro={erro} />;
  const [afastamentos, funcionarios] = dados;
  const hoje = isoDe(new Date());
  const porId = new Map(funcionarios.map((f) => [f.id, f]));
  const termo = busca.trim().toLowerCase();
  const visiveis = afastamentos.filter((a) => !termo || a.funcionarioNome.toLowerCase().includes(termo));

  const andamento = visiveis.filter((a) => a.inicio <= hoje && a.fim >= hoje).sort((a, b) => a.fim.localeCompare(b.fim));
  const programados = visiveis.filter((a) => a.inicio > hoje).sort((a, b) => a.inicio.localeCompare(b.inicio));
  const encerrados = visiveis.filter((a) => a.fim < hoje).sort((a, b) => b.fim.localeCompare(a.fim));
  const mes = hoje.slice(0, 7);

  // O supervisor registra (atestado chega no dia a dia), mas só a gerência edita ou remove.
  const editar = podeEditar
    ? (a: Ausencia) => setForm({ id: a.id, funcionarioId: a.funcionarioId, motivo: a.motivo, inicio: a.inicio, fim: a.fim, observacao: a.observacao ?? '' })
    : undefined;

  return (
    <div className="stack">
      <div className="cabecalho">
        <div>
          <h1>Afastamentos</h1>
          <p>Atestados, licenças e outras ausências. Os turnos já escalados são repassados na hora.</p>
        </div>
        <div className="spacer" />
        <label className="busca">
          <Search size={16} aria-hidden />
          <input placeholder="Buscar colaborador" value={busca} onChange={(e) => setBusca(e.target.value)} aria-label="Buscar colaborador" />
        </label>
        <button className="primario" onClick={() => setForm({ funcionarioId: '', motivo: 'ATESTADO', inicio: hoje, fim: '', observacao: '' })}>
          <Stethoscope size={16} aria-hidden />Registrar afastamento
        </button>
      </div>

      <div className="grid-kpi">
        <div className="card kpi destaque">
          <div className="rotulo">Afastados hoje</div>
          <div className="valor">{afastamentos.filter((a) => a.inicio <= hoje && a.fim >= hoje).length}</div>
          <div className="detalhe">colaboradores fora da escala</div>
        </div>
        <div className="card kpi">
          <div className="rotulo">Voltam em 7 dias</div>
          <div className="valor">{afastamentos.filter((a) => a.fim >= hoje && diasEntre(hoje, a.fim) < 7).length}</div>
          <div className="detalhe">retornos desta semana</div>
        </div>
        <div className="card kpi">
          <div className="rotulo">Atestados no mês</div>
          <div className="valor">{afastamentos.filter((a) => a.motivo === 'ATESTADO' && a.inicio.startsWith(mes)).length}</div>
          <div className="detalhe">para acompanhar o absenteísmo</div>
        </div>
      </div>

      <Secao titulo="Em andamento" itens={andamento} porId={porId} hoje={hoje} onEditar={editar} vazio="Ninguém afastado hoje." />
      <Secao titulo="Programados" itens={programados} porId={porId} hoje={hoje} onEditar={editar} vazio="Nenhum afastamento programado." />
      {encerrados.length > 0 && (
        <details className="card encerradas">
          <summary>Encerrados ({encerrados.length})</summary>
          <Tabela itens={encerrados} porId={porId} hoje={hoje} onEditar={editar} />
        </details>
      )}

      {form && (
        <ModalAfastamento form={form} setForm={setForm} funcionarios={funcionarios} podeRemover={podeEditar}
          onSalvo={() => {
            setForm(null);
            recarregar();
          }} />
      )}
    </div>
  );
}

/** Sem {@code onEditar} (perfil que só registra), as linhas não têm o botão Editar. */
type Lista = { itens: Ausencia[]; porId: Map<number, Funcionario>; hoje: string; onEditar?: (a: Ausencia) => void };

function Secao({ titulo, vazio, ...lista }: Lista & { titulo: string; vazio: string }) {
  const id = `afastamentos-${titulo.toLowerCase().replace(/\s+/g, '-')}`;
  return (
    <section className="card" aria-labelledby={id}>
      <h2 id={id}>{titulo} <span className="muted">({lista.itens.length})</span></h2>
      {lista.itens.length === 0 ? <p className="muted">{vazio}</p> : <Tabela {...lista} />}
    </section>
  );
}

function Tabela({ itens, porId, hoje, onEditar }: Lista) {
  return (
    <div className="tabela-wrap">
      <table className="tabela-ferias">
        <thead>
          <tr><th>Colaborador</th><th>Tipo</th><th>Período</th><th className="num">Duração</th><th>Situação</th><th></th></tr>
        </thead>
        <tbody>
          {itens.map((a) => {
            const pessoa = porId.get(a.funcionarioId);
            return (
              <tr key={a.id}>
                <td>
                  <div className="row" style={{ gap: 10, flexWrap: 'nowrap' }}>
                    <Avatar nome={a.funcionarioNome} foto={pessoa?.foto} tamanho={32} className="avatar-linha" />
                    <div>
                      <strong>{a.funcionarioNome}</strong>
                      {pessoa && (
                        <div className="small muted">
                          <span className="ponto-setor" style={{ background: pessoa.setor.cor }} aria-hidden />{pessoa.setor.nome}
                        </div>
                      )}
                    </div>
                  </div>
                </td>
                <td>
                  <span className="badge neutro">{rotuloTipo(a.motivo)}</span>
                  {!!a.documentos && (
                    <span className="anexos" title="Documentos anexados"
                      aria-label={a.documentos === 1 ? '1 documento anexado' : `${a.documentos} documentos anexados`}>
                      <Paperclip size={13} aria-hidden />{a.documentos}
                    </span>
                  )}
                </td>
                <td>{dataCurta(a.inicio)} – {dataLonga(a.fim)}</td>
                <td className="num">{rotuloDias(diasEntre(a.inicio, a.fim) + 1)}</td>
                <td><Situacao a={a} hoje={hoje} /></td>
                <td className="num">
                  {onEditar && (
                    <button className="link" onClick={() => onEditar(a)} aria-label={`Editar afastamento de ${a.funcionarioNome}`}>Editar</button>
                  )}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

function Situacao({ a, hoje }: { a: Ausencia; hoje: string }) {
  if (a.fim < hoje) return <span className="badge neutro">voltou em {dataCurta(somarDias(a.fim, 1))}</span>;
  if (a.inicio <= hoje) return <span className="badge laranja">volta em {dataCurta(somarDias(a.fim, 1))}</span>;
  const faltam = diasEntre(hoje, a.inicio);
  return <span className="badge ok">{faltam === 1 ? 'começa amanhã' : `começa em ${faltam} dias`}</span>;
}

function ModalAfastamento({ form, setForm, funcionarios, podeRemover, onSalvo }: {
  form: Form;
  setForm: (f: Form | null) => void;
  funcionarios: Funcionario[];
  podeRemover: boolean;
  onSalvo: () => void;
}) {
  const toast = useToast();
  const [analise, setAnalise] = useState<PreviaAusencia | null>(null);
  const [salvando, setSalvando] = useState(false);
  const [pendentes, setPendentes] = useState<File[]>([]);
  const completo = form.funcionarioId !== '' && !!form.inicio && !!form.fim;

  // Prévia ao vivo: dias, retorno, alertas da lei e quem cobre os turnos já escalados.
  useEffect(() => {
    setAnalise(null);
    if (!completo) return;
    let ativo = true;
    const ignorar = form.id ? `&ignorar=${form.id}` : '';
    api.get<PreviaAusencia>(`/afastamentos/analise?funcionarioId=${form.funcionarioId}&motivo=${form.motivo}&inicio=${form.inicio}&fim=${form.fim}${ignorar}`)
      .then((a) => ativo && setAnalise(a))
      .catch((e) => ativo && setAnalise({ dias: 0, retorno: null, erros: [mensagemErro(e)], alertas: [], valida: false, coberturas: [] }));
    return () => {
      ativo = false;
    };
  }, [completo, form.id, form.funcionarioId, form.motivo, form.inicio, form.fim]);

  const nome = funcionarios.find((f) => f.id === form.funcionarioId)?.nome;
  const ativos = funcionarios.filter((f) => f.ativo || f.id === form.funcionarioId);
  const setores = [...new Set(ativos.map((f) => f.setor.nome))].sort();
  const duracoes = DURACOES[form.motivo] ?? [];

  const salvar = async () => {
    setSalvando(true);
    try {
      const corpo = { funcionarioId: form.funcionarioId, motivo: form.motivo, inicio: form.inicio, fim: form.fim, observacao: form.observacao };
      const registro = form.id
        ? await api.put<RegistroAusencia>(`/afastamentos/${form.id}`, corpo)
        : await api.post<RegistroAusencia>('/afastamentos', corpo);
      const anexados = await enviarDocumentos(registro.ausencia.id);
      const resumo = resumoRegistro(form.id ? 'Afastamento atualizado' : `Afastamento de ${nome} registrado`, registro.coberturas);
      toast('ok', anexados
        ? resumo.replace(/\.$/, ` · ${anexados === 1 ? '1 documento anexado' : `${anexados} documentos anexados`}.`)
        : resumo);
      onSalvo();
    } catch (e) {
      toast('erro', mensagemErro(e));
    } finally {
      setSalvando(false);
    }
  };

  /** O afastamento já foi salvo: se um documento falhar, avisa e segue com os outros. */
  const enviarDocumentos = async (afastamentoId: number) => {
    let enviados = 0;
    for (const arquivo of pendentes) {
      const dados = new FormData();
      dados.append('arquivo', arquivo);
      try {
        await api.anexar(`/afastamentos/${afastamentoId}/documentos`, dados);
        enviados++;
      } catch (e) {
        toast('erro', `Afastamento salvo, mas "${arquivo.name}" não foi anexado: ${mensagemErro(e)}`);
      }
    }
    return enviados;
  };

  const remover = async () => {
    try {
      await api.del(`/afastamentos/${form.id}`);
      toast('ok', 'Afastamento removido.');
      onSalvo();
    } catch (e) {
      toast('erro', mensagemErro(e));
    }
  };

  return (
    <Modal titulo={form.id ? 'Editar afastamento' : 'Registrar afastamento'} aberto onFechar={() => setForm(null)}
      acoes={(
        <>
          {form.id && podeRemover && <button className="perigo" onClick={remover}>Remover</button>}
          <div className="spacer" />
          <button onClick={() => setForm(null)}>Cancelar</button>
          <button className="primario" onClick={salvar} disabled={!analise?.valida || salvando}>Salvar</button>
        </>
      )}>
      <div className="stack">
        <div className="form-grid">
          <label className="campo">Tipo
            <select value={form.motivo} onChange={(e) => setForm({ ...form, motivo: e.target.value as MotivoAusencia })}>
              {TIPOS.map((t) => <option key={t.valor} value={t.valor}>{t.rotulo}</option>)}
            </select>
          </label>
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
        </div>
        <div className="form-grid">
          <label className="campo">Início
            <input type="date" value={form.inicio} onChange={(e) => setForm({ ...form, inicio: e.target.value })} />
          </label>
          <label className="campo">Fim
            <input type="date" value={form.fim} min={form.inicio || undefined} onChange={(e) => setForm({ ...form, fim: e.target.value })} />
          </label>
        </div>
        {duracoes.length > 0 && (
          <div className="duracoes" role="group" aria-label="Duração">
            {duracoes.map((n) => {
              const fim = form.inicio ? somarDias(form.inicio, n - 1) : '';
              return (
                <button key={n} type="button" disabled={!form.inicio} className={fim && fim === form.fim ? 'ativa' : undefined}
                  onClick={() => setForm({ ...form, fim })}>
                  {rotuloDias(n)}
                </button>
              );
            })}
          </div>
        )}
        <label className="campo">Observação
          <input value={form.observacao} maxLength={255} onChange={(e) => setForm({ ...form, observacao: e.target.value })} placeholder="Opcional" />
          <span className="ajuda">Não informe diagnóstico nem CID: para a escala basta o tipo do afastamento (LGPD, dado de saúde).</span>
        </label>
        <Documentos afastamentoId={form.id} pendentes={pendentes} setPendentes={setPendentes} podeGerir={podeRemover} />

        {completo && !analise && <p className="muted small">Verificando o período…</p>}
        {analise && (
          <div className="previa-ferias" aria-live="polite">
            {analise.retorno && <strong>{rotuloDias(analise.dias)} · retorno em {dataLonga(analise.retorno)}</strong>}
            {analise.erros.map((e) => <div key={e} className="aviso erro">{e}</div>)}
            {analise.alertas.map((a) => <div key={a} className="aviso alerta">{a}</div>)}
            {analise.valida && analise.alertas.length === 0 && analise.coberturas.length === 0 && (
              <div className="aviso ok">Nenhum turno escalado no período.</div>
            )}
            {analise.coberturas.length > 0 && <PlanoCobertura coberturas={analise.coberturas} />}
          </div>
        )}
      </div>
    </Modal>
  );
}

const LIMITE_DOCUMENTO = 5 * 1024 * 1024;
const TIPOS_DOCUMENTO = ['application/pdf', 'image/jpeg', 'image/png'];
const tamanho = (bytes: number) => (bytes < 1024 * 1024 ? `${Math.max(1, Math.round(bytes / 1024))} KB` : `${(bytes / 1024 / 1024).toFixed(1).replace('.', ',')} MB`);

/**
 * Documentos do afastamento: os já enviados (a gerência abre e remove) e os escolhidos agora, enviados ao salvar.
 * O documento pode trazer diagnóstico ou CID: só a gerência consegue abri-lo.
 */
function Documentos({ afastamentoId, pendentes, setPendentes, podeGerir }: {
  afastamentoId?: number;
  pendentes: File[];
  setPendentes: (f: File[]) => void;
  podeGerir: boolean;
}) {
  const toast = useToast();
  const { dados: enviados, recarregar } = useCarregar(
    () => (afastamentoId ? api.get<DocumentoResumo[]>(`/afastamentos/${afastamentoId}/documentos`) : Promise.resolve([])),
    [afastamentoId],
  );

  const escolher = (arquivos: FileList | null) => {
    const aceitos: File[] = [];
    const recusados: string[] = [];
    for (const f of Array.from(arquivos ?? [])) {
      if (!TIPOS_DOCUMENTO.includes(f.type)) recusados.push(`${f.name}: envie PDF, JPG ou PNG.`);
      else if (f.size > LIMITE_DOCUMENTO) recusados.push(`${f.name}: máximo de 5 MB.`);
      else aceitos.push(f);
    }
    // Um aviso só, com todos os recusados (um aviso novo substituiria o anterior).
    if (recusados.length) toast('erro', recusados.join(' '));
    setPendentes([...pendentes, ...aceitos]);
  };

  const abrir = async (d: DocumentoResumo) => {
    // A aba abre já no clique (senão o navegador bloqueia) e recebe o arquivo quando ele chegar.
    const aba = window.open('', '_blank');
    try {
      const url = URL.createObjectURL(await api.arquivo(`/afastamentos/documentos/${d.id}`));
      if (aba) aba.location.href = url;
      else window.location.assign(url);
    } catch (e) {
      aba?.close();
      toast('erro', mensagemErro(e));
    }
  };

  const remover = async (d: DocumentoResumo) => {
    try {
      await api.del(`/afastamentos/documentos/${d.id}`);
      toast('ok', 'Documento removido.');
      recarregar();
    } catch (e) {
      toast('erro', mensagemErro(e));
    }
  };

  return (
    <div className="documentos-afastamento">
      <div className="row">
        <strong className="small">Documentos</strong>
        <div className="spacer" />
        <label className="btn anexar">
          <Paperclip size={15} aria-hidden />Anexar documento
          <input type="file" accept=".pdf,image/jpeg,image/png" multiple className="visualmente-oculto"
            onChange={(e) => {
              escolher(e.target.files);
              e.target.value = '';
            }} />
        </label>
      </div>
      <p className="ajuda">Atestado, declaração ou certidão em PDF, JPG ou PNG, até 5 MB. O documento pode conter
        diagnóstico: só a gerência consegue abrir.</p>
      {((enviados?.length ?? 0) > 0 || pendentes.length > 0) && (
        <ul>
          {enviados?.map((d) => (
            <li key={d.id}>
              <FileText size={15} aria-hidden />
              <span className="nome">{d.nomeArquivo}</span>
              <span className="small muted">{tamanho(d.tamanho)} · enviado por {d.enviadoPor} em {dataCurta(d.enviadoEm.slice(0, 10))}</span>
              {podeGerir && (
                <>
                  <button type="button" className="link" onClick={() => abrir(d)} aria-label={`Abrir ${d.nomeArquivo}`}>Abrir</button>
                  <button type="button" className="link perigo" onClick={() => remover(d)} aria-label={`Remover ${d.nomeArquivo}`}>Remover</button>
                </>
              )}
            </li>
          ))}
          {pendentes.map((f, i) => (
            <li key={`${f.name}-${i}`} className="pendente">
              <FileText size={15} aria-hidden />
              <span className="nome">{f.name}</span>
              <span className="small muted">{tamanho(f.size)} · será enviado ao salvar</span>
              <button type="button" className="icone" aria-label={`Tirar ${f.name}`}
                onClick={() => setPendentes(pendentes.filter((_, j) => j !== i))}>
                <X size={14} aria-hidden />
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
