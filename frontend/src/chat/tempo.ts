const doisDigitos = (n: number) => String(n).padStart(2, '0');

const mesmoDia = (a: Date, b: Date) =>
  a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();

export const hora = (iso: string) => {
  const d = new Date(iso);
  return `${doisDigitos(d.getHours())}:${doisDigitos(d.getMinutes())}`;
};

/** Na lista de conversas: hora se for hoje, "Ontem" ou a data curta. */
export function horarioCurto(iso: string, agora = new Date()) {
  const d = new Date(iso);
  if (mesmoDia(d, agora)) return hora(iso);
  const ontem = new Date(agora);
  ontem.setDate(agora.getDate() - 1);
  if (mesmoDia(d, ontem)) return 'Ontem';
  return `${doisDigitos(d.getDate())}/${doisDigitos(d.getMonth() + 1)}`;
}

/** Separador de dias dentro da conversa. */
export function rotuloDia(iso: string, agora = new Date()) {
  const d = new Date(iso);
  if (mesmoDia(d, agora)) return 'Hoje';
  const ontem = new Date(agora);
  ontem.setDate(agora.getDate() - 1);
  if (mesmoDia(d, ontem)) return 'Ontem';
  return `${doisDigitos(d.getDate())}/${doisDigitos(d.getMonth() + 1)}/${d.getFullYear()}`;
}

export const chaveDia = (iso: string) => iso.slice(0, 10);
