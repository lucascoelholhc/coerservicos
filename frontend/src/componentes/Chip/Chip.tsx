import { Icone } from '../../icones/Icone';
import estilos from './Chip.module.css';

interface ChipProps {
  children: string;
  /** Com esta função o chip ganha o botão "Remover <nome>". */
  aoRemover?: () => void;
  /** Nome do botão de remover, quando "Remover <nome>" não basta (ex.: "Remover filtro Blumenau"). */
  rotuloDoRemover?: string;
}

export function Chip({ children, aoRemover, rotuloDoRemover }: ChipProps) {
  if (!aoRemover) {
    return <span className={estilos.chip}>{children}</span>;
  }
  return (
    <span className={estilos.chip} data-removivel="true">
      {children}
      <button
        type="button"
        className={estilos.remover}
        onClick={aoRemover}
        aria-label={rotuloDoRemover ?? `Remover ${children}`}
        data-alvo="44"
      >
        <Icone nome="x" tamanho={16} />
      </button>
    </span>
  );
}
