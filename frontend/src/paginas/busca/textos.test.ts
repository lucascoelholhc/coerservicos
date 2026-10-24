import { describe, expect, it } from 'vitest';

import {
  casasDoCalendario,
  textoDaExperiencia,
  textoDasFerramentas,
  textoDasProfissoes,
  textoDoProximoLivre,
} from './textos';

describe('textos da busca e do perfil', () => {
  it('próximo dia livre: amanhã, um dia da semana ou nenhum nas quatro semanas', () => {
    expect(textoDoProximoLivre('2026-10-24', '2026-10-23')).toBe('Livre amanhã');
    expect(textoDoProximoLivre('2026-10-27', '2026-10-23')).toBe('Livre ter, 27/10');
    expect(textoDoProximoLivre(null, '2026-10-23')).toBe('Sem dia livre nas próximas 4 semanas');
  });

  it('profissões: a principal primeiro, as outras em minúscula', () => {
    const nome = (codigo: string) => ({ pedreiro: 'Pedreiro', pintor: 'Pintor', eletricista: 'Eletricista' })[codigo] ?? codigo;
    expect(textoDasProfissoes(['pedreiro'], nome)).toBe('Pedreiro');
    expect(textoDasProfissoes(['pedreiro', 'pintor'], nome)).toBe('Pedreiro e pintor');
    expect(textoDasProfissoes(['pedreiro', 'pintor', 'eletricista'], nome)).toBe('Pedreiro, pintor e eletricista');
    expect(textoDasProfissoes([], nome)).toBe('');
  });

  it('experiência como a pessoa fala', () => {
    expect(textoDaExperiencia(0)).toBe('Menos de 1 ano');
    expect(textoDaExperiencia(1)).toBe('1 ano');
    expect(textoDaExperiencia(20)).toBe('20 anos');
  });

  it('ferramentas: o mesmo sentido do cadastro, do ponto de vista de quem contrata', () => {
    expect(textoDasFerramentas('sim')).toBe('Leva as suas');
    expect(textoDasFerramentas('parte')).toBe('Leva parte');
    expect(textoDasFerramentas('nao')).toBe('Você fornece');
    expect(textoDasFerramentas('outra')).toBe('A combinar');
  });
});

describe('calendário de dias livres (quatro semanas a partir de amanhã)', () => {
  // Sexta, 23/10/2026: amanhã é sábado, 24; a janela vai até sexta, 20/11.
  const casas = casasDoCalendario('2026-10-23', [1, 2, 3, 4, 5], ['2026-10-26', '2026-10-27', '2026-11-20']);

  it('semanas inteiras, de segunda a domingo', () => {
    expect(casas).toHaveLength(35);
    expect(casas[0]?.dia).toBe('2026-10-19');
    expect(casas.at(-1)?.dia).toBe('2026-11-22');
  });

  it('cada casa com o estado (nunca só pela cor: o rótulo diz o estado)', () => {
    const estado = (dia: string) => casas.find((casa) => casa.dia === dia)?.estado;
    expect(estado('2026-10-23')).toBe('passado'); // hoje
    expect(estado('2026-10-24')).toBe('folga'); // sábado: não trabalha
    expect(estado('2026-10-26')).toBe('livre');
    expect(estado('2026-10-28')).toBe('ocupado'); // trabalha na quarta, mas não está livre
    expect(estado('2026-11-20')).toBe('livre'); // último dia da janela
    expect(estado('2026-11-21')).toBe('fora'); // depois da janela da API
    expect(casas.find((casa) => casa.dia === '2026-10-26')?.rotulo).toBe('segunda-feira, 26 de outubro: livre');
    expect(casas.find((casa) => casa.dia === '2026-10-23')?.hoje).toBe(true);
  });
});
