import { CAMINHOS } from '../caminhos';

/**
 * Telas privadas que funcionam com contato pendente (RN61). Seguem o backend: só as telas cujas
 * chamadas têm @LiberadoComContatoPendente (lista conferida no LiberadosComContatoPendenteTest).
 * Negar por padrão: rota privada nova fica bloqueada até entrar aqui (e, antes, no backend).
 */
export const ROTAS_LIBERADAS_COM_CONTATO_PENDENTE: readonly string[] = [
  CAMINHOS.conta,
  CAMINHOS.recolocarContato,
  CAMINHOS.trocarSenha,
  CAMINHOS.confirmarCelular,
];

export function liberadaComContatoPendente(caminho: string): boolean {
  const semBarraNoFim = caminho.length > 1 ? caminho.replace(/\/+$/, '') : caminho;
  return ROTAS_LIBERADAS_COM_CONTATO_PENDENTE.includes(semBarraNoFim);
}
