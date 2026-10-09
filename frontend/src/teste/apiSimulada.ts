import type { Catalogo, Regras } from '../api/tipos';
import { json, simularFetch } from './fetchSimulado';

/** Catálogo igual ao do banco local (V3 e V11), na ordem que a API devolve. */
export const CATALOGO_PADRAO: Catalogo = {
  areas: [
    {
      codigo: 'obra',
      nome: 'Obra e reforma',
      profissoes: [
        { codigo: 'pedreiro', nome: 'Pedreiro', nomePlural: 'Pedreiros', icone: 'brick', servicos: [{ nome: 'Alvenaria' }] },
        { codigo: 'pintor', nome: 'Pintor', nomePlural: 'Pintores', icone: 'roller', servicos: [] },
        { codigo: 'eletricista', nome: 'Eletricista', nomePlural: 'Eletricistas', icone: 'bolt', servicos: [] },
      ],
    },
    {
      codigo: 'casa',
      nome: 'Casa e jardim',
      profissoes: [
        { codigo: 'diarista', nome: 'Diarista', nomePlural: 'Diaristas', icone: 'broom', servicos: [] },
        { codigo: 'jardineiro', nome: 'Jardineiro', nomePlural: 'Jardineiros', icone: 'leaf', servicos: [] },
      ],
    },
  ],
  cidades: [
    { codigoIbge: 4202008, nome: 'Balneário Camboriú', uf: 'SC' },
    { codigoIbge: 4202404, nome: 'Blumenau', uf: 'SC' },
    { codigoIbge: 4202909, nome: 'Brusque', uf: 'SC' },
    { codigoIbge: 4205902, nome: 'Gaspar', uf: 'SC' },
    { codigoIbge: 4207502, nome: 'Indaial', uf: 'SC' },
    { codigoIbge: 4208203, nome: 'Itajaí', uf: 'SC' },
    { codigoIbge: 4208906, nome: 'Jaraguá do Sul', uf: 'SC' },
    { codigoIbge: 4211306, nome: 'Navegantes', uf: 'SC' },
    { codigoIbge: 4213203, nome: 'Pomerode', uf: 'SC' },
    { codigoIbge: 4218202, nome: 'Timbó', uf: 'SC' },
  ],
};

export const REGRAS_PADRAO: Regras = { comissao: '0.1000', prazoLiberacao: 'PT12H', taxaPagaPor: 'CLIENTE' };

type Resposta = () => Response | Promise<Response>;

/** API pública simulada: cada endpoint responde com os dados dados (ou com a função de resposta). */
export function simularApiPublica(opcoes: { catalogo?: Catalogo | Resposta; regras?: unknown } = {}) {
  const catalogo = opcoes.catalogo ?? CATALOGO_PADRAO;
  const regras = opcoes.regras ?? REGRAS_PADRAO;
  return simularFetch((pedido) => {
    if (pedido.url === '/api/publico/catalogo') {
      return typeof catalogo === 'function' ? catalogo() : json(catalogo);
    }
    if (pedido.url === '/api/publico/regras') {
      return typeof regras === 'function' ? (regras as Resposta)() : json(regras);
    }
    return json({ type: 'urn:coe:erro:nao-encontrado', status: 404 }, 404, 'application/problem+json');
  });
}
