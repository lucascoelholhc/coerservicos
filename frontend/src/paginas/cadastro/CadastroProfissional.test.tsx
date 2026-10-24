import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { semViolacoes } from '../../teste/axe';
import { CADASTRO_COMPLETO, CADASTRO_VAZIO } from '../../teste/dadosDoCadastro';
import { json } from '../../teste/fetchSimulado';
import { abrirCadastro, corpoDe, simularCadastro } from './testeDoCadastro';

describe('/cadastro: o assistente', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('sem rascunho: começa no passo 1, salva o trabalho e vai para o passo 2', async () => {
    const api = simularCadastro({ cadastro: null, escrita: () => json({ ...CADASTRO_VAZIO, etapaAtual: 2, profissao: 'pedreiro' }) });
    const { container } = abrirCadastro();

    expect(await screen.findByRole('heading', { level: 1, name: 'Qual é o seu trabalho?' })).toBeInTheDocument();
    expect(screen.getByText('Passo 1 de 11 · Seu trabalho')).toBeInTheDocument();
    expect(screen.getByRole('progressbar', { name: 'Progresso do cadastro' })).toHaveAttribute('aria-valuenow', '1');
    expect(document.title).toBe('Cadastro de profissional · COE Serviços');
    await semViolacoes(container);

    await userEvent.click(screen.getByRole('radio', { name: /Pedreiro/ }));
    await userEvent.click(screen.getByRole('checkbox', { name: /Pintor/ }));
    await userEvent.click(screen.getByRole('button', { name: 'Continuar' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Seus dados' })).toHaveFocus();
    expect(api.escritas()[0]?.url).toBe('/api/profissional/cadastro/trabalho');
    expect(corpoDe(api.escritas()[0])).toEqual({ profissao: 'pedreiro', outras: ['pintor'] });
  });

  it('a profissão principal sai da lista de "outro trabalho"', async () => {
    simularCadastro();
    abrirCadastro();

    await userEvent.click(await screen.findByRole('radio', { name: /Pedreiro/ }));

    const outras = screen.getByRole('group', { name: /Faz outro trabalho também/ });
    expect(within(outras).queryByRole('checkbox', { name: /Pedreiro/ })).not.toBeInTheDocument();
    expect(within(outras).getByRole('checkbox', { name: /Pintor/ })).toBeInTheDocument();
  });

  it('continuar sem escolher: avisa no grupo e não chama a API', async () => {
    const api = simularCadastro();
    abrirCadastro();

    await userEvent.click(await screen.findByRole('button', { name: 'Continuar' }));

    expect(screen.getByRole('group', { name: 'Profissão' })).toHaveAccessibleDescription('Escolha o seu trabalho.');
    // o foco vai para o campo com erro (o leitor de tela lê o rótulo e o erro)
    expect(screen.getByRole('radio', { name: /Pedreiro/ })).toHaveFocus();
    expect(api.escritas()).toHaveLength(0);
  });

  it('enquanto salva, Voltar fica desabilitado (a tela não pula depois)', async () => {
    let terminar: (resposta: Response) => void = () => undefined;
    simularCadastro({
      cadastro: { ...CADASTRO_VAZIO, profissao: 'pedreiro' },
      escrita: () => new Promise<Response>((pronto) => (terminar = pronto)),
    });
    abrirCadastro(8);

    await userEvent.click(await screen.findByRole('checkbox', { name: 'Segunda-feira' }));
    await userEvent.click(screen.getByRole('button', { name: 'Continuar' }));

    expect(screen.getByRole('button', { name: 'Voltar' })).toBeDisabled();
    terminar(json({ ...CADASTRO_VAZIO, profissao: 'pedreiro', dias: [1] }));
    expect(await screen.findByRole('heading', { level: 1, name: 'Fotos dos seus trabalhos' })).toBeInTheDocument();
  });

  it('?etapa= que não é número: abre onde parou', async () => {
    simularCadastro({ cadastro: { ...CADASTRO_VAZIO, etapaAtual: 2 } });
    abrirCadastro('abc');

    expect(await screen.findByRole('heading', { level: 1, name: 'Seus dados' })).toBeInTheDocument();
  });

  it('abre na etapa em que a pessoa parou (etapaAtual da API)', async () => {
    simularCadastro({ cadastro: { ...CADASTRO_VAZIO, etapaAtual: 5, profissao: 'pedreiro' } });
    abrirCadastro();

    expect(await screen.findByRole('heading', { level: 1, name: 'Sua experiência' })).toBeInTheDocument();
    expect(screen.getByText('Passo 5 de 11 · Seu trabalho')).toBeInTheDocument();
  });

  it('?etapa=N abre aquela etapa; Voltar vai para a anterior sem salvar', async () => {
    const api = simularCadastro({ cadastro: { ...CADASTRO_VAZIO, profissao: 'pedreiro' } });
    abrirCadastro(8);

    expect(await screen.findByRole('heading', { level: 1, name: 'Em quais dias você trabalha?' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Voltar' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Onde você trabalha?' })).toBeInTheDocument();
    expect(api.escritas()).toHaveLength(0);
  });

  it('etapa fora da faixa: vai para a etapa em que parou', async () => {
    simularCadastro({ cadastro: { ...CADASTRO_VAZIO, etapaAtual: 3 } });
    abrirCadastro(99);

    expect(await screen.findByRole('heading', { level: 1, name: 'Documento e selfie' })).toBeInTheDocument();
  });

  it('"Continuar depois" leva para Minha conta', async () => {
    simularCadastro({ cadastro: { ...CADASTRO_VAZIO, etapaAtual: 2 } });
    abrirCadastro();

    await userEvent.click(await screen.findByRole('link', { name: 'Continuar depois' }));

    expect(screen.getByText('Fui para /conta')).toBeInTheDocument();
  });

  it('correção pedida: mostra o que a equipe pediu', async () => {
    simularCadastro({
      cadastro: { ...CADASTRO_COMPLETO, status: 'correcao_pedida', etapaAtual: 11, motivoCorrecao: 'A selfie está escura.' },
    });
    abrirCadastro(3);

    expect(await screen.findByText(/A selfie está escura\./)).toBeInTheDocument();
    expect(screen.getByText(/A equipe da COE pediu uma correção/)).toBeInTheDocument();
  });

  it('erro ao carregar: mensagem e tentar de novo', async () => {
    simularCadastro();
    const fetchSimulado = globalThis.fetch;
    let falhar = true;
    vi.stubGlobal(
      'fetch',
      vi.fn((entrada: RequestInfo | URL, init?: RequestInit) =>
        falhar && String(entrada) === '/api/profissional/cadastro'
          ? Promise.reject(new TypeError('Failed to fetch'))
          : fetchSimulado(entrada, init),
      ),
    );
    abrirCadastro();

    expect(await screen.findByRole('alert')).toHaveTextContent('Sem conexão.');
    falhar = false;
    await userEvent.click(screen.getByRole('button', { name: 'Tentar de novo' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Qual é o seu trabalho?' })).toBeInTheDocument();
  });
});

describe('/cadastro: depois de enviar', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('em análise: "Cadastro enviado", sem o assistente', async () => {
    simularCadastro({ cadastro: { ...CADASTRO_COMPLETO, status: 'em_analise' } });
    const { container } = abrirCadastro(2);

    expect(await screen.findByRole('heading', { level: 1, name: 'Cadastro enviado' })).toHaveFocus();
    expect(screen.getByText(/Você recebe um SMS quando seu perfil estiver no ar/)).toBeInTheDocument();
    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
    await semViolacoes(container);
  });

  it.each([
    ['ativo', 'Seu cadastro foi aprovado'],
    ['recusado', 'Cadastro não aprovado'],
    ['suspenso', 'Seu perfil está suspenso'],
  ] as const)('%s tem a própria tela', async (status, titulo) => {
    simularCadastro({ cadastro: { ...CADASTRO_COMPLETO, status } });
    abrirCadastro();

    expect(await screen.findByRole('heading', { level: 1, name: titulo })).toBeInTheDocument();
  });
});
