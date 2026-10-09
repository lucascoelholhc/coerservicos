const ORIGEM_FALSA = 'http://coe.invalido';
const INICIO = '/';

/**
 * Caminho de volta depois de entrar: só caminho interno ("/..."), nunca outro site. Recusa "//",
 * barra invertida, esquema, caracteres de controle, codificação que esconde "//" e o próprio /entrar
 * (para não ficar em ciclo). Qualquer coisa estranha volta ao início.
 */
export function caminhoInternoSeguro(valor: string | null | undefined): string {
  if (!valor?.startsWith('/') || valor.startsWith('//') || valor.includes('\\') || temControle(valor)) {
    return INICIO;
  }
  // Começando por uma só "/", o caminho é relativo à origem: o URL só normaliza ("/a/../b" = "/b").
  const url = new URL(valor, ORIGEM_FALSA);
  let caminhoDecodificado: string;
  try {
    caminhoDecodificado = decodeURIComponent(url.pathname);
  } catch {
    return INICIO;
  }
  if (caminhoDecodificado.startsWith('//') || ehATelaDeEntrar(caminhoDecodificado)) {
    return INICIO;
  }
  return `${url.pathname}${url.search}${url.hash}`;
}

/** "/entrar", "/entrar/", "/ENTRAR" ou "/%65ntrar": voltar para a própria tela de entrar faria um ciclo. */
function ehATelaDeEntrar(caminhoDecodificado: string): boolean {
  return caminhoDecodificado.toLowerCase().replace(/\/+$/, '') === '/entrar';
}

/** Caracteres de controle (0x00 a 0x1F e 0x7F) não têm lugar num caminho de volta. */
function temControle(valor: string): boolean {
  return [...valor].some((caractere) => {
    const codigo = caractere.charCodeAt(0);
    return codigo < 0x20 || codigo === 0x7f;
  });
}

/** /entrar com o caminho atual em "voltar" (já conferido e codificado). */
export function caminhoDeEntrar(atual: string): string {
  return `/entrar?voltar=${encodeURIComponent(caminhoInternoSeguro(atual))}`;
}
