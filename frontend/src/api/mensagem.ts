/** Respostas 202 trazem {mensagem}; sem ela (ou fora do formato), vale o texto padrão da tela. */
export function mensagemDaResposta(resposta: unknown, padrao: string): string {
  const mensagem = (resposta as { mensagem?: unknown } | undefined)?.mensagem;
  return typeof mensagem === 'string' ? mensagem : padrao;
}
