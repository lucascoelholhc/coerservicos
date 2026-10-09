import { afterEach, describe, expect, it, vi } from 'vitest';

import { json, simularFetch } from '../teste/fetchSimulado';
import { buscarCatalogo, profissoesDoCatalogo } from './catalogo';
import type { Catalogo } from './tipos';

const CATALOGO: Catalogo = {
  areas: [
    {
      codigo: 'obra',
      nome: 'Obra e reforma',
      profissoes: [
        { codigo: 'pedreiro', nome: 'Pedreiro', nomePlural: 'Pedreiros', icone: 'brick', servicos: [] },
        { codigo: 'pintor', nome: 'Pintor', nomePlural: 'Pintores', icone: 'roller', servicos: [] },
      ],
    },
    {
      codigo: 'casa',
      nome: 'Casa e jardim',
      profissoes: [{ codigo: 'diarista', nome: 'Diarista', nomePlural: 'Diaristas', icone: 'broom', servicos: [] }],
    },
  ],
  cidades: [{ codigoIbge: 4202404, nome: 'Blumenau', uf: 'SC' }],
};

describe('catálogo público', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('lê /api/publico/catalogo', async () => {
    const { pedidos } = simularFetch(() => json(CATALOGO));

    await expect(buscarCatalogo(new AbortController().signal)).resolves.toEqual(CATALOGO);
    expect(pedidos[0]?.url).toBe('/api/publico/catalogo');
  });

  it('profissões de todas as áreas, na ordem das áreas', () => {
    expect(profissoesDoCatalogo(CATALOGO).map((profissao) => profissao.codigo)).toEqual([
      'pedreiro',
      'pintor',
      'diarista',
    ]);
  });
});
