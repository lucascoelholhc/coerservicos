import { erroDaFalha, erroDaResposta, ErroDaApi } from './erros';
import { avisarSessaoPerdida, encerrarSessao, iniciarSessao, tokenAtual } from './sessao';
import type { SessaoResposta } from './tipos';

/** Tempo máximo de uma chamada; passou disso, o erro é "tempo" (diferente de "sem conexão"). */
export const TEMPO_LIMITE_MS = 15_000;

export interface OpcoesDaChamada {
  metodo?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  corpo?: unknown;
  /** Sinal de quem chamou (ex.: tela desmontada); vira erro "cancelado". */
  sinal?: AbortSignal;
  tempoLimiteMs?: number;
}

type ResultadoDaRenovacao = 'renovada' | 'recusada' | 'indisponivel' | 'sem-conexao';

let renovacaoEmAndamento: Promise<ResultadoDaRenovacao> | null = null;

/**
 * Chamada à API em /api com JSON. Com sessão, manda o token no header Authorization; se a resposta
 * for 401, renova a sessão uma vez (uma única renovação para várias 401 juntas) e repete a chamada.
 */
export async function chamar<T>(caminho: string, opcoes: OpcoesDaChamada = {}): Promise<T> {
  const token = tokenAtual();
  let resposta = await enviar(caminho, opcoes, token);
  if (resposta.status === 401 && token !== null) {
    resposta = await repetirDepoisDeRenovar(caminho, opcoes, token);
  }
  return lerCorpo<T>(resposta);
}

async function repetirDepoisDeRenovar(caminho: string, opcoes: OpcoesDaChamada, tokenUsado: string): Promise<Response> {
  // Outra chamada já renovou enquanto esta esperava: só repete com o token novo.
  if (tokenAtual() === tokenUsado) {
    const resultado = await renovarSessao();
    if (resultado === 'sem-conexao') {
      throw new ErroDaApi('sem-conexao');
    }
    if (resultado === 'indisponivel') {
      throw new ErroDaApi('servidor');
    }
    if (resultado === 'recusada') {
      throw sessaoPerdida();
    }
  }
  const repetida = await enviar(caminho, opcoes, tokenAtual());
  if (repetida.status === 401) {
    throw sessaoPerdida();
  }
  return repetida;
}

/** POST /api/auth/renovar com o cookie HttpOnly; uma só em andamento por vez. */
export function renovarSessao(): Promise<ResultadoDaRenovacao> {
  renovacaoEmAndamento ??= pedirRenovacao().finally(() => {
    renovacaoEmAndamento = null;
  });
  return renovacaoEmAndamento;
}

async function pedirRenovacao(): Promise<ResultadoDaRenovacao> {
  let resposta: Response;
  try {
    resposta = await fetch('/api/auth/renovar', {
      method: 'POST',
      headers: { Accept: 'application/json' },
      credentials: 'same-origin',
      signal: AbortSignal.timeout(TEMPO_LIMITE_MS),
    });
  } catch {
    return 'sem-conexao';
  }
  if (resposta.status === 401 || resposta.status === 403) {
    return 'recusada';
  }
  if (!resposta.ok) {
    return 'indisponivel';
  }
  try {
    iniciarSessao((await resposta.json()) as SessaoResposta);
  } catch {
    return 'indisponivel';
  }
  return 'renovada';
}

function sessaoPerdida(): ErroDaApi {
  encerrarSessao();
  avisarSessaoPerdida();
  return new ErroDaApi('sessao', { status: 401 });
}

async function enviar(caminho: string, opcoes: OpcoesDaChamada, token: string | null): Promise<Response> {
  if (!navigator.onLine) {
    throw new ErroDaApi('sem-conexao');
  }
  const cabecalhos: Record<string, string> = { Accept: 'application/json' };
  if (opcoes.corpo !== undefined) {
    cabecalhos['Content-Type'] = 'application/json';
  }
  if (token !== null) {
    cabecalhos.Authorization = `Bearer ${token}`;
  }
  const sinais = [AbortSignal.timeout(opcoes.tempoLimiteMs ?? TEMPO_LIMITE_MS)];
  if (opcoes.sinal) {
    sinais.push(opcoes.sinal);
  }
  try {
    return await fetch(`/api${caminho}`, {
      method: opcoes.metodo ?? 'GET',
      headers: cabecalhos,
      body: opcoes.corpo === undefined ? undefined : JSON.stringify(opcoes.corpo),
      credentials: 'same-origin',
      signal: AbortSignal.any(sinais),
    });
  } catch (falha) {
    throw erroDaFalha(falha);
  }
}

async function lerCorpo<T>(resposta: Response): Promise<T> {
  if (!resposta.ok) {
    throw await erroDaResposta(resposta);
  }
  if (resposta.status === 204) {
    return undefined as T;
  }
  try {
    return (await resposta.json()) as T;
  } catch {
    throw new ErroDaApi('resposta');
  }
}
