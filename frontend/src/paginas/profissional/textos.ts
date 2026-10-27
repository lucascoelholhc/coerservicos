/** Textos da área do profissional (protótipo telas-profissional.js). */

const HORA = new Intl.DateTimeFormat('pt-BR', { timeZone: 'America/Sao_Paulo', hour: '2-digit', hourCycle: 'h23' });

/** A hora de agora em São Paulo (0–23), sem depender do fuso do aparelho. */
export function horaEmSaoPaulo(agora: Date = new Date()): number {
  return Number(HORA.format(agora));
}

export function saudacao(hora: number): string {
  if (hora < 12) {
    return 'Bom dia';
  }
  return hora < 18 ? 'Boa tarde' : 'Boa noite';
}

/** Recados prontos do "Terminei o dia" (o servidor confere a censura de contato). */
export const RECADOS = [
  'Não pisar por 24 horas',
  'Não pisar por 48 horas',
  'Falta material para amanhã',
  'Volto amanhã às 7h',
  'Deixei tudo limpo',
] as const;

/** Limite de fotos por dia (o servidor confere: até 5). */
export const FOTOS_POR_DIA = 5;
