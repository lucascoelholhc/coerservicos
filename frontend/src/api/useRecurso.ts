import { useCallback, useEffect, useRef, useState } from 'react';

import { ErroDaApi } from './erros';

export type EstadoDoRecurso<T> =
  | { situacao: 'carregando'; dados?: undefined; erro?: undefined }
  | { situacao: 'pronto' | 'vazio'; dados: T; erro?: undefined }
  | { situacao: 'erro'; dados?: undefined; erro: ErroDaApi };

const CARREGANDO = { situacao: 'carregando' } as const;

/**
 * Busca um recurso da API para uma tela: carregando, pronto, vazio (pela regra dada) ou erro tipado,
 * com "tentar de novo". Ao desmontar, cancela a busca e ignora a resposta. Com {@link chave} (ex.: os
 * filtros da busca), trocar a chave cancela a busca anterior e busca de novo.
 */
export function useRecurso<T>(
  buscar: (sinal: AbortSignal) => Promise<T>,
  estaVazio?: (dados: T) => boolean,
  chave?: string,
): EstadoDoRecurso<T> & { tentarDeNovo: () => void } {
  // O estado lembra de qual chave ele é: chave nova = carregando até a resposta dela chegar.
  const [estado, setEstado] = useState<{ chave?: string; valor: EstadoDoRecurso<T> }>({ chave, valor: CARREGANDO });
  const [tentativa, setTentativa] = useState(0);
  // As funções podem mudar a cada render (lambdas): a busca só recomeça em "tentar de novo" ou com outra chave.
  const funcoes = useRef({ buscar, estaVazio });
  useEffect(() => {
    funcoes.current = { buscar, estaVazio };
  });

  useEffect(() => {
    const controle = new AbortController();
    funcoes.current.buscar(controle.signal).then(
      (dados) => {
        if (controle.signal.aborted) {
          return;
        }
        try {
          setEstado({ chave, valor: { situacao: funcoes.current.estaVazio?.(dados) ? 'vazio' : 'pronto', dados } });
        } catch {
          setEstado({ chave, valor: { situacao: 'erro', erro: new ErroDaApi('servidor') } });
        }
      },
      (falha: unknown) => {
        if (!controle.signal.aborted) {
          const erro = falha instanceof ErroDaApi ? falha : new ErroDaApi('servidor');
          setEstado({ chave, valor: { situacao: 'erro', erro } });
        }
      },
    );
    return () => controle.abort();
  }, [tentativa, chave]);

  const tentarDeNovo = useCallback(() => {
    setEstado((anterior) => ({ chave: anterior.chave, valor: CARREGANDO }));
    setTentativa((anterior) => anterior + 1);
  }, []);

  return { ...(estado.chave === chave ? estado.valor : CARREGANDO), tentarDeNovo };
}
