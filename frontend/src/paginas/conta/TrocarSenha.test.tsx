import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { encerrarSessao, iniciarSessao, tokenAtual } from '../../api/sessao';
import { semViolacoes } from '../../teste/axe';
import { SESSAO_PADRAO } from '../../teste/dadosDaApi';
import { problema, simularFetch } from '../../teste/fetchSimulado';
import { TrocarSenha } from './TrocarSenha';

function OndeEstou() {
  const local = useLocation();
  const aviso = (local.state as { aviso?: string } | null)?.aviso;
  return <p>{`Fui para ${local.pathname}${aviso ? ` · ${aviso}` : ''}`}</p>;
}

function abrir() {
  return render(
    <MemoryRouter initialEntries={['/conta/trocar-senha']}>
      <Routes>
        <Route path="/conta/trocar-senha" element={<TrocarSenha />} />
        <Route path="*" element={<OndeEstou />} />
      </Routes>
    </MemoryRouter>,
  );
}

const senhaAtual = () => screen.getByLabelText('Senha atual');
const novaSenha = () => screen.getByLabelText('Nova senha');

async function preencherETrocar(atual = 'Casa-Azul-2026', nova = 'Casa-Verde-2026') {
  if (atual) {
    await userEvent.type(senhaAtual(), atual);
  }
  if (nova) {
    await userEvent.type(novaSenha(), nova);
  }
  await userEvent.click(screen.getByRole('button', { name: 'Trocar senha' }));
}

describe('/conta/trocar-senha', () => {
  beforeEach(() => iniciarSessao(SESSAO_PADRAO));
  afterEach(() => {
    encerrarSessao();
    vi.unstubAllGlobals();
  });

  it('troca, mantém a sessão deste aparelho e volta para Minha conta com aviso', async () => {
    const { pedidos } = simularFetch(() => new Response(null, { status: 204 }));
    const { container } = abrir();

    expect(screen.getByRole('heading', { level: 1, name: 'Trocar senha' })).toBeInTheDocument();
    expect(document.title).toBe('Trocar senha · COE Serviços');
    expect(senhaAtual()).toHaveAttribute('autocomplete', 'current-password');
    expect(novaSenha()).toHaveAttribute('autocomplete', 'new-password');
    expect(novaSenha()).toHaveAccessibleDescription(/Use pelo menos 8 caracteres/);
    expect(screen.getAllByRole('button', { name: 'Mostrar senha' })).toHaveLength(2);
    expect(screen.getByRole('link', { name: 'Voltar para minha conta' })).toHaveAttribute('href', '/conta');
    await semViolacoes(container);
    await preencherETrocar();

    expect(
      await screen.findByText('Fui para /conta · Senha trocada. Os outros aparelhos foram desconectados.'),
    ).toBeInTheDocument();
    expect(pedidos).toHaveLength(1);
    expect(JSON.parse(pedidos[0]?.corpo ?? '{}')).toEqual({ senhaAtual: 'Casa-Azul-2026', novaSenha: 'Casa-Verde-2026' });
    expect(tokenAtual()).toBe('token-de-teste');
  });

  it('422 senha-atual-incorreta: erro no campo da senha atual', async () => {
    simularFetch(() => problema(422, 'senha-atual-incorreta', 'A senha atual não confere.'));
    abrir();

    await preencherETrocar();

    expect(await screen.findByText('A senha atual não confere.')).toBeInTheDocument();
    expect(senhaAtual()).toHaveAttribute('aria-invalid', 'true');
    expect(novaSenha()).not.toHaveAttribute('aria-invalid', 'true');
  });

  it('422 senha-nao-permitida: erro no campo da nova senha', async () => {
    simularFetch(() => problema(422, 'senha-nao-permitida', 'Escolha uma senha menos óbvia.'));
    abrir();

    await preencherETrocar();

    expect(await screen.findByText('Escolha uma senha menos óbvia.')).toBeInTheDocument();
    expect(novaSenha()).toHaveAttribute('aria-invalid', 'true');
    expect(senhaAtual()).not.toHaveAttribute('aria-invalid', 'true');
  });

  it('429: mensagem de espera', async () => {
    simularFetch(() => problema(429, 'muitas-tentativas', 'x'));
    abrir();

    await preencherETrocar();

    expect(await screen.findByRole('alert')).toHaveTextContent('Muitas tentativas seguidas.');
  });

  it('sem conexão: mensagem', async () => {
    simularFetch(() => {
      throw new TypeError('Failed to fetch');
    });
    abrir();

    await preencherETrocar();

    expect(await screen.findByRole('alert')).toHaveTextContent('Sem conexão. Confira a internet e tente de novo.');
  });

  it('envio duplo bloqueado: um pedido só, botão "Salvando…" desabilitado', async () => {
    const { pedidos } = simularFetch(() => new Promise<Response>(() => undefined));
    abrir();
    await userEvent.type(senhaAtual(), 'Casa-Azul-2026');
    await userEvent.type(novaSenha(), 'Casa-Verde-2026');

    await userEvent.click(screen.getByRole('button', { name: 'Trocar senha' }));
    await userEvent.click(screen.getByRole('button', { name: 'Salvando…' }));

    expect(screen.getByRole('button', { name: 'Salvando…' })).toBeDisabled();
    expect(pedidos).toHaveLength(1);
  });

  it('campos vazios ou nova senha curta: avisa sem chamar a API', async () => {
    const { falso } = simularFetch(() => new Response(null, { status: 204 }));
    abrir();

    await preencherETrocar('', 'curta');

    expect(senhaAtual()).toHaveAccessibleDescription('Informe a senha atual.');
    expect(novaSenha()).toHaveAccessibleDescription(/A senha precisa ter pelo menos 8 caracteres./);
    expect(falso).not.toHaveBeenCalled();
  });
});
