import { caminhoInternoSeguro } from '../../api/voltar';
import { CAMINHOS } from '../../caminhos';

/** Para onde ir depois de entrar: o "voltar" só se for caminho interno; senão, Minha conta. */
export function destinoDepoisDeEntrar(busca: string): string {
  const voltar = new URLSearchParams(busca).get('voltar');
  const seguro = caminhoInternoSeguro(voltar);
  return voltar && seguro !== CAMINHOS.inicio ? seguro : CAMINHOS.conta;
}
