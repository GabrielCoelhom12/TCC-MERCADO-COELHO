import { useEffect, useState } from 'react';
import { ImagePlus, Trash2 } from 'lucide-react';
import { api } from '../api';
import Avatar from './Avatar';
import { useToast } from './ui';

const TIPOS = ['image/png', 'image/jpeg'];
const LIMITE_BYTES = 2 * 1024 * 1024;

/** Foto atual (data URL vinda da API), nova foto escolhida e pedido de remoção, pendentes até salvar. */
export type EstadoFoto = { atual: string | null; nova: File | null; remover: boolean };

export const fotoInicial = (atual: string | null = null): EstadoFoto => ({ atual, nova: null, remover: false });

/** Envia ou remove a foto em `{recurso}/foto` conforme o que foi alterado no formulário. */
export async function aplicarFoto(recurso: string, foto: EstadoFoto) {
  if (foto.nova) {
    const dados = new FormData();
    dados.append('arquivo', foto.nova);
    await api.enviar(`${recurso}/foto`, dados);
  } else if (foto.remover && foto.atual) {
    await api.del(`${recurso}/foto`);
  }
}

export default function SeletorFoto({ nome, foto, onMudar }: {
  nome: string;
  foto: EstadoFoto;
  onMudar: (foto: EstadoFoto) => void;
}) {
  const [previa, setPrevia] = useState<string | null>(null);
  const toast = useToast();

  // Prévia local da foto escolhida, liberada ao trocar ou fechar.
  useEffect(() => {
    if (!foto.nova || typeof URL.createObjectURL !== 'function') {
      setPrevia(null);
      return;
    }
    const url = URL.createObjectURL(foto.nova);
    setPrevia(url);
    return () => URL.revokeObjectURL(url);
  }, [foto.nova]);

  const exibida = previa ?? (foto.remover ? null : foto.atual);
  const temFoto = !!foto.nova || (!foto.remover && !!foto.atual);

  const escolher = (arquivo: File | undefined) => {
    if (!arquivo) return;
    if (!TIPOS.includes(arquivo.type)) {
      toast('erro', 'A foto precisa ser uma imagem PNG ou JPEG.');
      return;
    }
    if (arquivo.size > LIMITE_BYTES) {
      toast('erro', 'A foto deve ter no máximo 2 MB.');
      return;
    }
    onMudar({ ...foto, nova: arquivo, remover: false });
  };

  return (
    <div className="foto-usuario">
      <Avatar nome={nome || '?'} foto={exibida} tamanho={72} className="avatar-grande" />
      <div className="stack" style={{ gap: 6 }}>
        <div className="row" style={{ gap: 8 }}>
          <label className="btn botao-arquivo">
            <ImagePlus size={16} strokeWidth={1.75} aria-hidden />
            {temFoto ? 'Trocar foto' : 'Escolher foto'}
            <input type="file" accept="image/png,image/jpeg" aria-label="Escolher foto"
              onChange={(e) => { escolher(e.target.files?.[0]); e.target.value = ''; }} />
          </label>
          {temFoto && (
            <button type="button" className="perigo" onClick={() => onMudar({ ...foto, nova: null, remover: true })}>
              <Trash2 size={15} strokeWidth={1.75} aria-hidden /> Remover foto
            </button>
          )}
        </div>
        <span className="ajuda">PNG ou JPEG de até 2 MB. A imagem é recortada em formato quadrado.</span>
      </div>
    </div>
  );
}
