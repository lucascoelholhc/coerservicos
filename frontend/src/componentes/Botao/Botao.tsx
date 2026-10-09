import type { MouseEventHandler, ReactNode } from 'react';
import { Link } from 'react-router';

import estilos from './Botao.module.css';

export type VarianteDoBotao = 'principal' | 'escuro' | 'contorno' | 'suave' | 'perigo' | 'sucesso';
export type TamanhoDoBotao = 'sm' | 'padrao' | 'lg' | 'xl';

interface Comum {
  children: ReactNode;
  /** Uma ação principal por tela (docs/identidade-visual.md). */
  variante?: VarianteDoBotao;
  /** sm 44 px, padrão 52 px, lg 58 px, xl 68 px (largura toda). */
  tamanho?: TamanhoDoBotao;
  /** Sobre o fundo --carimbo, a ação vira amarela com texto --tinta. */
  sobreFundoEscuro?: boolean;
}

interface ComoBotao extends Comum {
  para?: undefined;
  type?: 'button' | 'submit';
  onClick?: MouseEventHandler<HTMLButtonElement>;
  desabilitado?: boolean;
}

interface ComoLink extends Comum {
  /** Rota do React Router: vira um link com cara de botão. */
  para: string;
}

export type BotaoProps = ComoBotao | ComoLink;

export function Botao(props: BotaoProps) {
  const { children, variante = 'principal', tamanho = 'padrao', sobreFundoEscuro = false } = props;
  const atributos = {
    className: estilos.botao,
    'data-variante': variante,
    'data-tamanho': tamanho,
    'data-fundo-escuro': sobreFundoEscuro ? 'true' : undefined,
  };

  if (props.para !== undefined) {
    return (
      <Link to={props.para} {...atributos}>
        {children}
      </Link>
    );
  }

  return (
    <button type={props.type ?? 'button'} onClick={props.onClick} disabled={props.desabilitado} {...atributos}>
      {children}
    </button>
  );
}
