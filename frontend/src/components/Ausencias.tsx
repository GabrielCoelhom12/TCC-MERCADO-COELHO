import { DIAS_SEMANA, dataCurta, diaSemanaDe, hora, isoDe, paraData } from '../format';
import type { CoberturaTurno } from '../types';

/** Peças comuns às operações de Férias e de Afastamentos. */

const DIA_MS = 86400000;
export const diasEntre = (de: string, ate: string) => Math.round((paraData(ate).getTime() - paraData(de).getTime()) / DIA_MS);
export const somarDias = (iso: string, n: number) => isoDe(new Date(paraData(iso).getTime() + n * DIA_MS));
export const rotuloDias = (n: number) => (n === 1 ? '1 dia' : `${n} dias`);
const rotuloDia = (d: string) => DIAS_SEMANA.find((x) => x.dia === diaSemanaDe(d))?.rotulo;
const turnos = (n: number, um: string, varios: string) => (n === 1 ? `1 turno ${um}` : `${n} turnos ${varios}`);

/** "Férias de Ana registradas · 2 turnos repassados · 1 turno retirado sem substituto." */
export function resumoRegistro(inicio: string, coberturas: CoberturaTurno[]) {
  const repassados = coberturas.filter((c) => c.substitutoId !== null).length;
  const retirados = coberturas.length - repassados;
  const partes = [inicio];
  if (repassados) partes.push(turnos(repassados, 'repassado', 'repassados'));
  if (retirados) partes.push(turnos(retirados, 'retirado sem substituto', 'retirados sem substituto'));
  return `${partes.join(' · ')}.`;
}

/** Quem assume cada turno que o colaborador já tinha no período; aplicado ao salvar. */
export function PlanoCobertura({ coberturas }: { coberturas: CoberturaTurno[] }) {
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
              : <span className="texto-alerta">{c.passado ? 'Dia já passou: o turno será retirado' : 'Ninguém pode cobrir: o turno será retirado'}</span>}
          </li>
        ))}
      </ul>
    </div>
  );
}
