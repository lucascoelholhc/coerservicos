import estilos from './Carimbo.module.css';

export type TipoDeCarimbo = 'guardado' | 'aprovado' | 'liberado' | 'recusado';

const CARIMBOS: Record<TipoDeCarimbo, { texto: string; tom: 'marca' | 'ok' | 'bad' }> = {
  guardado: { texto: 'Guardado', tom: 'marca' },
  aprovado: { texto: 'Aprovado', tom: 'marca' },
  liberado: { texto: 'Liberado', tom: 'ok' },
  recusado: { texto: 'Recusado', tom: 'bad' },
};

interface CarimboProps {
  tipo: TipoDeCarimbo;
  /** Bate o carimbo uma vez ao aparecer (desligado com prefers-reduced-motion). */
  animar?: boolean;
  /** Versão menor, dentro da célula do dia. */
  compacto?: boolean;
}

/** Selo inclinado do "Dia carimbado". Texto real (a caixa alta é só visual). */
export function Carimbo({ tipo, animar = false, compacto = false }: CarimboProps) {
  const { texto, tom } = CARIMBOS[tipo];
  return (
    <span
      className={estilos.carimbo}
      data-tom={tom}
      data-animar={animar ? 'true' : undefined}
      data-compacto={compacto ? 'true' : undefined}
    >
      {texto}
    </span>
  );
}
