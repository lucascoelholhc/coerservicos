/** URL assinada só com https (http só para o armazenamento local da máquina); outra coisa nunca vira link. */
export function enderecoSeguro(url: string): boolean {
  try {
    const endereco = new URL(url);
    const local = endereco.hostname === 'localhost' || endereco.hostname === '127.0.0.1';
    return endereco.protocol === 'https:' || (endereco.protocol === 'http:' && local);
  } catch {
    return false;
  }
}
