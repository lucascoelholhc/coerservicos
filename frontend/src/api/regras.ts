import { formatarPercentual, formatarPrazo } from '../formatacao/regras';
import { chamar } from './cliente';
import { ErroDaApi } from './erros';
import type { Regras } from './tipos';

export async function buscarRegras(sinal: AbortSignal): Promise<Regras> {
  return validar(await chamar<unknown>('/publico/regras', { sinal }));
}

/** Valor desconhecido (ex.: quem paga a taxa) ou fora do formato vira erro tipado: a frase não aparece. */
function validar(corpo: unknown): Regras {
  const { comissao, prazoLiberacao, taxaPagaPor } = corpo as Record<string, unknown>;
  if (
    typeof comissao !== 'string' ||
    typeof prazoLiberacao !== 'string' ||
    (taxaPagaPor !== 'CLIENTE' && taxaPagaPor !== 'PROFISSIONAL')
  ) {
    throw new ErroDaApi('resposta');
  }
  try {
    formatarPercentual(comissao);
    formatarPrazo(prazoLiberacao);
  } catch {
    throw new ErroDaApi('resposta');
  }
  return { comissao, prazoLiberacao, taxaPagaPor };
}
