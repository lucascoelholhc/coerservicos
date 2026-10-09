import { useEffect } from 'react';

const SITE = 'COE Serviços';

/** Título da aba: "<página> · COE Serviços" (ou só o nome do site no Início). */
export function useTitulo(pagina?: string): void {
  useEffect(() => {
    document.title = pagina ? `${pagina} · ${SITE}` : SITE;
  }, [pagina]);
}
