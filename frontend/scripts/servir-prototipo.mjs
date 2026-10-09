/**
 * Serve docs/prototipo (protótipo "Dia carimbado", ef7d397) por HTTP em http://localhost:4174, só
 * para olhar e para o `npm run comparar`. Só leitura, só arquivos dentro da pasta, só localhost.
 * Não roda no build nem nos testes do front.
 */
import { createReadStream, statSync } from 'node:fs';
import { createServer } from 'node:http';
import { extname, join, normalize, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const RAIZ = resolve(fileURLToPath(new URL('../../docs/prototipo/', import.meta.url)));
const PORTA = 4174;
const TIPOS = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.md': 'text/plain; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
};

function arquivoPedido(url) {
  const caminho = decodeURIComponent(new URL(url, 'http://localhost').pathname);
  const relativo = normalize(caminho === '/' ? '/index.html' : caminho);
  const completo = resolve(join(RAIZ, relativo));
  return completo.startsWith(RAIZ + sep) ? completo : null;
}

createServer((pedido, resposta) => {
  if (pedido.method !== 'GET' && pedido.method !== 'HEAD') {
    resposta.writeHead(405).end();
    return;
  }
  const arquivo = arquivoPedido(pedido.url ?? '/');
  try {
    if (!arquivo || !statSync(arquivo).isFile()) throw new Error('não é arquivo');
  } catch {
    resposta.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' }).end('Não encontrado');
    return;
  }
  resposta.writeHead(200, { 'Content-Type': TIPOS[extname(arquivo)] ?? 'application/octet-stream' });
  createReadStream(arquivo).pipe(resposta);
}).listen(PORTA, '127.0.0.1', () => {
  console.log(`Protótipo (ef7d397) em http://localhost:${PORTA}`);
});
