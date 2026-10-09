import { useId, type ReactNode, type Ref } from 'react';

import estilos from './CampoTexto.module.css';

export interface CampoTextoProps {
  nome: string;
  rotulo: string;
  valor: string;
  aoMudar: (valor: string) => void;
  tipo?: 'text' | 'email' | 'tel' | 'password';
  autoComplete?: string;
  inputMode?: 'text' | 'email' | 'tel' | 'numeric';
  maxLength?: number;
  /** Ajuda curta, sempre visível e lida pelo leitor de tela. */
  dica?: string;
  /** Erro deste campo (da API ou do aviso antes de enviar), ao lado do campo. */
  erro?: string;
  desabilitado?: boolean;
  /** Algo ao lado do campo (ex.: "Mostrar senha"). */
  acessorio?: ReactNode;
  ref?: Ref<HTMLInputElement>;
}

/** Campo de texto com rótulo visível; erro e dica ligados por aria-describedby, erro com aria-invalid. */
export function CampoTexto({
  nome,
  rotulo,
  valor,
  aoMudar,
  tipo = 'text',
  autoComplete,
  inputMode,
  maxLength,
  dica,
  erro,
  desabilitado = false,
  acessorio,
  ref,
}: CampoTextoProps) {
  const id = useId();
  const idDaDica = `${id}-dica`;
  const idDoErro = `${id}-erro`;
  const descricao = [erro ? idDoErro : '', dica ? idDaDica : ''].filter(Boolean).join(' ');
  return (
    <div className={estilos.campo}>
      <label className={estilos.rotulo} htmlFor={id}>
        {rotulo}
      </label>
      <div className={estilos.caixa}>
        <input
          ref={ref}
          className={estilos.entrada}
          id={id}
          name={nome}
          type={tipo}
          value={valor}
          onChange={(evento) => aoMudar(evento.target.value)}
          autoComplete={autoComplete}
          inputMode={inputMode}
          maxLength={maxLength}
          disabled={desabilitado}
          aria-invalid={erro ? true : undefined}
          aria-describedby={descricao || undefined}
        />
        {acessorio}
      </div>
      {erro && (
        <p className={estilos.erro} id={idDoErro}>
          {erro}
        </p>
      )}
      {dica && (
        <p className={estilos.dica} id={idDaDica}>
          {dica}
        </p>
      )}
    </div>
  );
}
