import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { semViolacoes } from '../../teste/axe';
import { CADASTRO_COMPLETO, CADASTRO_VAZIO } from '../../teste/dadosDoCadastro';
import { json, problema } from '../../teste/fetchSimulado';
import { abrirCadastro, corpoDe, simularCadastro } from './testeDoCadastro';

const COM_TRABALHO = { ...CADASTRO_VAZIO, profissao: 'pedreiro', etapas: { ...CADASTRO_VAZIO.etapas, trabalho: true } };

describe('etapa 2: seus dados', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('nome e celular da conta só para ler; CPF e nascimento vão no PUT', async () => {
    const api = simularCadastro({ cadastro: COM_TRABALHO });
    const { container } = abrirCadastro(2);

    expect(await screen.findByRole('textbox', { name: 'Nome completo' })).toHaveAttribute('readonly');
    expect(screen.getByRole('textbox', { name: 'Celular' })).toHaveValue('47900000001');
    await semViolacoes(container);
    await userEvent.type(screen.getByRole('textbox', { name: 'CPF' }), '529.982.247-25');
    await userEvent.type(screen.getByLabelText('Data de nascimento'), '1990-01-31');
    await userEvent.click(screen.getByRole('button', { name: 'Continuar' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Documento e selfie' })).toBeInTheDocument();
    expect(api.escritas()[0]?.url).toBe('/api/profissional/cadastro/dados');
    expect(corpoDe(api.escritas()[0])).toEqual({ cpf: '52998224725', dataNascimento: '1990-01-31' });
  });

  it('CPF incompleto ou sem data: avisa nos campos sem chamar a API', async () => {
    const api = simularCadastro({ cadastro: COM_TRABALHO });
    abrirCadastro(2);

    await userEvent.type(await screen.findByRole('textbox', { name: 'CPF' }), '123');
    await userEvent.click(screen.getByRole('button', { name: 'Continuar' }));

    expect(screen.getByRole('textbox', { name: 'CPF' })).toHaveAccessibleDescription(/O CPF tem 11 números\./);
    expect(screen.getByLabelText('Data de nascimento')).toHaveAccessibleDescription('Digite a sua data de nascimento.');
    expect(api.escritas()).toHaveLength(0);
  });

  it('422 idade-minima vira erro no campo da data', async () => {
    simularCadastro({
      cadastro: COM_TRABALHO,
      escrita: () => problema(422, 'idade-minima', 'Para trabalhar na COE é preciso ter 18 anos ou mais.'),
    });
    abrirCadastro(2);

    await userEvent.type(await screen.findByRole('textbox', { name: 'CPF' }), '52998224725');
    await userEvent.type(screen.getByLabelText('Data de nascimento'), '2010-01-31');
    await userEvent.click(screen.getByRole('button', { name: 'Continuar' }));

    expect(await screen.findByText('Para trabalhar na COE é preciso ter 18 anos ou mais.')).toBeInTheDocument();
    expect(screen.getByLabelText('Data de nascimento')).toHaveAttribute('aria-invalid', 'true');
  });

  it('dados já guardados: o CPF aparece mascarado e Continuar segue sem PUT', async () => {
    const api = simularCadastro({ cadastro: CADASTRO_COMPLETO });
    abrirCadastro(2);

    expect(await screen.findByText(/Já guardamos o CPF \*\*\*\.\*\*\*\.\*\*\*-35/)).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: 'CPF' })).toHaveValue('');
    await userEvent.click(screen.getByRole('button', { name: 'Continuar' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Documento e selfie' })).toBeInTheDocument();
    expect(api.escritas()).toHaveLength(0);
  });

  it('celular não confirmado: aviso com o caminho para confirmar', async () => {
    simularCadastro({ cadastro: COM_TRABALHO, conta: { celularConfirmado: false } });
    abrirCadastro(2);

    expect(await screen.findByRole('link', { name: 'Confirmar celular' })).toHaveAttribute('href', '/conta/confirmar-celular');
    expect(screen.getByText(/Confirme seu celular antes de informar o CPF\./)).toBeInTheDocument();
  });

  it('422 celular-nao-confirmado (o servidor só aceita o CPF com o celular confirmado): erro no campo do CPF', async () => {
    simularCadastro({
      cadastro: COM_TRABALHO,
      conta: { celularConfirmado: false },
      escrita: () => problema(422, 'celular-nao-confirmado', 'Confirme seu celular antes de informar o CPF.'),
    });
    abrirCadastro(2);

    await userEvent.type(await screen.findByRole('textbox', { name: 'CPF' }), '52998224725');
    await userEvent.type(screen.getByLabelText('Data de nascimento'), '1990-01-31');
    await userEvent.click(screen.getByRole('button', { name: 'Continuar' }));

    expect(await screen.findByRole('textbox', { name: 'CPF' })).toHaveAttribute('aria-invalid', 'true');
  });
});

describe('etapa 3: documento e selfie', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('manda o RG e a selfie na hora, atualiza o rascunho e libera o Continuar', async () => {
    const api = simularCadastro({
      cadastro: COM_TRABALHO,
      escrita: (pedido) => (pedido.url.endsWith('/documentos') ? json({ id: 'd1', tipo: 'rg_frente', status: 'pendente' }, 201) : undefined),
    });
    const { container } = abrirCadastro(3);

    await userEvent.upload(await screen.findByLabelText(/Foto da frente do RG/), new File(['x'], 'rg.jpg', { type: 'image/jpeg' }));
    api.definir({
      ...COM_TRABALHO,
      etapas: { ...COM_TRABALHO.etapas, documentos: true },
      documentos: [
        { id: 'd1', tipo: 'rg_frente', status: 'pendente' },
        { id: 'd2', tipo: 'selfie', status: 'pendente' },
      ],
    });
    await userEvent.upload(screen.getByLabelText(/Selfie segurando o documento/), new File(['x'], 'eu.jpg', { type: 'image/jpeg' }));

    expect(await screen.findAllByText('Foto enviada. Se quiser, mande outra no lugar.')).toHaveLength(2);
    await semViolacoes(container);
    await userEvent.click(screen.getByRole('button', { name: 'Continuar' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'O que você faz?' })).toBeInTheDocument();
    expect(api.escritas().map((p) => p.url)).toEqual([
      '/api/profissional/cadastro/documentos',
      '/api/profissional/cadastro/documentos',
    ]);
  });

  it('CNH escolhida manda o tipo cnh', async () => {
    const api = simularCadastro({
      cadastro: COM_TRABALHO,
      escrita: () => json({ id: 'd1', tipo: 'cnh', status: 'pendente' }, 201),
    });
    abrirCadastro(3);

    await userEvent.click(await screen.findByRole('radio', { name: 'CNH' }));
    await userEvent.upload(screen.getByLabelText(/Foto da CNH/), new File(['x'], 'cnh.jpg', { type: 'image/jpeg' }));

    await screen.findByRole('button', { name: 'Continuar' });
    expect(api.falso.mock.calls.some(([, init]) => (init?.body as FormData | undefined)?.get?.('tipo') === 'cnh')).toBe(true);
  });

  it('arquivo recusado (415): mensagem no campo, sem avançar', async () => {
    simularCadastro({
      cadastro: COM_TRABALHO,
      escrita: () => problema(415, 'arquivo-tipo-invalido', 'Mande uma foto em JPEG, PNG ou WebP.'),
    });
    abrirCadastro(3);

    await userEvent.upload(await screen.findByLabelText(/Selfie segurando/), new File(['x'], 'eu.jpg', { type: 'image/jpeg' }));

    expect(await screen.findByText('Mande uma foto em JPEG, PNG ou WebP.')).toBeInTheDocument();
    expect(screen.getByLabelText(/Selfie segurando/)).toHaveAttribute('aria-invalid', 'true');
  });

  it('sem documento e selfie: Continuar avisa', async () => {
    simularCadastro({ cadastro: COM_TRABALHO });
    abrirCadastro(3);

    await userEvent.click(await screen.findByRole('button', { name: 'Continuar' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Mande a foto do documento e a selfie para continuar.');
  });

  it('NR-10 só aparece para eletricista', async () => {
    simularCadastro({ cadastro: { ...COM_TRABALHO, profissao: 'eletricista' } });
    abrirCadastro(3);

    expect(await screen.findByLabelText(/Certificado de NR-10/)).toBeInTheDocument();
  });
});

describe('etapas 4 a 6: serviços, experiência e valor', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('serviços da profissão escolhida, do catálogo', async () => {
    const api = simularCadastro({ cadastro: COM_TRABALHO });
    abrirCadastro(4);

    expect(await screen.findByRole('group', { name: 'Pedreiro' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Continuar' }));
    expect(screen.getByRole('alert')).toHaveTextContent('Escolha pelo menos um serviço.');
    await userEvent.click(screen.getByRole('checkbox', { name: 'Alvenaria' }));
    await userEvent.click(screen.getByRole('button', { name: 'Continuar' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Sua experiência' })).toBeInTheDocument();
    expect(corpoDe(api.escritas()[0])).toEqual({ servicos: [{ profissao: 'pedreiro', nome: 'Alvenaria' }] });
  });

  it('sem trabalho escolhido, serviços manda de volta para a etapa 1', async () => {
    simularCadastro();
    abrirCadastro(4);

    await userEvent.click(await screen.findByRole('button', { name: 'Escolher o trabalho' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Qual é o seu trabalho?' })).toBeInTheDocument();
  });

  it('experiência, ferramentas e MEI', async () => {
    const api = simularCadastro({ cadastro: COM_TRABALHO });
    const { container } = abrirCadastro(5);

    await userEvent.click(await screen.findByRole('button', { name: 'Continuar' }));
    expect(screen.getByRole('group', { name: 'Há quanto tempo trabalha nisso?' })).toHaveAccessibleDescription(
      'Escolha há quanto tempo você trabalha nisso.',
    );
    await userEvent.click(screen.getByRole('radio', { name: '10 a 20 anos' }));
    await userEvent.click(screen.getByRole('radio', { name: 'Levo parte das ferramentas' }));
    await userEvent.click(screen.getByRole('radio', { name: 'Sim, emito nota' }));
    await semViolacoes(container);
    await userEvent.click(screen.getByRole('button', { name: 'Continuar' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Quanto você cobra por dia?' })).toBeInTheDocument();
    expect(corpoDe(api.escritas()[0])).toEqual({ anos: 10, ferramentas: 'parte', mei: true });
  });

  it('MEI sem resposta vai como null', async () => {
    const api = simularCadastro({ cadastro: COM_TRABALHO });
    abrirCadastro(5);

    await userEvent.click(await screen.findByRole('radio', { name: 'Menos de 1 ano' }));
    await userEvent.click(screen.getByRole('radio', { name: 'Levo as minhas ferramentas' }));
    await userEvent.click(screen.getByRole('button', { name: 'Continuar' }));

    await screen.findByRole('heading', { level: 1, name: 'Quanto você cobra por dia?' });
    expect(corpoDe(api.escritas()[0])).toEqual({ anos: 0, ferramentas: 'sim', mei: null });
  });

  it('valor: − e +, prévia com a taxa da API, meia diária e horário', async () => {
    const api = simularCadastro({ cadastro: COM_TRABALHO });
    const { container } = abrirCadastro(6);

    await userEvent.type(await screen.findByRole('textbox', { name: 'Valor da diária (R$)' }), '280');
    expect(screen.getByText(/Você recebe/)).toHaveTextContent('Você recebe R$ 280,00 por dia. O cliente paga R$ 308,00 com a taxa da COE.');
    await userEvent.click(screen.getByRole('button', { name: 'Aumentar 10 reais' }));
    expect(screen.getByRole('textbox', { name: 'Valor da diária (R$)' })).toHaveValue('290');
    await userEvent.click(screen.getByRole('checkbox', { name: /Aceito meia diária/ }));
    await userEvent.type(screen.getByRole('textbox', { name: 'Valor da meia diária (R$)' }), '160');
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Seu horário' }), '8h às 17h');
    await semViolacoes(container);
    await userEvent.click(screen.getByRole('button', { name: 'Continuar' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Onde você trabalha?' })).toBeInTheDocument();
    expect(corpoDe(api.escritas()[0])).toEqual({ valorDiaria: '290.00', valorMeiaDiaria: '160.00', horario: '8h às 17h' });
  });

  it('valor abaixo do mínimo (422): erro no campo, com a mensagem do servidor', async () => {
    simularCadastro({
      cadastro: COM_TRABALHO,
      escrita: () => problema(422, 'valor-abaixo-do-minimo', 'A diária precisa ser de pelo menos R$ 100,00.'),
    });
    abrirCadastro(6);

    await userEvent.type(await screen.findByRole('textbox', { name: 'Valor da diária (R$)' }), '50');
    await userEvent.click(screen.getByRole('button', { name: 'Continuar' }));

    expect(await screen.findByText('A diária precisa ser de pelo menos R$ 100,00.')).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: 'Valor da diária (R$)' })).toHaveAttribute('aria-invalid', 'true');
  });

  it('valor vazio ou com letras: avisa sem chamar a API', async () => {
    const api = simularCadastro({ cadastro: COM_TRABALHO });
    abrirCadastro(6);

    await userEvent.type(await screen.findByRole('textbox', { name: 'Valor da diária (R$)' }), 'abc');
    await userEvent.click(screen.getByRole('button', { name: 'Continuar' }));

    expect(screen.getByRole('textbox', { name: 'Valor da diária (R$)' })).toHaveAccessibleDescription(/só com números/);
    expect(api.escritas()).toHaveLength(0);
  });

  it('taxa paga pelo profissional: a prévia desconta', async () => {
    simularCadastro({ cadastro: { ...COM_TRABALHO, valorDiaria: '200.00' } });
    const fetchSimulado = globalThis.fetch;
    vi.stubGlobal(
      'fetch',
      vi.fn((entrada: RequestInfo | URL, init?: RequestInit) =>
        String(entrada) === '/api/publico/regras'
          ? Promise.resolve(json({ comissao: '0.1000', prazoLiberacao: 'PT12H', taxaPagaPor: 'PROFISSIONAL', versaoTermos: '1.0' }))
          : fetchSimulado(entrada, init),
      ),
    );
    abrirCadastro(6);

    expect(await screen.findByText(/Você recebe/)).toHaveTextContent('Você recebe R$ 180,00 por dia, já com a taxa da COE descontada.');
  });
});
