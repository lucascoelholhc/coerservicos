import type { AgendaDoProfissional, DiariaDoProfissional, DinheiroDoProfissional } from '../api/tipos';
import { diaDaSemana, hojeEmSaoPaulo, somarDias } from '../formatacao/datas';
import { CONTRATO_PAGO } from './dadosDosContratos';

/** Dados da área do profissional (FE-06) para o Vitest e o Playwright, com as datas a partir de hoje. */
export const HOJE = hojeEmSaoPaulo();

/** O próximo dia (a partir de amanhã) que cai no dia da semana pedido (0 = domingo). */
export function proximo(dia: number, depoisDe = HOJE): string {
  let data = somarDias(depoisDe, 1);
  while (diaDaSemana(data) !== dia) {
    data = somarDias(data, 1);
  }
  return data;
}

function diaria(parcial: Partial<DiariaDoProfissional> & Pick<DiariaDoProfissional, 'id' | 'data'>): DiariaDoProfissional {
  return {
    contratoId: CONTRATO_PAGO.id,
    numero: CONTRATO_PAGO.numero,
    tipo: 'inteira',
    status: 'paga',
    repasse: '280.00',
    cliente: 'Carla M.',
    cidade: { nome: 'Blumenau', uf: 'SC' },
    servicos: ['Reboco', 'Assentamento de piso'],
    chegouEm: null,
    terminouEm: null,
    autoLiberaEm: null,
    ...parcial,
  };
}

export const DIARIA_DE_HOJE = diaria({ id: 'd-hoje', data: HOJE });
export const DIARIA_EM_ANDAMENTO = { ...DIARIA_DE_HOJE, status: 'andamento' as const, chegouEm: `${HOJE}T10:32:00Z` };
export const DIARIA_ESPERANDO = diaria({
  id: 'd-esperando',
  data: somarDias(HOJE, -2),
  status: 'aguardando',
  terminouEm: `${somarDias(HOJE, -2)}T19:00:00Z`,
  autoLiberaEm: `${somarDias(HOJE, -1)}T07:00:00Z`,
});
export const DIARIA_LIBERADA = diaria({ id: 'd-liberada', data: somarDias(HOJE, -7), status: 'liberada' });
/** Uma diária na próxima quarta-feira (dia de trabalho na agenda abaixo). */
export const DIARIA_FUTURA = diaria({ id: 'd-futura', data: proximo(3) });

export const DINHEIRO: DinheiroDoProfissional = { liberado: '280.00', vaiCair: '840.00', emReclamacao: '0.00' };

/** Trabalha de segunda a sexta. */
export const AGENDA: AgendaDoProfissional = {
  perfilId: 'perfil-valdir',
  diasDaSemana: [1, 2, 3, 4, 5],
  folgas: [],
  ate: somarDias(HOJE, 90),
};
