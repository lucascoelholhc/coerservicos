/**
 * Gera UMA VEZ as ilustrações das 5 profissões a partir do protótipo "Dia carimbado"
 * (docs/prototipo/js/cenas.js, commit ef7d397): cena(profissao, CENA_CAT[profissao], 0).
 *
 * Não roda no build, nos testes nem no `npm run dev`: os SVGs gerados ficam no git em
 * src/assets/ilustracoes/ e o React só importa esses arquivos. Rodar de novo só quando o protótipo
 * for copiado de outro commit:  node scripts/gerar-ilustracoes.mjs
 *
 * As cores dentro dos SVGs são do desenho (tijolo, céu, grama) e ficam fora dos tokens: são
 * imagens, como uma foto, e o stylelint só olha o CSS.
 */
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import vm from 'node:vm';

const ORIGEM = new URL('../../docs/prototipo/js/cenas.js', import.meta.url);
const DESTINO = new URL('../src/assets/ilustracoes/', import.meta.url);
const PROFISSOES = ['pedreiro', 'pintor', 'eletricista', 'jardineiro', 'diarista'];

const contexto = vm.createContext({});
vm.runInContext(`${readFileSync(ORIGEM, 'utf-8')}\nglobalThis.__cena = cena; globalThis.__cat = CENA_CAT;`, contexto);

mkdirSync(DESTINO, { recursive: true });
for (const profissao of PROFISSOES) {
  const svg = contexto.__cena(profissao, contexto.__cat[profissao], 0);
  // Arquivo solto: precisa do xmlns; aria-hidden/focusable saem (quem usa é um <img alt="">).
  const arquivo = svg.replace(
    '<svg viewBox="0 0 400 300" preserveAspectRatio="xMidYMid slice" aria-hidden="true" focusable="false">',
    '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 400 300" preserveAspectRatio="xMidYMid slice">',
  );
  if (arquivo === svg) {
    throw new Error(`O cabeçalho do SVG de ${profissao} mudou no protótipo: revise o script.`);
  }
  writeFileSync(new URL(`${profissao}.svg`, DESTINO), `${arquivo}\n`);
  console.log(`${profissao}.svg (${contexto.__cat[profissao]})`);
}
