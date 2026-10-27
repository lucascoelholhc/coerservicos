import { describe, expect, it } from 'vitest';

import { json, simularFetch } from '../teste/fetchSimulado';
import { buscarAgenda, buscarDinheiro, listarMinhasDiarias, marcarFolga, tirarFolga } from './areaDoProfissional';
import { aprovarDia, cheguei, enviarFotoDoDia, fotosDoDia, terminei } from './diarias';

describe('API da execução da diária e da área do profissional', () => {
  it('chama as rotas certas com o método e o corpo certos', async () => {
    const { pedidos, falso } = simularFetch((pedido) =>
      pedido.metodo === 'PUT' || pedido.metodo === 'DELETE' ? new Response(null, { status: 204 }) : json({ itens: [] }),
    );
    const sinal = new AbortController().signal;

    await cheguei('d 1');
    await terminei('d1', '   ');
    await terminei('d1', ' Volto amanhã ');
    await aprovarDia('d1');
    await fotosDoDia('d1', sinal);
    await enviarFotoDoDia('d1', new File(['x'], 'f.jpg', { type: 'image/jpeg' }));
    await listarMinhasDiarias(sinal);
    await buscarDinheiro(sinal);
    await buscarAgenda(sinal);
    await marcarFolga('2026-11-02');
    await tirarFolga('2026-11-02');

    expect(pedidos.map((p) => `${p.metodo} ${p.url}`)).toEqual([
      'POST /api/diarias/d%201/cheguei',
      'POST /api/diarias/d1/terminei',
      'POST /api/diarias/d1/terminei',
      'POST /api/diarias/d1/aprovar',
      'GET /api/diarias/d1/fotos',
      'POST /api/diarias/fotos',
      'GET /api/profissional/diarias',
      'GET /api/profissional/dinheiro',
      'GET /api/profissional/agenda',
      'PUT /api/profissional/agenda/folgas/2026-11-02',
      'DELETE /api/profissional/agenda/folgas/2026-11-02',
    ]);
    expect(pedidos[1]?.corpo).toBe('{}');
    expect(pedidos[2]?.corpo).toBe('{"recado":"Volto amanhã"}');
    const multipart = falso.mock.calls[5]?.[1]?.body;
    expect(multipart).toBeInstanceOf(FormData);
    expect((multipart as FormData).get('diaria')).toBe('d1');
  });
});
