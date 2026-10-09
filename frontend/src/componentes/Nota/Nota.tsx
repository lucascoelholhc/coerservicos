import type { ReactNode } from 'react';

import { Icone, type NomeDoIcone } from '../../icones/Icone';
import estilos from './Nota.module.css';

export type TipoDeNota = 'info' | 'atencao' | 'ok' | 'erro';

const ICONES: Record<TipoDeNota, NomeDoIcone> = {
  info: 'info',
  atencao: 'alert',
  ok: 'check',
  erro: 'alert',
};

interface NotaProps {
  tipo?: TipoDeNota;
  children: ReactNode;
  /**
   * Só quando a nota aparece depois de uma ação (salvar, pagar...): erro vira `alert`, os outros
   * `status`. Nota fixa da tela fica sem papel para não ser lida duas vezes.
   */
  anunciar?: boolean;
}

export function Nota({ tipo = 'info', children, anunciar = false }: NotaProps) {
  const papel = anunciar ? (tipo === 'erro' ? 'alert' : 'status') : undefined;
  return (
    <div className={estilos.nota} data-tipo={tipo} role={papel}>
      <Icone nome={ICONES[tipo]} className={estilos.icone} />
      <div>{children}</div>
    </div>
  );
}
