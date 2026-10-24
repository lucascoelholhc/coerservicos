import { chamar } from './cliente';
import type { Contrato, NovoContrato, Pagamento, ResumoDoContrato } from './tipos';

/**
 * Contratação e pagamento (DOM-06/07, FE-05). O valor e a comissão saem sempre da API: o front nunca manda preço.
 * O contrato só vira pago pelo webhook do gateway (no local, pelo "Simular pagamento" do gateway falso).
 */
export function criarContrato(pedido: NovoContrato, sinal?: AbortSignal): Promise<Contrato> {
  return chamar<Contrato>('/contratos', { metodo: 'POST', corpo: pedido, sinal });
}

export function listarContratos(sinal: AbortSignal): Promise<{ itens: ResumoDoContrato[] }> {
  return chamar<{ itens: ResumoDoContrato[] }>('/contratos', { sinal });
}

export function buscarContrato(id: string, sinal: AbortSignal): Promise<Contrato> {
  return chamar<Contrato>(`/contratos/${encodeURIComponent(id)}`, { sinal });
}

/** Pede a cobrança (201 nova, 200 a pendente do mesmo método). */
export function pedirPagamento(contrato: string, metodo: Pagamento['metodo']): Promise<Pagamento> {
  return chamar<Pagamento>(`/contratos/${encodeURIComponent(contrato)}/pagamento`, {
    metodo: 'POST',
    corpo: { metodo },
  });
}

/** Só com o gateway falso (`simulavel`): paga a cobrança pendente como o gateway faria. */
export function simularPagamento(contrato: string): Promise<void> {
  return chamar<void>(`/contratos/${encodeURIComponent(contrato)}/pagamento/simular`, { metodo: 'POST' });
}
