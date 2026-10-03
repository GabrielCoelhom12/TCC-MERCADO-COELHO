import { useId, useState, type KeyboardEvent } from 'react';
import { ArrowBigUp, Check, Eye, EyeOff } from 'lucide-react';
import { SENHA_MAXIMO, requisitosSenha } from '../senha';

/** Campo de senha com mostrar/ocultar, aviso de Caps Lock e, opcionalmente, a lista de requisitos da política. */
export default function CampoSenha({ rotulo, valor, onMudar, mostrarRequisitos, placeholder, autoComplete = 'new-password' }: {
  rotulo: string;
  valor: string;
  onMudar: (valor: string) => void;
  mostrarRequisitos?: boolean;
  placeholder?: string;
  autoComplete?: string;
}) {
  const id = useId();
  const [visivel, setVisivel] = useState(false);
  const [capsLock, setCapsLock] = useState(false);

  const verificarCaps = (e: KeyboardEvent<HTMLInputElement>) => setCapsLock(e.getModifierState?.('CapsLock') ?? false);

  return (
    <div className="campo-grupo">
      <label className="campo" htmlFor={id}>{rotulo}</label>
      <div className="campo-senha">
        <input id={id} type={visivel ? 'text' : 'password'} value={valor} autoComplete={autoComplete}
          placeholder={placeholder} maxLength={SENHA_MAXIMO} aria-describedby={mostrarRequisitos ? `${id}-req` : undefined}
          onChange={(e) => onMudar(e.target.value)} onKeyDown={verificarCaps} onKeyUp={verificarCaps}
          onBlur={() => setCapsLock(false)} />
        <button type="button" className="alternar-senha" onClick={() => setVisivel((v) => !v)}
          aria-label={visivel ? 'Ocultar senha' : 'Mostrar senha'} title={visivel ? 'Ocultar senha' : 'Mostrar senha'}>
          {visivel ? <EyeOff size={16} strokeWidth={1.75} /> : <Eye size={16} strokeWidth={1.75} />}
        </button>
      </div>
      {capsLock && (
        <div className="aviso-caps" role="alert">
          <ArrowBigUp size={15} strokeWidth={2} aria-hidden /> Caps Lock ativado
        </div>
      )}
      {mostrarRequisitos && (
        <ul className="requisitos-senha" id={`${id}-req`}>
          {requisitosSenha(valor).map((r) => (
            <li key={r.rotulo} className={r.ok ? 'ok' : undefined}>
              <span className="marcador" aria-hidden>{r.ok && <Check size={11} strokeWidth={3} />}</span>
              {r.rotulo}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
