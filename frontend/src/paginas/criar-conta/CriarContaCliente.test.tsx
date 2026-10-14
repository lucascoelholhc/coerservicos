import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { encerrarSessao, tokenAtual } from '../../api/sessao';
import { semViolacoes } from '../../teste/axe';
import { REGRAS_PADRAO, SESSAO_PADRAO } from '../../teste/dadosDaApi';
import { json, problema, simularFetch, type Pedido } from '../../teste/fetchSimulado';
import { CriarContaCliente } from './CriarContaCliente';

function OndeEstou() {
  const local = useLocation();
  const aviso = (local.state as { aviso?: string } | null)?.aviso ?? '';
  return (
    <p>
      Fui para {local.pathname} {aviso}
    </p>
  );
}

function abrir() {
  return render(
    <MemoryRouter initialEntries={['/criar-conta/cliente']}>
      <Routes>
        <Route path="/criar-conta/cliente" element={<CriarContaCliente />} />
        <Route path="*" element={<OndeEstou />} />
      </Routes>
    </MemoryRouter>,
  );
}

type Responder = (pedido: Pedido) => Response | Promise<Response>;

/** API simulada: regras ok; cadastro e entrar respondem como o teste quiser. */
function simular({ cadastro, entrar }: { cadastro?: Responder; entrar?: Responder } = {}) {
  return simularFetch((pedido) => {
    if (pedido.url === '/api/publico/regras') {
      return json(REGRAS_PADRAO);
    }
    if (pedido.url === '/api/contas/cliente') {
      return cadastro ? cadastro(pedido) : json({ id: 'u-ana', nome: 'Ana Souza' }, 201);
    }
    if (pedido.url === '/api/auth/entrar') {
      return entrar ? entrar(pedido) : json(SESSAO_PADRAO);
    }
    return problema(404, 'nao-encontrado', 'x');
  });
}

async function preencher() {
  await userEvent.type(screen.getByRole('textbox', { name: 'Seu nome' }), 'Ana Souza');
  await userEvent.type(screen.getByRole('textbox', { name: 'Celular' }), '47900000001');
  await userEvent.type(screen.getByRole('textbox', { name: 'E-mail' }), 'ana@exemplo.com');
  await userEvent.type(screen.getByRole('textbox', { name: 'Seu CEP' }), '89010000');
  await userEvent.type(screen.getByLabelText('Crie uma senha'), 'Casa-Azul-2026');
  await userEvent.click(screen.getByRole('checkbox', { name: /Li e aceito/ }));
}

async function criar() {
  await userEvent.click(screen.getByRole('button', { name: 'Criar conta' }));
}

describe('/criar-conta/cliente', () => {
  afterEach(() => {
    encerrarSessao();
    vi.unstubAllGlobals();
  });

  it('campos com autocomplete, termos com links, um botão principal e nenhuma violação de acessibilidade', async () => {
    simular();
    const { container } = abrir();
    await screen.findByRole('button', { name: 'Criar conta' });

    expect(screen.getByRole('heading', { level: 1, name: 'Criar conta para contratar' })).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: 'Seu nome' })).toHaveAttribute('autocomplete', 'name');
    expect(screen.getByRole('textbox', { name: 'Celular' })).toHaveAttribute('autocomplete', 'tel');
    expect(screen.getByRole('textbox', { name: 'E-mail' })).toHaveAttribute('autocomplete', 'email');
    expect(screen.getByRole('textbox', { name: 'Seu CEP' })).toHaveAttribute('autocomplete', 'postal-code');
    expect(screen.getByLabelText('Crie uma senha')).toHaveAttribute('autocomplete', 'new-password');
    expect(screen.getByLabelText('Crie uma senha')).toHaveAccessibleDescription(
      'Use pelo menos 8 caracteres. Não use seu celular nem seu e-mail.',
    );
    expect(screen.getByRole('link', { name: 'termos de uso' })).toHaveAttribute('href', '/termos');
    expect(screen.getByRole('link', { name: 'política de privacidade' })).toHaveAttribute('href', '/privacidade');
    expect(container.querySelectorAll('[data-variante="principal"]')).toHaveLength(1);
    expect(document.title).toBe('Criar conta · COE Serviços');
    await semViolacoes(container);
  });

  it('antes de enviar, avisa o que falta e põe o foco no primeiro campo com erro (sem chamar a API)', async () => {
    const { pedidos } = simular();
    abrir();
    await screen.findByRole('button', { name: 'Criar conta' });
    await userEvent.type(screen.getByLabelText('Crie uma senha'), 'curta');

    await criar();

    const nome = screen.getByRole('textbox', { name: 'Seu nome' });
    expect(nome).toHaveFocus();
    expect(nome).toHaveAttribute('aria-invalid', 'true');
    expect(nome).toHaveAccessibleDescription('Informe seu nome.');
    expect(screen.getByLabelText('Crie uma senha')).toHaveAccessibleDescription(
      'A senha precisa ter pelo menos 8 caracteres. Use pelo menos 8 caracteres. Não use seu celular nem seu e-mail.',
    );
    expect(screen.getByRole('checkbox', { name: /Li e aceito/ })).toHaveAttribute('aria-invalid', 'true');
    expect(pedidos.some((pedido) => pedido.url === '/api/contas/cliente')).toBe(false);
  });

  it('cria a conta, entra com o celular e a senha digitados e vai para Minha conta com o aviso do e-mail', async () => {
    const { pedidos } = simular();
    abrir();
    await screen.findByRole('button', { name: 'Criar conta' });
    await preencher();

    await criar();

    expect(
      await screen.findByText('Fui para /conta Enviamos um link para o seu e-mail. Confirme para conseguir recuperar a senha.'),
    ).toBeInTheDocument();
    const cadastro = pedidos.find((pedido) => pedido.url === '/api/contas/cliente');
    expect(JSON.parse(cadastro?.corpo ?? '{}')).toEqual({
      nome: 'Ana Souza',
      celular: '47900000001',
      email: 'ana@exemplo.com',
      cep: '89010000',
      senha: 'Casa-Azul-2026',
      versaoTermosAceita: '1.0',
    });
    const login = pedidos.find((pedido) => pedido.url === '/api/auth/entrar');
    expect(JSON.parse(login?.corpo ?? '{}')).toEqual({ login: '47900000001', senha: 'Casa-Azul-2026' });
    expect(tokenAtual()).toBe('token-de-teste');
  });

  it('erro por campo vindo da API: ao lado do campo, com aria-invalid, e foco nele', async () => {
    simular({
      cadastro: () =>
        problema(400, 'validacao', 'Confira os campos.', { campos: [{ campo: 'celular', mensagem: 'Celular inválido.' }] }),
    });
    abrir();
    await screen.findByRole('button', { name: 'Criar conta' });
    await preencher();

    await criar();

    const celular = await screen.findByRole('textbox', { name: 'Celular' });
    await vi.waitFor(() => expect(celular).toHaveAttribute('aria-invalid', 'true'));
    expect(celular).toHaveAccessibleDescription('Celular inválido.');
    expect(celular).toHaveFocus();
  });

  it('409 contato-em-uso: a mensagem neutra da API com "Entrar" e "Esqueci minha senha"', async () => {
    simular({
      cadastro: () =>
        problema(409, 'contato-em-uso', 'Esse contato já está em uso. Se ele for seu, confirme que é seu para usar nesta conta.', {
          campo: 'email',
        }),
    });
    abrir();
    await screen.findByRole('button', { name: 'Criar conta' });
    await preencher();

    await criar();

    const aviso = await screen.findByRole('alert');
    expect(aviso).toHaveTextContent('Esse contato já está em uso. Se ele for seu, confirme que é seu para usar nesta conta.');
    expect(within(aviso).getByRole('link', { name: 'Entrar' })).toHaveAttribute('href', '/entrar');
    expect(within(aviso).getByRole('link', { name: 'Esqueci minha senha' })).toHaveAttribute('href', '/esqueci-senha');
    expect(screen.getByRole('textbox', { name: 'E-mail' })).toHaveAttribute('aria-invalid', 'true');
  });

  it('422 termos-desatualizados: busca a versão nova e pede o aceite de novo', async () => {
    const { pedidos } = simular({ cadastro: () => problema(422, 'termos-desatualizados', 'Os termos mudaram.') });
    abrir();
    await screen.findByRole('button', { name: 'Criar conta' });
    await preencher();

    await criar();

    const aceite = await screen.findByRole('checkbox', { name: /Li e aceito/ });
    await vi.waitFor(() => expect(aceite).not.toBeChecked());
    expect(aceite).toHaveAccessibleDescription('Os termos mudaram. Leia e aceite de novo para continuar.');
    await vi.waitFor(() => expect(pedidos.filter((pedido) => pedido.url === '/api/publico/regras')).toHaveLength(2));
  });

  it('envio duplo bloqueado: "Criando sua conta…" desabilitado e um único cadastro', async () => {
    let responder: (resposta: Response) => void = () => undefined;
    const { pedidos } = simular({
      cadastro: () =>
        new Promise<Response>((resolver) => {
          responder = resolver;
        }),
    });
    abrir();
    await screen.findByRole('button', { name: 'Criar conta' });
    await preencher();

    await criar();
    const botao = screen.getByRole('button', { name: 'Criando sua conta…' });
    expect(botao).toBeDisabled();
    await userEvent.click(botao);

    expect(pedidos.filter((pedido) => pedido.url === '/api/contas/cliente')).toHaveLength(1);
    responder(json({ id: 'u-ana', nome: 'Ana Souza' }, 201));
    expect(await screen.findByText(/Fui para \/conta/)).toBeInTheDocument();
  });

  it('conta criada, mas o entrar automático falhou: vai para /entrar com o aviso', async () => {
    simular({ entrar: () => problema(429, 'muitas-tentativas', 'x') });
    abrir();
    await screen.findByRole('button', { name: 'Criar conta' });
    await preencher();

    await criar();

    expect(await screen.findByText('Fui para /entrar Sua conta foi criada. Entre com seu celular e sua senha.')).toBeInTheDocument();
  });

  it('sem conexão ao criar: mensagem própria', async () => {
    simular({
      cadastro: () => {
        throw new TypeError('Failed to fetch');
      },
    });
    abrir();
    await screen.findByRole('button', { name: 'Criar conta' });
    await preencher();

    await criar();

    expect(await screen.findByRole('alert')).toHaveTextContent('Sem conexão. Confira a internet e tente de novo.');
  });

  it('versão dos termos não carregou: mostra o erro com "Tentar de novo" e o botão fica desabilitado', async () => {
    let tentativas = 0;
    simularFetch((pedido) => {
      if (pedido.url === '/api/publico/regras') {
        tentativas += 1;
        return tentativas === 1 ? problema(500, 'x', 'x') : json(REGRAS_PADRAO);
      }
      return problema(404, 'x', 'x');
    });
    abrir();

    expect(await screen.findByRole('alert')).toHaveTextContent('A COE está com um problema agora.');
    expect(screen.getByRole('button', { name: 'Criar conta' })).toBeDisabled();

    await userEvent.click(screen.getByRole('button', { name: 'Tentar de novo' }));

    await vi.waitFor(() => expect(screen.getByRole('button', { name: 'Criar conta' })).toBeEnabled());
  });
});

describe('cadastro: provar que o contato é meu (RN61)', () => {
  afterEach(() => {
    encerrarSessao();
    vi.unstubAllGlobals();
  });

  const EM_USO = 'Esse contato já está em uso. Se ele for seu, confirme que é seu para usar nesta conta.';

  /** Regras, entrar e posse; o cadastro responde em sequência (uma resposta por envio). */
  function simularPosse(cadastros: Responder[]) {
    let envios = 0;
    return simularFetch((pedido) => {
      switch (pedido.url) {
        case '/api/publico/regras':
          return json(REGRAS_PADRAO);
        case '/api/contas/cliente': {
          const responder = cadastros[Math.min(envios, cadastros.length - 1)] as Responder;
          envios += 1;
          return responder(pedido);
        }
        case '/api/auth/entrar':
          return json(SESSAO_PADRAO);
        case '/api/contas/posse/celular/confirmar':
          return json({ comprovante: 'comp-cel' });
        default:
          return json({ mensagem: 'Enviamos a confirmação.' }, 202);
      }
    });
  }

  async function chegarNoConflito() {
    abrir();
    await screen.findByRole('button', { name: 'Criar conta' });
    await preencher();
    await criar();
    return screen.findByRole('alert');
  }

  async function provarCelular() {
    await userEvent.click(await screen.findByRole('button', { name: 'Esse celular é meu' }));
    await userEvent.type(await screen.findByRole('textbox', { name: 'Código' }), '123456');
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar' }));
  }

  it('409 no celular: "Esse celular é meu" → código → cadastro reenviado com o comprovante, sem redigitar', async () => {
    const { pedidos } = simularPosse([
      () => problema(409, 'contato-em-uso', EM_USO, { campo: 'celular' }),
      () => json({ id: 'u-ana', nome: 'Ana Souza' }, 201),
    ]);

    const aviso = await chegarNoConflito();
    expect(within(aviso).getByRole('link', { name: 'Entrar' })).toBeInTheDocument();
    expect(within(aviso).getByRole('link', { name: 'Esqueci minha senha' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Esse e-mail é meu' })).not.toBeInTheDocument();
    await provarCelular();

    expect(await screen.findByText(/Fui para \/conta/)).toBeInTheDocument();
    const cadastros = pedidos.filter((pedido) => pedido.url === '/api/contas/cliente');
    expect(cadastros).toHaveLength(2);
    expect(JSON.parse(cadastros[1]?.corpo ?? '{}')).toEqual({
      ...JSON.parse(cadastros[0]?.corpo ?? '{}'),
      comprovanteCelular: 'comp-cel',
    });
    expect(pedidos.find((pedido) => pedido.url === '/api/contas/posse/celular')?.corpo).toBe(
      JSON.stringify({ celular: '47900000001' }),
    );
  });

  it('409 no e-mail: "Esse e-mail é meu" manda o link e espera a confirmação', async () => {
    const { pedidos } = simularPosse([() => problema(409, 'contato-em-uso', EM_USO, { campo: 'email' })]);

    await chegarNoConflito();
    expect(screen.queryByRole('button', { name: 'Esse celular é meu' })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Esse e-mail é meu' }));

    expect(await screen.findByText(/Enviamos um link para ana@exemplo.com/)).toBeInTheDocument();
    expect(pedidos.find((pedido) => pedido.url === '/api/contas/posse/email')?.corpo).toBe(
      JSON.stringify({ email: 'ana@exemplo.com' }),
    );
  });

  it('comprovante vencido (422 comprovante-invalido): mensagem clara e a prova recomeça', async () => {
    const { pedidos } = simularPosse([
      () => problema(409, 'contato-em-uso', EM_USO, { campo: 'celular' }),
      () => problema(422, 'comprovante-invalido', 'A confirmação deste contato venceu ou não vale. Confirme de novo.'),
      () => json({ id: 'u-ana', nome: 'Ana Souza' }, 201),
    ]);

    await chegarNoConflito();
    await provarCelular();

    expect(await screen.findByText('A confirmação deste contato venceu ou não vale. Confirme de novo.')).toBeInTheDocument();
    await provarCelular();

    expect(await screen.findByText(/Fui para \/conta/)).toBeInTheDocument();
    const cadastros = pedidos.filter((pedido) => pedido.url === '/api/contas/cliente');
    expect(JSON.parse(cadastros[2]?.corpo ?? '{}')).toMatchObject({ comprovanteCelular: 'comp-cel' });
  });

  it('mudar o celular depois de provar descarta o comprovante', async () => {
    const { pedidos } = simularPosse([
      () => problema(409, 'contato-em-uso', EM_USO, { campo: 'celular' }),
      () => problema(409, 'contato-em-uso', EM_USO, { campo: 'email' }),
      () => json({ id: 'u-ana', nome: 'Ana Souza' }, 201),
    ]);

    await chegarNoConflito();
    await provarCelular();
    await screen.findByRole('button', { name: 'Esse e-mail é meu' });
    const celular = screen.getByRole('textbox', { name: 'Celular' });
    await userEvent.clear(celular);
    await userEvent.type(celular, '47900000002');
    await criar();

    await screen.findByText(/Fui para \/conta/);
    const cadastros = pedidos.filter((pedido) => pedido.url === '/api/contas/cliente');
    expect(JSON.parse(cadastros[1]?.corpo ?? '{}')).toMatchObject({ comprovanteCelular: 'comp-cel' });
    expect(JSON.parse(cadastros[2]?.corpo ?? '{}')).not.toHaveProperty('comprovanteCelular');
  });

  it('"Cancelar" volta para o aviso do conflito', async () => {
    simularPosse([() => problema(409, 'contato-em-uso', EM_USO, { campo: 'email' })]);

    await chegarNoConflito();
    await userEvent.click(screen.getByRole('button', { name: 'Esse e-mail é meu' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Cancelar' }));

    expect(screen.getByRole('button', { name: 'Esse e-mail é meu' })).toBeInTheDocument();
  });
});
