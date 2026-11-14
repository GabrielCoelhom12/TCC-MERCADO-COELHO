import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from './auth';
import Layout from './components/Layout';
import Login from './pages/Login';
import Painel from './pages/Painel';
import Escalas from './pages/Escalas';
import MatrizEscala from './pages/MatrizEscala';
import Simulador from './pages/Simulador';
import Funcionarios from './pages/Funcionarios';
import Setores from './pages/Setores';
import Turnos from './pages/Turnos';
import Calendario from './pages/Calendario';
import ParametrosPage from './pages/Parametros';
import Usuarios from './pages/Usuarios';
import Movimento from './pages/Movimento';
import Ferias from './pages/Ferias';
import Afastamentos from './pages/Afastamentos';

export default function App() {
  const { usuario } = useAuth();
  if (!usuario) {
    return (
      <Routes>
        <Route path="*" element={<Login />} />
      </Routes>
    );
  }
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<Painel />} />
        <Route path="escalas" element={<Escalas />} />
        <Route path="escalas/:id" element={<MatrizEscala />} />
        <Route path="ferias" element={<Ferias />} />
        <Route path="afastamentos" element={<Afastamentos />} />
        <Route path="movimento" element={<Movimento />} />
        {usuario.perfil !== 'SUPERVISOR' && <Route path="simulador" element={<Simulador />} />}
        <Route path="funcionarios" element={<Funcionarios />} />
        <Route path="setores" element={<Setores />} />
        <Route path="turnos" element={<Turnos />} />
        <Route path="calendario" element={<Calendario />} />
        <Route path="parametros" element={<ParametrosPage />} />
        {usuario.perfil === 'ADMIN' && <Route path="usuarios" element={<Usuarios />} />}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}
