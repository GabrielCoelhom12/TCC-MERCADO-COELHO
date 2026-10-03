import { useEffect, useState } from 'react';
import { Mic, MicOff, Phone, PhoneOff } from 'lucide-react';
import Avatar from '../components/Avatar';
import { useChamada, type EstadoChamada } from './ChamadaProvider';

const doisDigitos = (n: number) => String(n).padStart(2, '0');

function Cronometro({ desde }: { desde: number }) {
  const [agora, setAgora] = useState(desde);
  useEffect(() => {
    const id = window.setInterval(() => setAgora(Date.now()), 1000);
    return () => window.clearInterval(id);
  }, []);
  const total = Math.max(0, Math.floor((agora - desde) / 1000));
  return <>{doisDigitos(Math.floor(total / 60))}:{doisDigitos(total % 60)}</>;
}

/** Painel flutuante da chamada de voz, visível em qualquer tela do sistema. */
export default function PainelChamada({ estado }: { estado: EstadoChamada }) {
  const { atender, recusar, desligar, alternarMudo } = useChamada();
  const { fase, outro, mudo } = estado;
  const tocando = fase === 'recebendo' || fase === 'chamando';

  const status = {
    recebendo: 'Chamada de voz recebida',
    chamando: 'Chamando…',
    conectando: 'Conectando…',
    'em-andamento': null,
    encerrada: estado.mensagem,
  }[fase];

  return (
    <div className={`painel-chamada fase-${fase}`} role="dialog" aria-label="Chamada de voz">
      <div className="painel-chamada-topo">
        <span className={`painel-avatar ${tocando ? 'pulsando' : ''}`}>
          <Avatar nome={outro.nome} foto={outro.foto} tamanho={52} className="avatar-linha" />
        </span>
        <div className="painel-chamada-texto">
          <strong>{outro.nome}</strong>
          <span className="painel-status">
            {fase === 'em-andamento' && estado.conectadaEm ? <Cronometro desde={estado.conectadaEm} /> : status}
          </span>
        </div>
      </div>

      {fase !== 'encerrada' && (
        <div className="painel-acoes">
          {fase === 'recebendo' && (
            <>
              <button className="acao-chamada desligar" onClick={() => void recusar()} aria-label="Recusar">
                <PhoneOff size={20} aria-hidden />
              </button>
              <button className="acao-chamada atender" onClick={() => void atender()} aria-label="Atender">
                <Phone size={20} aria-hidden />
              </button>
            </>
          )}
          {fase === 'chamando' && (
            <button className="acao-chamada desligar" onClick={() => void desligar()} aria-label="Cancelar">
              <PhoneOff size={20} aria-hidden />
            </button>
          )}
          {(fase === 'conectando' || fase === 'em-andamento') && (
            <>
              <button className={`acao-chamada mudo ${mudo ? 'ativo' : ''}`} onClick={alternarMudo}
                aria-label={mudo ? 'Ativar microfone' : 'Silenciar microfone'} aria-pressed={mudo}>
                {mudo ? <MicOff size={20} aria-hidden /> : <Mic size={20} aria-hidden />}
              </button>
              <button className="acao-chamada desligar" onClick={() => void desligar()} aria-label="Desligar">
                <PhoneOff size={20} aria-hidden />
              </button>
            </>
          )}
        </div>
      )}
    </div>
  );
}
