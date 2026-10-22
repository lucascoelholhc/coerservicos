import { afterEach, describe, expect, it, vi } from 'vitest';

import { DETALHE_DA_VERIFICACAO, FILA_DA_VERIFICACAO } from '../teste/dadosDaVerificacao';
import { json, simularFetch } from '../teste/fetchSimulado';
import {
  aprovarCadastro,
  buscarDetalheDaVerificacao,
  buscarFila,
  pedirCorrecao,
  recusarCadastro,
  urlDaFotoParaAdmin,
  urlDoDocumentoParaAdmin,
} from './verificacao';

describe('api da verificação (admin)', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('fila paginada e detalhe pelo id', async () => {
    const { pedidos } = simularFetch((pedido) =>
      pedido.url.includes('pagina') ? json(FILA_DA_VERIFICACAO) : json(DETALHE_DA_VERIFICACAO),
    );

    const fila = await buscarFila(2, new AbortController().signal);
    const detalhe = await buscarDetalheDaVerificacao('p 1', new AbortController().signal);

    expect(fila.itens).toHaveLength(2);
    expect(detalhe.cpf).toBe('111.444.777-35');
    expect(pedidos.map((p) => p.url)).toEqual(['/api/admin/verificacao?pagina=2', '/api/admin/verificacao/p%201']);
  });

  it('URL do documento e da foto pelo id (o admin nunca vê a chave do arquivo)', async () => {
    const { pedidos } = simularFetch(() => json({ url: 'https://b/x', expiraEm: 'x' }));

    await urlDoDocumentoParaAdmin('p1', 'd1');
    await urlDaFotoParaAdmin('p1', 'f1');

    expect(pedidos.map((p) => p.url)).toEqual([
      '/api/admin/verificacao/p1/documentos/d1/url',
      '/api/admin/verificacao/p1/fotos/f1/url',
    ]);
  });

  it('aprovar com o checklist e o NR-10; correção e recusa com o motivo', async () => {
    const { pedidos } = simularFetch(() => json({ id: 'p1', status: 'ativo', nr10: 'verificado' }));
    const itens = { cpf: true, documento: true, selfie: true, fotos: true, texto: true };

    await aprovarCadastro('p1', { itens, nr10: 'verificado' });
    await pedirCorrecao('p1', 'A selfie está escura.');
    await recusarCadastro('p1', 'Documento de outra pessoa.');

    expect(pedidos.map((p) => `${p.metodo} ${p.url}`)).toEqual([
      'POST /api/admin/verificacao/p1/aprovar',
      'POST /api/admin/verificacao/p1/correcao',
      'POST /api/admin/verificacao/p1/recusar',
    ]);
    expect(JSON.parse(pedidos[0]?.corpo ?? '{}')).toEqual({ itens, nr10: 'verificado' });
    expect(JSON.parse(pedidos[1]?.corpo ?? '{}')).toEqual({ motivo: 'A selfie está escura.' });
    expect(JSON.parse(pedidos[2]?.corpo ?? '{}')).toEqual({ motivo: 'Documento de outra pessoa.' });
  });
});
