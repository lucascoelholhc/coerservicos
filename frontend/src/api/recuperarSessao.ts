import { renovarSessao } from './cliente';
import { iniciarVerificacao, terminarVerificacaoSemSessao } from './sessao';

let recuperacao: Promise<void> | null = null;

/**
 * Uma tentativa silenciosa de POST /api/auth/renovar ao abrir o app: o cookie HttpOnly do refresh
 * traz a sessão de volta depois de um F5. Sem cookie, 401, erro ou sem internet = visitante, sem
 * mensagem. Chamada no main.tsx, antes do primeiro render; repetir a chamada não repete o pedido.
 */
export function recuperarSessaoAoAbrir(): Promise<void> {
  recuperacao ??= (async () => {
    iniciarVerificacao();
    const resultado = await renovarSessao();
    if (resultado !== 'renovada') {
      terminarVerificacaoSemSessao();
    }
  })();
  return recuperacao;
}

/** Só para os testes: cada teste começa como se o app tivesse acabado de abrir. */
export function reiniciarRecuperacaoParaTestes(): void {
  recuperacao = null;
}
