import { afterEach, describe, expect, it, vi } from 'vitest';

import { json, simularFetch } from '../teste/fetchSimulado';
import { ErroDaApi } from './erros';
import { confirmarPosseDoCelular, confirmarPosseDoEmail, pedirPosseDoCelular, pedirPosseDoEmail } from './posse';

const corpo = (texto: string | undefined) => JSON.parse(texto ?? '{}') as unknown;
const PADRAO = 'Se este contato estiver em outra conta sem confirmação, enviamos a confirmação.';

describe('prova de posse (RN61)', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('pedirPosseDoCelular: POST /api/contas/posse/celular; mensagem da API ou a padrão', async () => {
    const { pedidos } = simularFetch(() =>
      pedidos.length === 1 ? json({ mensagem: 'Enviamos.' }, 202) : new Response(null, { status: 202 }),
    );

    await expect(pedirPosseDoCelular('47900000001')).resolves.toBe('Enviamos.');
    await expect(pedirPosseDoCelular('47900000001')).resolves.toBe(PADRAO);
    expect(pedidos[0]).toMatchObject({ url: '/api/contas/posse/celular', metodo: 'POST' });
    expect(corpo(pedidos[0]?.corpo)).toEqual({ celular: '47900000001' });
  });

  it('pedirPosseDoEmail: POST /api/contas/posse/email', async () => {
    const { pedidos } = simularFetch(() => new Response(null, { status: 202 }));

    await expect(pedirPosseDoEmail('ana@exemplo.com')).resolves.toBe(PADRAO);
    expect(pedidos[0]).toMatchObject({ url: '/api/contas/posse/email', metodo: 'POST' });
    expect(corpo(pedidos[0]?.corpo)).toEqual({ email: 'ana@exemplo.com' });
  });

  it('confirmarPosseDoCelular: devolve o comprovante', async () => {
    const { pedidos } = simularFetch(() => json({ comprovante: 'comp-cel' }));

    await expect(confirmarPosseDoCelular({ celular: '47900000001', codigo: '123456' })).resolves.toBe('comp-cel');
    expect(pedidos[0]?.url).toBe('/api/contas/posse/celular/confirmar');
    expect(corpo(pedidos[0]?.corpo)).toEqual({ celular: '47900000001', codigo: '123456' });
  });

  it('confirmarPosseDoEmail: devolve o comprovante e o e-mail do link', async () => {
    const { pedidos } = simularFetch(() => json({ comprovante: 'comp-email', email: 'ana@exemplo.com' }));

    await expect(confirmarPosseDoEmail('tok')).resolves.toEqual({ comprovante: 'comp-email', email: 'ana@exemplo.com' });
    expect(pedidos[0]?.url).toBe('/api/contas/posse/email/confirmar');
    expect(corpo(pedidos[0]?.corpo)).toEqual({ token: 'tok' });
  });

  it.each([[{}], [{ comprovante: 7 }], [null]])('celular: resposta fora do formato vira erro de resposta (%o)', async (resposta) => {
    simularFetch(() => json(resposta));

    const erro = await confirmarPosseDoCelular({ celular: '47900000001', codigo: '123456' }).catch((falha: unknown) => falha);

    expect((erro as ErroDaApi).tipo).toBe('resposta');
  });

  it.each([[{ comprovante: 'c' }], [{ email: 'a@b.c' }], [{ comprovante: '', email: 'a@b.c' }], [null]])(
    'e-mail: resposta fora do formato vira erro de resposta (%o)',
    async (resposta) => {
      simularFetch(() => json(resposta));

      const erro = await confirmarPosseDoEmail('tok').catch((falha: unknown) => falha);

      expect((erro as ErroDaApi).tipo).toBe('resposta');
    },
  );
});
