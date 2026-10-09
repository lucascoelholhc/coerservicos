// @vitest-environment node
import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

/** docs/identidade-visual.md, seções 3 a 6 (com o ajuste de contraste da seção 3). */
const ESPERADOS: Record<string, string> = {
  '--carimbo': '#3B3DC4',
  '--carimbo-2': '#2D2F9F',
  '--carimbo-3': '#5254D6',
  '--carimbo-soft': '#E4E5FA',
  '--carimbo-txt': '#2D2F9F',
  '--sinal': '#FFCF33',
  '--sinal-2': '#F5BE0B',
  '--sinal-soft': '#FFF3C7',
  '--tinta': '#1C1F2E',
  '--tinta-2': '#4A4F63',
  '--tinta-3': '#6C7186',
  '--papel': '#EEF0F4',
  '--surface': '#FFFFFF',
  '--surface-2': '#E6E8EE',
  '--surface-3': '#D9DCE5',
  '--line': '#D9DCE5',
  '--line-2': '#A9AEBF',
  '--ok': '#1E7A4C',
  '--ok-soft': '#DDF0E5',
  '--ok-txt': '#17633D',
  '--bad': '#C2362C',
  '--bad-soft': '#FBE2DF',
  '--bad-txt': '#A52A21',
  '--warn': '#7A5300',
  '--warn-soft': '#FFF3C7',
  // Fora da tabela da identidade, vindos do CSS do protótipo (sem token igual ou quase igual)
  '--tinta-hover': '#33374A',
  '--carimbo-soft-2': '#D3D5F6',
  '--rodape-txt': '#B9BDCC',
  '--mascara': '#000000',
  '--r': '12px',
  '--r-sm': '6px',
  '--r-pilula': '999px',
  '--largura-max': '1160px',
  '--margem': '16px',
  '--margem-larga': '28px',
  '--altura-cabecalho': '64px',
  '--altura-menu': '66px',
};

/** #fff e #ffffff são a mesma cor (o stylelint exige a forma curta); compara sempre a longa, em maiúsculas. */
function normalizar(valor: string): string {
  const curto = /^#([0-9a-f])([0-9a-f])([0-9a-f])$/i.exec(valor);
  const longo = curto ? `#${curto[1]}${curto[1]}${curto[2]}${curto[2]}${curto[3]}${curto[3]}` : valor;
  return longo.toUpperCase();
}

function tokens(): Map<string, string> {
  const css = readFileSync(new URL('./tokens.css', import.meta.url), 'utf-8');
  const pares = [...css.matchAll(/(--[a-z0-9-]+)\s*:\s*([^;]+);/gi)];
  return new Map(pares.map((par) => [par[1] ?? '', (par[2] ?? '').trim()]));
}

describe('tokens.css (identidade "Dia carimbado")', () => {
  it.each(Object.entries(ESPERADOS))('%s = %s', (nome, valor) => {
    expect(normalizar(tokens().get(nome) ?? '')).toBe(normalizar(valor));
  });

  it('declara a família Archivo (hospedada no front) com o fallback da identidade', () => {
    expect(tokens().get('--fonte')).toBe('"Archivo Variable", "Segoe UI", "Roboto", "Arial", sans-serif');
  });
});
