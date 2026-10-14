/*
 * Links do e-mail (confirmar e-mail, redefinir senha, provar posse) trazem o token no fragmento
 * (#token=...), que o navegador nunca manda ao servidor. A página lê o token na primeira renderização
 * (sem efeito colateral, seguro no StrictMode) e limpa o fragmento antes de qualquer chamada à API; o
 * token fica só na memória e vai no corpo do POST, nunca na URL.
 */

/** O token do fragmento, sem mexer na barra de endereço. */
export function lerTokenDoFragmento(): string | null {
  const token = new URLSearchParams(window.location.hash.slice(1)).get('token');
  return token ? token : null;
}

/** Tira o fragmento da barra de endereço (e do histórico), mantendo caminho, busca e o estado do Router. */
export function limparFragmento(): void {
  const { hash, pathname, search } = window.location;
  if (hash) {
    window.history.replaceState(window.history.state, '', `${pathname}${search}`);
  }
}

/** Lê e limpa de uma vez. */
export function tirarTokenDoFragmento(): string | null {
  const token = lerTokenDoFragmento();
  limparFragmento();
  return token;
}
