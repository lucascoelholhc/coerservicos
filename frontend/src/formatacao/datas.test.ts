import { describe, expect, it } from 'vitest';

import {
  diaDaSemana,
  formatarData,
  formatarDiaCurto,
  formatarDiaEHora,
  formatarDiaLongo,
  hojeEmSaoPaulo,
  somarDias,
} from './datas';

describe('datas no fuso de São Paulo', () => {
  it('instante da API vira dia e hora de São Paulo', () => {
    expect(formatarDiaEHora('2026-10-20T13:00:00Z')).toBe('20/10 às 10:00');
    // 02:30 UTC ainda é o dia anterior em São Paulo
    expect(formatarDiaEHora('2026-10-21T02:30:00Z')).toBe('20/10 às 23:30');
  });

  it('data sem hora fica como está, só no formato brasileiro', () => {
    expect(formatarData('1980-05-10')).toBe('10/05/1980');
  });
});

describe('dias (sem hora) para a busca e o calendário', () => {
  it('dia curto com o dia da semana, como o protótipo ("ter, 27/10")', () => {
    expect(formatarDiaCurto('2026-10-27')).toBe('ter, 27/10');
    expect(formatarDiaCurto('2026-11-01')).toBe('dom, 01/11');
  });

  it('dia por extenso para o leitor de tela', () => {
    expect(formatarDiaLongo('2026-10-27')).toBe('terça-feira, 27 de outubro');
  });

  it('dia da semana como o backend (0 = domingo), sem depender do fuso do aparelho', () => {
    expect(diaDaSemana('2026-10-25')).toBe(0);
    expect(diaDaSemana('2026-10-31')).toBe(6);
  });

  it('somar dias passa o mês e o ano', () => {
    expect(somarDias('2026-10-31', 1)).toBe('2026-11-01');
    expect(somarDias('2026-12-31', 1)).toBe('2027-01-01');
    expect(somarDias('2026-03-01', -1)).toBe('2026-02-28');
  });

  it('"hoje" é o dia em São Paulo, não o do aparelho', () => {
    expect(hojeEmSaoPaulo(new Date('2026-10-24T02:00:00Z'))).toBe('2026-10-23');
    expect(hojeEmSaoPaulo(new Date('2026-10-24T03:00:00Z'))).toBe('2026-10-24');
  });
});
