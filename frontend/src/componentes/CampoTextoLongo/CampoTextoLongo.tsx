import { useId } from 'react';

import estilos from '../CampoTexto/CampoTexto.module.css';
import proprios from './CampoTextoLongo.module.css';

interface CampoTextoLongoProps {
  nome: string;
  rotulo: string;
  valor: string;
  aoMudar: (valor: string) => void;
  maxLength: number;
  dica?: string;
  erro?: string;
  exemplo?: string;
}

/** Texto de várias linhas com rótulo visível e contador de letras (o limite também vale no servidor). */
export function CampoTextoLongo({ nome, rotulo, valor, aoMudar, maxLength, dica, erro, exemplo }: CampoTextoLongoProps) {
  const id = useId();
  const descricao = [erro ? `${id}-erro` : '', dica ? `${id}-dica` : '', `${id}-contador`].filter(Boolean).join(' ');
  return (
    <div className={estilos.campo}>
      <label className={estilos.rotulo} htmlFor={id}>
        {rotulo}
      </label>
      <textarea
        className={`${estilos.entrada} ${proprios.area}`}
        id={id}
        name={nome}
        value={valor}
        maxLength={maxLength}
        placeholder={exemplo}
        onChange={(evento) => aoMudar(evento.target.value)}
        aria-invalid={erro ? true : undefined}
        aria-describedby={descricao}
      />
      {erro && (
        <p className={estilos.erro} id={`${id}-erro`}>
          {erro}
        </p>
      )}
      {dica && (
        <p className={estilos.dica} id={`${id}-dica`}>
          {dica}
        </p>
      )}
      <p className={estilos.dica} id={`${id}-contador`}>
        {`${valor.length} de ${maxLength} letras`}
      </p>
    </div>
  );
}
