import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { encerrarSessao, iniciarSessao, usuarioAtual } from '../../api/sessao';
import type { MinhaConta } from '../../api/tipos';
import { semViolacoes } from '../../teste/axe';
import { CONTA_PADRAO, SESSAO_PADRAO } from '../../teste/dadosDaApi';
import { json, problema, simularFetch, type Pedido } from '../../teste/fetchSimulado';
import { RecolocarContato } from './RecolocarContato';

function OndeEstou() {
  const local = useLocation();
  const aviso = (local.state as { aviso?: string } | null)?.aviso;
  return <p>{`Fui para ${local.pathname}${aviso ? ` · ${aviso}` : ''}`}</p>;
}

function abrir() {
  return render(
    <MemoryRouter initialEntries={['/conta/recolocar-contato']}>
      <Routes>
        <Route path="/conta/recolocar-contato" element={<RecolocarContato />} />
        <Route path="*" element={<OndeEstou />} />
      </Routes>
    </MemoryRouter>,
  );
}

const PENDENTE = { ...SESSAO_PADRAO, usuario: { ...SESSAO_PADRAO.usuario, contatoPendente: true } };
const SEM_EMAIL: MinhaConta = { ...CONTA_PADRAO, email: null, emailConfirmado: false, contatoPendente: true };
const SEM_CELULAR: MinhaConta = { ...CONTA_PADRAO, celular: null, celularConfirmado: false, contatoPendente: true };
const VOLTOU = 'Fui para /conta · Contato confirmado. Sua conta voltou ao normal.';

type Responder = (pedido: Pedido) => Response | Promise<Response>;

function simular(conta: MinhaConta, outros: Record<string, Responder> = {}) {
  return simularFetch((pedido) => {
    if (pedido.url === '/api/contas/eu') {
      return json(conta);
    }
    if (pedido.url === '/api/auth/renovar') {
      return json(SESSAO_PADRAO);
    }
    const outro = outros[pedido.url];
    return outro ? outro(pedido) : json({ mensagem: 'Enviado.' }, 202);
  });
}

const doTipo = (pedidos: Pedido[], url: string) => pedidos.filter((pedido) => pedido.url === url);

describe('/conta/recolocar-contato', () => {
  beforeEach(() => iniciarSessao(PENDENTE));
  afterEach(() => {
    vi.useRealTimers();
    encerrarSessao();
    vi.unstubAllGlobals();
  });

  it('e-mail: PUT com o e-mail novo, aviso do link e "Já confirmei pelo link" renova e volta para Minha conta', async () => {
    const { pedidos } = simular(SEM_EMAIL);
    const { container } = abrir();

    expect(screen.getByRole('heading', { level: 1, name: 'Recolocar contato' })).toBeInTheDocument();
    expect(document.title).toBe('Recolocar contato · COE Serviços');
    const campo = await screen.findByRole('textbox', { name: 'Novo e-mail' });
    expect(campo).toHaveAttribute('autocomplete', 'email');
    await semViolacoes(container);
    await userEvent.type(campo, 'nova@exemplo.com');
    await userEvent.click(screen.getByRole('button', { name: 'Mandar link' }));

    expect(await screen.findByText(/Enviamos um link para nova@exemplo.com/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Mandar o link de novo' })).toBeDisabled();
    expect(doTipo(pedidos, '/api/contas/eu/email')[0]).toMatchObject({ metodo: 'PUT', corpo: JSON.stringify({ email: 'nova@exemplo.com' }) });
    await userEvent.click(screen.getByRole('button', { name: 'Já confirmei pelo link' }));

    expect(await screen.findByText(VOLTOU)).toBeInTheDocument();
    expect(usuarioAtual()?.contatoPendente).toBe(false);
  });

  it('"Já confirmei pelo link" com a conta ainda pendente: diz isso e continua na tela', async () => {
    simularFetch((pedido) => {
      if (pedido.url === '/api/contas/eu') {
        return json(SEM_EMAIL);
      }
      return pedido.url === '/api/auth/renovar' ? json(PENDENTE) : json({ mensagem: 'Enviado.' }, 202);
    });
    abrir();
    await userEvent.type(await screen.findByRole('textbox', { name: 'Novo e-mail' }), 'nova@exemplo.com');
    await userEvent.click(screen.getByRole('button', { name: 'Mandar link' }));

    await userEvent.click(await screen.findByRole('button', { name: 'Já confirmei pelo link' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Ainda não recebemos a confirmação. Abra o link que enviamos para nova@exemplo.com.',
    );
  });

  it('"Mandar o link de novo" depois da espera repete o PUT com o mesmo e-mail', async () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] });
    const { pedidos } = simular(SEM_EMAIL);
    abrir();
    await userEvent.type(await screen.findByRole('textbox', { name: 'Novo e-mail' }), 'nova@exemplo.com');
    await userEvent.click(screen.getByRole('button', { name: 'Mandar link' }));
    await screen.findByText(/Enviamos um link para nova@exemplo.com/);

    await act(async () => {}); // roda o efeito que liga a contagem antes de adiantar o relógio
    act(() => vi.advanceTimersByTime(60_000));
    await userEvent.click(screen.getByRole('button', { name: 'Mandar o link de novo' }));

    expect(await screen.findByText('Mandamos o link de novo.')).toBeInTheDocument();
    const puts = doTipo(pedidos, '/api/contas/eu/email');
    expect(puts).toHaveLength(2);
    expect(puts[1]?.corpo).toBe(puts[0]?.corpo);
  });

  it('celular: PUT, código, confirmar, renova e volta para Minha conta', async () => {
    const { pedidos } = simular(SEM_CELULAR, { '/api/contas/eu/celular/confirmar': () => new Response(null, { status: 204 }) });
    const { container } = abrir();

    const campo = await screen.findByRole('textbox', { name: 'Novo celular' });
    expect(campo).toHaveAttribute('autocomplete', 'tel');
    await userEvent.type(campo, '47900000009');
    await userEvent.click(screen.getByRole('button', { name: 'Mandar código' }));
    await userEvent.type(await screen.findByRole('textbox', { name: 'Código' }), '123456');
    await semViolacoes(container);
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar' }));

    expect(await screen.findByText(VOLTOU)).toBeInTheDocument();
    expect(doTipo(pedidos, '/api/contas/eu/celular')[0]).toMatchObject({ metodo: 'PUT', corpo: JSON.stringify({ celular: '47900000009' }) });
    expect(doTipo(pedidos, '/api/contas/eu/celular/confirmar')[0]?.corpo).toBe(JSON.stringify({ codigo: '123456' }));
    expect(doTipo(pedidos, '/api/auth/renovar')).toHaveLength(1);
  });

  it('"Mandar outro código" repete o PUT com o mesmo número; 429 vira aviso sem apagar o código digitado', async () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] });
    let puts = 0;
    const { pedidos } = simular(SEM_CELULAR, {
      '/api/contas/eu/celular': () => {
        puts += 1;
        return puts === 1 ? json({ mensagem: 'Enviado.' }, 202) : problema(429, 'muitas-tentativas', 'x');
      },
    });
    abrir();
    await userEvent.type(await screen.findByRole('textbox', { name: 'Novo celular' }), '47900000009');
    await userEvent.click(screen.getByRole('button', { name: 'Mandar código' }));
    const codigo = await screen.findByRole('textbox', { name: 'Código' });
    await userEvent.type(codigo, '123');

    await act(async () => {}); // roda o efeito que liga a contagem antes de adiantar o relógio
    act(() => vi.advanceTimersByTime(60_000));
    await userEvent.click(screen.getByRole('button', { name: 'Mandar outro código' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Muitas tentativas seguidas.');
    expect(codigo).toHaveValue('123');
    const enviados = doTipo(pedidos, '/api/contas/eu/celular');
    expect(enviados).toHaveLength(2);
    expect(enviados[1]?.corpo).toBe(enviados[0]?.corpo);
  });

  it('422 codigo-invalido: erro no campo do código', async () => {
    simular(SEM_CELULAR, {
      '/api/contas/eu/celular/confirmar': () => problema(422, 'codigo-invalido', 'Código incorreto ou vencido. Peça um novo.'),
    });
    abrir();
    await userEvent.type(await screen.findByRole('textbox', { name: 'Novo celular' }), '47900000009');
    await userEvent.click(screen.getByRole('button', { name: 'Mandar código' }));

    await userEvent.type(await screen.findByRole('textbox', { name: 'Código' }), '000000');
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar' }));

    expect(await screen.findByText('Código incorreto ou vencido. Peça um novo.')).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: 'Código' })).toHaveAttribute('aria-invalid', 'true');
  });

  it.each([
    ['contato-em-uso', 'Esse contato já está em uso. Se ele for seu, confirme que é seu para usar nesta conta.'],
    ['email-ja-preenchido', 'Sua conta já tem e-mail. Para trocar, fale com a equipe da COE.'],
    ['transferencia-indisponivel', 'Não foi possível usar esse contato. Fale com a equipe da COE.'],
  ])('409 %s no e-mail: a mensagem da API no campo', async (codigo, mensagem) => {
    simular(SEM_EMAIL, { '/api/contas/eu/email': () => problema(409, codigo, mensagem, { campo: 'email' }) });
    abrir();
    const campo = await screen.findByRole('textbox', { name: 'Novo e-mail' });

    await userEvent.type(campo, 'nova@exemplo.com');
    await userEvent.click(screen.getByRole('button', { name: 'Mandar link' }));

    expect(await screen.findByText(mensagem)).toBeInTheDocument();
    expect(campo).toHaveAttribute('aria-invalid', 'true');
  });

  it('409 sem campo no celular: a mensagem da API como aviso', async () => {
    simular(SEM_CELULAR, {
      '/api/contas/eu/celular': () => problema(409, 'transferencia-indisponivel', 'Fale com a equipe da COE.'),
    });
    abrir();

    await userEvent.type(await screen.findByRole('textbox', { name: 'Novo celular' }), '47900000009');
    await userEvent.click(screen.getByRole('button', { name: 'Mandar código' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Fale com a equipe da COE.');
  });

  it('campo vazio: avisa sem chamar a API', async () => {
    const { pedidos } = simular(SEM_EMAIL);
    abrir();

    await userEvent.click(await screen.findByRole('button', { name: 'Mandar link' }));

    expect(screen.getByRole('textbox', { name: 'Novo e-mail' })).toHaveAccessibleDescription('Informe o novo e-mail.');
    expect(doTipo(pedidos, '/api/contas/eu/email')).toHaveLength(0);
  });

  it('conta sem pendência: diz que está tudo certo', async () => {
    simular({ ...CONTA_PADRAO, contatoPendente: false });
    abrir();

    expect(await screen.findByText('Sua conta já tem celular e e-mail.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Voltar para minha conta' })).toHaveAttribute('href', '/conta');
  });
});
