import { api } from '../api';
import { moeda, numero } from '../format';
import type { ComparacaoAlgoritmos as Comparacao, MetricasAlgoritmo } from '../types';
import { Carregando, Modal, useCarregar } from './ui';

type Linha = {
  rotulo: string;
  ajuda: string;
  valor: (m: MetricasAlgoritmo) => number;
  formato: (n: number) => string;
  /** Maior é melhor (só a quantidade de turnos); nas demais, menor é melhor. */
  maiorMelhor?: boolean;
};

const LINHAS: Linha[] = [
  { rotulo: 'Vagas sem ninguém', ajuda: 'pessoas que faltaram para a demanda', valor: (m) => m.vagasDescobertas, formato: String },
  { rotulo: 'Turnos escalados', ajuda: 'vagas preenchidas', valor: (m) => m.turnos, formato: String, maiorMelhor: true },
  { rotulo: 'Custo da escala', ajuda: 'com encargos e acréscimos de domingo/feriado', valor: (m) => m.custo, formato: moeda },
  { rotulo: 'Erros', ajuda: 'regras da CLT e mínimos por setor', valor: (m) => m.erros, formato: String },
  { rotulo: 'Alertas', ajuda: 'hora extra, picos descobertos…', valor: (m) => m.alertas, formato: String },
  { rotulo: 'Equilíbrio de horas', ajuda: 'desvio-padrão das horas por pessoa (menor = mais justo)', valor: (m) => m.desvioPadraoHoras, formato: (n) => `${numero(n)} h` },
  { rotulo: 'Tempo', ajuda: 'para gerar a escala', valor: (m) => m.milissegundos, formato: (n) => `${numero(Math.round(n / 10) / 100)} s` },
];

/**
 * Compara o gerador guloso e o otimizador (Timefold) gerando do zero para o período da escala, sem alterá-la — base do
 * capítulo de resultados do TCC.
 */
export default function ComparacaoAlgoritmos({ escalaId, onFechar }: { escalaId: number; onFechar: () => void }) {
  const { dados, erro } = useCarregar(() => api.get<Comparacao>(`/escalas/${escalaId}/comparacao`), [escalaId]);

  return (
    <Modal titulo="Comparar algoritmos" aberto onFechar={onFechar} acoes={<button onClick={onFechar}>Fechar</button>}>
      {!dados ? (
        erro ? <Carregando erro={erro} /> : <p className="muted">Gerando as duas escalas para comparar (alguns segundos)…</p>
      ) : (
        <div className="stack comparacao">
          <p className="small muted">
            Os dois algoritmos geram do zero para o mesmo período, com os mesmos dados; a escala atual não é alterada.
          </p>
          <div className="tabela-wrap">
            <table>
              <thead>
                <tr><th>Métrica</th><th className="num">{dados.guloso.algoritmo}</th><th className="num">{dados.otimizado.algoritmo}</th></tr>
              </thead>
              <tbody>
                {LINHAS.map((l) => {
                  const g = l.valor(dados.guloso);
                  const o = l.valor(dados.otimizado);
                  const melhorG = l.maiorMelhor ? g > o : g < o;
                  const melhorO = l.maiorMelhor ? o > g : o < g;
                  return (
                    <tr key={l.rotulo}>
                      <th scope="row">{l.rotulo}<div className="small muted">{l.ajuda}</div></th>
                      <td className="num"><span className={melhorG ? 'melhor' : undefined}>{l.formato(g)}</span></td>
                      <td className="num"><span className={melhorO ? 'melhor' : undefined}>{l.formato(o)}</span></td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          <Resumo guloso={dados.guloso} otimizado={dados.otimizado} />
        </div>
      )}
    </Modal>
  );
}

function Resumo({ guloso, otimizado }: { guloso: MetricasAlgoritmo; otimizado: MetricasAlgoritmo }) {
  const economia = guloso.custo - otimizado.custo;
  const partes: string[] = [];
  if (otimizado.vagasDescobertas < guloso.vagasDescobertas) {
    partes.push(`cobre ${guloso.vagasDescobertas - otimizado.vagasDescobertas} vaga(s) a mais`);
  }
  if (Math.abs(economia) >= 0.01) partes.push(economia > 0 ? `custa ${moeda(economia)} a menos` : `custa ${moeda(-economia)} a mais`);
  if (otimizado.desvioPadraoHoras < guloso.desvioPadraoHoras) partes.push('distribui as horas de forma mais equilibrada');
  if (partes.length === 0) return <div className="aviso info">Os dois algoritmos chegaram a resultados equivalentes nesta semana.</div>;
  return <div className="aviso ok">Nesta semana, a otimização {partes.join(', ')}.</div>;
}
