import { useEffect, useRef, type RefObject } from 'react';
import { useLocation } from 'react-router';

/**
 * Navegação de SPA sem perder o lugar:
 * - endereço com âncora (/#como-funciona): rola até a seção depois de ela existir na tela;
 * - troca de página: leva o foco ao <main>, para leitor de tela e teclado recomeçarem no conteúdo.
 * Na primeira página o foco fica onde o navegador deixou.
 */
export function useNavegacaoNaPagina(principal: RefObject<HTMLElement | null>): void {
  const { pathname, hash } = useLocation();
  const paginaAnterior = useRef(pathname);

  useEffect(() => {
    const trocouDePagina = paginaAnterior.current !== pathname;
    paginaAnterior.current = pathname;
    if (hash !== '') {
      document.getElementById(decodeURIComponent(hash.slice(1)))?.scrollIntoView();
      return;
    }
    if (trocouDePagina) {
      principal.current?.focus();
    }
  }, [pathname, hash, principal]);
}
