import { createContext, useCallback, useEffect, useRef, useState } from 'react';

import { comoErroDaApi, type ErroDaApi } from '../../../api/erros';
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

/**
 * Mensagem de um envio de arquivo que falhou. Passou do tempo: o servidor pode ter gravado mesmo assim,
 * então a pessoa confere antes de mandar de novo (a lista é atualizada logo depois).
 */
export function mensagemDoEnvio(falha: unknown): string {
  const problema = comoErroDaApi(falha);
  return problema.tipo === 'tempo' ? `${problema.message} Confira se a foto já aparece antes de mandar de novo.` : problema.message;
}

/** URL assinada só com https (http só para o armazenamento local da máquina); outra coisa nunca vira link. */
export function enderecoSeguro(url: string): boolean {
  try {
    const endereco = new URL(url);
    const local = endereco.hostname === 'localhost' || endereco.hostname === '127.0.0.1';
    return endereco.protocol === 'https:' || (endereco.protocol === 'http:' && local);
  } catch {
    return false;
  }
}

/** Prévia da foto escolhida (só no navegador, blob:); some ao sair da etapa. */
export function usePrevias() {
  const [previas, setPrevias] = useState<Partial<Record<string, string>>>({});
  const urls = useRef(new Map<string, string>());
  const montada = useRef(true);
  useEffect(() => {
    const abertas = urls.current;
    montada.current = true;
    return () => {
      montada.current = false;
      abertas.forEach((url) => URL.revokeObjectURL(url));
      abertas.clear();
    };
  }, []);
  /** Ainda na etapa? O envio em série para quando a pessoa sai. */
  const ativa = useCallback(() => montada.current, []);
  function guardar(chave: string, arquivo: File) {
    if (!montada.current || typeof URL.createObjectURL !== 'function') {
      return;
    }
    const anterior = urls.current.get(chave);
    if (anterior) {
      URL.revokeObjectURL(anterior);
    }
    const url = URL.createObjectURL(arquivo);
    urls.current.set(chave, url);
    setPrevias((anteriores) => ({ ...anteriores, [chave]: url }));
  }
  return { previas, guardar, ativa };
}
