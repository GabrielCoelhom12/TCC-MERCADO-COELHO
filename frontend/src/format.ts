import type { DiaSemana, TipoDia } from './types';

const moedaFmt = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
const numFmt = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 2 });

export const moeda = (v: number | null | undefined) => moedaFmt.format(v ?? 0);
export const numero = (v: number | null | undefined) => numFmt.format(v ?? 0);

export const pct = (parte: number, total: number) =>
  total > 0 ? `${numFmt.format((parte / total) * 100)}%` : '—';

/** "2026-10-05" → Date local (sem deslocamento de fuso). */
export const paraData = (iso: string) => {
  const [a, m, d] = iso.split('-').map(Number);
  return new Date(a, m - 1, d);
};

export const isoDe = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

export const dataCurta = (iso: string) => {
  const [, m, d] = iso.split('-');
  return `${d}/${m}`;
};

export const dataLonga = (iso: string) => {
  const [a, m, d] = iso.split('-');
  return `${d}/${m}/${a}`;
};

export const hora = (h: string) => h.slice(0, 5);

export const horas = (v: number) => {
  const min = Math.round(v * 60);
  return `${Math.floor(min / 60)}h${String(min % 60).padStart(2, '0')}`;
};

export const TIPOS_DIA: { tipo: TipoDia; rotulo: string }[] = [
  { tipo: 'SEGUNDA', rotulo: 'Seg' },
  { tipo: 'TERCA', rotulo: 'Ter' },
  { tipo: 'QUARTA', rotulo: 'Qua' },
  { tipo: 'QUINTA', rotulo: 'Qui' },
  { tipo: 'SEXTA', rotulo: 'Sex' },
  { tipo: 'SABADO', rotulo: 'Sáb' },
  { tipo: 'DOMINGO', rotulo: 'Dom' },
  { tipo: 'FERIADO', rotulo: 'Feriado' },
];

export const DIAS_SEMANA: { dia: DiaSemana; rotulo: string }[] = [
  { dia: 'MONDAY', rotulo: 'Seg' },
  { dia: 'TUESDAY', rotulo: 'Ter' },
  { dia: 'WEDNESDAY', rotulo: 'Qua' },
  { dia: 'THURSDAY', rotulo: 'Qui' },
  { dia: 'FRIDAY', rotulo: 'Sex' },
  { dia: 'SATURDAY', rotulo: 'Sáb' },
  { dia: 'SUNDAY', rotulo: 'Dom' },
];

/** Dia da semana (enum Java) de uma data ISO. */
export const diaSemanaDe = (iso: string): DiaSemana => {
  const idx = paraData(iso).getDay(); // 0 = domingo
  return DIAS_SEMANA[(idx + 6) % 7].dia;
};

export const ROTULO_REGRA: Record<string, string> = {
  SOBREPOSICAO: 'Sobreposição',
  JORNADA_DIARIA_MAXIMA: 'Jornada máxima',
  HORA_EXTRA_DIARIA: 'Hora extra diária',
  INTERVALO_INTRAJORNADA: 'Intervalo intrajornada',
  INTERJORNADA: 'Interjornada',
  HORA_EXTRA_SEMANAL: 'Hora extra semanal',
  DIAS_CONSECUTIVOS: 'Repouso semanal',
  DOMINGOS_CONSECUTIVOS: 'Folga dominical',
  INDISPONIBILIDADE: 'Indisponibilidade',
  AUSENCIA: 'Ausência',
  FUNCIONARIO_INATIVO: 'Inativo',
  SETOR_ABAIXO_MINIMO: 'Subdimensionamento',
  FAIXA_ABAIXO_DEMANDA: 'Pico descoberto',
  OPERADORES_ACIMA_LMAX: 'Superdimensionamento',
  CUSTO_ACIMA_TETO: 'Custo acima do teto',
  PROJECAO_INVIAVEL: 'Projeção inviável',
};
