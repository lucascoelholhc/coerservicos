import type { StatusDoContrato } from '../../api/tipos';

/** Situação do contrato em palavras do cliente (status nunca só por cor). */
export const SITUACAO_DO_CONTRATO: Record<StatusDoContrato, string> = {
  aguardando_pagamento: 'Esperando pagamento',
  pago: 'Pago · dinheiro guardado',
  em_andamento: 'Em andamento',
  concluido: 'Concluído',
  cancelado: 'Cancelado',
  expirado: 'Prazo de pagamento acabou',
};

export const MATERIAL: Record<string, string> = {
  cliente_tem: 'Você já tem o material',
  cliente_compra: 'Você compra o que faltar',
  combinar: 'Combinar pelo chat',
};
