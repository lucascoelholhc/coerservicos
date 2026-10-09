import { useCallback, useEffect, useRef, useState } from 'react';

import { ErroDaApi } from './erros';

export type EstadoDoRecurso<T> =
  | { situacao: 'carregando'; dados?: undefined; erro?: undefined }
  | { situacao: 'pronto' | 'vazio'; dados: T; erro?: undefined }
  | { situacao: 'erro'; dados?: undefined; erro: ErroDaApi };

/**
 * Busca um recurso da API para uma tela: carregando, pronto, vazio (pela regra dada) ou erro tipado,
 * com "tentar de novo". Ao desmontar, cancela a busca e ignora a resposta.
 */
export function useRecurso<T>(
  buscar: (sinal: AbortSignal) => Promise<T>,
  estaVazio?: (dados: T) => boolean,
): EstadoDoRecurso<T> & { tentarDeNovo: () => void } {
  const [estado, setEstado] = useState<EstadoDoRecurso<T>>({ situacao: 'carregando' });
  const [tentativa, setTentativa] = useState(0);
  // As funções podem mudar a cada render (lambdas): a busca só recomeça em "tentar de novo".
  const funcoes = useRef({ buscar, estaVazio });
  useEffect(() => {
    funcoes.current = { buscar, estaVazio };
  });

  useEffect(() => {
    const controle = new AbortController();
    funcoes.current.buscar(controle.signal).then(
      (dados) => {
        if (!controle.signal.aborted) {
          setEstado({ situacao: funcoes.current.estaVazio?.(dados) ? 'vazio' : 'pronto', dados });
        }
      },
      (falha: unknown) => {
        if (!controle.signal.aborted) {
          setEstado({ situacao: 'erro', erro: falha instanceof ErroDaApi ? falha : new ErroDaApi('servidor') });
        }
      },
    );
    return () => controle.abort();
  }, [tentativa]);

  const tentarDeNovo = useCallback(() => {
    setEstado({ situacao: 'carregando' });
    setTentativa((anterior) => anterior + 1);
  }, []);

  return { ...estado, tentarDeNovo };
}
