import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { encerrarSessao, iniciarSessao } from '../../api/sessao';
import { SessaoProvider } from '../../api/SessaoProvider';
import { semViolacoes } from '../../teste/axe';
import { REGRAS_PADRAO, SESSAO_PADRAO } from '../../teste/dadosDaApi';
import { json, problema, simularFetch } from '../../teste/fetchSimulado';
import { CriarContaProfissional } from './CriarContaProfissional';

function OndeEstou() {
  return <p>{`Fui para ${useLocation().pathname}`}</p>;
}

function abrir() {
  return render(
    <MemoryRouter initialEntries={['/criar-conta/profissional']}>
      <SessaoProvider>
        <Routes>
          <Route path="/criar-conta/profissional" element={<CriarContaProfissional />} />
          <Route path="*" element={<OndeEstou />} />
        </Routes>
      </SessaoProvider>
    </MemoryRouter>,
  );
}

function simular() {
  return simularFetch((pedido) => {
    if (pedido.url === '/api/publico/regras') {
      return json(REGRAS_PADRAO);
    }
    if (pedido.url === '/api/contas/cliente') {
      return json({ id: 'u-ana', nome: 'Ana Souza' }, 201);
    }
    if (pedido.url === '/api/auth/entrar') {
      return json(SESSAO_PADRAO);
    }
    return problema(404, 'nao-encontrado', 'x');
  });
}

describe('/criar-conta/profissional', () => {
  afterEach(() => {
    encerrarSessao();
    vi.unstubAllGlobals();
  });

  it('primeiro a conta (o mesmo formulário), depois segue para o cadastro do trabalho', async () => {
    const { pedidos } = simular();
    const { container } = abrir();

    expect(screen.getByRole('heading', { level: 1, name: 'Criar conta para trabalhar' })).toBeInTheDocument();
    expect(screen.getByText(/Primeiro, sua conta/)).toBeInTheDocument();
    expect(document.title).toBe('Criar conta para trabalhar · COE Serviços');
    await screen.findByRole('button', { name: 'Criar conta' });
    await semViolacoes(container);

    await userEvent.type(screen.getByRole('textbox', { name: 'Seu nome' }), 'Ana Souza');
    await userEvent.type(screen.getByRole('textbox', { name: 'Celular' }), '47900000001');
    await userEvent.type(screen.getByRole('textbox', { name: 'E-mail' }), 'ana@exemplo.com');
    await userEvent.type(screen.getByRole('textbox', { name: 'Seu CEP' }), '89010000');
    await userEvent.type(screen.getByLabelText('Crie uma senha'), 'Casa-Azul-2026');
    await userEvent.click(screen.getByRole('checkbox', { name: /Li e aceito/ }));
    await userEvent.click(screen.getByRole('button', { name: 'Criar conta' }));

    expect(await screen.findByText('Fui para /cadastro')).toBeInTheDocument();
    expect(pedidos.some((pedido) => pedido.url === '/api/contas/cliente')).toBe(true);
  });

  it('quem já entrou vai direto para o cadastro', async () => {
    simular();
    iniciarSessao(SESSAO_PADRAO);
    abrir();

    expect(await screen.findByText('Fui para /cadastro')).toBeInTheDocument();
  });

  it('já tem conta: o link de entrar volta para o cadastro', () => {
    simular();
    abrir();

    expect(screen.getByRole('link', { name: 'Entrar' })).toHaveAttribute('href', '/entrar?voltar=%2Fcadastro');
  });
});
