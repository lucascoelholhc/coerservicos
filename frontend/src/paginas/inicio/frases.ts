import type { Regras } from '../../api/tipos';
import { formatarPercentual, formatarPrazo } from '../../formatacao/regras';

/** Fato "Você aprova": com o prazo da API ou, sem as regras, a versão sem número. */
export function fraseDaAprovacao(regras: Regras | undefined): string {
  if (!regras) {
    return 'Terminou o dia, você aprova. Se não responder, o valor é liberado ao profissional depois de um prazo.';
  }
  return `Terminou o dia, você aprova. Se não responder em ${formatarPrazo(regras.prazoLiberacao)}, o valor é liberado ao profissional.`;
}

/** Item do convite ao profissional sobre a liberação automática. */
export function fraseDaLiberacaoAoProfissional(regras: Regras | undefined): string {
  if (!regras) {
    return 'Sem resposta do cliente, o dinheiro é liberado depois de um prazo';
  }
  return `Sem resposta do cliente em ${formatarPrazo(regras.prazoLiberacao)}, o dinheiro é liberado`;
}

/** Taxa da COE com o percentual da API e quem paga (decisão de 09/10 para os dois casos). */
export function fraseDaTaxa(regras: Regras): string {
  const percentual = formatarPercentual(regras.comissao);
  return regras.taxaPagaPor === 'CLIENTE'
    ? `A taxa da COE é de ${percentual}, paga pelo cliente.`
    : `A taxa da COE é de ${percentual}, descontada do valor do profissional.`;
}
