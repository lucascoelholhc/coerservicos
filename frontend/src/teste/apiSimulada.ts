import type { Catalogo } from '../api/tipos';
import { CATALOGO_PADRAO, REGRAS_PADRAO } from './dadosDaApi';
import { json, simularFetch } from './fetchSimulado';

export { CATALOGO_PADRAO, REGRAS_PADRAO };

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
