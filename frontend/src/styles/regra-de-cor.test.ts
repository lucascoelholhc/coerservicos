// @vitest-environment node
import { fileURLToPath } from 'node:url';
import stylelint from 'stylelint';
import { describe, expect, it } from 'vitest';

const CONFIG = fileURLToPath(new URL('../../stylelint.config.mjs', import.meta.url));

async function avisos(codigo: string, arquivo: string): Promise<string[]> {
  const resultado = await stylelint.lint({ code: codigo, codeFilename: arquivo, configFile: CONFIG });
  return (resultado.results[0]?.warnings ?? []).map((aviso) => aviso.rule);
}

describe('cor só por variável do tokens.css', () => {
  it('hex num componente quebra o lint', async () => {
    expect(await avisos('.caixa {\n  color: #fff;\n}\n', 'src/componentes/X/X.module.css')).toContain('color-no-hex');
  });

  it('nome de cor num componente quebra o lint', async () => {
    expect(await avisos('.caixa {\n  color: white;\n}\n', 'src/componentes/X/X.module.css')).toContain('color-named');
  });

  it('variável do tokens.css passa', async () => {
    expect(await avisos('.caixa {\n  color: var(--tinta);\n}\n', 'src/componentes/X/X.module.css')).toEqual([]);
  });

  it('o tokens.css pode ter hex', async () => {
    expect(await avisos(':root {\n  --tinta: #1c1f2e;\n}\n', 'src/styles/tokens.css')).not.toContain('color-no-hex');
  });
});
