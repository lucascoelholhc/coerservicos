import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { ouvirComprovanteDoEmail } from '../../api/canalDaPosse';
import { encerrarSessao } from '../../api/sessao';
import { semViolacoes } from '../../teste/axe';
import { REGRAS_PADRAO, SESSAO_PADRAO } from '../../teste/dadosDaApi';
import { json, problema, simularFetch, type Pedido } from '../../teste/fetchSimulado';
import { ProvarEmail } from './ProvarEmail';

function OndeEstou() {
  const local = useLocation();
  return <p>{`Fui para ${local.pathname}`}</p>;
}

function abrir(endereco = '/provar-email#token=tok-posse') {
  window.history.replaceState(null, '', endereco);
  return render(
    <MemoryRouter initialEntries={['/provar-email']}>
      <Routes>
        <Route path="/provar-email" element={<ProvarEmail />} />
        <Route path="*" element={<OndeEstou />} />
      </Routes>
    </MemoryRouter>,
  );
}

type Responder = (pedido: Pedido) => Response | Promise<Response>;

function simular(confirmar: Responder = () => json({ comprovante: 'comp-email', email: 'ana@exemplo.com' })) {
  return simularFetch((pedido) => {
    switch (pedido.url) {
      case '/api/contas/posse/email/confirmar':
        return confirmar(pedido);
      case '/api/publico/regras':
        return json(REGRAS_PADRAO);
      case '/api/contas/cliente':
        return json({ id: 'u-ana', nome: 'Ana Souza' }, 201);
      case '/api/auth/entrar':
        return json(SESSAO_PADRAO);
      default:
        return problema(404, 'nao-encontrado', 'x');
    }
  });
}

const paradas: Array<() => void> = [];

describe('/provar-email', () => {
  afterEach(() => {
    paradas.splice(0).forEach((parar) => parar());
    encerrarSessao();
    window.history.replaceState(null, '', '/');
    vi.unstubAllGlobals();
  });

  it('limpa o #token= antes de chamar a API e entrega o comprovante à aba do cadastro', async () => {
    let fragmentoNaHora: string | null = null;
    const { pedidos } = simular(() => {
      fragmentoNaHora = window.location.hash;
      return json({ comprovante: 'comp-email', email: 'ana@exemplo.com' });
    });
    const abaDoCadastro = vi.fn();
    paradas.push(ouvirComprovanteDoEmail('ana@exemplo.com', abaDoCadastro));
    const { container } = abrir();

    expect(await screen.findByRole('heading', { level: 1, name: 'E-mail confirmado' })).toBeInTheDocument();
    expect(screen.getByText('Pronto. Volte para a aba do cadastro para terminar.')).toBeInTheDocument();
    expect(fragmentoNaHora).toBe('');
    expect(JSON.parse(pedidos[0]?.corpo ?? '{}')).toEqual({ token: 'tok-posse' });
    expect(abaDoCadastro).toHaveBeenCalledWith('comp-email');
    expect(document.title).toBe('Confirmar que o e-mail é seu · COE Serviços');
    await semViolacoes(container);
  });

  it('ninguém responde em 1 s: continua o cadastro aqui, com o e-mail fixo e o resto em branco', async () => {
    const { pedidos } = simular();
    const { container } = abrir();

    expect(await screen.findByRole('heading', { level: 1, name: 'Termine seu cadastro' }, { timeout: 3000 })).toBeInTheDocument();
    const email = screen.getByRole('textbox', { name: 'E-mail' });
    expect(email).toHaveValue('ana@exemplo.com');
    expect(email).toHaveAttribute('readonly');
    expect(screen.getByRole('textbox', { name: 'Seu nome' })).toHaveValue('');
    expect(screen.getByLabelText('Crie uma senha')).toHaveValue('');
    await semViolacoes(container);

    await userEvent.type(screen.getByRole('textbox', { name: 'Seu nome' }), 'Ana Souza');
    await userEvent.type(screen.getByRole('textbox', { name: 'Celular' }), '47900000001');
    await userEvent.type(screen.getByRole('textbox', { name: 'Seu CEP' }), '89010000');
    await userEvent.type(screen.getByLabelText('Crie uma senha'), 'Casa-Azul-2026');
    await userEvent.click(await screen.findByRole('checkbox', { name: /Li e aceito/ }));
    await userEvent.click(screen.getByRole('button', { name: 'Criar conta' }));

    expect(await screen.findByText('Fui para /conta')).toBeInTheDocument();
    const cadastro = pedidos.find((pedido) => pedido.url === '/api/contas/cliente');
    expect(JSON.parse(cadastro?.corpo ?? '{}')).toMatchObject({ email: 'ana@exemplo.com', comprovanteEmail: 'comp-email' });
  });

  it('sem BroadcastChannel: continua o cadastro aqui', async () => {
    vi.stubGlobal('BroadcastChannel', undefined);
    simular();
    abrir();

    expect(await screen.findByRole('heading', { level: 1, name: 'Termine seu cadastro' })).toBeInTheDocument();
  });

  it('link inválido: explica e leva de volta ao cadastro', async () => {
    simular(() => problema(422, 'link-invalido', 'Este link não vale mais. Peça um novo.'));
    abrir();

    expect(await screen.findByRole('heading', { level: 1, name: 'Este link não vale mais' })).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('Este link não vale mais. Peça um novo.');
    expect(screen.getByRole('link', { name: 'Voltar ao cadastro' })).toHaveAttribute('href', '/criar-conta/cliente');
  });

  it('sem token: explica e não chama a API', () => {
    const { falso } = simular();
    abrir('/provar-email');

    expect(screen.getByRole('heading', { level: 1, name: 'Este link não vale mais' })).toBeInTheDocument();
    expect(falso).not.toHaveBeenCalled();
  });

  it('enquanto confirma: aviso de carregando; sem conexão: "Tentar de novo" com o token da memória', async () => {
    let tentativas = 0;
    simular(() => {
      tentativas += 1;
      if (tentativas === 1) {
        throw new TypeError('Failed to fetch');
      }
      return json({ comprovante: 'comp-email', email: 'ana@exemplo.com' });
    });
    paradas.push(ouvirComprovanteDoEmail('ana@exemplo.com', vi.fn()));
    abrir();

    expect(screen.getByRole('status')).toHaveTextContent('Confirmando seu e-mail…');
    expect(await screen.findByRole('alert')).toHaveTextContent('Sem conexão.');
    await userEvent.click(screen.getByRole('button', { name: 'Tentar de novo' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'E-mail confirmado' })).toBeInTheDocument();
    expect(tentativas).toBe(2);
  });
});
