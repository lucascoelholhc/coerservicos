import { describe, expect, it } from 'vitest';

import { fraseDaAprovacao, fraseDaLiberacaoAoProfissional, fraseDaTaxa } from './frases';

const REGRAS = { comissao: '0.1000', prazoLiberacao: 'PT12H', taxaPagaPor: 'CLIENTE' } as const;

describe('frases com os números da API', () => {
  it('aprovação: com o prazo da API, ou sem número enquanto não há regra', () => {
    expect(fraseDaAprovacao(REGRAS)).toBe(
      'Terminou o dia, você aprova. Se não responder em 12 h, o valor é liberado ao profissional.',
    );
    expect(fraseDaAprovacao({ ...REGRAS, prazoLiberacao: 'PT24H' })).toBe(
      'Terminou o dia, você aprova. Se não responder em 24 h, o valor é liberado ao profissional.',
    );
    expect(fraseDaAprovacao(undefined)).toBe(
      'Terminou o dia, você aprova. Se não responder, o valor é liberado ao profissional depois de um prazo.',
    );
  });

  it('liberação para o profissional: com o prazo, ou sem número', () => {
    expect(fraseDaLiberacaoAoProfissional(REGRAS)).toBe('Sem resposta do cliente em 12 h, o dinheiro é liberado');
    expect(fraseDaLiberacaoAoProfissional(undefined)).toBe(
      'Sem resposta do cliente, o dinheiro é liberado depois de um prazo',
    );
  });

  it('taxa paga pelo cliente ou descontada do profissional, com o percentual formatado', () => {
    expect(fraseDaTaxa(REGRAS)).toBe('A taxa da COE é de 10%, paga pelo cliente.');
    expect(fraseDaTaxa({ ...REGRAS, comissao: '0.1250' })).toBe('A taxa da COE é de 12,5%, paga pelo cliente.');
    expect(fraseDaTaxa({ ...REGRAS, taxaPagaPor: 'PROFISSIONAL' })).toBe(
      'A taxa da COE é de 10%, descontada do valor do profissional.',
    );
  });
});
