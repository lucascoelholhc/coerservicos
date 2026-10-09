import type { SessaoResposta, UsuarioDaSessao } from './tipos';

/*
 * Sessão só na memória da aba: o token de acesso nunca vai para localStorage, sessionStorage, cookie
 * legível ou log. Recarregar a página perde o token; quem recupera a sessão é o cookie HttpOnly do
 * refresh (renovação silenciosa ao abrir o app: dia 10, junto com o entrar).
 */
let token: string | null = null;
let usuario: UsuarioDaSessao | null = null;
let aoPerder: () => void = () => undefined;
const ouvintes = new Set<() => void>();

export function tokenAtual(): string | null {
  return token;
}

export function usuarioAtual(): UsuarioDaSessao | null {
  return usuario;
}

export function iniciarSessao(sessao: SessaoResposta): void {
  token = sessao.accessToken;
  usuario = sessao.usuario;
  avisar();
}

export function encerrarSessao(): void {
  token = null;
  usuario = null;
  avisar();
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
