/*
 * Tipos das respostas da API, escritos à mão iguais aos DTOs do backend (OpenAPI fica para depois).
 */

/** GET /api/publico/catalogo (CatalogoResposta). */
export interface Catalogo {
  areas: AreaDoCatalogo[];
  cidades: CidadeDoCatalogo[];
}

export interface AreaDoCatalogo {
  codigo: string;
  nome: string;
  profissoes: ProfissaoDoCatalogo[];
}

/** O front escolhe a ilustração pelo {@link codigo}, não pelo {@link icone}. */
export interface ProfissaoDoCatalogo {
  codigo: string;
  nome: string;
  nomePlural: string;
  icone: string;
  servicos: { nome: string }[];
}

export interface CidadeDoCatalogo {
  codigoIbge: number;
  nome: string;
  uf: string;
}

export type TaxaPagaPor = 'CLIENTE' | 'PROFISSIONAL';

export type Papel = 'CLIENTE' | 'PROFISSIONAL' | 'ADMIN';

/** GET /api/publico/regras: comissão como fração ("0.1000") e prazo em ISO-8601 ("PT12H"). */
export interface Regras {
  comissao: string;
  prazoLiberacao: string;
  taxaPagaPor: TaxaPagaPor;
  /** Versão dos termos em vigor: o cadastro manda esta versão como aceita. */
  versaoTermos: string;
}

/** GET /api/contas/eu (MinhaContaResponse): celular e e-mail como a API devolve (podem faltar com contato pendente). */
export interface MinhaConta {
  id: string;
  nome: string;
  papeis: Papel[];
  email: string | null;
  celular: string | null;
  celularConfirmado: boolean;
  emailConfirmado: boolean;
  contatoPendente: boolean;
  /** Código por SMS no login escolhido pela pessoa (para o ADMIN o código é sempre obrigatório). */
  mfaAtivo: boolean;
}

/** Resposta do entrar e do renovar (SessaoResponse). */
export interface SessaoResposta {
  accessToken: string;
  expiraEm: string;
  usuario: UsuarioDaSessao;
}

export interface UsuarioDaSessao {
  id: string;
  nome: string;
  papeis: string[];
  contatoPendente: boolean;
}

/** Situação do cadastro do profissional (coluna profissional.status). */
export type SituacaoDoCadastro = 'rascunho' | 'em_analise' | 'correcao_pedida' | 'ativo' | 'suspenso' | 'recusado';

/** As 11 etapas, na ordem do protótipo (as chaves de CadastroResposta.etapas). */
export type EtapaDoCadastro =
  | 'trabalho'
  | 'dados'
  | 'documentos'
  | 'servicos'
  | 'experiencia'
  | 'valor'
  | 'onde'
  | 'dias'
  | 'fotos'
  | 'sobre'
  | 'pix';

export type TipoDeDocumento = 'rg_frente' | 'rg_verso' | 'cnh' | 'selfie' | 'certificado_nr10';

export type Ferramentas = 'sim' | 'parte' | 'nao';

export type TipoDeChavePix = 'cpf' | 'celular' | 'email' | 'aleatoria';

export interface DocumentoDoCadastro {
  id: string;
  tipo: TipoDeDocumento;
  status: string;
}

export interface FotoDoCadastro {
  id: string;
  servico: string | null;
  legenda: string | null;
}

export interface ServicoDoCadastro {
  profissao: string;
  nome: string;
}

/**
 * GET /api/profissional/cadastro (CadastroResposta): o rascunho do próprio cadastro. CPF e chave Pix
 * chegam só mascarados; dinheiro em texto ("280.00"); cidades pelo código IBGE; dias 0 = domingo.
 */
export interface CadastroDoProfissional {
  status: SituacaoDoCadastro;
  etapaAtual: number;
  etapas: Record<EtapaDoCadastro, boolean>;
  nome: string;
  profissao: string | null;
  outrasProfissoes: string[];
  cpf: string | null;
  dataNascimento: string | null;
  documentos: DocumentoDoCadastro[];
  servicos: ServicoDoCadastro[];
  experienciaAnos: number | null;
  ferramentas: Ferramentas | null;
  mei: boolean;
  valorDiaria: string | null;
  valorMeiaDiaria: string | null;
  horario: string | null;
  cidade: number | null;
  raioKm: number | null;
  outrasCidades: number[];
  dias: number[];
  fotos: FotoDoCadastro[];
  frases: string[];
  bio: string | null;
  chavePixTipo: TipoDeChavePix | null;
  chavePix: string | null;
  /** O que a equipe pediu para corrigir (só com status correcao_pedida). */
  motivoCorrecao: string | null;
}

/** URL assinada e curta de um arquivo do próprio cadastro. */
export interface UrlAssinada {
  url: string;
  expiraEm: string;
}

/** Situação do certificado de NR-10 (profissional.nr10_status). */
export type SituacaoDoNr10 = 'nao_tem' | 'em_analise' | 'verificado' | 'recusado';

/** GET /api/admin/verificacao (VerificacaoResposta.Fila). */
export interface FilaDeVerificacao {
  itens: ItemDaFila[];
  temMais: boolean;
}

export interface ItemDaFila {
  id: string;
  nome: string;
  /** Código da profissão principal (o nome vem do catálogo). */
  profissao: string;
  enviadoEm: string;
  nr10: boolean;
}

/** GET /api/admin/verificacao/{id}: o CPF completo (auditado) e o cadastro como o profissional vê. */
export interface DetalheDaVerificacao {
  id: string;
  cpf: string;
  celularConfirmado: boolean;
  nr10: SituacaoDoNr10;
  enviadoEm: string;
  cadastro: CadastroDoProfissional;
}

/** O checklist da RN12 (PedidosDaVerificacao.Itens). */
export interface ItensDaConferencia {
  cpf: boolean;
  documento: boolean;
  selfie: boolean;
  fotos: boolean;
  texto: boolean;
}

export interface DecisaoDaVerificacao {
  id: string;
  status: SituacaoDoCadastro;
  nr10: SituacaoDoNr10;
}
