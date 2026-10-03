import { useEffect, useState } from 'react';
import { Plus, Trash2 } from 'lucide-react';
import { api } from '../api';
import { Carregando, mensagemErro, useCarregar, useToast } from '../components/ui';
import type { FaixaHoraria, Parametros } from '../types';

type Campo = { chave: keyof Parametros; rotulo: string; ajuda: string; passo?: string };

const FINANCEIRO: Campo[] = [
  { chave: 'percentualTetoFolha', rotulo: 'Teto da folha do dia (% da receita)', ajuda: 'Quanto da receita prevista do dia pode ser gasto com a equipe.', passo: '0.1' },
  { chave: 'fatorDomingo', rotulo: 'Acréscimo de custo aos domingos', ajuda: 'Ex.: 1,5 = o custo da hora no domingo é 50% maior.', passo: '0.05' },
  { chave: 'fatorFeriado', rotulo: 'Acréscimo de custo aos feriados', ajuda: 'Valor padrão; cada feriado pode ter o seu.', passo: '0.05' },
  { chave: 'jornadaReferenciaHoras', rotulo: 'Jornada de referência (h)', ajuda: 'Jornada média usada para estimar o custo de cada colaborador no dia.', passo: '0.01' },
];

const TRABALHISTA: Campo[] = [
  { chave: 'jornadaNormalDiariaHoras', rotulo: 'Jornada normal diária (h)', ajuda: 'CLT art. 58 — acima disso, alerta de hora extra.', passo: '0.25' },
  { chave: 'jornadaMaximaDiariaHoras', rotulo: 'Jornada máxima diária (h)', ajuda: 'CLT art. 59 — normal + 2h extras. Acima disso, erro.', passo: '0.25' },
  { chave: 'jornadaSemanalHoras', rotulo: 'Jornada semanal (h)', ajuda: 'CF art. 7º, XIII — acima disso, alerta de hora extra.', passo: '0.5' },
  { chave: 'interjornadaMinimaHoras', rotulo: 'Interjornada mínima (h)', ajuda: 'CLT art. 66 — descanso entre duas jornadas.', passo: '0.5' },
  { chave: 'maxDiasConsecutivos', rotulo: 'Máx. dias seguidos de trabalho', ajuda: 'CLT art. 67 — repouso semanal remunerado.' },
  { chave: 'maxDomingosConsecutivos', rotulo: 'Máx. domingos seguidos', ajuda: 'Lei 10.101/2000, art. 6º — folga dominical no comércio a cada 3 semanas.' },
];

export default function ParametrosPage() {
  const { dados, erro, recarregar } = useCarregar(() => api.get<Parametros>('/parametros'));
  const [form, setForm] = useState<Parametros | null>(null);
  const toast = useToast();

  useEffect(() => {
    if (dados) setForm({ ...dados });
  }, [dados]);

  if (!form) return <Carregando erro={erro} />;

  const salvar = async () => {
    try {
      await api.put('/parametros', form);
      toast('ok', 'Parâmetros salvos.');
      recarregar();
    } catch (e) {
      toast('erro', mensagemErro(e));
    }
  };

  const campo = (c: Campo) => (
    <label className="campo" key={c.chave}>{c.rotulo}
      <input type="number" step={c.passo ?? '1'} value={form[c.chave] as number}
        onChange={(e) => setForm({ ...form, [c.chave]: Number(e.target.value) })} />
      <span className="ajuda">{c.ajuda}</span>
    </label>
  );

  return (
    <div className="stack">
      <div className="cabecalho">
        <div>
          <h1>Parâmetros</h1>
          <p>Configure conforme a CLT e a convenção coletiva do sindicato dos comerciários.</p>
        </div>
        <div className="spacer" />
        <button className="primario" onClick={salvar}>Salvar</button>
      </div>

      <div className="card">
        <h2>Custos</h2>
        <div className="form-grid">{FINANCEIRO.map(campo)}</div>
        <div style={{ marginTop: 16 }}>
          <label className="campo">Quando uma alocação ultrapassar o limite de custo do dia</label>
          <div className="row" style={{ marginTop: 6 }}>
            {(['BLOQUEAR', 'ALERTAR'] as const).map((m) => (
              <label key={m} className="row" style={{ gap: 6, fontWeight: 600 }}>
                <input type="radio" checked={form.modoFinanceiro === m} onChange={() => setForm({ ...form, modoFinanceiro: m })} />
                {m === 'BLOQUEAR' ? 'Bloquear a alocação (recomendado)' : 'Apenas alertar a gestão'}
              </label>
            ))}
          </div>
        </div>
      </div>

      <div className="card">
        <h2>Geração automática de escalas</h2>
        <p className="ajuda" style={{ marginBottom: 12 }}>
          Todo dia o sistema confere se as próximas semanas já têm escala; as que faltam são criadas como rascunho, com a previsão
          de movimento. Escalas existentes nunca são alteradas.
        </p>
        <div className="row" style={{ alignItems: 'flex-end', gap: 24 }}>
          <label className="row checkbox">
            <input type="checkbox" checked={form.geracaoAutomatica}
              onChange={(e) => setForm({ ...form, geracaoAutomatica: e.target.checked })} />
            Gerar automaticamente o rascunho das próximas semanas
          </label>
          <label className="campo" style={{ width: 200 }}>Semanas de antecedência
            <input type="number" min={1} max={4} value={form.semanasAntecedencia} disabled={!form.geracaoAutomatica}
              onChange={(e) => setForm({ ...form, semanasAntecedencia: Number(e.target.value) })} />
          </label>
        </div>
      </div>

      <EditorFaixas />

      <div className="card">
        <h2>Regras trabalhistas</h2>
        <div className="form-grid">{TRABALHISTA.map(campo)}</div>
        <p className="ajuda" style={{ marginTop: 12 }}>
          Intervalo intrajornada (CLT art. 71) também é validado: jornadas acima de 6h exigem 1h de intervalo; entre 4h e 6h, 15 minutos.
        </p>
      </div>
    </div>
  );
}

type LinhaFaixa = { inicio: string; fim: string };

/** Faixas de horário usadas no fechamento do dia e na previsão de movimento. */
function EditorFaixas() {
  const { dados, erro, recarregar } = useCarregar(() => api.get<FaixaHoraria[]>('/movimento/faixas'));
  const [linhas, setLinhas] = useState<LinhaFaixa[] | null>(null);
  const toast = useToast();

  useEffect(() => {
    if (dados) setLinhas(dados.map((f) => ({ inicio: f.inicio.slice(0, 5), fim: f.fim.slice(0, 5) })));
  }, [dados]);

  if (!linhas) return <div className="card"><Carregando erro={erro} /></div>;

  const mudar = (i: number, campo: keyof LinhaFaixa, valor: string) =>
    setLinhas(linhas.map((l, j) => (j === i ? { ...l, [campo]: valor } : l)));

  const salvar = async () => {
    try {
      await api.put('/movimento/faixas', linhas);
      toast('ok', 'Faixas de horário salvas.');
      recarregar();
    } catch (e) {
      toast('erro', mensagemErro(e));
    }
  };

  return (
    <div className="card">
      <h2>Faixas de horário do movimento</h2>
      <p className="ajuda" style={{ marginBottom: 12 }}>
        O fechamento do dia é lançado por faixa, e a previsão encontra os horários de pico a partir delas. Alterar as faixas não
        apaga os fechamentos já lançados.
      </p>
      <div className="lista-faixas">
        {linhas.map((l, i) => (
          <div key={i} className="row" style={{ gap: 8 }}>
            <input type="time" value={l.inicio} aria-label={`Início da faixa ${i + 1}`} onChange={(e) => mudar(i, 'inicio', e.target.value)} />
            <span className="muted">até</span>
            <input type="time" value={l.fim} aria-label={`Fim da faixa ${i + 1}`} onChange={(e) => mudar(i, 'fim', e.target.value)} />
            <button className="icone perigo" aria-label={`Remover faixa ${i + 1}`} disabled={linhas.length === 1}
              onClick={() => setLinhas(linhas.filter((_, j) => j !== i))}>
              <Trash2 size={15} aria-hidden />
            </button>
          </div>
        ))}
      </div>
      <div className="row" style={{ marginTop: 12 }}>
        <button onClick={() => setLinhas([...linhas, { inicio: linhas[linhas.length - 1]?.fim ?? '', fim: '' }])}>
          <Plus size={15} aria-hidden /> Adicionar faixa
        </button>
        <span className="spacer" />
        <button className="primario" disabled={linhas.some((l) => !l.inicio || !l.fim)} onClick={salvar}>Salvar faixas</button>
      </div>
    </div>
  );
}
