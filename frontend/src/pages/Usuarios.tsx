import { useState } from 'react';
import { api } from '../api';
import { useAuth } from '../auth';
import Avatar from '../components/Avatar';
import CampoSenha from '../components/CampoSenha';
import SeletorFoto, { aplicarFoto, fotoInicial, type EstadoFoto } from '../components/SeletorFoto';
import { Carregando, Modal, mensagemErro, useCarregar, useToast } from '../components/ui';
import { senhaForte } from '../senha';
import type { Perfil, Usuario } from '../types';

export const ROTULO_PERFIL: Record<Perfil, string> = {
  ADMIN: 'Administrador',
  GESTOR: 'Gestor',
  SUPERVISOR: 'Supervisor',
};

const DESCRICAO_PERFIL: Record<Perfil, string> = {
  ADMIN: 'Acesso total, inclusive à gestão de usuários.',
  GESTOR: 'Monta, ajusta e aprova escalas; mantém os cadastros.',
  SUPERVISOR: 'Acompanha escalas e cadastros do dia a dia.',
};

type Form = {
  id?: number;
  nome: string;
  login: string;
  perfil: Perfil;
  ativo: boolean;
  senha: string;
  foto: EstadoFoto;
};

const VAZIO: Form = { nome: '', login: '', perfil: 'GESTOR', ativo: true, senha: '', foto: fotoInicial() };

export default function Usuarios() {
  const { dados, erro, recarregar } = useCarregar(() => api.get<Usuario[]>('/usuarios'));
  const [form, setForm] = useState<Form | null>(null);
  const [salvando, setSalvando] = useState(false);
  const { usuario: logado, atualizar } = useAuth();
  const toast = useToast();

  if (!dados) return <Carregando erro={erro} />;

  const editando = !!form?.id;
  const senhaOk = !!form && ((editando && form.senha === '') || senhaForte(form.senha));
  const valido = !!form && form.nome.trim() !== '' && form.login.trim() !== '' && senhaOk;

  const salvar = async () => {
    if (!form) return;
    setSalvando(true);
    try {
      const { nome, login, perfil, ativo, senha } = form;
      const salvo = editando
        ? await api.put<Usuario>(`/usuarios/${form.id}`, { nome, login, perfil, ativo, senha: senha === '' ? null : senha })
        : await api.post<Usuario>('/usuarios', { nome, login, perfil, senha });

      await aplicarFoto(`/usuarios/${salvo.id}`, form.foto);

      toast('ok', editando ? 'Usuário atualizado.' : 'Usuário criado.');
      if (editando && logado && dados.find((u) => u.id === form.id)?.login === logado.login) {
        await atualizar().catch(() => undefined);
      }
      setForm(null);
      recarregar();
    } catch (e) {
      toast('erro', mensagemErro(e));
    } finally {
      setSalvando(false);
    }
  };

  const abrirEdicao = (u: Usuario) =>
    setForm({ id: u.id, nome: u.nome, login: u.login, perfil: u.perfil, ativo: u.ativo, senha: '', foto: fotoInicial(u.foto) });

  return (
    <div className="stack">
      <div className="cabecalho">
        <div>
          <h1>Usuários</h1>
          <p>Quem acessa o sistema e o que cada pessoa pode fazer.</p>
        </div>
        <div className="spacer" />
        <button className="primario" onClick={() => setForm({ ...VAZIO })}>Novo usuário</button>
      </div>

      <div className="card tabela-wrap">
        <table>
          <thead><tr><th>Nome</th><th>Login</th><th>Perfil</th><th>Situação</th><th></th></tr></thead>
          <tbody>
            {dados.map((u) => (
              <tr key={u.id} className={u.ativo ? undefined : 'inativo'}>
                <td>
                  <div className="row" style={{ gap: 10 }}>
                    <Avatar nome={u.nome} foto={u.foto} tamanho={32} className="avatar-linha" />
                    <strong>{u.nome}</strong>
                  </div>
                </td>
                <td className="muted">{u.login}</td>
                <td><span className={`badge ${u.perfil === 'ADMIN' ? 'laranja' : 'neutro'}`}>{ROTULO_PERFIL[u.perfil]}</span></td>
                <td><span className={`badge ${u.ativo ? 'ok' : 'neutro'}`}>{u.ativo ? 'Ativo' : 'Inativo'}</span></td>
                <td className="num"><button onClick={() => abrirEdicao(u)}>Editar</button></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <Modal titulo={editando ? 'Editar usuário' : 'Novo usuário'} aberto={!!form} onFechar={() => setForm(null)}
        acoes={<>
          <button onClick={() => setForm(null)}>Cancelar</button>
          <button className="primario" disabled={!valido || salvando} onClick={salvar}>
            {salvando ? 'Salvando…' : editando ? 'Salvar' : 'Criar usuário'}
          </button>
        </>}>
        {form && (
          <div className="stack">
            <SeletorFoto nome={form.nome} foto={form.foto} onMudar={(foto) => setForm({ ...form, foto })} />

            <div className="form-grid">
              <label className="campo">Nome<input value={form.nome} onChange={(e) => setForm({ ...form, nome: e.target.value })} autoFocus /></label>
              <label className="campo">Login
                <input value={form.login} autoComplete="off" onChange={(e) => setForm({ ...form, login: e.target.value })} />
              </label>
            </div>
            <div className="campo-grupo">
              <label className="campo">Perfil
                <select value={form.perfil} aria-describedby="ajuda-perfil"
                  onChange={(e) => setForm({ ...form, perfil: e.target.value as Perfil })}>
                  {(Object.keys(ROTULO_PERFIL) as Perfil[]).map((p) => <option key={p} value={p}>{ROTULO_PERFIL[p]}</option>)}
                </select>
              </label>
              <span className="ajuda" id="ajuda-perfil">{DESCRICAO_PERFIL[form.perfil]}</span>
            </div>
            <CampoSenha rotulo={editando ? 'Nova senha' : 'Senha'} valor={form.senha}
              onMudar={(senha) => setForm({ ...form, senha })}
              placeholder={editando ? 'Deixe em branco para manter a atual' : undefined}
              mostrarRequisitos={!editando || form.senha !== ''} />
            {editando && (
              <label className="row checkbox">
                <input type="checkbox" checked={form.ativo} onChange={(e) => setForm({ ...form, ativo: e.target.checked })} />
                Usuário ativo
              </label>
            )}
          </div>
        )}
      </Modal>
    </div>
  );
}
