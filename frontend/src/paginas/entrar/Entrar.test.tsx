import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { encerrarSessao, iniciarSessao, tokenAtual } from '../../api/sessao';
import { semViolacoes } from '../../teste/axe';
import { SESSAO_PADRAO } from '../../teste/dadosDaApi';
import { json, problema, simularFetch, type Pedido } from '../../teste/fetchSimulado';
import { Entrar } from './Entrar';

function OndeEstou() {
  const local = useLocation();
  return <p>Fui para {`${local.pathname}${local.search}`}</p>;
}

function abrir(busca = '', estado?: unknown) {
  return render(
    <MemoryRouter initialEntries={[{ pathname: '/entrar', search: busca, state: estado }]}>
      <Routes>
        <Route path="/entrar" element={<Entrar />} />
        <Route path="*" element={<OndeEstou />} />
      </Routes>
    </MemoryRouter>,
  );
}

async function preencherEEntrar(login = 'ana@exemplo.com', senha = 'Casa-Azul-2026') {
  await userEvent.type(screen.getByRole('textbox', { name: 'Celular ou e-mail' }), login);
  await userEvent.type(screen.getByLabelText('Senha'), senha);
  await userEvent.click(screen.getByRole('button', { name: 'Entrar' }));
}

describe('/entrar', () => {
  afterEach(() => {
    encerrarSessao();
    vi.unstubAllGlobals();
  });

  it('campos com autocomplete, um botão principal, links e nenhuma violação de acessibilidade', async () => {
    const { container } = abrir();

    expect(screen.getByRole('heading', { level: 1, name: 'Entrar' })).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: 'Celular ou e-mail' })).toHaveAttribute('autocomplete', 'username');
    expect(screen.getByLabelText('Senha')).toHaveAttribute('autocomplete', 'current-password');
    expect(screen.getByRole('button', { name: 'Mostrar senha' })).toBeInTheDocument();
    expect(container.querySelectorAll('[data-variante="principal"]')).toHaveLength(1);
    expect(screen.getByRole('link', { name: 'Esqueci minha senha' })).toHaveAttribute('href', '/esqueci-senha');
    expect(screen.getByRole('link', { name: 'Criar conta' })).toHaveAttribute('href', '/criar-conta');
    expect(screen.queryByRole('button', { name: 'Entrar com código por SMS' })).not.toBeInTheDocument();
    expect(document.title).toBe('Entrar · COE Serviços');
    await semViolacoes(container);
  });

  it('entra, guarda a sessão só na memória e vai para Minha conta', async () => {
    const { pedidos } = simularFetch(() => json(SESSAO_PADRAO));
    abrir();

    await preencherEEntrar();

    expect(await screen.findByText('Fui para /conta')).toBeInTheDocument();
    expect(pedidos[0]).toMatchObject({ url: '/api/auth/entrar', metodo: 'POST' });
    expect(JSON.parse(pedidos[0]?.corpo ?? '{}')).toEqual({ login: 'ana@exemplo.com', senha: 'Casa-Azul-2026' });
    expect(tokenAtual()).toBe('token-de-teste');
    expect(JSON.stringify({ ...localStorage, ...sessionStorage })).not.toContain('token-de-teste');
  });

  it('com voltar válido, volta para lá', async () => {
    simularFetch(() => json(SESSAO_PADRAO));
    abrir('?voltar=%2Fbusca%3Fprofissao%3Dpintor');

    await preencherEEntrar();

    expect(await screen.findByText('Fui para /busca?profissao=pintor')).toBeInTheDocument();
  });

  it('voltar inválido (outro site) é ignorado: vai para Minha conta', async () => {
    simularFetch(() => json(SESSAO_PADRAO));
    abrir('?voltar=%2F%2Fevil.com');

    await preencherEEntrar();

    expect(await screen.findByText('Fui para /conta')).toBeInTheDocument();
  });

  it.each([
    ['login-invalido', () => problema(401, 'login-invalido', 'Celular, e-mail ou senha incorretos.'), 'Celular, e-mail ou senha incorretos.'],
    ['conta-suspensa', () => problema(403, 'conta-suspensa', 'Sua conta está suspensa. Fale com a equipe COE.'), 'Sua conta está suspensa. Fale com a equipe COE.'],
    ['celular-nao-confirmado', () => problema(403, 'celular-nao-confirmado', 'Confirme seu celular para entrar.'), 'Confirme seu celular para entrar.'],
    ['muitas-tentativas', () => new Response('', { status: 429 }), 'Muitas tentativas seguidas. Espere um pouco e tente de novo.'],
  ])('%s: mensagem em português, anunciada, e a pessoa continua na página', async (_, resposta, mensagem) => {
    simularFetch(resposta);
    abrir();

    await preencherEEntrar();

    expect(await screen.findByRole('alert')).toHaveTextContent(mensagem);
    expect(screen.getByRole('heading', { level: 1, name: 'Entrar' })).toBeInTheDocument();
    expect(tokenAtual()).toBeNull();
  });

  it('sem conexão: mensagem própria', async () => {
    simularFetch(() => {
      throw new TypeError('Failed to fetch');
    });
    abrir();

    await preencherEEntrar();

    expect(await screen.findByRole('alert')).toHaveTextContent('Sem conexão. Confira a internet e tente de novo.');
  });

  it('envio duplo bloqueado: um único POST e "Entrando…" enquanto espera', async () => {
    let responder: (resposta: Response) => void = () => undefined;
    const { pedidos } = simularFetch(
      () =>
        new Promise<Response>((resolver) => {
          responder = resolver;
        }),
    );
    abrir();

    await preencherEEntrar();
    const botao = screen.getByRole('button', { name: 'Entrando…' });
    expect(botao).toBeDisabled();
    await userEvent.click(botao);

    expect(pedidos).toHaveLength(1);
    responder(json(SESSAO_PADRAO));
    expect(await screen.findByText('Fui para /conta')).toBeInTheDocument();
  });

  it('segundo passo (MFA ou ADMIN): a mesma página pede o código e entra com o desafio', async () => {
    const pedidos: Pedido[] = [];
    simularFetch((pedido) => {
      pedidos.push(pedido);
      return pedido.url === '/api/auth/entrar'
        ? problema(403, 'segundo-passo-necessario', 'Digite o código que enviamos por SMS.', { desafioId: 'd-1' })
        : json(SESSAO_PADRAO);
    });
    const { container } = abrir();

    await preencherEEntrar();
    expect(await screen.findByRole('heading', { level: 1, name: 'Digite o código que enviamos por SMS' })).toBeInTheDocument();
    await semViolacoes(container);
    await userEvent.type(screen.getByRole('textbox', { name: 'Código' }), '123456');
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }));

    expect(await screen.findByText('Fui para /conta')).toBeInTheDocument();
    expect(pedidos[1]).toMatchObject({ url: '/api/auth/segundo-passo', metodo: 'POST' });
    expect(JSON.parse(pedidos[1]?.corpo ?? '{}')).toEqual({ desafioId: 'd-1', codigo: '123456' });
    expect(JSON.stringify({ ...localStorage, ...sessionStorage })).not.toContain('d-1');
  });

  it('código errado no segundo passo: mensagem e continua no código', async () => {
    simularFetch((pedido) =>
      pedido.url === '/api/auth/entrar'
        ? problema(403, 'segundo-passo-necessario', 'x', { desafioId: 'd-1' })
        : problema(401, 'login-invalido', 'Código incorreto ou vencido.'),
    );
    abrir();

    await preencherEEntrar();
    await userEvent.type(await screen.findByRole('textbox', { name: 'Código' }), '000000');
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Código incorreto ou vencido.');
    expect(screen.getByRole('heading', { level: 1, name: 'Digite o código que enviamos por SMS' })).toBeInTheDocument();
  });

  it('quem já entrou vai direto para Minha conta', () => {
    iniciarSessao(SESSAO_PADRAO);
    abrir();

    expect(screen.getByText('Fui para /conta')).toBeInTheDocument();
  });

  it('aviso vindo de outra página (ex.: senha alterada) aparece em cima do formulário', () => {
    abrir('', { aviso: 'Senha alterada. Entre com a nova senha.' });

    expect(screen.getByRole('status')).toHaveTextContent('Senha alterada. Entre com a nova senha.');
  });
});
