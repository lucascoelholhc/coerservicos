import { Link } from 'react-router';

import estilos from './Logo.module.css';

interface SimboloProps {
  tamanho?: number;
  /** Para fundo escuro (rodapé): parede branca, telhado amarelo. */
  claro?: boolean;
}

/** Casa enxaimel do protótipo (SIMBOLO, ef7d397). As cores vêm das classes, nunca de hex aqui. */
export function Simbolo({ tamanho = 38, claro = false }: SimboloProps) {
  return (
    <svg
      className={estilos.simbolo}
      data-claro={claro ? 'true' : undefined}
      width={tamanho}
      height={tamanho}
      viewBox="0 0 64 64"
      aria-hidden="true"
      focusable="false"
    >
      <rect className={estilos.parede} x="12" y="28" width="40" height="30" strokeWidth="4.5" strokeLinejoin="round" />
      <path className={estilos.traco} d="M12 42H52M32 28V42M12 28L23 42M52 28L41 42" strokeWidth="3.6" strokeLinecap="round" />
      <rect className={estilos.porta} x="25" y="42" width="14" height="16" strokeWidth="3.6" strokeLinejoin="round" />
      <path className={estilos.telhado} d="M5 30L32 7L59 30" strokeWidth="6.5" strokeLinejoin="round" strokeLinecap="round" />
    </svg>
  );
}

/** Símbolo + "COE serviços", link para o início. */
export function Logo() {
  return (
    <Link to="/" className={estilos.logo} aria-label="COE Serviços, página inicial">
      <Simbolo />
      <span className={estilos.texto}>
        <b>COE</b>
        <small>serviços</small>
      </span>
    </Link>
  );
}
