import { chamar } from './cliente';
import { ErroDaApi } from './erros';
import type {
  CadastroDoProfissional,
  DocumentoDoCadastro,
  Ferramentas,
  FotoDoCadastro,
  TipoDeChavePix,
  TipoDeDocumento,
  UrlAssinada,
} from './tipos';

const BASE = '/profissional/cadastro';

/** Foto de celular em rede fraca demora mais que os 15 s das outras chamadas. */
export const TEMPO_DO_UPLOAD_MS = 90_000;

/** Corpo de cada PUT de etapa, igual aos records de PedidosDoCadastro. */
export interface CorposDasEtapas {
  trabalho: { profissao: string; outras: string[] };
  dados: { cpf: string; dataNascimento: string };
  servicos: { servicos: { profissao: string; nome: string }[] };
  experiencia: { anos: number; ferramentas: Ferramentas; mei: boolean | null };
  valor: { valorDiaria: string; valorMeiaDiaria: string | null; horario: string };
  onde: { cidade: number; raioKm: number; outrasCidades: number[] };
  dias: { dias: number[] };
  sobre: { frases: string[]; texto: string | null };
  pix: { tipo: TipoDeChavePix; chave: string };
}

export type EtapaComPut = keyof CorposDasEtapas;

/** GET do próprio rascunho; null quando ainda não existe (404 antes da primeira etapa). */
export async function buscarCadastro(sinal: AbortSignal): Promise<CadastroDoProfissional | null> {
  try {
    return await chamar<CadastroDoProfissional>(BASE, { sinal });
  } catch (falha) {
    if (falha instanceof ErroDaApi && falha.status === 404) {
      return null;
    }
    throw falha;
  }
}

/** PUT de uma etapa: o servidor valida só aquele pedaço e devolve o rascunho inteiro. */
export function salvarEtapa<E extends EtapaComPut>(etapa: E, corpo: CorposDasEtapas[E]): Promise<CadastroDoProfissional> {
  return chamar<CadastroDoProfissional>(`${BASE}/${etapa}`, { metodo: 'PUT', corpo });
}

/** POST /envio: confere tudo de novo e manda para análise. */
export function enviarCadastro(corpo: {
  versaoTermosAceita: string;
  aceitoRegraDeContato: boolean;
}): Promise<CadastroDoProfissional> {
  return chamar<CadastroDoProfissional>(`${BASE}/envio`, { metodo: 'POST', corpo });
}

/** Documento ou selfie (multipart); o mesmo tipo substitui o anterior. */
export function enviarDocumento(tipo: TipoDeDocumento, arquivo: File): Promise<DocumentoDoCadastro> {
  const corpo = new FormData();
  corpo.append('tipo', tipo);
  corpo.append('arquivo', arquivo);
  return chamar<DocumentoDoCadastro>(`${BASE}/documentos`, {
    metodo: 'POST',
    corpo,
    tempoLimiteMs: TEMPO_DO_UPLOAD_MS,
  });
}

export async function apagarDocumento(id: string): Promise<void> {
  await chamar<unknown>(`${BASE}/documentos/${encodeURIComponent(id)}`, { metodo: 'DELETE' });
}

/** Foto de trabalho (multipart) com o serviço e a legenda, se a pessoa escolheu. */
export function enviarFoto(arquivo: File, descricao: { servico?: string; legenda?: string }): Promise<FotoDoCadastro> {
  const corpo = new FormData();
  corpo.append('arquivo', arquivo);
  if (descricao.servico) {
    corpo.append('servico', descricao.servico);
  }
  if (descricao.legenda) {
    corpo.append('legenda', descricao.legenda);
  }
  return chamar<FotoDoCadastro>(`${BASE}/fotos`, { metodo: 'POST', corpo, tempoLimiteMs: TEMPO_DO_UPLOAD_MS });
}

export function descreverFoto(id: string, descricao: { servico: string | null; legenda: string | null }): Promise<FotoDoCadastro> {
  return chamar<FotoDoCadastro>(`${BASE}/fotos/${encodeURIComponent(id)}`, { metodo: 'PUT', corpo: descricao });
}

export async function apagarFoto(id: string): Promise<void> {
  await chamar<unknown>(`${BASE}/fotos/${encodeURIComponent(id)}`, { metodo: 'DELETE' });
}

/** URL assinada e curta (só do dono): abre a foto já enviada. */
export function urlDaFoto(id: string): Promise<UrlAssinada> {
  return chamar<UrlAssinada>(`${BASE}/fotos/${encodeURIComponent(id)}/url`);
}

/** URL assinada de 2 min do documento (só do dono). */
export function urlDoDocumento(id: string): Promise<UrlAssinada> {
  return chamar<UrlAssinada>(`${BASE}/documentos/${encodeURIComponent(id)}/url`);
}
