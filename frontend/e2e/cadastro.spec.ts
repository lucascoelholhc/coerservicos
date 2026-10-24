import { expect, test, type Page, type Route } from '@playwright/test';

import { semRolagemLateral, semViolacoesAxe, vigiarCsp } from './ajudantes';
import { simularApi } from './apiSimulada';
import type { CadastroDoProfissional, EtapaDoCadastro, TipoDeDocumento } from '../src/api/tipos';
import { CONTA_PADRAO, SESSAO_PADRAO } from '../src/teste/dadosDaApi';
import { CADASTRO_VAZIO } from '../src/teste/dadosDoCadastro';

/** PNG de 1x1 px: a prévia da foto escolhida usa blob:, que a CSP precisa deixar. */
const PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==',
  'base64',
);

const ORDEM: EtapaDoCadastro[] = [
  'trabalho',
  'dados',
  'documentos',
  'servicos',
  'experiencia',
  'valor',
  'onde',
  'dias',
  'fotos',
  'sobre',
  'pix',
];

/** O cadastro do DOM-02 em memória: cada PUT grava o seu pedaço e marca a etapa, como o backend. */
async function simularCadastro(pagina: Page) {
  const estado: { cadastro: CadastroDoProfissional | null } = { cadastro: null };
  const json = (rota: Route, corpo: unknown, status = 200) =>
    rota.fulfill({ status, contentType: 'application/json', body: JSON.stringify(corpo) });
  const completar = (mudanca: Partial<CadastroDoProfissional>, etapa?: EtapaDoCadastro) => {
    const atual = estado.cadastro ?? { ...CADASTRO_VAZIO, nome: SESSAO_PADRAO.usuario.nome };
    const etapas = etapa ? { ...atual.etapas, [etapa]: true } : atual.etapas;
    const primeira = ORDEM.findIndex((chave) => !etapas[chave]);
    estado.cadastro = { ...atual, ...mudanca, etapas, etapaAtual: primeira === -1 ? 11 : primeira + 1 };
    return estado.cadastro;
  };

  await pagina.route('**/api/auth/renovar', (rota) => json(rota, SESSAO_PADRAO));
  await pagina.route('**/api/contas/eu', (rota) => json(rota, { ...CONTA_PADRAO, celularConfirmado: true }));
  await pagina.route('**/api/profissional/cadastro**', async (rota) => {
    const pedido = rota.request();
    const caminho = new URL(pedido.url()).pathname.replace('/api/profissional/cadastro', '');
    const metodo = pedido.method();
    if (metodo === 'GET' && caminho === '') {
      return estado.cadastro
        ? json(rota, estado.cadastro)
        : json(rota, { type: 'urn:coe:erro:nao-encontrado', status: 404, detail: 'Cadastro não encontrado.' }, 404);
    }
    if (metodo === 'POST' && caminho === '/documentos') {
      const tipo: TipoDeDocumento = (pedido.postData() ?? '').includes('selfie') ? 'selfie' : 'rg_frente';
      const atual = completar({});
      const documentos = [...atual.documentos.filter((d) => d.tipo !== tipo), { id: `doc-${tipo}`, tipo, status: 'pendente' }];
      const completo = documentos.some((d) => d.tipo === 'rg_frente') && documentos.some((d) => d.tipo === 'selfie');
      completar({ documentos }, completo ? 'documentos' : undefined);
      return json(rota, { id: `doc-${tipo}`, tipo, status: 'pendente' }, 201);
    }
    if (metodo === 'POST' && caminho === '/fotos') {
      const atual = completar({});
      const foto = { id: `foto-${atual.fotos.length + 1}`, servico: null, legenda: null };
      completar({ fotos: [...atual.fotos, foto] }, 'fotos');
      return json(rota, foto, 201);
    }
    if (metodo === 'PUT' && caminho.startsWith('/fotos/')) {
      return json(rota, { id: caminho.slice('/fotos/'.length), ...pedido.postDataJSON() });
    }
    if (metodo === 'POST' && caminho === '/envio') {
      return json(rota, completar({ status: 'em_analise' }));
    }
    const corpo = pedido.postDataJSON() as Record<string, unknown>;
    const etapa = caminho.slice(1) as EtapaDoCadastro;
    const mudancas: Partial<Record<EtapaDoCadastro, Partial<CadastroDoProfissional>>> = {
      trabalho: { profissao: corpo.profissao as string, outrasProfissoes: corpo.outras as string[] },
      dados: { cpf: '***.***.***-25', dataNascimento: corpo.dataNascimento as string },
      servicos: { servicos: corpo.servicos as CadastroDoProfissional['servicos'] },
      experiencia: {
        experienciaAnos: corpo.anos as number,
        ferramentas: corpo.ferramentas as CadastroDoProfissional['ferramentas'],
        mei: corpo.mei === true,
      },
      valor: {
        valorDiaria: corpo.valorDiaria as string,
        valorMeiaDiaria: corpo.valorMeiaDiaria as string | null,
        horario: corpo.horario as string,
      },
      onde: { cidade: corpo.cidade as number, raioKm: corpo.raioKm as number, outrasCidades: corpo.outrasCidades as number[] },
      dias: { dias: corpo.dias as number[] },
      sobre: { frases: corpo.frases as string[], bio: corpo.texto as string | null },
      pix: { chavePixTipo: corpo.tipo as CadastroDoProfissional['chavePixTipo'], chavePix: '123e…' },
    };
    return json(rota, completar(mudancas[etapa] ?? {}, etapa));
  });
}

async function continuar(pagina: Page, proximo: string) {
  await pagina.getByRole('button', { name: 'Continuar' }).click();
  await expect(pagina.getByRole('heading', { level: 1, name: proximo })).toBeVisible();
}

test.describe('Cadastro do profissional', () => {
  test('as 11 etapas até "Cadastro enviado", com fotos, sem violação de CSP', async ({ page }) => {
    // Fluxo longo (11 telas, uploads e dois axe): com a suíte inteira em paralelo passa dos 30 s padrão.
    test.slow();
    const violacoes = await vigiarCsp(page);
    await simularApi(page);
    await simularCadastro(page);

    await page.goto('/cadastro');
    await expect(page.getByRole('heading', { level: 1, name: 'Qual é o seu trabalho?' })).toBeVisible();
    await semViolacoesAxe(page);
    await semRolagemLateral(page);
    await page.getByRole('radio', { name: /Pedreiro/ }).check();
    await continuar(page, 'Seus dados');

    await page.getByRole('textbox', { name: 'CPF' }).fill('52998224725');
    await page.getByLabel('Data de nascimento').fill('1990-01-31');
    await continuar(page, 'Documento e selfie');

    await page.getByLabel(/Foto da frente do RG/).setInputFiles({ name: 'rg.png', mimeType: 'image/png', buffer: PNG });
    await expect(page.getByText('Foto enviada.', { exact: false })).toHaveCount(1);
    await page.getByLabel(/Selfie segurando/).setInputFiles({ name: 'eu.png', mimeType: 'image/png', buffer: PNG });
    await expect(page.getByText('Foto enviada.', { exact: false })).toHaveCount(2);
    await semViolacoesAxe(page);
    await continuar(page, 'O que você faz?');

    await page.getByRole('checkbox', { name: 'Alvenaria' }).check();
    await continuar(page, 'Sua experiência');

    await page.getByRole('radio', { name: '10 a 20 anos' }).check();
    await page.getByRole('radio', { name: 'Levo as minhas ferramentas' }).check();
    await continuar(page, 'Quanto você cobra por dia?');

    await page.getByRole('textbox', { name: 'Valor da diária (R$)' }).fill('280');
    await expect(page.getByText('Você recebe R$ 280,00 por dia.', { exact: false })).toBeVisible();
    await semRolagemLateral(page);
    await continuar(page, 'Onde você trabalha?');

    await page.getByRole('combobox', { name: 'Em que cidade você mora?' }).selectOption({ label: 'Blumenau' });
    await page.getByRole('radio', { name: /Minha cidade e vizinhas/ }).check();
    await continuar(page, 'Em quais dias você trabalha?');

    await page.getByRole('checkbox', { name: 'Segunda-feira' }).check();
    await page.getByRole('checkbox', { name: 'Terça-feira' }).check();
    await semViolacoesAxe(page);
    await continuar(page, 'Fotos dos seus trabalhos');

    await page.getByLabel(/Fotos de trabalhos/).setInputFiles({ name: 'muro.png', mimeType: 'image/png', buffer: PNG });
    await expect(page.getByRole('img', { name: 'Foto 1' })).toBeVisible();
    await page.getByRole('combobox', { name: 'Que serviço é a foto 1?' }).selectOption('Alvenaria');
    await semViolacoesAxe(page);
    await continuar(page, 'Conte sobre você');

    await page.getByRole('checkbox', { name: 'Chego no horário combinado' }).check();
    await page.getByRole('textbox', { name: /Quer contar mais/ }).fill('Trabalho com obra há muitos anos.');
    await continuar(page, 'Onde receber o dinheiro');

    await page.getByRole('radio', { name: 'Chave aleatória' }).check();
    await page.getByRole('textbox', { name: 'Sua chave Pix' }).fill('123e4567-e89b-12d3-a456-426614174000');
    await page.getByRole('checkbox', { name: /Li e aceito/ }).check();
    await page.getByRole('checkbox', { name: /não posso passar meu telefone/ }).check();
    await semViolacoesAxe(page);
    await semRolagemLateral(page);
    await page.getByRole('button', { name: 'Enviar cadastro' }).click();

    await expect(page.getByRole('heading', { level: 1, name: 'Cadastro enviado' })).toBeVisible();
    await semViolacoesAxe(page);
    expect(violacoes).toEqual([]);
  });

  test('"Quero trabalhar" sem conta leva à conta de quem vai trabalhar', async ({ page }) => {
    await simularApi(page);

    await page.goto('/criar-conta');
    await page.getByRole('link', { name: /Quero trabalhar/ }).click();

    await expect(page.getByRole('heading', { level: 1, name: 'Criar conta para trabalhar' })).toBeVisible();
    await semViolacoesAxe(page);
  });
});
