/** Mesma política validada no servidor (SenhaForteValidator). */
export const SENHA_MAXIMO = 64;

const REGRAS: { rotulo: string; teste: (s: string) => boolean }[] = [
  { rotulo: 'Pelo menos 8 caracteres', teste: (s) => s.length >= 8 },
  { rotulo: 'Uma letra maiúscula', teste: (s) => /\p{Lu}/u.test(s) },
  { rotulo: 'Uma letra minúscula', teste: (s) => /\p{Ll}/u.test(s) },
  { rotulo: 'Um número', teste: (s) => /\p{N}/u.test(s) },
  { rotulo: 'Um caractere especial (ex.: @ # $ !)', teste: (s) => /[^\p{L}\p{N}\s]/u.test(s) },
];

export const requisitosSenha = (senha: string) => REGRAS.map((r) => ({ rotulo: r.rotulo, ok: r.teste(senha) }));

export const senhaForte = (senha: string) => senha.length <= SENHA_MAXIMO && REGRAS.every((r) => r.teste(senha));
