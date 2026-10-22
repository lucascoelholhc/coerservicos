import { describe, expect, it } from 'vitest';

import { formatarData, formatarDiaEHora } from './datas';

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
