import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { semViolacoes } from '../../teste/axe';
import { CADASTRO_COMPLETO, CADASTRO_VAZIO } from '../../teste/dadosDoCadastro';
import { json, problema } from '../../teste/fetchSimulado';
import { abrirCadastro, corpoDe, simularCadastro } from './testeDoCadastro';

const COM_TRABALHO = {
  ...CADASTRO_VAZIO,
  profissao: 'pedreiro',
  servicos: [{ profissao: 'pedreiro', nome: 'Alvenaria' }],
  etapas: { ...CADASTRO_VAZIO.etapas, trabalho: true, servicos: true },
};

describe('etapas 7 e 8: onde e quando', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('cidade do catálogo, raio e outras cidades (sem a própria)', async () => {
    const api = simularCadastro({ cadastro: COM_TRABALHO });
    const { container } = abrirCadastro(7);

    await userEvent.click(await screen.findByRole('button', { name: 'Continuar' }));
    expect(screen.getByRole('combobox', { name: 'Em que cidade você mora?' })).toHaveAccessibleDescription('Escolha a cidade onde você mora.');
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Em que cidade você mora?' }), 'Blumenau');
    await userEvent.click(screen.getByRole('radio', { name: /Minha cidade e vizinhas/ }));
    const outras = screen.getByRole('group', { name: /Outras cidades/ });
    expect(within(outras).queryByRole('checkbox', { name: 'Blumenau' })).not.toBeInTheDocument();
    await userEvent.click(within(outras).getByRole('checkbox', { name: 'Gaspar' }));
    await semViolacoes(container);
    await userEvent.click(screen.getByRole('button', { name: 'Continuar' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Em quais dias você trabalha?' })).toBeInTheDocument();
    expect(corpoDe(api.escritas()[0])).toEqual({ cidade: 4202404, raioKm: 20, outrasCidades: [4205902] });
  });

  it('dias da semana: nome inteiro para o leitor de tela, em ordem na API', async () => {
    const api = simularCadastro({ cadastro: COM_TRABALHO });
    const { container } = abrirCadastro(8);

    await userEvent.click(await screen.findByRole('button', { name: 'Continuar' }));
    expect(screen.getByRole('group', { name: 'Dias da semana' })).toHaveAccessibleDescription('Escolha pelo menos um dia.');
    await userEvent.click(screen.getByRole('checkbox', { name: 'Sábado' }));
    await userEvent.click(screen.getByRole('checkbox', { name: 'Segunda-feira' }));
    await userEvent.click(screen.getByRole('checkbox', { name: 'Domingo' }));
    await semViolacoes(container);
    await userEvent.click(screen.getByRole('button', { name: 'Continuar' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Fotos dos seus trabalhos' })).toBeInTheDocument();
    expect(corpoDe(api.escritas()[0])).toEqual({ dias: [0, 1, 6] });
  });
});

describe('etapa 9: fotos', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('manda as fotos, descreve com serviço e legenda e segue', async () => {
    const comFoto = {
      ...COM_TRABALHO,
      fotos: [{ id: 'f1', servico: null, legenda: null }],
      etapas: { ...COM_TRABALHO.etapas, fotos: true },
    };
    const api = simularCadastro({
      cadastro: COM_TRABALHO,
      escrita: (pedido) =>
        pedido.metodo === 'POST' ? json({ id: 'f1', servico: null, legenda: null }, 201) : json({ id: 'f1', servico: 'Alvenaria', legenda: 'Muro' }),
    });
    const { container } = abrirCadastro(9);

    await userEvent.click(await screen.findByRole('button', { name: 'Continuar' }));
    expect(screen.getByRole('alert')).toHaveTextContent('Mande pelo menos 1 foto para continuar.');
    api.definir(comFoto);
    await userEvent.upload(screen.getByLabelText(/Fotos de trabalhos/), new File(['x'], 'muro.jpg', { type: 'image/jpeg' }));
    await userEvent.selectOptions(await screen.findByRole('combobox', { name: 'Que serviço é a foto 1?' }), 'Alvenaria');
    await userEvent.type(screen.getByRole('textbox', { name: 'Legenda da foto 1 (se quiser)' }), 'Muro');
    await semViolacoes(container);
    await userEvent.click(screen.getByRole('button', { name: 'Continuar' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Conte sobre você' })).toBeInTheDocument();
    expect(api.escritas().map((p) => `${p.metodo} ${p.url}`)).toEqual([
      'POST /api/profissional/cadastro/fotos',
      'PUT /api/profissional/cadastro/fotos/f1',
    ]);
    expect(corpoDe(api.escritas()[1])).toEqual({ servico: 'Alvenaria', legenda: 'Muro' });
  });

  it('foto já enviada: ver pela URL assinada e tirar', async () => {
    const comFoto = { ...COM_TRABALHO, fotos: [{ id: 'f1', servico: 'Alvenaria', legenda: null }] };
    const api = simularCadastro({
      cadastro: comFoto,
      escrita: (pedido) => (pedido.metodo === 'DELETE' ? new Response(null, { status: 204 }) : undefined),
    });
    const fetchSimulado = globalThis.fetch;
    vi.stubGlobal(
      'fetch',
      vi.fn((entrada: RequestInfo | URL, init?: RequestInit) =>
        String(entrada).endsWith('/fotos/f1/url')
          ? Promise.resolve(json({ url: 'https://bucket.exemplo/portfolio/f1.jpg?assinatura', expiraEm: '2026-10-22T12:05:00Z' }))
          : fetchSimulado(entrada, init),
      ),
    );
    abrirCadastro(9);

    await userEvent.click(await screen.findByRole('button', { name: 'Ver a foto 1' }));
    expect(await screen.findByRole('link', { name: 'Abrir a foto 1' })).toHaveAttribute(
      'href',
      'https://bucket.exemplo/portfolio/f1.jpg?assinatura',
    );
    api.definir(COM_TRABALHO);
    await userEvent.click(screen.getByRole('button', { name: 'Tirar a foto 1' }));

    expect(await screen.findByRole('button', { name: 'Continuar' })).toBeEnabled();
    expect(screen.queryByText('Foto 1')).not.toBeInTheDocument();
  });

  it('a segunda foto falha: a primeira continua na lista, com a mensagem do servidor', async () => {
    let enviadas = 0;
    const api = simularCadastro({
      cadastro: COM_TRABALHO,
      escrita: (pedido) => {
        if (pedido.metodo !== 'POST') {
          return undefined;
        }
        enviadas += 1;
        return enviadas === 1
          ? json({ id: 'f1', servico: null, legenda: null }, 201)
          : problema(422, 'limite-de-fotos', 'Você já mandou o máximo de fotos.');
      },
    });
    abrirCadastro(9);
    api.definir({ ...COM_TRABALHO, fotos: [{ id: 'f1', servico: null, legenda: null }], etapas: { ...COM_TRABALHO.etapas, fotos: true } });

    await userEvent.upload(await screen.findByLabelText(/Fotos de trabalhos/), [
      new File(['a'], 'a.jpg', { type: 'image/jpeg' }),
      new File(['b'], 'b.jpg', { type: 'image/jpeg' }),
    ]);

    expect(await screen.findByText('Você já mandou o máximo de fotos.')).toBeInTheDocument();
    expect(screen.getByRole('combobox', { name: 'Que serviço é a foto 1?' })).toBeInTheDocument();
  });

  it('tirar a foto avisa e devolve o foco ao botão de adicionar', async () => {
    const api = simularCadastro({
      cadastro: { ...COM_TRABALHO, fotos: [{ id: 'f1', servico: null, legenda: null }] },
      escrita: () => new Response(null, { status: 204 }),
    });
    abrirCadastro(9);

    api.definir(COM_TRABALHO);
    await userEvent.click(await screen.findByRole('button', { name: 'Tirar a foto 1' }));

    expect(await screen.findByRole('status')).toHaveTextContent('Foto tirada.');
    expect(screen.getByLabelText(/Fotos de trabalhos/)).toHaveFocus();
  });

  it('serviço salvo na foto que saiu da lista continua como opção', async () => {
    simularCadastro({ cadastro: { ...COM_TRABALHO, fotos: [{ id: 'f1', servico: 'Reboco', legenda: null }] } });
    abrirCadastro(9);

    expect(await screen.findByRole('combobox', { name: 'Que serviço é a foto 1?' })).toHaveValue('Reboco');
  });

  it('legenda com contato (422) diz qual foto', async () => {
    simularCadastro({
      cadastro: { ...COM_TRABALHO, fotos: [{ id: 'f1', servico: null, legenda: null }] },
      escrita: () => problema(422, 'texto-com-contato', 'Tire o telefone do texto.'),
    });
    abrirCadastro(9);

    await userEvent.type(await screen.findByRole('textbox', { name: 'Legenda da foto 1 (se quiser)' }), 'liga 47 99999 0000');
    await userEvent.click(screen.getByRole('button', { name: 'Continuar' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Foto 1: Tire o telefone do texto.');
  });
});

describe('etapa 10: sobre você', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('frases prontas da profissão, texto livre e a prévia igual ao perfil', async () => {
    const api = simularCadastro({ cadastro: COM_TRABALHO });
    const { container } = abrirCadastro(10);

    await userEvent.click(await screen.findByRole('checkbox', { name: 'Chego no horário combinado' }));
    await userEvent.click(screen.getByRole('checkbox', { name: 'Faço obra do começo ao fim' }));
    await userEvent.type(screen.getByRole('textbox', { name: 'Quer contar mais alguma coisa? (se quiser)' }), 'Trabalho no Garcia.');

    expect(screen.getByText('Chego no horário combinado. Faço obra do começo ao fim. Trabalho no Garcia.')).toBeInTheDocument();
    await semViolacoes(container);
    await userEvent.click(screen.getByRole('button', { name: 'Continuar' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Onde receber o dinheiro' })).toBeInTheDocument();
    expect(corpoDe(api.escritas()[0])).toEqual({
      frases: ['Chego no horário combinado', 'Faço obra do começo ao fim'],
      texto: 'Trabalho no Garcia.',
    });
  });

  it('texto com contato (422): erro no campo de texto', async () => {
    simularCadastro({ cadastro: COM_TRABALHO, escrita: () => problema(422, 'texto-com-contato', 'Tire o telefone do texto.') });
    abrirCadastro(10);

    await userEvent.type(await screen.findByRole('textbox', { name: /Quer contar mais/ }), 'me liga 47 99999-0000');
    await userEvent.click(screen.getByRole('button', { name: 'Continuar' }));

    expect(await screen.findByText('Tire o telefone do texto.')).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: /Quer contar mais/ })).toHaveAttribute('aria-invalid', 'true');
  });
});

describe('etapa 11: Pix, revisão e envio', () => {
  afterEach(() => vi.unstubAllGlobals());

  const PRONTO_SEM_PIX = {
    ...CADASTRO_COMPLETO,
    etapaAtual: 11,
    chavePixTipo: null,
    chavePix: null,
    etapas: { ...CADASTRO_COMPLETO.etapas, pix: false },
  };

  it('salva o Pix, manda para análise e mostra "Cadastro enviado"', async () => {
    const api = simularCadastro({
      cadastro: PRONTO_SEM_PIX,
      escrita: (pedido) =>
        pedido.url.endsWith('/envio') ? json({ ...CADASTRO_COMPLETO, status: 'em_analise' }) : json(CADASTRO_COMPLETO),
    });
    const { container } = abrirCadastro(11);

    expect(await screen.findByRole('heading', { level: 2, name: 'Confira antes de enviar' })).toBeInTheDocument();
    expect(screen.getByText('Pedreiro, Pintor')).toBeInTheDocument();
    expect(screen.getByText('R$ 280,00 · meia R$ 160,00')).toBeInTheDocument();
    expect(screen.getByText('Blumenau · até 20 km · + Gaspar')).toBeInTheDocument();
    expect(screen.getByText('seg, ter, qua, qui, sex')).toBeInTheDocument();
    await semViolacoes(container);

    await userEvent.click(screen.getByRole('button', { name: 'Enviar cadastro' }));
    expect(screen.getByRole('alert')).toHaveTextContent('Para enviar, marque as duas confirmações.');
    expect(screen.getByRole('group', { name: 'Tipo de chave Pix' })).toHaveAccessibleDescription('Escolha o tipo da chave Pix.');

    await userEvent.click(screen.getByRole('radio', { name: 'Chave aleatória' }));
    await userEvent.type(screen.getByRole('textbox', { name: 'Sua chave Pix' }), ' 123e4567-e89b-12d3-a456-426614174000 ');
    await userEvent.click(screen.getByRole('checkbox', { name: /Li e aceito os termos de uso/ }));
    await userEvent.click(screen.getByRole('checkbox', { name: /não posso passar meu telefone/ }));
    await userEvent.click(screen.getByRole('button', { name: 'Enviar cadastro' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Cadastro enviado' })).toBeInTheDocument();
    expect(api.escritas().map((p) => p.url)).toEqual(['/api/profissional/cadastro/pix', '/api/profissional/cadastro/envio']);
    expect(corpoDe(api.escritas()[0])).toEqual({ tipo: 'aleatoria', chave: '123e4567-e89b-12d3-a456-426614174000' });
    expect(corpoDe(api.escritas()[1])).toEqual({ versaoTermosAceita: '1.0', aceitoRegraDeContato: true });
  });

  it('Pix salvo e envio recusado: tentar de novo não pede a chave outra vez', async () => {
    let envios = 0;
    const api = simularCadastro({
      cadastro: PRONTO_SEM_PIX,
      escrita: (pedido) => {
        if (pedido.url.endsWith('/pix')) {
          return json(CADASTRO_COMPLETO);
        }
        envios += 1;
        return envios === 1
          ? problema(409, 'edicao-concorrente', 'Seu cadastro está sendo salvo em outro aparelho. Tente de novo.')
          : json({ ...CADASTRO_COMPLETO, status: 'em_analise' });
      },
    });
    abrirCadastro(11);

    await userEvent.click(await screen.findByRole('radio', { name: 'Chave aleatória' }));
    await userEvent.type(screen.getByRole('textbox', { name: 'Sua chave Pix' }), '123e4567-e89b-12d3-a456-426614174000');
    await userEvent.click(screen.getByRole('checkbox', { name: /Li e aceito/ }));
    await userEvent.click(screen.getByRole('checkbox', { name: /não posso passar meu telefone/ }));
    await userEvent.click(screen.getByRole('button', { name: 'Enviar cadastro' }));
    expect(await screen.findByText(/sendo salvo em outro aparelho/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Enviar cadastro' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Cadastro enviado' })).toBeInTheDocument();
    expect(api.escritas().map((p) => p.url)).toEqual([
      '/api/profissional/cadastro/pix',
      '/api/profissional/cadastro/envio',
      '/api/profissional/cadastro/envio',
    ]);
  });

  it('Pix já guardado: mostra mascarado e envia sem novo PUT', async () => {
    const api = simularCadastro({
      cadastro: CADASTRO_COMPLETO,
      escrita: () => json({ ...CADASTRO_COMPLETO, status: 'em_analise' }),
    });
    abrirCadastro(11);

    expect(await screen.findByText(/Já guardamos a chave 0000…/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('checkbox', { name: /Li e aceito/ }));
    await userEvent.click(screen.getByRole('checkbox', { name: /não posso passar meu telefone/ }));
    await userEvent.click(screen.getByRole('button', { name: 'Enviar cadastro' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Cadastro enviado' })).toBeInTheDocument();
    expect(api.escritas().map((p) => p.url)).toEqual(['/api/profissional/cadastro/envio']);
  });

  it('etapa incompleta aparece como "Falta completar" e "Mudar" vai até ela', async () => {
    simularCadastro({ cadastro: { ...CADASTRO_COMPLETO, fotos: [], etapas: { ...CADASTRO_COMPLETO.etapas, fotos: false } } });
    abrirCadastro(11);

    expect(await screen.findByText('Falta completar')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Mudar: Fotos dos seus trabalhos' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Fotos dos seus trabalhos' })).toBeInTheDocument();
  });

  it('422 do envio (cadastro incompleto) mostra a mensagem do servidor', async () => {
    simularCadastro({
      cadastro: CADASTRO_COMPLETO,
      escrita: () => problema(422, 'cadastro-incompleto', 'Falta completar: fotos de trabalhos.'),
    });
    abrirCadastro(11);

    await userEvent.click(await screen.findByRole('checkbox', { name: /Li e aceito/ }));
    await userEvent.click(screen.getByRole('checkbox', { name: /não posso passar meu telefone/ }));
    await userEvent.click(screen.getByRole('button', { name: 'Enviar cadastro' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Falta completar: fotos de trabalhos.');
  });

  it('chave Pix inválida (422): erro no campo da chave', async () => {
    simularCadastro({ cadastro: PRONTO_SEM_PIX, escrita: () => problema(422, 'pix-invalido', 'Essa chave Pix não é válida.') });
    abrirCadastro(11);

    await userEvent.click(await screen.findByRole('radio', { name: 'CPF' }));
    await userEvent.type(screen.getByRole('textbox', { name: 'Sua chave Pix' }), '111');
    await userEvent.click(screen.getByRole('checkbox', { name: /Li e aceito/ }));
    await userEvent.click(screen.getByRole('checkbox', { name: /não posso passar meu telefone/ }));
    await userEvent.click(screen.getByRole('button', { name: 'Enviar cadastro' }));

    expect(await screen.findByText('Essa chave Pix não é válida.')).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: 'Sua chave Pix' })).toHaveAttribute('aria-invalid', 'true');
  });
});
