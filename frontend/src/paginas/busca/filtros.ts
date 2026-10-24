/*
 * Filtros da busca (DOM-04). A URL é a fonte: dá para voltar, recarregar e mandar o link. Os nomes dos
 * parâmetros são os mesmos da API, então a mesma consulta serve para a URL e para o GET.
 * O front só limpa o que veio torto da URL; quem valida de verdade é o servidor (422 com a mensagem).
 */
import type { CidadeDoCatalogo } from '../../api/tipos';
import { formatarDiaCurto } from '../../formatacao/datas';
import { formatarReais } from '../../formatacao/dinheiro';

export type Ordem = 'livre' | 'preco' | 'experiencia';
export type Ferramentas = '' | 'sim' | 'parte';

export interface FiltrosDaBusca {
  profissao: string;
  servico: string;
  /** Código IBGE. */
  cidade: string;
  /** "AAAA-MM-DD". */
  dia: string;
  /** Como a API ("250.00"). */
  valorMaximo: string;
  experienciaMinima: string;
  meiaDiaria: boolean;
  ferramentas: Ferramentas;
  ordem: Ordem;
}

export const FILTROS_VAZIOS: FiltrosDaBusca = {
  profissao: '',
  servico: '',
  cidade: '',
  dia: '',
  valorMaximo: '',
  experienciaMinima: '',
  meiaDiaria: false,
  ferramentas: '',
  ordem: 'livre',
};

export const ORDENS: readonly { valor: Ordem; texto: string }[] = [
  { valor: 'livre', texto: 'Quem está livre antes' },
  { valor: 'preco', texto: 'Menor preço' },
  { valor: 'experiencia', texto: 'Mais experiência' },
];

const CODIGO = /^[a-z0-9-]{1,40}$/;
const TEXTO_CURTO = /^.{1,80}$/u;
const NUMERO = /^\d{1,7}$/;
const DIA = /^\d{4}-\d{2}-\d{2}$/;
const REAIS = /^\d{1,7}(\.\d{2})?$/;

function se(valor: string | null, formato: RegExp): string {
  return valor !== null && formato.test(valor) ? valor : '';
}

export function filtrosDaUrl(parametros: URLSearchParams): FiltrosDaBusca {
  const ferramentas = parametros.get('ferramentas');
  const ordem = parametros.get('ordem');
  const valorMaximo = se(parametros.get('valorMaximo'), REAIS);
  return {
    profissao: se(parametros.get('profissao'), CODIGO),
    servico: se(parametros.get('servico')?.trim() ?? null, TEXTO_CURTO),
    cidade: se(parametros.get('cidade'), NUMERO),
    dia: se(parametros.get('dia'), DIA),
    valorMaximo: Number(valorMaximo) > 0 ? valorMaximo : '',
    experienciaMinima: se(parametros.get('experienciaMinima'), NUMERO),
    meiaDiaria: parametros.get('meiaDiaria') === 'true',
    ferramentas: ferramentas === 'sim' || ferramentas === 'parte' ? ferramentas : '',
    ordem: ordem === 'preco' || ordem === 'experiencia' ? ordem : 'livre',
  };
}

/** Só o que foi escolhido, sempre na mesma ordem (URL estável). */
export function consultaDosFiltros(filtros: FiltrosDaBusca): string {
  const consulta = new URLSearchParams();
  const pares: [string, string][] = [
    ['profissao', filtros.profissao],
    ['servico', filtros.servico],
    ['cidade', filtros.cidade],
    ['dia', filtros.dia],
    ['valorMaximo', filtros.valorMaximo],
    ['experienciaMinima', filtros.experienciaMinima],
    ['meiaDiaria', filtros.meiaDiaria ? 'true' : ''],
    ['ferramentas', filtros.ferramentas],
    ['ordem', filtros.ordem === 'livre' ? '' : filtros.ordem],
  ];
  for (const [nome, valor] of pares) {
    if (valor !== '') {
      consulta.set(nome, valor);
    }
  }
  return consulta.toString();
}

/** Troca filtros; o serviço é da profissão, então trocar a profissão o tira. */
export function comFiltro(filtros: FiltrosDaBusca, troca: Partial<FiltrosDaBusca>): FiltrosDaBusca {
  const novos = { ...filtros, ...troca };
  if (troca.profissao !== undefined && troca.profissao !== filtros.profissao && troca.servico === undefined) {
    novos.servico = '';
  }
  return novos;
}

export type ChaveRemovivel =
  | 'servico'
  | 'cidade'
  | 'dia'
  | 'valorMaximo'
  | 'experienciaMinima'
  | 'ferramentas'
  | 'meiaDiaria';

export interface Etiqueta {
  chave: ChaveRemovivel;
  texto: string;
}

/** Uma etiqueta por filtro ativo (a profissão fica na barra de categorias; a ordem, no seletor). */
export function etiquetasDosFiltros(filtros: FiltrosDaBusca, cidades: readonly CidadeDoCatalogo[]): Etiqueta[] {
  const etiquetas: Etiqueta[] = [];
  if (filtros.servico) {
    etiquetas.push({ chave: 'servico', texto: filtros.servico });
  }
  if (filtros.cidade) {
    const cidade = cidades.find((c) => String(c.codigoIbge) === filtros.cidade);
    etiquetas.push({ chave: 'cidade', texto: cidade ? cidade.nome : `Cidade ${filtros.cidade}` });
  }
  if (filtros.dia) {
    etiquetas.push({ chave: 'dia', texto: `Livre ${formatarDiaCurto(filtros.dia)}` });
  }
  if (filtros.valorMaximo) {
    etiquetas.push({ chave: 'valorMaximo', texto: `Até ${formatarReais(filtros.valorMaximo)}` });
  }
  if (filtros.experienciaMinima) {
    etiquetas.push({ chave: 'experienciaMinima', texto: `${filtros.experienciaMinima} anos ou mais` });
  }
  if (filtros.ferramentas) {
    etiquetas.push({
      chave: 'ferramentas',
      texto: filtros.ferramentas === 'sim' ? 'Leva as ferramentas' : 'Leva ao menos parte das ferramentas',
    });
  }
  if (filtros.meiaDiaria) {
    etiquetas.push({ chave: 'meiaDiaria', texto: 'Meia diária' });
  }
  return etiquetas;
}

export function semFiltro(filtros: FiltrosDaBusca, chave: ChaveRemovivel): FiltrosDaBusca {
  return { ...filtros, [chave]: chave === 'meiaDiaria' ? false : '' };
}
