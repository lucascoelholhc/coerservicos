import type { SessaoResposta, UsuarioDaSessao } from './tipos';

/*
 * Sessão só na memória da aba: o token de acesso nunca vai para localStorage, sessionStorage, cookie
 * legível ou log. Recarregar a página perde o token; quem recupera a sessão é o cookie HttpOnly do
 * refresh (renovação silenciosa ao abrir o app: dia 10, junto com o entrar).
 */
/** verificando: o app acabou de abrir e tenta recuperar a sessão pelo cookie; ninguém é mandado para /entrar. */
export type SituacaoDaSessao = 'verificando' | 'visitante' | 'logado';

let situacao: SituacaoDaSessao = 'visitante';
let token: string | null = null;
/** "Sair" pedido pela pessoa: a página privada vai para o Início (o mesmo destino do botão), não para /entrar. */
let saidaVoluntaria = false;
/** Muda a cada encerramento: renovação iniciada antes de "sair" não pode trazer a sessão de volta. */
let geracao = 0;
let usuario: UsuarioDaSessao | null = null;
let aoPerder: () => void = () => undefined;
const ouvintes = new Set<() => void>();

export function tokenAtual(): string | null {
  return token;
}

export function usuarioAtual(): UsuarioDaSessao | null {
  return usuario;
}

export function situacaoDaSessao(): SituacaoDaSessao {
  return situacao;
}

/** Ao abrir o app, antes da renovação silenciosa. */
export function iniciarVerificacao(): void {
  if (situacao === 'visitante') {
    situacao = 'verificando';
    avisar();
  }
}

/** A renovação silenciosa não trouxe sessão: visitante (sem mensagem). */
export function terminarVerificacaoSemSessao(): void {
  if (situacao === 'verificando') {
    situacao = 'visitante';
    avisar();
  }
}

export function iniciarSessao(sessao: SessaoResposta): void {
  saidaVoluntaria = false;
  situacao = 'logado';
  token = sessao.accessToken;
  usuario = sessao.usuario;
  avisar();
}

export function encerrarSessao(opcoes: { voluntaria?: boolean } = {}): void {
  saidaVoluntaria = opcoes.voluntaria ?? false;
  geracao += 1;
  situacao = 'visitante';
  token = null;
  usuario = null;
  avisar();
}

export function foiSaidaVoluntaria(): boolean {
  return saidaVoluntaria;
}

/** Depois de levar a pessoa ao Início, a próxima página privada volta a mandar para /entrar. */
export function esquecerSaidaVoluntaria(): void {
  saidaVoluntaria = false;
}

export function geracaoDaSessao(): number {
  return geracao;
}

export function ouvirSessao(ouvinte: () => void): () => void {
  ouvintes.add(ouvinte);
  return () => {
    ouvintes.delete(ouvinte);
  };
}

/** Quem leva para a tela de entrar quando a sessão acaba (o SessaoProvider registra). */
export function definirAoPerderSessao(acao: () => void): void {
  aoPerder = acao;
}

export function avisarSessaoPerdida(): void {
  aoPerder();
}

function avisar(): void {
  ouvintes.forEach((ouvinte) => ouvinte());
}
