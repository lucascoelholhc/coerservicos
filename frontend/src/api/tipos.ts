import type { EstadoDaDiaria } from '../componentes/StatusDiaria/StatusDiaria';

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

/** Cidade nas respostas públicas da busca (BuscaResposta.Cidade). */
export interface CidadeDaBusca {
  codigoIbge: number;
  nome: string;
  uf: string;
}

/** Um profissional na lista da busca (BuscaResposta.Item). Nome sempre "Primeiro S.", nunca contato. */
export interface ProfissionalNaBusca {
  id: string;
  nome: string;
  /** Código da profissão principal (o nome vem do catálogo). */
  profissao: string;
  outrasProfissoes: string[];
  valorDiaria: string;
  valorMeiaDiaria: string | null;
  experienciaAnos: number;
  cidade: CidadeDaBusca;
  /** Só quando a busca tem cidade: da cidade base do profissional até ela. */
  distanciaKm: string | number | null;
  /** "AAAA-MM-DD" ou null (sem dia livre nas próximas quatro semanas). */
  proximoDiaLivre: string | null;
  seloNr10: boolean;
  novoNaCoe: boolean;
}

/** GET /api/publico/profissionais (BuscaResposta.Busca): 20 por página. */
export interface ResultadoDaBusca {
  itens: ProfissionalNaBusca[];
  temMais: boolean;
}

/** Foto do portfólio com URL assinada curta (o bucket é privado). */
export interface FotoDoPerfil {
  id: string;
  servico: string | null;
  legenda: string | null;
  url: string;
  expiraEm: string;
}

/** GET /api/publico/profissionais/{id} (BuscaResposta.Perfil). */
export interface PerfilPublico {
  id: string;
  nome: string;
  profissao: string;
  outrasProfissoes: string[];
  servicos: { profissao: string; nome: string }[];
  experienciaAnos: number;
  /** "sim", "parte" ou "nao" (como no cadastro). */
  ferramentas: string;
  horario: string;
  valorDiaria: string;
  valorMeiaDiaria: string | null;
  cidade: CidadeDaBusca;
  raioKm: number;
  outrasCidades: CidadeDaBusca[];
  /** Dias da semana em que trabalha: 0 = domingo ... 6 = sábado. */
  diasDaSemana: number[];
  /** Dias livres ("AAAA-MM-DD") das quatro semanas a partir de amanhã. */
  diasLivres: string[];
  sobre: string;
  seloNr10: boolean;
  avaliacoes: { total: number; nota: string | number | null; novoNaCoe: boolean };
  fotos: FotoDoPerfil[];
}

/** Contratação (DOM-06/07, FE-05): iguais aos DTOs de /api/contratos. Valores em texto ("440.00"). */
export type StatusDoContrato = 'aguardando_pagamento' | 'pago' | 'em_andamento' | 'concluido' | 'cancelado' | 'expirado';

export interface ProfissionalDoContrato {
  id: string;
  /** "Primeiro S."; o nome completo vem em `contato`, depois do pagamento. */
  nome: string;
  profissao: string;
}

export interface DiaDoContrato {
  id: string;
  data: string;
  tipo: 'inteira' | 'meia';
  status: EstadoDaDiaria;
  valor: string;
  /** Quando o dia aguardando é liberado sozinho; nulo fora de "aguardando". */
  autoLiberaEm: string | null;
}

export interface Contrato {
  id: string;
  numero: number;
  status: StatusDoContrato;
  profissionalId: string;
  profissional: ProfissionalDoContrato;
  cidade: CidadeDaBusca;
  cep: string | null;
  dias: DiaDoContrato[];
  servicos: string[];
  descricao: string | null;
  material: 'cliente_tem' | 'cliente_compra' | 'combinar';
  valorServicos: string;
  valorComissao: string;
  valorTotal: string;
  pagamentoExpiraEm: string;
  endereco: { logradouro: string; numero: string; bairro: string | null; complemento: string | null } | null;
  contato: { nome: string; celular: string } | null;
}

export interface ResumoDoContrato {
  id: string;
  numero: number;
  status: StatusDoContrato;
  profissional: ProfissionalDoContrato;
  dias: { id: string; data: string; status: EstadoDaDiaria }[];
  valorTotal: string;
  criadoEm: string;
}

export interface NovoContrato {
  profissionalId: string;
  dias: { data: string; tipo: 'inteira' | 'meia' }[];
  servicos: string[];
  descricao?: string;
  material: Contrato['material'];
  endereco: {
    cep: string;
    cidade: number;
    logradouro: string;
    numero: string;
    bairro?: string;
    complemento?: string;
  };
  versaoTermosAceita: string;
}

export interface Pagamento {
  cobrancaId: string;
  metodo: 'pix' | 'cartao';
  status: 'pendente' | 'confirmada' | 'falhou' | 'expirada' | 'estornada';
  valor: string;
  pixCopiaECola: string | null;
  linkPagamento: string | null;
  expiraEm: string;
  /** Gateway falso (local e testes): mostra "Simular pagamento". */
  simulavel: boolean;
}

/** Chat (DOM-05): iguais aos DTOs de /api/conversas. */
export interface ResumoDaConversa {
  id: string;
  profissionalId: string;
  outroNome: string;
  souProfissional: boolean;
  ultimaMensagemEm: string | null;
  naoLidas: number;
  contatoLiberado: boolean;
}

export interface MensagemDoChat {
  id: string;
  minha: boolean;
  texto: string;
  bloqueada: boolean;
  motivos: string[];
  lida: boolean;
  criadoEm: string;
}

export interface EnvioDaMensagem {
  mensagem: MensagemDoChat;
  aviso: { tentativa: number; limite: number; contaEmAnalise: boolean } | null;
}

/** Execução da diária (DOM-08): resposta de cheguei, terminei e aprovar. */
export interface DiariaExecutada {
  id: string;
  contratoId: string;
  data: string;
  tipo: 'inteira' | 'meia';
  status: EstadoDaDiaria;
  chegouEm: string | null;
  terminouEm: string | null;
  autoLiberaEm: string | null;
  liberadaEm: string | null;
  liberadaPor: 'cliente' | 'automatica' | 'admin' | null;
}

/** Fotos do "Terminei" por URL assinada e curta, e o recado do profissional. */
export interface FotosDoDia {
  fotos: { id: string; url: string; expiraEm: string }[];
  recado: string | null;
}

/** Uma diária na área do profissional (GET /api/profissional/diarias). */
export interface DiariaDoProfissional {
  id: string;
  contratoId: string;
  numero: number;
  data: string;
  tipo: 'inteira' | 'meia';
  status: EstadoDaDiaria;
  /** O que o profissional recebe pelo dia (com a comissão e a taxa do contrato). */
  repasse: string;
  /** "Primeiro S."; o nome completo e o celular só no contrato, depois do pagamento. */
  cliente: string;
  cidade: { nome: string; uf: string };
  servicos: string[];
  chegouEm: string | null;
  terminouEm: string | null;
  autoLiberaEm: string | null;
}

/** GET /api/profissional/dinheiro: liberado (livro-caixa), o que vai cair e o que está em reclamação. */
export interface DinheiroDoProfissional {
  liberado: string;
  vaiCair: string;
  emReclamacao: string;
}

/** GET /api/profissional/agenda: dias da semana (0 = domingo) e folgas até `ate`. */
export interface AgendaDoProfissional {
  perfilId: string;
  diasDaSemana: number[];
  folgas: string[];
  ate: string;
}
