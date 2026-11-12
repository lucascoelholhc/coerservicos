import { chamar } from './cliente';

/** Saldos agora (view saldo_conta) e o que entrou e saiu no período (DOM-11 parte 2b, PA02). */
export interface ResumoFinanceiro {
  saldos: {
    guardado: string;
    aRepassar: string;
    receita: string;
    aEstornar: string;
    aDevolver: string;
    taxasDoGateway: string;
    noGateway: string;
    emDisputa: string;
  };
  periodo: {
    de: string;
    ate: string;
    recebido: string;
    liberadoAoProfissional: string;
    receita: string;
    reembolsado: string;
    repassado: string;
  };
}

export type TipoDeAlerta =
  | 'repasse-falhou'
  | 'estorno-falhou'
  | 'devolucao-falhou'
  | 'conciliacao'
  | 'dinheiro-sem-pedido'
  | 'cobranca-desconhecida';

export interface AlertaFinanceiro {
  id: string;
  tipo: TipoDeAlerta;
  referencia: string | null;
  detalhe: Record<string, unknown>;
  criadoEm: string;
  resolvidoEm: string | null;
}

export type StatusDoRepasse = 'pendente' | 'enviado' | 'confirmado' | 'falhou';

export interface RepasseDoPainel {
  id: string;
  diariaId: string;
  profissionalId: string;
  profissional: string;
  valor: string;
  status: StatusDoRepasse;
  erro: string | null;
  tentativas: number;
  pixConferido: boolean;
  criadoEm: string;
  enviadoEm: string | null;
  confirmadoEm: string | null;
}

/** A chave em claro, só para o admin conferir que é do profissional (RN15); a versão amarra a conferência. */
export interface PixParaConferir {
  tipo: string;
  chave: string;
  nome: string;
  versao: string;
  conferida: boolean;
}

interface Pagina<T> {
  itens: T[];
  temMais: boolean;
}

const BASE = '/admin/financeiro';

export function buscarResumoFinanceiro(sinal: AbortSignal): Promise<ResumoFinanceiro> {
  return chamar<ResumoFinanceiro>(`${BASE}?`, { sinal });
}

export function buscarAlertasAbertos(sinal: AbortSignal): Promise<Pagina<AlertaFinanceiro>> {
  return chamar<Pagina<AlertaFinanceiro>>(`${BASE}/alertas?abertos=true`, { sinal });
}

export function buscarRepasses(sinal: AbortSignal): Promise<Pagina<RepasseDoPainel>> {
  return chamar<Pagina<RepasseDoPainel>>(`${BASE}/repasses`, { sinal });
}

export async function mandarRepasseDeNovo(id: string): Promise<void> {
  await chamar<unknown>(`${BASE}/repasses/${encodeURIComponent(id)}/tentar-de-novo`, { metodo: 'POST' });
}

export async function resolverAlerta(id: string): Promise<void> {
  await chamar<unknown>(`${BASE}/alertas/${encodeURIComponent(id)}/resolver`, { metodo: 'POST' });
}

/** Abrir a chave fica auditado na API. */
export function buscarPixParaConferir(profissional: string): Promise<PixParaConferir> {
  return chamar<PixParaConferir>(`/admin/profissionais/${encodeURIComponent(profissional)}/pix`);
}

export async function conferirPix(profissional: string, versao: string): Promise<void> {
  await chamar<unknown>(`/admin/profissionais/${encodeURIComponent(profissional)}/pix/conferido`, {
    metodo: 'POST',
    corpo: { versao },
  });
}
