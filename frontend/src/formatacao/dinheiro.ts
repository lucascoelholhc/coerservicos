/*
 * Dinheiro no front, só para mostrar: a API manda e recebe texto com 2 casas ("280.00"). Contas em
 * centavos inteiros (nunca número com vírgula flutuante). A prévia da diária repete a regra do backend
 * (CalculadoraDiaria) para avisar antes; quem decide o valor é sempre o servidor.
 */
import type { TaxaPagaPor } from '../api/tipos';

const REAIS = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });

/** "280.00" (da API) ou centavos/100 = "R$ 280,00". */
export function formatarReais(valor: string | number): string {
  // O Intl põe um espaço inseparável depois do "R$"; o texto da COE usa espaço comum.
  return REAIS.format(Number(valor)).replace(/\u00a0/g, ' ');
}

/** Centavos inteiros ("280,5" = 28050). Texto vazio, negativo, com mais de 2 casas ou com milhar = null. */
export function centavosDoTexto(texto: string): number | null {
  const partes = /^(\d{1,7})(?:[.,](\d{1,2}))?$/.exec(texto.trim());
  if (!partes) {
    return null;
  }
  return Number(partes[1]) * 100 + Number((partes[2] ?? '').padEnd(2, '0'));
}

/** 28050 = "280.50", o formato do Dinheiro na API. */
export function textoDosCentavos(centavos: number): string {
  const inteiros = Math.floor(centavos / 100);
  return `${inteiros}.${String(centavos % 100).padStart(2, '0')}`;
}

/** Comissão em centavos com arredondamento HALF_EVEN, como o Dinheiro.multiplicar(Percentual) do backend. */
function comissaoEmCentavos(centavos: number, fracao: string): number {
  const partes = /^0\.(\d{1,4})$/.exec(fracao);
  if (!partes) {
    throw new Error(`Comissão fora do formato: ${fracao}`);
  }
  const deDezMil = Number((partes[1] ?? '').padEnd(4, '0'));
  const produto = centavos * deDezMil;
  const inteiro = Math.floor(produto / 10_000);
  const resto = produto % 10_000;
  if (resto > 5_000 || (resto === 5_000 && inteiro % 2 === 1)) {
    return inteiro + 1;
  }
  return inteiro;
}

export interface PreviaDaDiaria {
  recebe: number;
  clientePaga: number;
}

/** Quanto o profissional recebe e quanto o cliente paga por uma diária, com a comissão e quem paga da API. */
export function previaDaDiaria(centavos: number, comissao: string, taxaPagaPor: TaxaPagaPor): PreviaDaDiaria {
  const taxa = comissaoEmCentavos(centavos, comissao);
  return taxaPagaPor === 'CLIENTE'
    ? { recebe: centavos, clientePaga: centavos + taxa }
    : { recebe: centavos - taxa, clientePaga: centavos };
}
