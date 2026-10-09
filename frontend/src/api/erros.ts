/** Tipos de erro que a tela trata; nunca mostra stack, JSON cru ou mensagem em inglês. */
export type TipoDeErro =
  | 'problema'
  | 'sem-conexao'
  | 'tempo'
  | 'limite'
  | 'servidor'
  | 'sessao'
  | 'resposta'
  | 'cancelado';

export const MENSAGENS: Record<TipoDeErro, string> = {
  problema: 'Não foi possível concluir. Tente de novo.',
  'sem-conexao': 'Sem conexão. Confira a internet e tente de novo.',
  tempo: 'A COE demorou para responder. Tente de novo.',
  limite: 'Muitas tentativas seguidas. Espere um pouco e tente de novo.',
  servidor: 'A COE está com um problema agora. Tente de novo daqui a pouco.',
  sessao: 'Sua sessão terminou. Entre de novo para continuar.',
  resposta: 'Não foi possível entender a resposta da COE. Tente de novo.',
  cancelado: 'A busca foi cancelada.',
};

const PREFIXO_DO_TIPO = 'urn:coe:erro:';

interface Detalhes {
  status?: number;
  codigo?: string;
  mensagem?: string;
  campos?: Record<string, string>;
}

export class ErroDaApi extends Error {
  readonly tipo: TipoDeErro;
  readonly status: number | undefined;
  /** Código do Problem Details (urn:coe:erro:<codigo>), ex.: "contato-em-uso". */
  readonly codigo: string | undefined;
  /** Mensagem por campo do formulário (validação ou conflito). */
  readonly campos: Readonly<Record<string, string>>;

  constructor(tipo: TipoDeErro, detalhes: Detalhes = {}) {
    super(detalhes.mensagem ?? MENSAGENS[tipo]);
    this.name = 'ErroDaApi';
    this.tipo = tipo;
    this.status = detalhes.status;
    this.codigo = detalhes.codigo;
    this.campos = detalhes.campos ?? {};
  }
}

/** Resposta não OK: 429 e 5xx com mensagem própria; o resto lê o Problem Details (RFC 9457). */
export async function erroDaResposta(resposta: Response): Promise<ErroDaApi> {
  const status = resposta.status;
  if (status === 429) {
    return new ErroDaApi('limite', { status });
  }
  if (status >= 500) {
    return new ErroDaApi('servidor', { status });
  }
  const problema = await lerProblema(resposta);
  return new ErroDaApi('problema', { status, ...problema });
}

/** Falha do fetch: tempo esgotado, cancelamento de quem chamou ou falta de rede. */
export function erroDaFalha(falha: unknown): ErroDaApi {
  // typeof em vez de instanceof: a falha pode vir de outro realm (iframe, jsdom) e ainda ter name.
  const nome = typeof falha === 'object' && falha !== null && 'name' in falha ? falha.name : undefined;
  if (nome === 'TimeoutError') {
    return new ErroDaApi('tempo');
  }
  if (nome === 'AbortError') {
    return new ErroDaApi('cancelado');
  }
  return new ErroDaApi('sem-conexao');
}

async function lerProblema(resposta: Response): Promise<Omit<Detalhes, 'status'>> {
  let corpo: unknown;
  try {
    corpo = await resposta.json();
  } catch {
    return {};
  }
  if (typeof corpo !== 'object' || corpo === null) {
    return {};
  }
  const { type, detail, campos, campo } = corpo as Record<string, unknown>;
  const codigo = typeof type === 'string' && type.startsWith(PREFIXO_DO_TIPO) ? type.slice(PREFIXO_DO_TIPO.length) : undefined;
  const mensagem = typeof detail === 'string' && detail.trim() !== '' ? detail : undefined;
  const porCampo: Record<string, string> = {};
  if (Array.isArray(campos)) {
    for (const item of campos as unknown[]) {
      const { campo: nome, mensagem: texto } = (item ?? {}) as Record<string, unknown>;
      if (typeof nome === 'string' && typeof texto === 'string') {
        porCampo[nome] = texto;
      }
    }
  }
  if (typeof campo === 'string' && mensagem !== undefined) {
    porCampo[campo] = mensagem;
  }
  return { codigo, mensagem, campos: porCampo };
}
