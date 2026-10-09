import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { semViolacoes } from '../../teste/axe';
import { StatusDiaria, type EstadoDaDiaria } from './StatusDiaria';

/** docs/identidade-visual.md, seção 8 (fonte: ST e ST_PRO do protótipo). */
const TABELA: [EstadoDaDiaria, string, string, string][] = [
  ['agendada', 'Agendada', 'Marcado', 'neutro'],
  ['paga', 'Paga · guardada', 'Garantido', 'azul'],
  ['andamento', 'Em andamento', 'Hoje', 'aviso'],
  ['aguardando', 'Aprove o dia', 'Esperando o cliente', 'amarelo'],
  ['liberada', 'Liberada', 'Pago', 'ok'],
  ['contestada', 'Em análise', 'Reclamação', 'bad'],
  ['reembolsada', 'Reembolsada', 'Devolvido ao cliente', 'neutro'],
  ['cancelada', 'Cancelada', 'Cancelado', 'neutro'],
];

const COMBINACOES = TABELA.flatMap(([estado, cliente, profissional, tom]) => [
  [estado, 'cliente', cliente, tom] as const,
  [estado, 'profissional', profissional, tom] as const,
]);

describe('StatusDiaria (estado do backend + lado)', () => {
  it('cobre as 16 combinações', () => {
    expect(COMBINACOES).toHaveLength(16);
  });

  it.each(COMBINACOES)('%s para o %s: "%s" (tom %s, com ponto de cor e texto)', async (estado, lado, texto, tom) => {
    const { container } = render(<StatusDiaria estado={estado} lado={lado} />);
    const etiqueta = screen.getByText(texto);

    expect(etiqueta).toHaveAttribute('data-tom', tom);
    expect(etiqueta).toHaveAttribute('data-estado', estado);
    await semViolacoes(container);
  });
});
