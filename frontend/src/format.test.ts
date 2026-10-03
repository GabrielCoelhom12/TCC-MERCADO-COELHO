import { describe, expect, it } from 'vitest';
import { dataCurta, dataLonga, diaSemanaDe, hora, horas, isoDe, moeda, numero, paraData, pct } from './format';

describe('format', () => {
  it('formata moeda e números no padrão brasileiro', () => {
    expect(moeda(1234.5).replace(/\s/g, ' ')).toBe('R$ 1.234,50');
    expect(moeda(null).replace(/\s/g, ' ')).toBe('R$ 0,00');
    expect(numero(2.456)).toBe('2,46');
    expect(numero(undefined)).toBe('0');
  });

  it('calcula percentual e trata total zero', () => {
    expect(pct(1, 4)).toBe('25%');
    expect(pct(1, 0)).toBe('—');
  });

  it('converte datas ISO sem deslocamento de fuso', () => {
    const d = paraData('2026-10-05');
    expect(d.getDate()).toBe(5);
    expect(d.getMonth()).toBe(9);
    expect(isoDe(d)).toBe('2026-10-05');
    expect(dataCurta('2026-10-05')).toBe('05/10');
    expect(dataLonga('2026-10-05')).toBe('05/10/2026');
  });

  it('formata horários e durações', () => {
    expect(hora('07:00:00')).toBe('07:00');
    expect(horas(7.33)).toBe('7h20');
    expect(horas(8)).toBe('8h00');
  });

  it('identifica o dia da semana de uma data', () => {
    expect(diaSemanaDe('2026-10-05')).toBe('MONDAY');
    expect(diaSemanaDe('2026-10-11')).toBe('SUNDAY');
  });
});
