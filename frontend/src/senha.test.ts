import { describe, expect, it } from 'vitest';
import { requisitosSenha, senhaForte } from './senha';

describe('senha', () => {
  it('lista o estado de cada requisito', () => {
    const r = requisitosSenha('abc');
    expect(r.map((x) => x.rotulo)).toEqual([
      'Pelo menos 8 caracteres',
      'Uma letra maiúscula',
      'Uma letra minúscula',
      'Um número',
      'Um caractere especial (ex.: @ # $ !)',
    ]);
    expect(r.map((x) => x.ok)).toEqual([false, false, true, false, false]);
  });

  it.each(['Senha@Forte1', 'Coelho#2026', 'Ação!2026x'])('aceita %s', (s) => {
    expect(senhaForte(s)).toBe(true);
  });

  it.each(['Ab1!Ab1', 'senha@forte1', 'SENHA@FORTE1', 'Senha@Forte', 'SenhaForte12', 'Senha Forte 12', 'Aa1!' + 'x'.repeat(61)])(
    'recusa %s',
    (s) => {
      expect(senhaForte(s)).toBe(false);
    },
  );
});
