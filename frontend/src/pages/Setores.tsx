import { useEffect, useState } from 'react';
import { api } from '../api';
import { Carregando, Modal, mensagemErro, useCarregar, useToast } from '../components/ui';
import { TIPOS_DIA } from '../format';
import type { Setor, TipoDia } from '../types';

type Linha = Setor & { alterado?: boolean };

export default function Setores() {
  const { dados, erro, recarregar } = useCarregar(() => api.get<Setor[]>('/setores'));
  const [linhas, setLinhas] = useState<Linha[]>([]);
  const [novo, setNovo] = useState<{ nome: string; cor: string } | null>(null);
  const toast = useToast();

  useEffect(() => {
    if (dados) setLinhas(dados.map((s) => ({ ...s })));
  }, [dados]);

  if (!dados) return <Carregando erro={erro} />;

  const alterar = (id: number, mudanca: Partial<Setor>) =>
    setLinhas((ls) => ls.map((l) => (l.id === id ? { ...l, ...mudanca, alterado: true } : l)));

  const alterarMinimo = (s: Linha, tipo: TipoDia, valor: number) =>
    alterar(s.id, { minimoPorDia: { ...s.minimoPorDia, [tipo]: Math.max(0, valor) } });

  const salvar = async () => {
    try {
      await Promise.all(linhas.filter((l) => l.alterado).map((l) => api.put(`/setores/${l.id}`, l)));
      toast('ok', 'Setores e demandas mínimas salvos.');
      recarregar();
    } catch (e) {
      toast('erro', mensagemErro(e));
    }
  };

  const criar = async () => {
    try {
      await api.post('/setores', { ...novo, minimoPorDia: Object.fromEntries(TIPOS_DIA.map((t) => [t.tipo, 1])) });
      setNovo(null);
      recarregar();
    } catch (e) {
      toast('erro', mensagemErro(e));
    }
  };

  const excluir = async (s: Setor) => {
    if (!window.confirm(`Excluir o setor ${s.nome}?`)) return;
    try {
      await api.del(`/setores/${s.id}`);
      recarregar();
    } catch (e) {
      toast('erro', mensagemErro(e));
    }
  };

  const total = (tipo: TipoDia) => linhas.filter((l) => l.ativo).reduce((s, l) => s + (l.minimoPorDia[tipo] ?? 0), 0);
  const pendentes = linhas.some((l) => l.alterado);

  return (
    <div className="stack">
      <div className="cabecalho">
        <div>
          <h1>Setores e demanda mínima</h1>
          <p>Número mínimo de colaboradores por setor em cada dia. Setores com capacidade de atendimento acompanham a previsão de movimento.</p>
        </div>
        <div className="spacer" />
        <button onClick={() => setNovo({ nome: '', cor: '#F26A1B' })}>+ Novo setor</button>
        <button className="primario" disabled={!pendentes} onClick={salvar}>Salvar alterações</button>
      </div>

      <div className="card tabela-wrap">
        <table>
          <thead>
            <tr>
              <th>Setor</th>
              {TIPOS_DIA.map((t) => <th key={t.tipo} className="num">{t.rotulo}</th>)}
              <th className="num" title="Clientes por hora que uma pessoa do setor atende. Vazio = demanda fixa.">Clientes/h por pessoa</th>
              <th>Ativo</th><th></th>
            </tr>
          </thead>
          <tbody>
            {linhas.map((s) => (
              <tr key={s.id}>
                <td>
                  <div className="row" style={{ gap: 8 }}>
                    <input type="color" value={s.cor} onChange={(e) => alterar(s.id, { cor: e.target.value })} style={{ width: 34, padding: 2 }} aria-label="Cor" />
                    <input value={s.nome} onChange={(e) => alterar(s.id, { nome: e.target.value })} />
                  </div>
                </td>
                {TIPOS_DIA.map((t) => (
                  <td key={t.tipo} className="num">
                    <input type="number" min={0} style={{ width: 58, textAlign: 'right' }} value={s.minimoPorDia[t.tipo] ?? 0}
                      onChange={(e) => alterarMinimo(s, t.tipo, Number(e.target.value))} />
                  </td>
                ))}
                <td className="num">
                  <input type="number" min={1} max={500} style={{ width: 76, textAlign: 'right' }} placeholder="fixa"
                    aria-label={`Clientes por hora por pessoa — ${s.nome}`} value={s.clientesPorColaboradorHora ?? ''}
                    onChange={(e) => alterar(s.id, { clientesPorColaboradorHora: e.target.value === '' ? null : Number(e.target.value) })} />
                </td>
                <td><input type="checkbox" checked={s.ativo} onChange={(e) => alterar(s.id, { ativo: e.target.checked })} /></td>
                <td className="num"><button className="perigo" onClick={() => excluir(s)}>Excluir</button></td>
              </tr>
            ))}
            <tr>
              <td><strong>Total da loja</strong></td>
              {TIPOS_DIA.map((t) => <td key={t.tipo} className="num"><strong>{total(t.tipo)}</strong></td>)}
              <td colSpan={3} />
            </tr>
          </tbody>
        </table>
      </div>
      <p className="ajuda">O perfil "Feriado" se aplica a qualquer feriado cadastrado, independentemente do dia da semana em que caia.
        Com "Clientes/h por pessoa" preenchido (ex.: 30 na frente de caixa), o mínimo vira um piso e o sistema calcula quantas pessoas
        cada faixa de horário precisa a partir da previsão de movimento.</p>

      <Modal titulo="Novo setor" aberto={!!novo} onFechar={() => setNovo(null)}
        acoes={<><button onClick={() => setNovo(null)}>Cancelar</button><button className="primario" disabled={!novo?.nome} onClick={criar}>Criar</button></>}>
        {novo && (
          <div className="form-grid">
            <label className="campo">Nome<input value={novo.nome} onChange={(e) => setNovo({ ...novo, nome: e.target.value })} autoFocus /></label>
            <label className="campo">Cor<input type="color" value={novo.cor} onChange={(e) => setNovo({ ...novo, cor: e.target.value })} /></label>
          </div>
        )}
      </Modal>
    </div>
  );
}
