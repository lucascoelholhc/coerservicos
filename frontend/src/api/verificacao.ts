import { chamar } from './cliente';
import type {
  DecisaoDaVerificacao,
  DetalheDaVerificacao,
  FilaDeVerificacao,
  ItensDaConferencia,
  UrlAssinada,
} from './tipos';

const BASE = '/admin/verificacao';

const caminhoDo = (id: string) => `${BASE}/${encodeURIComponent(id)}`;

/** Fila de cadastros em análise, mais antigo primeiro (DOM-03). */
export function buscarFila(pagina: number, sinal: AbortSignal): Promise<FilaDeVerificacao> {
  return chamar<FilaDeVerificacao>(`${BASE}?pagina=${pagina}`, { sinal });
}

/** Detalhe para o checklist; o CPF completo vem aqui (a API audita cada abertura). */
export function buscarDetalheDaVerificacao(id: string, sinal: AbortSignal): Promise<DetalheDaVerificacao> {
  return chamar<DetalheDaVerificacao>(caminhoDo(id), { sinal });
}

/** URL de 2 min do documento (auditada como "documento visto pelo admin"). */
export function urlDoDocumentoParaAdmin(id: string, documento: string): Promise<UrlAssinada> {
  return chamar<UrlAssinada>(`${caminhoDo(id)}/documentos/${encodeURIComponent(documento)}/url`);
}

export function urlDaFotoParaAdmin(id: string, foto: string): Promise<UrlAssinada> {
  return chamar<UrlAssinada>(`${caminhoDo(id)}/fotos/${encodeURIComponent(foto)}/url`);
}

export interface PedidoDeAprovacao {
  itens: ItensDaConferencia;
  nr10?: 'verificado' | 'recusado';
  motivoNr10?: string;
}

export function aprovarCadastro(id: string, pedido: PedidoDeAprovacao): Promise<DecisaoDaVerificacao> {
  return chamar<DecisaoDaVerificacao>(`${caminhoDo(id)}/aprovar`, { metodo: 'POST', corpo: pedido });
}

/** O motivo vai por SMS ao profissional (até 160 letras, uma linha, sem contato). */
export function pedirCorrecao(id: string, motivo: string): Promise<DecisaoDaVerificacao> {
  return chamar<DecisaoDaVerificacao>(`${caminhoDo(id)}/correcao`, { metodo: 'POST', corpo: { motivo } });
}

export function recusarCadastro(id: string, motivo: string): Promise<DecisaoDaVerificacao> {
  return chamar<DecisaoDaVerificacao>(`${caminhoDo(id)}/recusar`, { metodo: 'POST', corpo: { motivo } });
}
