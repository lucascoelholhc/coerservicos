import { useId } from 'react';

import estilos from './CampoSelecao.module.css';

export interface OpcaoDeSelecao {
  valor: string;
  texto: string;
}

interface CampoSelecaoProps {
  nome: string;
  rotulo: string;
  opcoes: readonly OpcaoDeSelecao[];
  valorInicial?: string;
}

/** Select nativo (o leitor de tela e o celular já sabem usar) com rótulo sempre visível. */
export function CampoSelecao({ nome, rotulo, opcoes, valorInicial }: CampoSelecaoProps) {
  // Id único por campo: o rótulo nunca liga ao select errado, mesmo com o componente repetido
  const id = useId();
  return (
    <div className={estilos.campo}>
      <label className={estilos.rotulo} htmlFor={id}>
        {rotulo}
      </label>
      <div className={estilos.caixa}>
        <select className={estilos.select} id={id} name={nome} defaultValue={valorInicial}>
          {opcoes.map((opcao) => (
            <option key={opcao.valor} value={opcao.valor}>
              {opcao.texto}
            </option>
          ))}
        </select>
        <svg className={estilos.seta} width="12" height="8" viewBox="0 0 12 8" aria-hidden="true" focusable="false">
          <path d="M1 1l5 5 5-5" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
        </svg>
      </div>
    </div>
  );
}
