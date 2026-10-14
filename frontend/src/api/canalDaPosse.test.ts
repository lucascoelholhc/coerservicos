import { afterEach, describe, expect, it, vi } from 'vitest';

import { entregarComprovanteDoEmail, ouvirComprovanteDoEmail } from './canalDaPosse';

/*
 * No Vitest o BroadcastChannel é o do Node: instâncias no mesmo processo conversam entre si, como
 * abas do mesmo navegador. Toda instância criada aqui é fechada no fim do teste.
 */
const abertos: Array<() => void> = [];

function ouvir(email: string) {
  const aoReceber = vi.fn();
  const parar = ouvirComprovanteDoEmail(email, aoReceber);
  abertos.push(parar);
  return { aoReceber, parar };
}

function canalCru() {
  const canal = new BroadcastChannel('coe-posse');
  abertos.push(() => canal.close());
  return canal;
}

describe('canal da posse entre abas (RN61)', () => {
  afterEach(() => {
    abertos.splice(0).forEach((fechar) => fechar());
    vi.unstubAllGlobals();
  });

  it('a aba que prova o mesmo e-mail (normalizado) recebe o comprovante, responde e a entrega dá certo', async () => {
    const aba = ouvir('  Ana@Exemplo.com ');

    await expect(entregarComprovanteDoEmail('ana@exemplo.com', 'comp-email')).resolves.toBe(true);

    expect(aba.aoReceber).toHaveBeenCalledWith('comp-email');
  });

  it('a mensagem leva só tipo, e-mail e comprovante (nenhuma senha)', async () => {
    const mensagens: unknown[] = [];
    const espia = canalCru();
    espia.onmessage = (evento: MessageEvent) => mensagens.push(evento.data);

    await entregarComprovanteDoEmail('ana@exemplo.com', 'comp-email');

    expect(mensagens).toEqual([{ tipo: 'coe-posse-email', email: 'ana@exemplo.com', comprovante: 'comp-email' }]);
  });

  it('mensagem com outro e-mail é ignorada (e ninguém responde)', async () => {
    const aba = ouvir('outra@exemplo.com');

    await expect(entregarComprovanteDoEmail('ana@exemplo.com', 'comp-email')).resolves.toBe(false);

    expect(aba.aoReceber).not.toHaveBeenCalled();
  });

  it.each([
    ['texto solto', 'coe-posse-email'],
    ['nulo', null],
    ['tipo errado', { tipo: 'outro', email: 'ana@exemplo.com', comprovante: 'x' }],
    ['e-mail que não é texto', { tipo: 'coe-posse-email', email: 5, comprovante: 'x' }],
    ['sem comprovante', { tipo: 'coe-posse-email', email: 'ana@exemplo.com' }],
    ['comprovante vazio', { tipo: 'coe-posse-email', email: 'ana@exemplo.com', comprovante: '' }],
  ])('mensagem malformada (%s) é ignorada e a aba continua ouvindo', async (_caso, mensagem) => {
    const aba = ouvir('ana@exemplo.com');

    canalCru().postMessage(mensagem);
    await new Promise((resolver) => setTimeout(resolver, 50));
    expect(aba.aoReceber).not.toHaveBeenCalled();

    await expect(entregarComprovanteDoEmail('ana@exemplo.com', 'comp-email')).resolves.toBe(true);
    expect(aba.aoReceber).toHaveBeenCalledWith('comp-email');
  });

  it('duas abas de cadastro: só a do e-mail certo aceita; a outra continua esperando o dela', async () => {
    const daAna = ouvir('ana@exemplo.com');
    const daBia = ouvir('bia@exemplo.com');

    await expect(entregarComprovanteDoEmail('ana@exemplo.com', 'comp-ana')).resolves.toBe(true);
    expect(daAna.aoReceber).toHaveBeenCalledWith('comp-ana');
    expect(daBia.aoReceber).not.toHaveBeenCalled();

    await expect(entregarComprovanteDoEmail('bia@exemplo.com', 'comp-bia')).resolves.toBe(true);
    expect(daBia.aoReceber).toHaveBeenCalledWith('comp-bia');
    expect(daAna.aoReceber).toHaveBeenCalledTimes(1);
  });

  it('depois de aceitar, a aba para de ouvir: uma segunda entrega não tem resposta', async () => {
    const aba = ouvir('ana@exemplo.com');
    await entregarComprovanteDoEmail('ana@exemplo.com', 'comp-1');

    await expect(entregarComprovanteDoEmail('ana@exemplo.com', 'comp-2')).resolves.toBe(false);

    expect(aba.aoReceber).toHaveBeenCalledTimes(1);
  });

  it('parar de ouvir antes (aba fechada ou cancelou): a entrega não tem resposta; parar duas vezes não quebra', async () => {
    const aba = ouvir('ana@exemplo.com');
    aba.parar();
    aba.parar();

    await expect(entregarComprovanteDoEmail('ana@exemplo.com', 'comp-email')).resolves.toBe(false);

    expect(aba.aoReceber).not.toHaveBeenCalled();
  });

  it('resposta de "recebido" para outro e-mail não conta como entrega', async () => {
    const outraAba = canalCru();
    outraAba.onmessage = () => {
      outraAba.postMessage(null);
      outraAba.postMessage({ tipo: 'outro', email: 'ana@exemplo.com' });
      outraAba.postMessage({ tipo: 'coe-posse-recebido', email: 'outra@exemplo.com' });
    };

    await expect(entregarComprovanteDoEmail('ana@exemplo.com', 'comp-email')).resolves.toBe(false);
  });

  it('sem BroadcastChannel (navegador antigo): ouvir não faz nada e a entrega volta sem resposta', async () => {
    vi.stubGlobal('BroadcastChannel', undefined);
    const aoReceber = vi.fn();

    const parar = ouvirComprovanteDoEmail('ana@exemplo.com', aoReceber);
    parar();

    await expect(entregarComprovanteDoEmail('ana@exemplo.com', 'comp-email')).resolves.toBe(false);
    expect(aoReceber).not.toHaveBeenCalled();
  });
});
