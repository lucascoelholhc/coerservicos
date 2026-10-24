import { afterEach, describe, expect, it, vi } from 'vitest';

import { PERFIL_DE_EXEMPLO, RESULTADO_DA_BUSCA } from '../teste/dadosDaBusca';
import { json, simularFetch } from '../teste/fetchSimulado';
import { buscarPerfilPublico, buscarProfissionais } from './busca';

describe('busca pública', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('lista com a consulta pronta e a página', async () => {
    const { pedidos } = simularFetch(() => json(RESULTADO_DA_BUSCA));

    await expect(
      buscarProfissionais('profissao=pedreiro&cidade=4202404', 2, new AbortController().signal),
    ).resolves.toEqual(RESULTADO_DA_BUSCA);
    expect(pedidos[0]?.url).toBe('/api/publico/profissionais?profissao=pedreiro&cidade=4202404&pagina=2');
    expect(pedidos[0]?.cabecalhos.Authorization).toBeUndefined();
  });

  it('sem filtro: só a página', async () => {
    const { pedidos } = simularFetch(() => json(RESULTADO_DA_BUSCA));

    await buscarProfissionais('', 0, new AbortController().signal);

    expect(pedidos[0]?.url).toBe('/api/publico/profissionais?pagina=0');
  });

  it('perfil pelo id, com o id codificado no caminho', async () => {
    const { pedidos } = simularFetch(() => json(PERFIL_DE_EXEMPLO));

    await expect(buscarPerfilPublico('a/b', new AbortController().signal)).resolves.toEqual(PERFIL_DE_EXEMPLO);
    expect(pedidos[0]?.url).toBe('/api/publico/profissionais/a%2Fb');
  });
});
