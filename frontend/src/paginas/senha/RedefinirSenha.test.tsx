import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { encerrarSessao, iniciarSessao, tokenAtual } from '../../api/sessao';
import { semViolacoes } from '../../teste/axe';
import { SESSAO_PADRAO } from '../../teste/dadosDaApi';
import { problema, simularFetch, type Pedido } from '../../teste/fetchSimulado';
import { RedefinirSenha } from './RedefinirSenha';

function OndeEstou() {
  const local = useLocation();
  const aviso = (local.state as { aviso?: string } | null)?.aviso ?? '';
  return (
    <p>
      Fui para {local.pathname} {aviso}
    </p>
  );
}

function abrir(endereco: string, estado?: unknown) {
  window.history.replaceState(null, '', endereco);
  return render(
    <MemoryRouter initialEntries={[{ pathname: '/redefinir-senha', state: estado }]}>
      <Routes>
        <Route path="/redefinir-senha" element={<RedefinirSenha />} />
        <Route path="*" element={<OndeEstou />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('/redefinir-senha', () => {
  afterEach(() => {
    encerrarSessao();
    window.history.replaceState(null, '', '/');
    vi.unstubAllGlobals();
  });

  it('pelo link: limpa o #token= antes de qualquer chamada, pede só a nova senha e manda o token no corpo', async () => {
    let fragmentoNaHoraDaChamada: string | null = null;
    const pedidos: Pedido[] = [];
    simularFetch((pedido) => {
      fragmentoNaHoraDaChamada = window.location.hash;
      pedidos.push(pedido);
      return new Response(null, { status: 204 });
    });
    const { container } = abrir('/redefinir-senha#token=abc123');

    expect(window.location.hash).toBe('');
    expect(screen.queryByRole('textbox', { name: 'Celular' })).not.toBeInTheDocument();
    expect(screen.getByLabelText('Nova senha')).toHaveAttribute('autocomplete', 'new-password');
    await semViolacoes(container);
    await userEvent.type(screen.getByLabelText('Nova senha'), 'Nova-Senha-2026');
    await userEvent.click(screen.getByRole('button', { name: 'Salvar nova senha' }));

    expect(await screen.findByText('Fui para /entrar Senha alterada. Entre com a nova senha.')).toBeInTheDocument();
    expect(fragmentoNaHoraDaChamada).toBe('');
    expect(pedidos[0]?.url).not.toContain('abc123');
    expect(JSON.parse(pedidos[0]?.corpo ?? '{}')).toEqual({ token: 'abc123', novaSenha: 'Nova-Senha-2026' });
  });

  it('pelo código do SMS: celular (vindo do "esqueci"), código e nova senha', async () => {
    const { pedidos } = simularFetch(() => new Response(null, { status: 204 }));
    abrir('/redefinir-senha', { login: '47900000001' });

    expect(screen.getByRole('textbox', { name: 'Celular' })).toHaveValue('47900000001');
    await userEvent.type(screen.getByRole('textbox', { name: 'Código' }), '123456');
    await userEvent.type(screen.getByLabelText('Nova senha'), 'Nova-Senha-2026');
    await userEvent.click(screen.getByRole('button', { name: 'Salvar nova senha' }));

    expect(await screen.findByText(/Fui para \/entrar/)).toBeInTheDocument();
    expect(JSON.parse(pedidos[0]?.corpo ?? '{}')).toEqual({
      celular: '47900000001',
      codigo: '123456',
      novaSenha: 'Nova-Senha-2026',
    });
  });

  it('login que não é celular não preenche o campo', () => {
    abrir('/redefinir-senha', { login: 'ana@exemplo.com' });

    expect(screen.getByRole('textbox', { name: 'Celular' })).toHaveValue('');
  });

  it('redefinir derruba as outras sessões: a sessão deste aparelho também sai', async () => {
    iniciarSessao(SESSAO_PADRAO);
    simularFetch(() => new Response(null, { status: 204 }));
    abrir('/redefinir-senha#token=abc123');

    await userEvent.type(screen.getByLabelText('Nova senha'), 'Nova-Senha-2026');
    await userEvent.click(screen.getByRole('button', { name: 'Salvar nova senha' }));

    expect(await screen.findByText(/Fui para \/entrar/)).toBeInTheDocument();
    expect(tokenAtual()).toBeNull();
  });

  it('link inválido: mensagem da API e caminho para pedir outro', async () => {
    simularFetch(() => problema(422, 'link-invalido', 'Este link não vale mais. Peça outro.'));
    abrir('/redefinir-senha#token=velho');

    await userEvent.type(screen.getByLabelText('Nova senha'), 'Nova-Senha-2026');
    await userEvent.click(screen.getByRole('button', { name: 'Salvar nova senha' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Este link não vale mais. Peça outro.');
    expect(screen.getByRole('link', { name: 'Pedir outro link' })).toHaveAttribute('href', '/esqueci-senha');
  });

  it('senha não permitida: erro ao lado do campo da nova senha', async () => {
    simularFetch(() => problema(422, 'senha-nao-permitida', 'Escolha uma senha menos óbvia.'));
    abrir('/redefinir-senha#token=abc123');

    await userEvent.type(screen.getByLabelText('Nova senha'), 'Senha-123456');
    await userEvent.click(screen.getByRole('button', { name: 'Salvar nova senha' }));

    await vi.waitFor(() => expect(screen.getByLabelText('Nova senha')).toHaveAttribute('aria-invalid', 'true'));
    expect(screen.getByLabelText('Nova senha')).toHaveAccessibleDescription(
      'Escolha uma senha menos óbvia. Use pelo menos 8 caracteres. Não use seu celular nem seu e-mail.',
    );
  });

  it('senha curta: avisa antes, sem chamar a API', async () => {
    const { falso } = simularFetch(() => new Response(null, { status: 204 }));
    abrir('/redefinir-senha#token=abc123');

    await userEvent.type(screen.getByLabelText('Nova senha'), 'curta');
    await userEvent.click(screen.getByRole('button', { name: 'Salvar nova senha' }));

    expect(screen.getByLabelText('Nova senha')).toHaveAttribute('aria-invalid', 'true');
    expect(falso).not.toHaveBeenCalled();
  });

  it('sem conexão: mensagem própria e o token continua só na memória', async () => {
    simularFetch(() => {
      throw new TypeError('Failed to fetch');
    });
    abrir('/redefinir-senha#token=abc123');

    await userEvent.type(screen.getByLabelText('Nova senha'), 'Nova-Senha-2026');
    await userEvent.click(screen.getByRole('button', { name: 'Salvar nova senha' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Sem conexão. Confira a internet e tente de novo.');
    expect(JSON.stringify({ ...localStorage, ...sessionStorage })).not.toContain('abc123');
  });
});
