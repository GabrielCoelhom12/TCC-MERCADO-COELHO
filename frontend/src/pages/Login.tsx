import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../auth';
import CampoSenha from '../components/CampoSenha';
import { mensagemErro } from '../components/ui';

export default function Login() {
  const { entrar } = useAuth();
  const navegar = useNavigate();
  const [login, setLogin] = useState('');
  const [senha, setSenha] = useState('');
  const [erro, setErro] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  const enviar = async (e: FormEvent) => {
    e.preventDefault();
    setEnviando(true);
    setErro(null);
    try {
      await entrar(login, senha);
      navegar('/', { replace: true });
    } catch (err) {
      setErro(mensagemErro(err));
    } finally {
      setEnviando(false);
    }
  };

  return (
    <div className="login">
      <div className="login-arte" aria-hidden><img src="/logo-branco.svg" alt="" /></div>
      <div className="login-form">
        <form onSubmit={enviar}>
          <img className="login-logo" src="/logo-preto.svg" alt="Coelho Supermercado" />
          <div>
            <h1>Escalas de trabalho</h1>
            <p className="muted">Acesse com seu usuário e senha.</p>
          </div>
          <label className="campo">
            Usuário
            <input value={login} onChange={(e) => setLogin(e.target.value)} autoFocus autoComplete="username" />
          </label>
          <CampoSenha rotulo="Senha" valor={senha} onMudar={setSenha} autoComplete="current-password" />
          {erro && <div className="aviso erro">{erro}</div>}
          <button className="primario" disabled={enviando || !login || !senha}>
            {enviando ? 'Entrando…' : 'Entrar'}
          </button>
        </form>
      </div>
    </div>
  );
}
