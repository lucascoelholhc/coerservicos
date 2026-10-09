import { useState } from 'react';

import { CampoTexto, type CampoTextoProps } from '../CampoTexto/CampoTexto';
import estilos from './CampoSenha.module.css';

type CampoSenhaProps = Omit<CampoTextoProps, 'tipo' | 'acessorio' | 'inputMode'> & {
  autoComplete: 'current-password' | 'new-password';
};

/** Senha com "Mostrar senha" (aria-pressed diz se está à mostra). */
export function CampoSenha(props: CampoSenhaProps) {
  const [mostrar, setMostrar] = useState(false);
  return (
    <CampoTexto
      {...props}
      tipo={mostrar ? 'text' : 'password'}
      acessorio={
        <button
          type="button"
          className={estilos.mostrar}
          aria-pressed={mostrar}
          onClick={() => setMostrar((anterior) => !anterior)}
        >
          Mostrar senha
        </button>
      }
    />
  );
}
