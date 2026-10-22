import { createContext, useCallback, useEffect, useRef, useState } from 'react';

import type { ErroDaApi } from '../../../api/erros';
import type { CadastroDoProfissional, Catalogo, MinhaConta, Regras } from '../../../api/tipos';
import { useEnvioUnico } from '../../conta/useEnvioUnico';

/** Para escolha única: o Opcoes entrega a lista; a etapa quer só o valor escolhido. */
export function umaSo(aoEscolher: (valor: string) => void): (valores: string[]) => void {
  return ([valor]) => {
    if (valor !== undefined) {
      aoEscolher(valor);
    }
  };
}

/** Verdadeiro depois que a pessoa trocou de etapa: a etapa nova põe o foco no título (troca de tela). */
export const TrocouDeEtapa = createContext(false);

/** O que toda etapa recebe da página do cadastro. */
export interface PropsDaEtapa {
  numero: number;
  cadastro: CadastroDoProfissional;
  catalogo: Catalogo;
  regras: Regras;
  conta: MinhaConta;
  /** Salvou: guarda o rascunho novo e vai para a próxima etapa. */
  aoSalvar: (cadastro: CadastroDoProfissional) => void;
  /** Mudou sem sair da etapa (ex.: depois de mandar uma foto). */
  aoAtualizar: (cadastro: CadastroDoProfissional) => void;
  aoVoltar: () => void;
  irPara: (numero: number) => void;
}

const SEM_CAMPOS: Readonly<Record<string, string>> = {};

/**
 * Salvar uma etapa: uma vez por clique, erro geral e erros por campo (código da API ou do aviso antes de
 * enviar). Cada etapa diz quais códigos da API viram erro de um campo.
 */
export function useSalvarEtapa(
  aoSalvar: (cadastro: CadastroDoProfissional) => void,
  camposPorCodigo: Readonly<Record<string, string>> = SEM_CAMPOS,
) {
  const { enviando, executar } = useEnvioUnico();
  const [erro, setErro] = useState<string | null>(null);
  const [campos, setCampos] = useState<Record<string, string>>({});

  const salvar = useCallback(
    (acao: () => Promise<CadastroDoProfissional>) => {
      setErro(null);
      setCampos({});
      void executar(
        async () => aoSalvar(await acao()),
        (problema: ErroDaApi) => {
          const campo = problema.codigo ? camposPorCodigo[problema.codigo] : undefined;
          if (campo) {
            setCampos({ [campo]: problema.message });
          } else {
            setErro(problema.message);
            setCampos({ ...problema.campos });
          }
        },
      );
    },
    [aoSalvar, camposPorCodigo, executar],
  );

  /** Aviso antes de enviar (sem chamar a API): erros por campo e, se quiser, um geral. */
  const avisar = useCallback((porCampo: Record<string, string>, geral: string | null = null) => {
    setCampos(porCampo);
    setErro(geral);
  }, []);

  return { enviando, erro, campos, salvar, avisar };
}

/** Prévia da foto escolhida (só no navegador, blob:); some ao sair da etapa. */
export function usePrevias() {
  const [previas, setPrevias] = useState<Partial<Record<string, string>>>({});
  const todas = useRef<string[]>([]);
  useEffect(() => {
    const urls = todas.current;
    return () => urls.forEach((url) => URL.revokeObjectURL(url));
  }, []);
  function guardar(chave: string, arquivo: File) {
    if (typeof URL.createObjectURL !== 'function') {
      return;
    }
    const url = URL.createObjectURL(arquivo);
    todas.current.push(url);
    setPrevias((anteriores) => ({ ...anteriores, [chave]: url }));
  }
  return { previas, guardar };
}
