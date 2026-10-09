import { Icone } from '../../icones/Icone';
import estilos from './Chip.module.css';

interface ChipProps {
  children: string;
  /** Com esta função o chip ganha o botão "Remover <nome>". */
  aoRemover?: () => void;
}

export function Chip({ children, aoRemover }: ChipProps) {
  if (!aoRemover) {
    return <span className={estilos.chip}>{children}</span>;
  }
  return (
    <span className={estilos.chip} data-removivel="true">
      {children}
      <button type="button" className={estilos.remover} onClick={aoRemover} aria-label={`Remover ${children}`}>
        <Icone nome="x" tamanho={16} />
      </button>
    </span>
  );
}
