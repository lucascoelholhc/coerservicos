import type { Ref } from 'react';

import { CampoTexto } from '../CampoTexto/CampoTexto';
import estilos from './CampoCodigo.module.css';
import { ESPERA_PADRAO_EM_SEGUNDOS, MandarDeNovo } from './MandarDeNovo';

export const DIGITOS_DO_CODIGO = 6;

export function codigoCompleto(codigo: string): boolean {
  return codigo.length === DIGITOS_DO_CODIGO;
}

interface CampoCodigoProps {
  valor: string;
  aoMudar: (codigo: string) => void;
  rotulo?: string;
  erro?: string;
  ref?: Ref<HTMLInputElement>;
  /** Pede outro código. Sem ela, não há "Mandar outro código" (ex.: segundo passo do MFA). */
  aoReenviar?: () => Promise<void>;
  /** Segundos até poder pedir outro código; a contagem começa ao abrir (um código acabou de ser enviado). */
  espera?: number;
}

/** O código de 6 números que chega por SMS, usado em toda tela de código. */
export function CampoCodigo({
  valor,
  aoMudar,
  rotulo = 'Código',
  erro,
  ref,
  aoReenviar,
  espera = ESPERA_PADRAO_EM_SEGUNDOS,
}: CampoCodigoProps) {
  return (
    <div className={estilos.campo}>
      <CampoTexto
        nome="codigo"
        rotulo={rotulo}
        valor={valor}
        aoMudar={(texto) => aoMudar(texto.replace(/\D/g, '').slice(0, DIGITOS_DO_CODIGO))}
        inputMode="numeric"
        autoComplete="one-time-code"
        erro={erro}
        ref={ref}
      />
      {aoReenviar && <MandarDeNovo aoReenviar={aoReenviar} espera={espera} />}
    </div>
  );
}
