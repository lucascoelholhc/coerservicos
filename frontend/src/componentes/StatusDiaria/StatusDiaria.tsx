import estilos from './StatusDiaria.module.css';

/** Estados da diária no backend (CLAUDE.md, máquina de estados). */
export type EstadoDaDiaria =
  | 'agendada'
  | 'paga'
  | 'andamento'
  | 'aguardando'
  | 'liberada'
  | 'contestada'
  | 'reembolsada'
  | 'cancelada';

export type LadoDaDiaria = 'cliente' | 'profissional';

type Tom = 'neutro' | 'azul' | 'aviso' | 'amarelo' | 'ok' | 'bad';

/** docs/identidade-visual.md, seção 8: o mesmo estado com a palavra de cada lado. */
const ROTULOS: Record<EstadoDaDiaria, { cliente: string; profissional: string; tom: Tom }> = {
  agendada: { cliente: 'Agendada', profissional: 'Marcado', tom: 'neutro' },
  paga: { cliente: 'Paga · guardada', profissional: 'Garantido', tom: 'azul' },
  andamento: { cliente: 'Em andamento', profissional: 'Hoje', tom: 'aviso' },
  aguardando: { cliente: 'Aprove o dia', profissional: 'Esperando o cliente', tom: 'amarelo' },
  liberada: { cliente: 'Liberada', profissional: 'Pago', tom: 'ok' },
  contestada: { cliente: 'Em análise', profissional: 'Reclamação', tom: 'bad' },
  reembolsada: { cliente: 'Reembolsada', profissional: 'Devolvido ao cliente', tom: 'neutro' },
  cancelada: { cliente: 'Cancelada', profissional: 'Cancelado', tom: 'neutro' },
};

interface StatusDiariaProps {
  estado: EstadoDaDiaria;
  lado: LadoDaDiaria;
}

/** Etiqueta de status: sempre texto + ponto de cor (nunca só cor). */
export function StatusDiaria({ estado, lado }: StatusDiariaProps) {
  const rotulo = ROTULOS[estado];
  return (
    <span className={estilos.status} data-tom={rotulo.tom} data-estado={estado}>
      {rotulo[lado]}
    </span>
  );
}
