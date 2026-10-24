/**
 * URL assinada só com https; http só para o armazenamento local da máquina e só em desenvolvimento
 * (no build de produção a API nunca devolve http). Outra coisa nunca vira link.
 */
export function enderecoSeguro(url: string): boolean {
  try {
    const endereco = new URL(url);
    const local = import.meta.env.DEV && (endereco.hostname === 'localhost' || endereco.hostname === '127.0.0.1');
    return endereco.protocol === 'https:' || (endereco.protocol === 'http:' && local);
  } catch {
    return false;
  }
}
