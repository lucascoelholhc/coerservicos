import { describe, expect, it } from 'vitest';

import { CATALOGO_PADRAO } from '../../teste/dadosDaApi';
import {
  FILTROS_VAZIOS,
  comFiltro,
  consultaDosFiltros,
  etiquetasDosFiltros,
  filtrosDaUrl,
  semFiltro,
} from './filtros';

const url = (texto: string) => new URLSearchParams(texto);

describe('filtros da busca na URL', () => {
  it('lê os filtros com os nomes da API', () => {
    expect(
      filtrosDaUrl(
        url(
          'profissao=pedreiro&servico=Alvenaria&cidade=4202404&dia=2026-10-27&valorMaximo=250.00&experienciaMinima=5&meiaDiaria=true&ferramentas=sim&ordem=preco',
        ),
      ),
    ).toEqual({
      profissao: 'pedreiro',
      servico: 'Alvenaria',
      cidade: '4202404',
      dia: '2026-10-27',
      valorMaximo: '250.00',
      experienciaMinima: '5',
      meiaDiaria: true,
      ferramentas: 'sim',
      ordem: 'preco',
    });
  });

  it('valor fora do formato vira "sem filtro" (a URL pode ter sido editada à mão)', () => {
    expect(
      filtrosDaUrl(
        url('cidade=abc&dia=27/10&valorMaximo=-3&experienciaMinima=x&meiaDiaria=sim&ferramentas=todas&ordem=nota'),
      ),
    ).toEqual(FILTROS_VAZIOS);
  });

  it('a consulta só leva o que foi escolhido, na ordem fixa (a mesma para a URL e para a API)', () => {
    expect(consultaDosFiltros(FILTROS_VAZIOS)).toBe('');
    expect(
      consultaDosFiltros({ ...FILTROS_VAZIOS, cidade: '4202404', profissao: 'pintor', ordem: 'experiencia' }),
    ).toBe('profissao=pintor&cidade=4202404&ordem=experiencia');
    expect(consultaDosFiltros({ ...FILTROS_VAZIOS, meiaDiaria: true, servico: 'Pintura interna' })).toBe(
      'servico=Pintura+interna&meiaDiaria=true',
    );
  });

  it('ler e escrever de volta dá a mesma consulta', () => {
    const texto = 'profissao=pedreiro&servico=Alvenaria&cidade=4202404&dia=2026-10-27&ferramentas=parte&ordem=preco';
    expect(consultaDosFiltros(filtrosDaUrl(url(texto)))).toBe(texto);
  });

  it('trocar a profissão tira o serviço (ele é da profissão anterior)', () => {
    const antes = { ...FILTROS_VAZIOS, profissao: 'pedreiro', servico: 'Alvenaria', cidade: '4202404' };
    expect(comFiltro(antes, { profissao: 'pintor' })).toEqual({ ...antes, profissao: 'pintor', servico: '' });
    expect(comFiltro(antes, { cidade: '' })).toEqual({ ...antes, cidade: '' });
  });
});

describe('etiquetas removíveis', () => {
  it('uma por filtro escolhido (a profissão fica na barra de categorias e a ordem no seletor)', () => {
    const filtros = {
      ...FILTROS_VAZIOS,
      profissao: 'pedreiro',
      servico: 'Alvenaria',
      cidade: '4202404',
      dia: '2026-10-27',
      valorMaximo: '250.00',
      experienciaMinima: '5',
      meiaDiaria: true,
      ferramentas: 'sim' as const,
      ordem: 'preco' as const,
    };

    expect(etiquetasDosFiltros(filtros, CATALOGO_PADRAO.cidades).map((etiqueta) => etiqueta.texto)).toEqual([
      'Alvenaria',
      'Blumenau',
      'Livre ter, 27/10',
      'Até R$ 250,00',
      '5 anos ou mais',
      'Leva as ferramentas',
      'Meia diária',
    ]);
  });

  it('cidade que não está no catálogo aparece pelo código (não some sem aviso)', () => {
    expect(etiquetasDosFiltros({ ...FILTROS_VAZIOS, cidade: '999' }, []).map((e) => e.texto)).toEqual([
      'Cidade 999',
    ]);
  });

  it('remover uma etiqueta tira só aquele filtro', () => {
    const filtros = { ...FILTROS_VAZIOS, cidade: '4202404', meiaDiaria: true, ferramentas: 'parte' as const };

    expect(semFiltro(filtros, 'meiaDiaria')).toEqual({ ...filtros, meiaDiaria: false });
    expect(semFiltro(filtros, 'ferramentas')).toEqual({ ...filtros, ferramentas: '' });
    expect(semFiltro(filtros, 'cidade')).toEqual({ ...filtros, cidade: '' });
  });
});
