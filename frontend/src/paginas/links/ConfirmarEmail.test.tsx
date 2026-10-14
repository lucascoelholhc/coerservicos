import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { encerrarSessao, iniciarSessao } from '../../api/sessao';
import { semViolacoes } from '../../teste/axe';
import { SESSAO_PADRAO } from '../../teste/dadosDaApi';
import { json, problema, simularFetch } from '../../teste/fetchSimulado';
import { ConfirmarEmail } from './ConfirmarEmail';

function abrir(endereco = '/confirmar-email#token=abc123') {
  window.history.replaceState(null, '', endereco);
  return render(
    <MemoryRouter initialEntries={['/confirmar-email']}>
      <ConfirmarEmail />
    </MemoryRouter>,
  );
}

describe('/confirmar-email', () => {
  afterEach(() => {
    encerrarSessao();
    window.history.replaceState(null, '', '/');
    vi.unstubAllGlobals();
  });

  it('limpa o #token= da barra de endereço ANTES de chamar a API e confirma', async () => {
    let fragmentoNaHoraDaChamada: string | null = null;
    const { pedidos } = simularFetch(() => {
      fragmentoNaHoraDaChamada = window.location.hash;
      return new Response(null, { status: 204 });
    });
    const { container } = abrir();

    expect(await screen.findByRole('heading', { level: 1, name: 'E-mail confirmado' })).toBeInTheDocument();
    expect(fragmentoNaHoraDaChamada).toBe('');
    expect(pedidos).toHaveLength(1);
    expect(JSON.parse(pedidos[0]?.corpo ?? '{}')).toEqual({ token: 'abc123' });
    expect(screen.getByRole('link', { name: 'Ir para o início' })).toHaveAttribute('href', '/');
    expect(document.title).toBe('Confirmar e-mail · COE Serviços');
    await semViolacoes(container);
  });

  it('enquanto confirma: aviso de carregando', () => {
    simularFetch(() => new Promise<Response>(() => undefined));
    abrir();

    expect(screen.getByRole('status')).toHaveTextContent('Confirmando seu e-mail…');
  });

  it('link inválido ou usado (visitante): mensagem da API, sem "Pedir outro link"', async () => {
    simularFetch(() => problema(422, 'link-invalido', 'Este link não vale mais. Peça outro.'));
    abrir();

    expect(await screen.findByRole('heading', { level: 1, name: 'Este link não vale mais' })).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('Este link não vale mais. Peça outro.');
    expect(screen.queryByRole('button', { name: 'Pedir outro link' })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Entrar' })).toHaveAttribute('href', '/entrar');
  });

  it('link inválido com a pessoa logada: "Pedir outro link" manda um novo', async () => {
    iniciarSessao(SESSAO_PADRAO);
    const { pedidos } = simularFetch((pedido) =>
      pedido.url === '/api/contas/email/confirmar'
        ? problema(422, 'link-invalido', 'Este link não vale mais. Peça outro.')
        : new Response(null, { status: 202 }),
    );
    abrir();

    await userEvent.click(await screen.findByRole('button', { name: 'Pedir outro link' }));

    expect(await screen.findByText('Enviamos um link novo. Confira seu e-mail.')).toBeInTheDocument();
    expect(pedidos.some((pedido) => pedido.url === '/api/contas/eu/email/confirmacao')).toBe(true);
  });

  it('sem conexão: mensagem e "Tentar de novo" (o token continua só na memória)', async () => {
    let tentativas = 0;
    simularFetch(() => {
      tentativas += 1;
      if (tentativas === 1) {
        throw new TypeError('Failed to fetch');
      }
      return new Response(null, { status: 204 });
    });
    abrir();

    expect(await screen.findByRole('alert')).toHaveTextContent('Sem conexão. Confira a internet e tente de novo.');
    await userEvent.click(screen.getByRole('button', { name: 'Tentar de novo' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'E-mail confirmado' })).toBeInTheDocument();
    expect(JSON.stringify({ ...localStorage, ...sessionStorage })).not.toContain('abc123');
  });

  it('sem token no endereço: explica e não chama a API', async () => {
    const { falso } = simularFetch(() => new Response(null, { status: 204 }));
    abrir('/confirmar-email');

    expect(screen.getByRole('heading', { level: 1, name: 'Este link não vale mais' })).toBeInTheDocument();
    expect(falso).not.toHaveBeenCalled();
  });

  it('logado: depois de confirmar, renova a sessão (o contato pendente some sem sair e entrar)', async () => {
    iniciarSessao({ ...SESSAO_PADRAO, usuario: { ...SESSAO_PADRAO.usuario, contatoPendente: true } });
    const { pedidos } = simularFetch((pedido) =>
      pedido.url === '/api/auth/renovar' ? json(SESSAO_PADRAO) : new Response(null, { status: 204 }),
    );
    abrir();

    expect(await screen.findByRole('heading', { level: 1, name: 'E-mail confirmado' })).toBeInTheDocument();
    await vi.waitFor(() => expect(pedidos.some((pedido) => pedido.url === '/api/auth/renovar')).toBe(true));
  });

  it('visitante: confirma sem tentar renovar', async () => {
    const { pedidos } = simularFetch(() => new Response(null, { status: 204 }));
    abrir();

    expect(await screen.findByRole('heading', { level: 1, name: 'E-mail confirmado' })).toBeInTheDocument();
    expect(pedidos.map((pedido) => pedido.url)).toEqual(['/api/contas/email/confirmar']);
  });
});
