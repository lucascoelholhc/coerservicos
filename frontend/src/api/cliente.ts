import { erroDaFalha, erroDaResposta, ErroDaApi } from './erros';
import { avisarSessaoPerdida, encerrarSessao, geracaoDaSessao, iniciarSessao, tokenAtual } from './sessao';
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

type ResultadoDaRenovacao = 'renovada' | 'recusada' | 'descartada' | 'indisponivel' | 'tempo' | 'sem-conexao';

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
  if (tokenAtual() === null) {
    // Outra chamada (ou "sair") já encerrou a sessão e já avisou: não renova nem avisa de novo.
    throw new ErroDaApi('sessao', { status: 401 });
  }
  // Se outra chamada já renovou enquanto esta esperava, só repete com o token novo.
  if (tokenAtual() === tokenUsado) {
    lancarSeNaoRenovou(await renovarSessao(opcoes.tempoLimiteMs ?? TEMPO_LIMITE_MS));
  }
  const repetida = await enviar(caminho, opcoes, tokenAtual());
  if (repetida.status === 401) {
    throw sessaoPerdida();
  }
  return repetida;
}

function lancarSeNaoRenovou(resultado: ResultadoDaRenovacao): void {
  switch (resultado) {
    case 'renovada':
      return;
    case 'recusada':
      throw sessaoPerdida();
    case 'descartada':
      throw new ErroDaApi('sessao', { status: 401 });
    case 'indisponivel':
      throw new ErroDaApi('servidor');
    case 'tempo':
      throw new ErroDaApi('tempo');
    case 'sem-conexao':
      throw new ErroDaApi('sem-conexao');
  }
}

/** POST /api/auth/renovar com o cookie HttpOnly; uma só em andamento por vez. */
export function renovarSessao(tempoLimiteMs = TEMPO_LIMITE_MS): Promise<ResultadoDaRenovacao> {
  renovacaoEmAndamento ??= pedirRenovacao(tempoLimiteMs).finally(() => {
    renovacaoEmAndamento = null;
  });
  return renovacaoEmAndamento;
}

async function pedirRenovacao(tempoLimiteMs: number): Promise<ResultadoDaRenovacao> {
  const geracaoNoInicio = geracaoDaSessao();
  const { sinal, soltar } = sinalComLimite(tempoLimiteMs);
  let resposta: Response;
  try {
    resposta = await fetch('/api/auth/renovar', {
      method: 'POST',
      headers: { Accept: 'application/json' },
      credentials: 'same-origin',
      signal: sinal,
    });
  } catch (falha) {
    return erroDaFalha(falha).tipo === 'tempo' ? 'tempo' : 'sem-conexao';
  } finally {
    soltar();
  }
  if (resposta.status === 401 || resposta.status === 403) {
    return 'recusada';
  }
  if (!resposta.ok) {
    return 'indisponivel';
  }
  let sessao: SessaoResposta;
  try {
    sessao = (await resposta.json()) as SessaoResposta;
  } catch {
    return 'indisponivel';
  }
  // Quem saiu enquanto a renovação estava no ar continua fora.
  if (geracaoDaSessao() !== geracaoNoInicio) {
    return 'descartada';
  }
  iniciarSessao(sessao);
  return 'renovada';
}

function sessaoPerdida(): ErroDaApi {
  encerrarSessao();
  avisarSessaoPerdida();
  return new ErroDaApi('sessao', { status: 401 });
}

/**
 * Sinal com limite de tempo e repasse do cancelamento de quem chamou, feito à mão: AbortSignal.any e
 * AbortSignal.timeout não existem em celulares mais antigos (Safari antes do 17.4, Chrome antes do 116).
 */
function sinalComLimite(tempoLimiteMs: number, sinalDeQuemChamou?: AbortSignal): { sinal: AbortSignal; soltar: () => void } {
  const controle = new AbortController();
  const relogio = setTimeout(() => controle.abort(new DOMException('Tempo esgotado', 'TimeoutError')), tempoLimiteMs);
  const repassar = () => controle.abort(sinalDeQuemChamou?.reason);
  if (sinalDeQuemChamou?.aborted) {
    repassar();
  } else {
    sinalDeQuemChamou?.addEventListener('abort', repassar, { once: true });
  }
  return {
    sinal: controle.signal,
    soltar: () => {
      clearTimeout(relogio);
      sinalDeQuemChamou?.removeEventListener('abort', repassar);
    },
  };
}

async function enviar(caminho: string, opcoes: OpcoesDaChamada, token: string | null): Promise<Response> {
  // navigator.onLine não bloqueia: em alguns celulares e VPNs ele diz "offline" com rede funcionando.
  const cabecalhos: Record<string, string> = { Accept: 'application/json' };
  if (opcoes.corpo !== undefined) {
    cabecalhos['Content-Type'] = 'application/json';
  }
  if (token !== null) {
    cabecalhos.Authorization = `Bearer ${token}`;
  }
  const { sinal, soltar } = sinalComLimite(opcoes.tempoLimiteMs ?? TEMPO_LIMITE_MS, opcoes.sinal);
  try {
    return await fetch(`/api${caminho}`, {
      method: opcoes.metodo ?? 'GET',
      headers: cabecalhos,
      body: opcoes.corpo === undefined ? undefined : JSON.stringify(opcoes.corpo),
      credentials: 'same-origin',
      signal: sinal,
    });
  } catch (falha) {
    throw erroDaFalha(falha);
  } finally {
    soltar();
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
