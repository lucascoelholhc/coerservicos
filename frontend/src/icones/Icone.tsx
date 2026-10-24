import type { ReactElement } from 'react';

/** Traços do ic() do protótipo (ef7d397, js/app.js): 24x24, linha da cor do texto. */
const DESENHOS = {
  home: (
    <>
      <path d="M3 11l9-8 9 8" />
      <path d="M5 10v10h14V10" />
    </>
  ),
  search: (
    <>
      <circle cx="11" cy="11" r="7" />
      <path d="M21 21l-4.3-4.3" />
    </>
  ),
  user: (
    <>
      <circle cx="12" cy="8" r="4" />
      <path d="M4 21c1.5-4 4.5-6 8-6s6.5 2 8 6" />
    </>
  ),
  help: (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="M9.5 9.5a2.5 2.5 0 015 .5c0 1.5-2.5 2-2.5 3.5M12 17v.5" />
    </>
  ),
  hand: (
    <path d="M7 11V6a1.5 1.5 0 013 0v4M10 10V4.5a1.5 1.5 0 013 0V10M13 10V5.5a1.5 1.5 0 013 0V12M16 9.5a1.5 1.5 0 013 0V14a7 7 0 01-7 7h-1a6 6 0 01-5-3l-2.5-4.5a1.5 1.5 0 012.5-1.6L7 13" />
  ),
  check: <path d="M5 12l5 5 9-10" />,
  x: <path d="M6 6l12 12M18 6L6 18" />,
  info: (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 11v6M12 7.5v.5" />
    </>
  ),
  xcircle: (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="M9 9l6 6M15 9l-6 6" />
    </>
  ),
  alert: (
    <>
      <path d="M12 3l10 18H2z" />
      <path d="M12 10v5M12 18v.5" />
    </>
  ),
  camera: (
    <>
      <path d="M4 8h3l2-3h6l2 3h3v11H4z" />
      <circle cx="12" cy="13" r="3.5" />
    </>
  ),
  shield: (
    <>
      <path d="M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6z" />
      <path d="M9 12l2 2 4-4" />
    </>
  ),
  clock: (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 7v5l3 2" />
    </>
  ),
  wallet: (
    <>
      <rect x="3" y="6" width="18" height="14" rx="2" />
      <path d="M3 10h18" />
      <circle cx="16.5" cy="15" r="1.2" />
    </>
  ),
  next: <path d="M9 5l7 7-7 7" />,
  back: <path d="M15 5l-7 7 7 7" />,
  trash: <path d="M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13" />,
  pin: (
    <>
      <path d="M12 21s-7-6.5-7-12a7 7 0 0114 0c0 5.5-7 12-7 12z" />
      <circle cx="12" cy="9" r="2.5" />
    </>
  ),
  cal: (
    <>
      <rect x="3" y="5" width="18" height="16" rx="2" />
      <path d="M3 10h18M8 3v4M16 3v4" />
    </>
  ),
  filter: <path d="M4 5h16l-6 7v6l-4 2v-8z" />,
  star: <path d="M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.8l6.1-.9z" fill="currentColor" />,
  bolt: <path d="M13 2L4 14h7l-1 8 9-12h-7z" />,
  lock: (
    <>
      <rect x="5" y="11" width="14" height="10" rx="2" />
      <path d="M8 11V7a4 4 0 018 0v4" />
    </>
  ),
  eyeoff: (
    <>
      <path d="M3 3l18 18" />
      <path d="M10.6 5.1A10 10 0 0112 5c5 0 9 4.5 10 7-.4 1-1.2 2.3-2.4 3.5M6.6 6.6C4.3 8 2.7 10.2 2 12c1 2.5 5 7 10 7 1.8 0 3.4-.5 4.8-1.3" />
      <path d="M9.9 9.9a3 3 0 004.2 4.2" />
    </>
  ),
} satisfies Record<string, ReactElement>;

export type NomeDoIcone = keyof typeof DESENHOS;

export const NOMES_DE_ICONE = Object.keys(DESENHOS) as NomeDoIcone[];

interface IconeProps {
  nome: NomeDoIcone;
  tamanho?: number;
  className?: string;
}

/** Ícone decorativo: sempre acompanha um texto visível ou um nome acessível no elemento pai. */
export function Icone({ nome, tamanho = 22, className }: IconeProps) {
  return (
    <svg
      className={className}
      width={tamanho}
      height={tamanho}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
      data-icone={nome}
    >
      {DESENHOS[nome]}
    </svg>
  );
}
