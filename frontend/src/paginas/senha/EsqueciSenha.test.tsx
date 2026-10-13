import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { semViolacoes } from '../../teste/axe';
import { problema, simularFetch } from '../../teste/fetchSimulado';
import { EsqueciSenha } from './EsqueciSenha';

function OndeEstou() {
  const local = useLocation();
  const login = (local.state as { login?: string } | null)?.login ?? '';
  return (
    <p>
      Fui para {local.pathname} {login}
    </p>
  );
}

function abrir() {
  return render(
    <MemoryRouter initialEntries={['/esqueci-senha']}>
      <Routes>
        <Route path="/esqueci-senha" element={<EsqueciSenha />} />
        <Route path="*" element={<OndeEstou />} />
      </Routes>
    </MemoryRouter>,
  );
}

async function pedir(login = 'ana@exemplo.com') {
  await userEvent.type(screen.getByRole('textbox', { name: 'Celular ou e-mail' }), login);
  await userEvent.click(screen.getByRole('button', { name: 'Recuperar senha' }));
}

describe('/esqueci-senha', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('um campo, um botão principal e nenhuma violação de acessibilidade', async () => {
    const { container } = abrir();

    expect(screen.getByRole('heading', { level: 1, name: 'Esqueci minha senha' })).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: 'Celular ou e-mail' })).toHaveAttribute('autocomplete', 'username');
    expect(container.querySelectorAll('[data-variante="principal"]')).toHaveLength(1);
    expect(document.title).toBe('Esqueci minha senha · COE Serviços');
    await semViolacoes(container);
  });

  it('resposta sempre igual (não revela se a conta existe) e oferece "Recebi um código por SMS"', async () => {
    const { pedidos } = simularFetch(() => new Response(null, { status: 202 }));
    abrir();

    await pedir('47900000001');

    expect(await screen.findByRole('status')).toHaveTextContent(
      'Se existir uma conta, enviamos um link para o e-mail confirmado ou um código por SMS.',
    );
    expect(JSON.parse(pedidos[0]?.corpo ?? '{}')).toEqual({ login: '47900000001' });
    await userEvent.click(screen.getByRole('button', { name: 'Recebi um código por SMS' }));
    expect(screen.getByText('Fui para /redefinir-senha 47900000001')).toBeInTheDocument();
  });

  it('muitas tentativas: mensagem de espera', async () => {
    simularFetch(() => new Response('', { status: 429 }));
    abrir();

    await pedir();

    expect(await screen.findByRole('alert')).toHaveTextContent('Muitas tentativas seguidas.');
  });

  it('sem conexão: mensagem própria', async () => {
    simularFetch(() => {
      throw new TypeError('Failed to fetch');
    });
    abrir();

    await pedir();

    expect(await screen.findByRole('alert')).toHaveTextContent('Sem conexão. Confira a internet e tente de novo.');
  });

  it('campo vazio: avisa antes, sem chamar a API', async () => {
    const { falso } = simularFetch(() => new Response(null, { status: 202 }));
    abrir();

    await userEvent.click(screen.getByRole('button', { name: 'Recuperar senha' }));

    expect(screen.getByRole('textbox', { name: 'Celular ou e-mail' })).toHaveAccessibleDescription(
      'Informe o celular ou o e-mail da sua conta.',
    );
    expect(falso).not.toHaveBeenCalled();
  });

  it('envio duplo bloqueado', async () => {
    const { pedidos } = simularFetch(() => new Promise<Response>(() => undefined));
    abrir();

    await pedir();
    const botao = screen.getByRole('button', { name: 'Enviando…' });
    expect(botao).toBeDisabled();
    await userEvent.click(botao);

    expect(pedidos).toHaveLength(1);
  });

  it('erro da API com Problem Details: mostra a mensagem dela', async () => {
    simularFetch(() => problema(400, 'validacao', 'Confira o que você digitou.'));
    abrir();

    await pedir();

    expect(await screen.findByRole('alert')).toHaveTextContent('Confira o que você digitou.');
  });
});
