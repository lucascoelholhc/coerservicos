import { expect, test, type Page, type Route } from '@playwright/test';

import { semRolagemLateral, semViolacoesAxe, vigiarCsp } from './ajudantes';
import { simularApi } from './apiSimulada';
import { CONTA_PADRAO, SESSAO_PADRAO } from '../src/teste/dadosDaApi';
import { PERFIL_DE_EXEMPLO } from '../src/teste/dadosDaBusca';
import { CONTRATO_AGUARDANDO, CONTRATO_PAGO, PAGAMENTO_PIX } from '../src/teste/dadosDosContratos';

const json = (rota: Route, corpo: unknown, status = 200) =>
  rota.fulfill({ status, contentType: 'application/json', body: JSON.stringify(corpo) });

/** Cliente logado e a API do FE-05: o contrato vira pago quando o pagamento simulado chega. */
async function simularContratacao(pagina: Page) {
  let pago = false;
  const pedidos: { caminho: string; corpo: unknown }[] = [];
  await pagina.route('**/api/auth/renovar', (rota) => json(rota, SESSAO_PADRAO));
  await pagina.route('**/api/contas/eu', (rota) => json(rota, CONTA_PADRAO));
  await pagina.route(`**/api/publico/profissionais/${PERFIL_DE_EXEMPLO.id}`, (rota) =>
    json(rota, { ...PERFIL_DE_EXEMPLO, fotos: [] }),
  );
  await pagina.route('**/api/contratos**', async (rota) => {
    const pedido = rota.request();
    const caminho = new URL(pedido.url()).pathname;
    if (pedido.method() === 'POST') {
      pedidos.push({ caminho, corpo: pedido.postDataJSON() as unknown });
      if (caminho === '/api/contratos') {
        return json(rota, CONTRATO_AGUARDANDO, 201);
      }
      if (caminho.endsWith('/pagamento/simular')) {
        pago = true;
        return rota.fulfill({ status: 204 });
      }
      return json(rota, PAGAMENTO_PIX, 201);
    }
    return json(rota, pago ? CONTRATO_PAGO : CONTRATO_AGUARDANDO);
  });
  return pedidos;
}

test.describe('Contratar e pagar (FE-05)', () => {
  test('perfil → dias → pedido → Pix → pagamento simulado → contato liberado, sem violação de CSP', async ({
    page,
  }) => {
    const violacoes = await vigiarCsp(page);
    await simularApi(page);
    const pedidos = await simularContratacao(page);

    await page.goto(`/profissionais/${PERFIL_DE_EXEMPLO.id}`);
    await page.getByRole('link', { name: 'Contratar diárias' }).click();

    await expect(page.getByRole('heading', { level: 1, name: 'Escolha os dias' })).toBeVisible();
    await semViolacoesAxe(page);
    await semRolagemLateral(page);
    await page.getByRole('checkbox', { name: /seg, 26\/10/ }).check();
    await page.getByRole('button', { name: 'Continuar' }).click();

    await expect(page.getByRole('heading', { level: 1, name: 'Conte o que precisa' })).toBeVisible();
    await page.getByRole('checkbox', { name: 'Alvenaria' }).check();
    await page.getByRole('radio', { name: 'Já tenho o material' }).check();
    await page.getByLabel('CEP').fill('89010-000');
    await page.getByLabel('Rua').fill('Rua das Palmeiras');
    await page.getByLabel('Número').fill('120');
    await semViolacoesAxe(page);
    await semRolagemLateral(page);
    await page.getByRole('button', { name: 'Ver valores e pagar' }).click();

    await expect(page.getByRole('heading', { level: 1, name: 'Valdir S.' })).toBeVisible();
    await expect(page.getByText('Esperando pagamento')).toBeVisible();
    await page.getByRole('button', { name: 'Gerar o Pix' }).click();
    await expect(page.getByLabel('Código Pix copia e cola')).toHaveValue(PAGAMENTO_PIX.pixCopiaECola ?? '');
    await semViolacoesAxe(page);
    await semRolagemLateral(page);
    await page.getByRole('button', { name: 'Simular pagamento (ambiente de teste)' }).click();

    await expect(page.getByText('Pagamento confirmado')).toBeVisible();
    await expect(page.getByText('(47) 90000-0099')).toBeVisible();
    await semViolacoesAxe(page);
    await semRolagemLateral(page);

    expect(pedidos.map((p) => p.caminho)).toEqual([
      '/api/contratos',
      `/api/contratos/${CONTRATO_AGUARDANDO.id}/pagamento`,
      `/api/contratos/${CONTRATO_AGUARDANDO.id}/pagamento/simular`,
    ]);
    expect(violacoes).toEqual([]);
  });
});
