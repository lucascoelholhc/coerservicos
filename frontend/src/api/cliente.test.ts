import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { esperarAborto, json, problema, simularFetch } from '../teste/fetchSimulado';
import { chamar } from './cliente';
import { ErroDaApi, MENSAGENS } from './erros';
import { definirAoPerderSessao, encerrarSessao, iniciarSessao, tokenAtual } from './sessao';

const USUARIO = { id: 'u1', nome: 'Ana', papeis: ['CLIENTE'], contatoPendente: false };

function entrarComo(token: string) {
  iniciarSessao({ accessToken: token, expiraEm: '2026-10-09T12:15:00Z', usuario: USUARIO });
}

async function erroDe(promessa: Promise<unknown>): Promise<ErroDaApi> {
  const erro = await promessa.then(
    () => null,
    (falha: unknown) => falha,
  );
  expect(erro).toBeInstanceOf(ErroDaApi);
  return erro as ErroDaApi;
}

describe('cliente da API', () => {
  const aoPerderSessao = vi.fn();

  beforeEach(() => {
    definirAoPerderSessao(aoPerderSessao);
  });

  afterEach(() => {
    encerrarSessao();
    aoPerderSessao.mockReset();
    vi.unstubAllGlobals();
  });

  it('GET em /api com JSON e sem Authorization quando não há sessão', async () => {
    const { pedidos } = simularFetch(() => json({ ok: true }));

    await expect(chamar('/publico/regras')).resolves.toEqual({ ok: true });

    expect(pedidos[0]?.url).toBe('/api/publico/regras');
    expect(pedidos[0]?.metodo).toBe('GET');
    expect(pedidos[0]?.cabecalhos.Accept).toBe('application/json');
    expect(pedidos[0]?.cabecalhos).not.toHaveProperty('Authorization');
  });

  it('com sessão manda Authorization: Bearer', async () => {
    entrarComo('t1');
    const { pedidos } = simularFetch(() => json({}));

    await chamar('/contas/eu');

    expect(pedidos[0]?.cabecalhos.Authorization).toBe('Bearer t1');
  });

  it('POST com corpo JSON; 204 devolve undefined', async () => {
    const { pedidos } = simularFetch(() => new Response(null, { status: 204 }));

    await expect(chamar('/contas/cliente', { metodo: 'POST', corpo: { nome: 'Ana' } })).resolves.toBeUndefined();

    expect(pedidos[0]?.metodo).toBe('POST');
    expect(pedidos[0]?.cabecalhos['Content-Type']).toBe('application/json');
    expect(pedidos[0]?.corpo).toBe('{"nome":"Ana"}');
  });

  it('401 numa chamada com token: renova uma vez e repete com o token novo', async () => {
    entrarComo('velho');
    const { pedidos } = simularFetch((pedido) => {
      if (pedido.url === '/api/auth/renovar') {
        return json({ accessToken: 'novo', expiraEm: '2026-10-09T12:30:00Z', usuario: USUARIO });
      }
      return pedido.cabecalhos.Authorization === 'Bearer novo' ? json({ nome: 'Ana' }) : problema(401, 'token-invalido', 'x');
    });

    await expect(chamar('/contas/eu')).resolves.toEqual({ nome: 'Ana' });

    expect(pedidos.map((pedido) => pedido.url)).toEqual(['/api/contas/eu', '/api/auth/renovar', '/api/contas/eu']);
    expect(pedidos[1]?.metodo).toBe('POST');
    expect(tokenAtual()).toBe('novo');
  });

  it('várias 401 ao mesmo tempo esperam a mesma renovação (um único POST /renovar)', async () => {
    entrarComo('velho');
    let liberarRenovacao: () => void = () => undefined;
    const renovacao = new Promise<void>((resolver) => {
      liberarRenovacao = resolver;
    });
    const { pedidos } = simularFetch(async (pedido) => {
      if (pedido.url === '/api/auth/renovar') {
        await renovacao;
        return json({ accessToken: 'novo', expiraEm: '2026-10-09T12:30:00Z', usuario: USUARIO });
      }
      return pedido.cabecalhos.Authorization === 'Bearer novo' ? json({ ok: pedido.url }) : problema(401, 'x', 'x');
    });

    const chamadas = Promise.all([chamar('/a'), chamar('/b'), chamar('/c')]);
    await vi.waitFor(() => expect(pedidos.filter((p) => p.url === '/api/auth/renovar')).toHaveLength(1));
    liberarRenovacao();

    await expect(chamadas).resolves.toEqual([{ ok: '/api/a' }, { ok: '/api/b' }, { ok: '/api/c' }]);
    expect(pedidos.filter((pedido) => pedido.url === '/api/auth/renovar')).toHaveLength(1);
  });

  it('renovação recusada: limpa a sessão, avisa (vai para /entrar) e devolve erro de sessão', async () => {
    entrarComo('velho');
    simularFetch((pedido) =>
      pedido.url === '/api/auth/renovar' ? problema(401, 'sessao-invalida', 'x') : problema(401, 'x', 'x'),
    );

    const erro = await erroDe(chamar('/contas/eu'));

    expect(erro.tipo).toBe('sessao');
    expect(erro.message).toBe(MENSAGENS.sessao);
    expect(tokenAtual()).toBeNull();
    expect(aoPerderSessao).toHaveBeenCalledOnce();
  });

  it('a chamada repetida ainda dá 401: sessão encerrada', async () => {
    entrarComo('velho');
    simularFetch((pedido) =>
      pedido.url === '/api/auth/renovar'
        ? json({ accessToken: 'novo', expiraEm: 'x', usuario: USUARIO })
        : problema(401, 'x', 'x'),
    );

    expect((await erroDe(chamar('/contas/eu'))).tipo).toBe('sessao');
    expect(tokenAtual()).toBeNull();
    expect(aoPerderSessao).toHaveBeenCalledOnce();
  });

  it('renovação sem internet: erro de conexão e a sessão fica (não manda para /entrar)', async () => {
    entrarComo('velho');
    simularFetch((pedido) => {
      if (pedido.url === '/api/auth/renovar') {
        throw new TypeError('Failed to fetch');
      }
      return problema(401, 'x', 'x');
    });

    expect((await erroDe(chamar('/contas/eu'))).tipo).toBe('sem-conexao');
    expect(tokenAtual()).toBe('velho');
    expect(aoPerderSessao).not.toHaveBeenCalled();
  });

  it.each([
    ['servidor fora (500)', () => problema(500, 'x', 'x')],
    ['resposta que não é JSON', () => new Response('<html>', { status: 200 })],
  ])('renovação com %s: erro de servidor e a sessão fica', async (_, respostaDaRenovacao) => {
    entrarComo('velho');
    simularFetch((pedido) => (pedido.url === '/api/auth/renovar' ? respostaDaRenovacao() : problema(401, 'x', 'x')));

    expect((await erroDe(chamar('/contas/eu'))).tipo).toBe('servidor');
    expect(tokenAtual()).toBe('velho');
    expect(aoPerderSessao).not.toHaveBeenCalled();
  });

  it('se outra chamada já renovou, repete sem renovar de novo', async () => {
    entrarComo('velho');
    const { pedidos } = simularFetch((pedido) => {
      if (pedido.cabecalhos.Authorization === 'Bearer velho') {
        entrarComo('novo');
        return problema(401, 'x', 'x');
      }
      return json({ ok: true });
    });

    await expect(chamar('/contas/eu')).resolves.toEqual({ ok: true });
    expect(pedidos.some((pedido) => pedido.url === '/api/auth/renovar')).toBe(false);
  });

  it('401 sem sessão (ex.: login errado) é erro comum, sem renovar', async () => {
    const { pedidos } = simularFetch(() => problema(401, 'login-invalido', 'Celular, e-mail ou senha incorretos.'));

    const erro = await erroDe(chamar('/auth/entrar', { metodo: 'POST', corpo: {} }));

    expect(erro).toMatchObject({ tipo: 'problema', status: 401, codigo: 'login-invalido' });
    expect(erro.message).toBe('Celular, e-mail ou senha incorretos.');
    expect(pedidos).toHaveLength(1);
  });

  it('Problem Details com campos vira erro por campo; 409 com um campo também', async () => {
    simularFetch(() =>
      problema(400, 'validacao', 'Confira os campos.', {
        campos: [
          { campo: 'celular', mensagem: 'Celular inválido.' },
          { campo: 'senha', mensagem: 'Senha curta.' },
        ],
      }),
    );
    const validacao = await erroDe(chamar('/contas/cliente', { metodo: 'POST', corpo: {} }));
    expect(validacao).toMatchObject({ codigo: 'validacao', campos: { celular: 'Celular inválido.', senha: 'Senha curta.' } });

    simularFetch(() => problema(409, 'contato-em-uso', 'Esse contato já está em uso.', { campo: 'email' }));
    const conflito = await erroDe(chamar('/contas/cliente', { metodo: 'POST', corpo: {} }));
    expect(conflito).toMatchObject({ status: 409, codigo: 'contato-em-uso', campos: { email: 'Esse contato já está em uso.' } });
  });

  it.each([
    [429, 'limite', MENSAGENS.limite],
    [500, 'servidor', MENSAGENS.servidor],
    [503, 'servidor', MENSAGENS.servidor],
  ])('%i vira erro "%s" com mensagem própria em português', async (status, tipo, mensagem) => {
    simularFetch(() => new Response('<html>Service Unavailable</html>', { status }));

    const erro = await erroDe(chamar('/publico/catalogo'));

    expect(erro).toMatchObject({ tipo, status });
    expect(erro.message).toBe(mensagem);
  });

  it('erro sem Problem Details legível: mensagem genérica, nunca o corpo cru', async () => {
    simularFetch(() => new Response('Bad Request: stack trace...', { status: 400 }));

    const erro = await erroDe(chamar('/x'));

    expect(erro).toMatchObject({ tipo: 'problema', status: 400 });
    expect(erro.message).toBe(MENSAGENS.problema);
  });

  it('corpo JSON que não é objeto, tipo fora do padrão e campo inválido: só o que é confiável aparece', async () => {
    simularFetch(() => json('texto solto', 400));
    expect(await erroDe(chamar('/x'))).toMatchObject({ codigo: undefined, campos: {} });

    simularFetch(() =>
      json(
        { type: 'about:blank', detail: '  ', campos: [null, { campo: 1 }, { campo: 'nome', mensagem: 'Informe o nome.' }] },
        400,
      ),
    );
    const erro = await erroDe(chamar('/x'));
    expect(erro).toMatchObject({ codigo: undefined, campos: { nome: 'Informe o nome.' } });
    expect(erro.message).toBe(MENSAGENS.problema);
  });

  it('Problem Details sem detail usa a mensagem genérica', async () => {
    simularFetch(() => json({ type: 'urn:coe:erro:x', status: 422 }, 422, 'application/problem+json'));

    expect((await erroDe(chamar('/x'))).message).toBe(MENSAGENS.problema);
  });

  it('200 com corpo que não é JSON: erro de resposta', async () => {
    simularFetch(() => new Response('<html>', { status: 200 }));

    expect((await erroDe(chamar('/x'))).tipo).toBe('resposta');
  });

  it('sem internet: "Sem conexão..."', async () => {
    simularFetch(() => {
      throw new TypeError('Failed to fetch');
    });

    const erro = await erroDe(chamar('/publico/catalogo'));

    expect(erro.tipo).toBe('sem-conexao');
    expect(erro.message).toBe('Sem conexão. Confira a internet e tente de novo.');
  });

  it.each([['texto'], [{}]])('falha estranha do fetch (%o) também vira "sem conexão"', async (falha) => {
    simularFetch(() => {
      throw falha;
    });

    expect((await erroDe(chamar('/x'))).tipo).toBe('sem-conexao');
  });

  it('navegador avisando que está offline: nem tenta', async () => {
    const { falso } = simularFetch(() => json({}));
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false);

    expect((await erroDe(chamar('/x'))).tipo).toBe('sem-conexao');
    expect(falso).not.toHaveBeenCalled();
    vi.restoreAllMocks();
  });

  it('demorou mais que o limite: erro de tempo (diferente de sem conexão)', async () => {
    simularFetch(esperarAborto);

    const erro = await erroDe(chamar('/x', { tempoLimiteMs: 20 }));

    expect(erro.tipo).toBe('tempo');
    expect(erro.message).toBe(MENSAGENS.tempo);
  });

  it('cancelado por quem chamou (tela desmontada): erro "cancelado"', async () => {
    simularFetch(esperarAborto);
    const controle = new AbortController();

    const promessa = chamar('/x', { sinal: controle.signal });
    controle.abort();

    expect((await erroDe(promessa)).tipo).toBe('cancelado');
  });

  it('nunca escreve o token no console', async () => {
    const espioes = (['log', 'info', 'warn', 'error', 'debug'] as const).map((nivel) => vi.spyOn(console, nivel));
    entrarComo('token-secreto');
    simularFetch(() => problema(500, 'x', 'x'));

    await erroDe(chamar('/x'));

    for (const espiao of espioes) {
      expect(JSON.stringify(espiao.mock.calls)).not.toContain('token-secreto');
    }
    vi.restoreAllMocks();
  });
});
