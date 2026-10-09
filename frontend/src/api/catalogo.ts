import { chamar } from './cliente';
import type { Catalogo, ProfissaoDoCatalogo } from './tipos';

export function buscarCatalogo(sinal: AbortSignal): Promise<Catalogo> {
  return chamar<Catalogo>('/publico/catalogo', { sinal });
}

/** Profissões de todas as áreas, na ordem das áreas (a da API). */
export function profissoesDoCatalogo(catalogo: Catalogo): ProfissaoDoCatalogo[] {
  return catalogo.areas.flatMap((area) => area.profissoes);
}
