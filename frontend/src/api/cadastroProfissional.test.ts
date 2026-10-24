import { afterEach, describe, expect, it, vi } from 'vitest';

import { CADASTRO_VAZIO } from '../teste/dadosDoCadastro';
import { json, problema, simularFetch } from '../teste/fetchSimulado';
import {
  apagarDocumento,
  apagarFoto,
  buscarCadastro,
  descreverFoto,
  enviarCadastro,
  enviarDocumento,
  enviarFoto,
  salvarEtapa,
  urlDaFoto,
  urlDoDocumento,
} from './cadastroProfissional';
import { ErroDaApi } from './erros';

describe('api do cadastro do profissional', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('GET devolve o rascunho', async () => {
    const { pedidos } = simularFetch(() => json(CADASTRO_VAZIO));

    const cadastro = await buscarCadastro(new AbortController().signal);

    expect(cadastro?.etapaAtual).toBe(1);
    expect(pedidos[0]?.url).toBe('/api/profissional/cadastro');
  });

  it('GET 404 nao-encontrado = ainda sem rascunho (null)', async () => {
    simularFetch(() => problema(404, 'nao-encontrado', 'Cadastro não encontrado.'));

    await expect(buscarCadastro(new AbortController().signal)).resolves.toBeNull();
  });

  it('GET com outro erro continua erro', async () => {
    simularFetch(() => problema(403, 'acesso-negado', 'Sem acesso.'));

    await expect(buscarCadastro(new AbortController().signal)).rejects.toBeInstanceOf(ErroDaApi);
  });

  it('PUT de uma etapa manda o corpo em JSON e devolve o rascunho', async () => {
    const { pedidos } = simularFetch(() => json({ ...CADASTRO_VAZIO, profissao: 'pintor' }));

    const cadastro = await salvarEtapa('trabalho', { profissao: 'pintor', outras: [] });

    expect(cadastro.profissao).toBe('pintor');
    expect(pedidos[0]?.metodo).toBe('PUT');
    expect(pedidos[0]?.url).toBe('/api/profissional/cadastro/trabalho');
    expect(JSON.parse(pedidos[0]?.corpo ?? '{}')).toEqual({ profissao: 'pintor', outras: [] });
  });

  it('documento vai por multipart, sem Content-Type (o navegador põe o boundary)', async () => {
    const { pedidos, falso } = simularFetch(() => json({ id: 'd1', tipo: 'selfie', status: 'pendente' }, 201));
    const arquivo = new File(['x'], 'selfie.jpg', { type: 'image/jpeg' });

    const documento = await enviarDocumento('selfie', arquivo);

    expect(documento.id).toBe('d1');
    expect(pedidos[0]?.metodo).toBe('POST');
    expect(pedidos[0]?.url).toBe('/api/profissional/cadastro/documentos');
    expect(pedidos[0]?.cabecalhos['Content-Type']).toBeUndefined();
    const corpo = falso.mock.calls[0]?.[1]?.body as FormData;
    expect(corpo.get('tipo')).toBe('selfie');
    expect((corpo.get('arquivo') as File).name).toBe('selfie.jpg');
  });

  it('foto vai com serviço e legenda; descrever, apagar e URL usam o id', async () => {
    const { pedidos, falso } = simularFetch((pedido) => {
      if (pedido.metodo === 'DELETE') {
        return new Response(null, { status: 204 });
      }
      if (pedido.url.endsWith('/url')) {
        return json({ url: 'http://bucket/f1', expiraEm: '2026-10-22T12:05:00Z' });
      }
      return json({ id: 'f1', servico: 'Reboco', legenda: 'Muro' }, 201);
    });

    await enviarFoto(new File(['x'], 'a.png', { type: 'image/png' }), { servico: 'Reboco', legenda: 'Muro' });
    await descreverFoto('f1', { servico: null, legenda: 'Outro' });
    await apagarFoto('f1');
    const url = await urlDaFoto('f1');

    const corpo = falso.mock.calls[0]?.[1]?.body as FormData;
    expect(corpo.get('servico')).toBe('Reboco');
    expect(corpo.get('legenda')).toBe('Muro');
    expect(pedidos.map((p) => `${p.metodo} ${p.url}`)).toEqual([
      'POST /api/profissional/cadastro/fotos',
      'PUT /api/profissional/cadastro/fotos/f1',
      'DELETE /api/profissional/cadastro/fotos/f1',
      'GET /api/profissional/cadastro/fotos/f1/url',
    ]);
    expect(url.url).toBe('http://bucket/f1');
  });

  it('foto sem serviço nem legenda não manda os campos vazios', async () => {
    const { falso } = simularFetch(() => json({ id: 'f1', servico: null, legenda: null }, 201));

    await enviarFoto(new File(['x'], 'a.png', { type: 'image/png' }), {});

    const corpo = falso.mock.calls[0]?.[1]?.body as FormData;
    expect(corpo.has('servico')).toBe(false);
    expect(corpo.has('legenda')).toBe(false);
  });

  it('envio manda a versão dos termos e o aceite da regra de contato', async () => {
    const { pedidos } = simularFetch(() => json({ ...CADASTRO_VAZIO, status: 'em_analise' }));

    const cadastro = await enviarCadastro({ versaoTermosAceita: '1.0', aceitoRegraDeContato: true });

    expect(cadastro.status).toBe('em_analise');
    expect(pedidos[0]?.url).toBe('/api/profissional/cadastro/envio');
    expect(JSON.parse(pedidos[0]?.corpo ?? '{}')).toEqual({ versaoTermosAceita: '1.0', aceitoRegraDeContato: true });
  });

  it('upload tem tempo maior que o padrão (foto grande em rede de celular)', async () => {
    vi.useFakeTimers();
    try {
      simularFetch(
        (pedido) =>
          new Promise((pronto, falhar) => {
            pedido.sinal?.addEventListener('abort', () => falhar(pedido.sinal?.reason));
            setTimeout(() => pronto(json({ id: 'd1', tipo: 'selfie', status: 'pendente' }, 201)), 30_000);
          }),
      );
      const envio = enviarDocumento('selfie', new File(['x'], 's.jpg', { type: 'image/jpeg' }));
      await vi.advanceTimersByTimeAsync(30_000);
      await expect(envio).resolves.toMatchObject({ id: 'd1' });
    } finally {
      vi.useRealTimers();
    }
  });

  it('documento: apagar e URL assinada pelo id (codificado no caminho)', async () => {
    const { pedidos } = simularFetch((pedido) =>
      pedido.metodo === 'DELETE' ? new Response(null, { status: 204 }) : json({ url: 'https://b/d', expiraEm: 'x' }),
    );

    await apagarDocumento('d 1');
    const url = await urlDoDocumento('d 1');

    expect(url.url).toBe('https://b/d');
    expect(pedidos.map((p) => `${p.metodo} ${p.url}`)).toEqual([
      'DELETE /api/profissional/cadastro/documentos/d%201',
      'GET /api/profissional/cadastro/documentos/d%201/url',
    ]);
  });
});
