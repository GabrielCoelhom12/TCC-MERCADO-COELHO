/** Foto do usuário ou, na falta dela, a inicial do nome. */
export default function Avatar({ nome, foto, tamanho = 28, className = '' }: {
  nome: string;
  foto?: string | null;
  tamanho?: number;
  className?: string;
}) {
  const estilo = { width: tamanho, height: tamanho, fontSize: Math.round(tamanho * 0.42) };
  if (foto) {
    return <img className={`avatar ${className}`} src={foto} alt={nome} style={estilo} />;
  }
  return <span className={`avatar ${className}`} style={estilo} aria-hidden>{nome.trim().charAt(0).toUpperCase() || '?'}</span>;
}
