import { useEffect, useState } from 'react';
import { sessao } from '../api';

/** Cache por URL: cada imagem é baixada uma única vez por sessão do navegador. */
const cache = new Map<string, Promise<string | null>>();

function baixar(url: string) {
  if (!cache.has(url)) {
    cache.set(url, fetch(url, { headers: { Authorization: `Bearer ${sessao.token()}` } })
      .then((r) => (r.ok ? r.blob() : Promise.reject(new Error(String(r.status)))))
      .then((b) => (typeof URL.createObjectURL === 'function' ? URL.createObjectURL(b) : null))
      .catch(() => {
        cache.delete(url);
        return null;
      }));
  }
  return cache.get(url)!;
}

/** Imagens do chat exigem o token (um <img src> comum não o envia): baixa com autenticação e devolve uma URL local. */
export function useImagemProtegida(url: string | null) {
  const [src, setSrc] = useState<string | null>(null);
  useEffect(() => {
    if (!url) return;
    let ativo = true;
    void baixar(url).then((s) => ativo && setSrc(s));
    return () => {
      ativo = false;
    };
  }, [url]);
  return src;
}
