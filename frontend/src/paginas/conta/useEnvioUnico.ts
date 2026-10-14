import { useCallback, useRef, useState } from 'react';

import { ErroDaApi } from '../../api/erros';

/** Uma ação por vez (trava contra duplo clique), com a falha já como ErroDaApi. */
export function useEnvioUnico() {
  const [enviando, setEnviando] = useState(false);
  const emEnvio = useRef(false);

  const executar = useCallback(async (acao: () => Promise<void>, aoFalhar: (erro: ErroDaApi) => void) => {
    if (emEnvio.current) {
      return;
    }
    emEnvio.current = true;
    setEnviando(true);
    try {
      await acao();
    } catch (falha) {
      aoFalhar(falha instanceof ErroDaApi ? falha : new ErroDaApi('servidor'));
    } finally {
      emEnvio.current = false;
      setEnviando(false);
    }
  }, []);

  return { enviando, executar };
}
