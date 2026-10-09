import { useEffect, useRef, useSyncExternalStore, type ReactNode } from 'react';
import { useLocation, useNavigate } from 'react-router';

import { definirAoPerderSessao, ouvirSessao, usuarioAtual } from './sessao';
import type { UsuarioDaSessao } from './tipos';
import { caminhoDeEntrar } from './voltar';

/** Liga a sessão em memória ao Router: sessão perdida leva para /entrar?voltar=<onde estava>. */
export function SessaoProvider({ children }: { children: ReactNode }) {
  const navegar = useNavigate();
  const local = useLocation();
  const ondeEsta = useRef(local);
  useEffect(() => {
    ondeEsta.current = local;
  });

  useEffect(() => {
    definirAoPerderSessao(() => {
      const { pathname, search, hash } = ondeEsta.current;
      void navegar(caminhoDeEntrar(`${pathname}${search}${hash}`));
    });
    return () => definirAoPerderSessao(() => undefined);
  }, [navegar]);

  return children;
}

/** Usuário da sessão atual (ou null para visitante), atualizado quando a sessão muda. */
export function useSessao(): UsuarioDaSessao | null {
  return useSyncExternalStore(ouvirSessao, usuarioAtual);
}
