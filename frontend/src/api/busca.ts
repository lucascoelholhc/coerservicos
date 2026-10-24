import { chamar } from './cliente';
import type { PerfilPublico, ResultadoDaBusca } from './tipos';

const BASE = '/publico/profissionais';

/**
 * Busca pública (DOM-04), sem login. {@link consulta} já vem montada pelos filtros da tela (mesmos nomes
 * de parâmetro da API); a página vai sempre no fim.
 */
export function buscarProfissionais(consulta: string, pagina: number, sinal: AbortSignal): Promise<ResultadoDaBusca> {
  const prefixo = consulta === '' ? '' : `${consulta}&`;
  return chamar<ResultadoDaBusca>(`${BASE}?${prefixo}pagina=${pagina}`, { sinal });
}

/** Perfil público: 404 igual para inexistente e para quem não está ativo. */
export function buscarPerfilPublico(id: string, sinal: AbortSignal): Promise<PerfilPublico> {
  return chamar<PerfilPublico>(`${BASE}/${encodeURIComponent(id)}`, { sinal });
}
