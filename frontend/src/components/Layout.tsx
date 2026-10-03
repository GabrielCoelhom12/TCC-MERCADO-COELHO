import { useLayoutEffect, useRef, useState, type CSSProperties } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import {
  CalendarDays, CalendarRange, Calculator, Clock3, LayoutDashboard, LogOut, MessageCircle, SlidersHorizontal, Store, TrendingUp,
  UserCog, Users,
  type LucideIcon,
} from 'lucide-react';
import { useAuth } from '../auth';
import { useChat } from '../chat/ChatProvider';
import Avatar from './Avatar';

type Item = { para: string; rotulo: string; Icone: LucideIcon; exato?: boolean };
type Grupo = { titulo: string; itens: Item[] };

const GRUPOS: Grupo[] = [
  {
    titulo: 'Operação',
    itens: [
      { para: '/', rotulo: 'Painel financeiro', Icone: LayoutDashboard, exato: true },
      { para: '/escalas', rotulo: 'Escalas', Icone: CalendarDays },
      { para: '/movimento', rotulo: 'Movimento e previsão', Icone: TrendingUp },
      { para: '/simulador', rotulo: 'Simulador de custos', Icone: Calculator },
      { para: '/mensagens', rotulo: 'Mensagens', Icone: MessageCircle },
    ],
  },
  {
    titulo: 'Cadastros e regras',
    itens: [
      { para: '/funcionarios', rotulo: 'Colaboradores', Icone: Users },
      { para: '/setores', rotulo: 'Setores e demanda', Icone: Store },
      { para: '/turnos', rotulo: 'Modelos de turno', Icone: Clock3 },
      { para: '/calendario', rotulo: 'Feriados e receita', Icone: CalendarRange },
      { para: '/parametros', rotulo: 'Parâmetros', Icone: SlidersHorizontal },
    ],
  },
];

const ADMINISTRACAO: Grupo = { titulo: 'Administração', itens: [{ para: '/usuarios', rotulo: 'Usuários', Icone: UserCog }] };

/** Posiciona o destaque sobre o item ativo e o faz deslizar quando a rota muda. */
function useIndicador(caminho: string) {
  const nav = useRef<HTMLElement>(null);
  const [estilo, setEstilo] = useState<CSSProperties>({ opacity: 0 });
  const animar = useRef(false);

  useLayoutEffect(() => {
    const medir = () => {
      const ativo = nav.current?.querySelector<HTMLElement>('a.active');
      if (!ativo) {
        setEstilo({ opacity: 0 });
        return;
      }
      setEstilo({
        opacity: 1,
        width: ativo.offsetWidth,
        height: ativo.offsetHeight,
        transform: `translate(${ativo.offsetLeft}px, ${ativo.offsetTop}px)`,
        transition: animar.current ? undefined : 'none',
      });
      ativo.scrollIntoView?.({ block: 'nearest', inline: 'nearest', behavior: animar.current ? 'smooth' : 'auto' });
      animar.current = true;
    };
    medir();
    const observador = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(medir);
    if (nav.current) observador?.observe(nav.current);
    document.fonts?.ready.then(medir);
    return () => observador?.disconnect();
  }, [caminho]);

  return { nav, estilo };
}

export default function Layout() {
  const { usuario, sair } = useAuth();
  const { pathname } = useLocation();
  const { nav, estilo } = useIndicador(pathname);
  const { naoLidas } = useChat();
  const grupos = usuario?.perfil === 'ADMIN' ? [...GRUPOS, ADMINISTRACAO] : GRUPOS;

  return (
    <div className="app">
      <aside className="sidebar">
        <div className="marca">
          <img src="/logo-branco.svg" alt="Coelho Supermercado" />
        </div>
        <nav className="navegacao" aria-label="Menu principal" ref={nav}>
          <span className="nav-indicador" style={estilo} aria-hidden />
          {grupos.map((g) => (
            <div className="nav-secao" key={g.titulo}>
              <div className="nav-grupo">{g.titulo}</div>
              {g.itens.map(({ para, rotulo, Icone, exato }) => (
                <NavLink key={para} to={para} end={exato}>
                  <Icone className="nav-icone" size={18} strokeWidth={1.75} aria-hidden />
                  <span>{rotulo}</span>
                  {para === '/mensagens' && naoLidas > 0 && (
                    <span className="nav-contador" aria-label={`${naoLidas} mensagens não lidas`}>{naoLidas > 99 ? '99+' : naoLidas}</span>
                  )}
                </NavLink>
              ))}
            </div>
          ))}
        </nav>
        <div className="rodape">
          <div className="usuario">
            <Avatar nome={usuario?.nome ?? ''} foto={usuario?.foto} tamanho={30} />
            <span className="nome">{usuario?.nome}</span>
          </div>
          <button className="sair" onClick={sair}>
            <LogOut size={16} strokeWidth={1.75} aria-hidden />
            Sair
          </button>
        </div>
      </aside>
      <main className="conteudo">
        <div className="pagina" key={pathname}>
          <Outlet />
        </div>
      </main>
    </div>
  );
}
