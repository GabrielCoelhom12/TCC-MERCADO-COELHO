import { useState } from 'react';
import { ArrowLeftRight } from 'lucide-react';
import { api } from '../api';
import { Carregando, Modal, mensagemErro, useCarregar, useToast } from '../components/ui';
import { DIAS_SEMANA, dataCurta, diaSemanaDe, hora, horas, isoDe, moeda, paraData } from '../format';
import type { CandidatoSubstituto, DisponibilidadeColaborador, Escala, Matriz, SugestaoSubstituicao, TurnoDTO } from '../types';

const rotuloDia = (d: string) => DIAS_SEMANA.find((x) => x.dia === diaSemanaDe(d))?.rotulo;

/** Segunda a domingo da semana da data. */
function semanaDe(iso: string) {
  const d = paraData(iso);
  d.setDate(d.getDate() - ((d.getDay() + 6) % 7));
  return Array.from({ length: 7 }, (_, i) => {
    const dia = new Date(d);
    dia.setDate(d.getDate() + i);
    return isoDe(dia);
  });
}

/**
 * Mini pop-up do colaborador escalado: a semana dele (horas e dias) e, em escala editável, a troca por um
 * colega que o sistema já validou (disponibilidade, ausências, interjornada, jornada...).
 */
export default function TrocaColaborador({ escala, turno, onFechar, onTrocado }: {
  escala: Escala;
  turno: TurnoDTO;
  onFechar: () => void;
  onTrocado: (matriz: Matriz) => void;
}) {
  const toast = useToast();
  const { dados, erro } = useCarregar(
    () => api.get<SugestaoSubstituicao>(`/escalas/${escala.id}/turnos/${turno.id}/substitutos`),
    [escala.id, turno.id],
  );
  const [trocando, setTrocando] = useState(false);
  const [enviando, setEnviando] = useState<number | null>(null);
  const editavel = escala.status !== 'APROVADA';

  const trocar = async (c: CandidatoSubstituto) => {
    setEnviando(c.funcionarioId);
    try {
      const matriz = await api.post<Matriz>(`/escalas/${escala.id}/turnos/${turno.id}/substituir`, { funcionarioId: c.funcionarioId });
      toast('ok', `Turno de ${dados?.titular.nome} passado para ${c.nome}.`);
      onTrocado(matriz);
    } catch (e) {
      toast('erro', mensagemErro(e));
    } finally {
      setEnviando(null);
    }
  };

  return (
    <Modal titulo={dados?.titular.nome ?? 'Colaborador'} aberto onFechar={onFechar} acoes={<button onClick={onFechar}>Fechar</button>}>
      {!dados ? <Carregando erro={erro} /> : (
        <div className="troca">
          <p className="troca-turno small">
            <span className="ponto-setor" style={{ background: dados.titular.setorCor }} aria-hidden />
            {dados.titular.setorNome} · {`${rotuloDia(turno.data)} ${dataCurta(turno.data)} · ${hora(turno.horaInicio)}–${hora(turno.horaFim)}`}
            {turno.rotulo && ` (${turno.rotulo})`}
          </p>

          <Disponibilidade titular={dados.titular} dataDoTurno={turno.data} />

          {!editavel && (
            <div className="aviso info">Escala aprovada: para trocar alguém, reabra a escala em Escalas.</div>
          )}
          {editavel && !trocando && (
            <button className="primario" onClick={() => setTrocando(true)}>
              <ArrowLeftRight size={16} aria-hidden />Trocar colaborador
            </button>
          )}
          {editavel && trocando && (
            <Candidatos candidatos={dados.candidatos} enviando={enviando} onTrocar={trocar} />
          )}
        </div>
      )}
    </Modal>
  );
}

function Disponibilidade({ titular, dataDoTurno }: { titular: DisponibilidadeColaborador; dataDoTurno: string }) {
  const uso = titular.limiteSemanal > 0 ? Math.min(100, (titular.horasSemana / titular.limiteSemanal) * 100) : 0;
  const acima = titular.horasSemana > titular.limiteSemanal;
  const livres = titular.limiteSemanal - titular.horasSemana;
  const disponivel = (d: string) => titular.diasDisponiveis.length === 0 || titular.diasDisponiveis.includes(diaSemanaDe(d));

  return (
    <section className="disponibilidade" aria-label="Disponibilidade na semana">
      <div className="row">
        <span className="small muted">Nesta semana</span>
        <div className="spacer" />
        <strong className={acima ? 'texto-erro' : undefined}>{`${horas(titular.horasSemana)} de ${horas(titular.limiteSemanal)}`}</strong>
      </div>
      <div className="barra-uso" aria-hidden><i className={acima ? 'acima' : ''} style={{ width: `${uso}%` }} /></div>
      <ul className="semana-colaborador" aria-label="Dias da semana">
        {semanaDe(dataDoTurno).map((d) => {
          const estado = titular.diasEscalados.includes(d) ? 'escalado' : disponivel(d) ? 'folga' : 'indisponível';
          return (
            <li key={d} title={`${rotuloDia(d)}: ${estado}`}
              className={`dia-colaborador ${estado === 'indisponível' ? 'indisponivel' : estado}${d === dataDoTurno ? ' atual' : ''}`}>
              <span>{rotuloDia(d)}</span>
              <small>{dataCurta(d)}</small>
            </li>
          );
        })}
      </ul>
      <p className="small muted">
        {acima ? `${horas(-livres)} acima do limite semanal.` : `${horas(livres)} livres até o limite semanal.`}
      </p>
    </section>
  );
}

function Candidatos({ candidatos, enviando, onTrocar }: {
  candidatos: CandidatoSubstituto[]; enviando: number | null; onTrocar: (c: CandidatoSubstituto) => void;
}) {
  const aptos = candidatos.filter((c) => c.apto);
  const impedidos = candidatos.filter((c) => !c.apto);
  return (
    <section className="candidatos">
      <h3>Quem pode cobrir</h3>
      {aptos.length === 0 && <p className="muted">Nenhum colega pode cobrir este turno sem ferir as regras.</p>}
      <ul className="lista-candidatos" aria-label="Podem cobrir este turno">
        {aptos.map((c) => (
          <li key={c.funcionarioId}>
            <span className="membro">
              <strong>{c.nome}</strong>
              <span className="small muted">
                <span className="ponto-setor" style={{ background: c.setorCor }} aria-hidden />{c.setorNome}
                {c.mesmoSetor && <span className="badge ok">Mesmo setor</span>}
              </span>
              <span className="small">{`${horas(c.horasSemana)} → ${horas(c.horasComTroca)} na semana`}{c.custoTurno != null && ` · ${moeda(c.custoTurno)}`}</span>
              {c.alertas.map((a) => <span key={a} className="small texto-alerta">{a}</span>)}
            </span>
            <button onClick={() => onTrocar(c)} disabled={enviando !== null} aria-label={`Passar turno para ${c.nome}`}>
              {enviando === c.funcionarioId ? 'Trocando…' : 'Trocar'}
            </button>
          </li>
        ))}
      </ul>
      {impedidos.length > 0 && (
        <details className="impedidos">
          <summary>{impedidos.length === 1 ? '1 colega não pode cobrir' : `${impedidos.length} colegas não podem cobrir`}</summary>
          <ul>
            {impedidos.map((c) => (
              <li key={c.funcionarioId} className="small">
                <strong>{c.nome}</strong> · {c.impedimentos.join(' ')}
              </li>
            ))}
          </ul>
        </details>
      )}
    </section>
  );
}
