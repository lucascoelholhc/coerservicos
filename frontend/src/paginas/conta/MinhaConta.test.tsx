import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { MinhaConta as Conta } from '../../api/tipos';
import { encerrarSessao, iniciarSessao, tokenAtual } from '../../api/sessao';
import { semViolacoes } from '../../teste/axe';
import { CONTA_PADRAO, SESSAO_PADRAO } from '../../teste/dadosDaApi';
import { json, problema, simularFetch, type Pedido } from '../../teste/fetchSimulado';
import { MinhaConta } from './MinhaConta';

function OndeEstou() {
  const local = useLocation();
  return <p>Fui para {local.pathname}</p>;
}

function abrir(estado?: unknown) {
  return render(
    <MemoryRouter initialEntries={[{ pathname: '/conta', state: estado }]}>
      <Routes>
        <Route path="/conta" element={<MinhaConta />} />
        <Route path="*" element={<OndeEstou />} />
      </Routes>
    </MemoryRouter>,
  );
}

type Responder = (pedido: Pedido) => Response | Promise<Response>;

function simular(conta: Conta | Responder = CONTA_PADRAO, outros: Record<string, Responder> = {}) {
  return simularFetch((pedido) => {
    if (pedido.url === '/api/contas/eu') {
      return typeof conta === 'function' ? conta(pedido) : json(conta);
    }
    const outro = outros[pedido.url];
    return outro ? outro(pedido) : new Response(null, { status: 204 });
  });
}

describe('/conta (Minha conta)', () => {
  beforeEach(() => iniciarSessao(SESSAO_PADRAO));
  afterEach(() => {
    encerrarSessao();
    vi.unstubAllGlobals();
  });

  it('mostra nome, papel em palavras, celular e e-mail como a API devolve; sem violação de acessibilidade', async () => {
    simular();
    const { container } = abrir();

    expect(await screen.findByText('Ana Souza')).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 1, name: 'Minha conta' })).toBeInTheDocument();
    expect(screen.getByText('Cliente')).toBeInTheDocument();
    expect(screen.getByText('47900000001')).toBeInTheDocument();
    expect(screen.getByText('ana@exemplo.com')).toBeInTheDocument();
    expect(document.title).toBe('Minha conta · COE Serviços');
    await semViolacoes(container);
  });

  it.each([
    [['ADMIN'], 'Equipe COE'],
    [['CLIENTE', 'PROFISSIONAL'], 'Cliente e Profissional'],
  ])('papéis %j aparecem como "%s"', async (papeis, texto) => {
    simular({ ...CONTA_PADRAO, papeis: papeis as Conta['papeis'] });
    abrir();

    expect(await screen.findByText(texto)).toBeInTheDocument();
  });

  it('aviso vindo do cadastro aparece no topo', async () => {
    simular();
    abrir({ aviso: 'Enviamos um link para o seu e-mail. Confirme para conseguir recuperar a senha.' });

    expect(await screen.findByText('Ana Souza')).toBeInTheDocument();
    expect(screen.getAllByRole('status').some((aviso) => aviso.textContent?.includes('Enviamos um link'))).toBe(true);
  });

  it('e-mail não confirmado: nota e "Mandar o link de novo" (POST), com resposta de sucesso', async () => {
    const { pedidos } = simular();
    abrir();

    expect(await screen.findByText('Confirme seu e-mail para conseguir recuperar a senha.')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Mandar o link de novo' }));

    expect(await screen.findByText('Enviamos o link de novo. Confira seu e-mail.')).toBeInTheDocument();
    expect(pedidos.some((pedido) => pedido.url === '/api/contas/eu/email/confirmacao' && pedido.metodo === 'POST')).toBe(true);
  });

  it('409 email-ja-confirmado: avisa e atualiza a conta (a nota some)', async () => {
    let confirmado = false;
    simular(() => json({ ...CONTA_PADRAO, emailConfirmado: confirmado }), {
      '/api/contas/eu/email/confirmacao': () => {
        confirmado = true;
        return problema(409, 'email-ja-confirmado', 'Seu e-mail já está confirmado.');
      },
    });
    abrir();

    await userEvent.click(await screen.findByRole('button', { name: 'Mandar o link de novo' }));

    expect(await screen.findByText('Seu e-mail já está confirmado.')).toBeInTheDocument();
    await vi.waitFor(() =>
      expect(screen.queryByText('Confirme seu e-mail para conseguir recuperar a senha.')).not.toBeInTheDocument(),
    );
  });

  it('429 ao mandar o link: mensagem de espera', async () => {
    simular(CONTA_PADRAO, { '/api/contas/eu/email/confirmacao': () => new Response('', { status: 429 }) });
    abrir();

    await userEvent.click(await screen.findByRole('button', { name: 'Mandar o link de novo' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Muitas tentativas seguidas.');
  });

  it('e-mail confirmado: sem a nota', async () => {
    simular({ ...CONTA_PADRAO, emailConfirmado: true });
    abrir();

    expect(await screen.findByText('Ana Souza')).toBeInTheDocument();
    expect(screen.queryByText('Confirme seu e-mail para conseguir recuperar a senha.')).not.toBeInTheDocument();
  });

  it('"Sair" pede confirmação na própria página (nunca window.confirm), sai e vai para o Início', async () => {
    const confirmar = vi.spyOn(window, 'confirm');
    const { pedidos } = simular();
    abrir();

    await userEvent.click(await screen.findByRole('button', { name: 'Sair' }));
    const confirmacao = screen.getByRole('group', { name: 'Sair deste aparelho?' });
    expect(pedidos.some((pedido) => pedido.url === '/api/auth/sair')).toBe(false);
    await userEvent.click(within(confirmacao).getByRole('button', { name: 'Sim, sair' }));

    expect(await screen.findByText('Fui para /')).toBeInTheDocument();
    expect(pedidos.some((pedido) => pedido.url === '/api/auth/sair' && pedido.metodo === 'POST')).toBe(true);
    expect(tokenAtual()).toBeNull();
    expect(confirmar).not.toHaveBeenCalled();
  });

  it('"Cancelar" fecha a confirmação sem sair', async () => {
    simular();
    abrir();

    await userEvent.click(await screen.findByRole('button', { name: 'Sair de todos os aparelhos' }));
    await userEvent.click(screen.getByRole('button', { name: 'Cancelar' }));

    expect(screen.queryByRole('group', { name: 'Sair de todos os aparelhos?' })).not.toBeInTheDocument();
    expect(tokenAtual()).toBe('token-de-teste');
  });

  it('"Sair de todos os aparelhos": falha da API não finge que saiu (o cookie continuaria valendo)', async () => {
    const { pedidos } = simular(CONTA_PADRAO, { '/api/auth/sair-de-todos': () => problema(500, 'x', 'x') });
    abrir();

    await userEvent.click(await screen.findByRole('button', { name: 'Sair de todos os aparelhos' }));
    await userEvent.click(screen.getByRole('button', { name: 'Sim, sair de todos' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Não foi possível sair agora.');
    expect(pedidos.some((pedido) => pedido.url === '/api/auth/sair-de-todos')).toBe(true);
    expect(tokenAtual()).not.toBeNull();
    expect(screen.getByRole('button', { name: 'Sim, sair de todos' })).toBeEnabled();
  });

  it('sessão já encerrada no servidor (401 e renovação recusada): limpa a sessão local e vai para o Início', async () => {
    simular(CONTA_PADRAO, {
      '/api/auth/sair': () => problema(401, 'sessao-expirada', 'Entre de novo.'),
      '/api/auth/renovar': () => problema(401, 'sessao-expirada', 'Entre de novo.'),
    });
    abrir();

    await userEvent.click(await screen.findByRole('button', { name: 'Sair' }));
    await userEvent.click(screen.getByRole('button', { name: 'Sim, sair' }));

    expect(await screen.findByText('Fui para /')).toBeInTheDocument();
    expect(tokenAtual()).toBeNull();
  });

  it('carregando e erro com "Tentar de novo"', async () => {
    let tentativas = 0;
    simular(() => {
      tentativas += 1;
      return tentativas === 1 ? problema(500, 'x', 'x') : json(CONTA_PADRAO);
    });
    abrir();

    expect(screen.getByRole('status')).toHaveTextContent('Carregando…');
    expect(await screen.findByRole('alert')).toHaveTextContent('A COE está com um problema agora.');
    await userEvent.click(screen.getByRole('button', { name: 'Tentar de novo' }));

    expect(await screen.findByText('Ana Souza')).toBeInTheDocument();
  });
});

describe('Minha conta: Segurança', () => {
  afterEach(() => vi.unstubAllGlobals());

  async function secao() {
    const titulo = await screen.findByRole('heading', { level: 2, name: 'Segurança' });
    return within(titulo.closest('section') as HTMLElement);
  }

  it('celular não confirmado: caminho para confirmar; código no login desligado', async () => {
    simular();
    abrir();
    const seguranca = await secao();

    expect(seguranca.getByRole('link', { name: 'Confirmar celular' })).toHaveAttribute('href', '/conta/confirmar-celular');
    expect(seguranca.getByRole('link', { name: 'Código no login' })).toHaveAttribute('href', '/conta/codigo-no-login');
    expect(seguranca.getByRole('link', { name: 'Trocar senha' })).toHaveAttribute('href', '/conta/trocar-senha');
    expect(seguranca.getByText('Não confirmado')).toBeInTheDocument();
    expect(seguranca.getByText('Desligado')).toBeInTheDocument();
  });

  it('celular confirmado e código ligado', async () => {
    simular({ ...CONTA_PADRAO, celularConfirmado: true, mfaAtivo: true });
    abrir();
    const seguranca = await secao();

    expect(seguranca.queryByRole('link', { name: 'Confirmar celular' })).not.toBeInTheDocument();
    expect(seguranca.getByText('Confirmado')).toBeInTheDocument();
    expect(seguranca.getByText('Ligado')).toBeInTheDocument();
  });

  it('ADMIN: código no login obrigatório', async () => {
    simular({ ...CONTA_PADRAO, papeis: ['ADMIN'], celularConfirmado: true, mfaAtivo: false });
    abrir();
    const seguranca = await secao();

    expect(seguranca.getByText('Obrigatório')).toBeInTheDocument();
  });

  it('sem celular na conta: não oferece confirmar', async () => {
    simular({ ...CONTA_PADRAO, celular: null });
    abrir();
    const seguranca = await secao();

    expect(seguranca.queryByRole('link', { name: 'Confirmar celular' })).not.toBeInTheDocument();
  });
});

describe('Minha conta: contato pendente', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('com contato pendente: nota no topo com o caminho para recolocar', async () => {
    simular({ ...CONTA_PADRAO, email: null, contatoPendente: true });
    abrir();

    expect(
      await screen.findByText('Cadastre e confirme um novo celular ou e-mail para continuar usando a COE.'),
    ).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Recolocar contato' })).toHaveAttribute('href', '/conta/recolocar-contato');
  });

  it('sem e-mail na conta: não pede para confirmar o e-mail', async () => {
    simular({ ...CONTA_PADRAO, email: null, emailConfirmado: false, contatoPendente: true });
    abrir();

    await screen.findByRole('heading', { level: 2, name: 'Segurança' });
    expect(screen.queryByText('Confirme seu e-mail para conseguir recuperar a senha.')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Mandar o link de novo' })).not.toBeInTheDocument();
  });

  it('sem pendência: sem a nota', async () => {
    simular();
    abrir();

    await screen.findByRole('heading', { level: 2, name: 'Segurança' });
    expect(screen.queryByRole('link', { name: 'Recolocar contato' })).not.toBeInTheDocument();
  });
});
