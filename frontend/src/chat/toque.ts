/**
 * Sons da chamada gerados com Web Audio (sem arquivos de áudio): "recebendo" toca dois bipes a cada 2 s;
 * "chamando" é o tom de espera de quem liga, mais baixo. Devolve a função que para o som.
 */
export function tocar(tipo: 'recebendo' | 'chamando'): () => void {
  const Contexto = window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
  if (!Contexto) return () => undefined;
  const ctx = new Contexto();

  const bipe = (inicio: number, frequencia: number, duracao: number, volume: number) => {
    const osc = ctx.createOscillator();
    const ganho = ctx.createGain();
    osc.frequency.value = frequencia;
    ganho.gain.setValueAtTime(0, inicio);
    ganho.gain.linearRampToValueAtTime(volume, inicio + 0.02);
    ganho.gain.setValueAtTime(volume, inicio + duracao - 0.05);
    ganho.gain.linearRampToValueAtTime(0, inicio + duracao);
    osc.connect(ganho).connect(ctx.destination);
    osc.start(inicio);
    osc.stop(inicio + duracao);
  };

  const ciclo = () => {
    const t = ctx.currentTime + 0.05;
    if (tipo === 'recebendo') {
      bipe(t, 880, 0.18, 0.18);
      bipe(t + 0.25, 660, 0.25, 0.18);
    } else {
      bipe(t, 425, 1, 0.06);
    }
  };
  ciclo();
  const intervalo = window.setInterval(ciclo, tipo === 'recebendo' ? 2000 : 3000);
  return () => {
    window.clearInterval(intervalo);
    void ctx.close();
  };
}
