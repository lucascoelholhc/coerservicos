import { render } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { Avatar, CORES_DO_AVATAR, corDoAvatar, iniciais } from './Avatar';

describe('Avatar', () => {
  it('iniciais das duas primeiras palavras, como no protótipo', () => {
    expect(iniciais('Valdir Schmitt')).toBe('VS');
    expect(iniciais('Ana')).toBe('A');
    expect(iniciais('  Bruno   Costa ')).toBe('BC');
  });

  it('cor determinística: o mesmo nome sempre tem a mesma cor, sempre uma cor dos tokens', () => {
    expect(corDoAvatar('Valdir Schmitt')).toBe(corDoAvatar('Valdir Schmitt'));
    const cores = new Set(
      ['Ana', 'Bruno Costa', 'Carla', 'Diego Lima', 'Eva', 'Fábio Reis', 'Gil', 'Hugo'].map(corDoAvatar),
    );
    for (const cor of cores) {
      expect(CORES_DO_AVATAR).toContain(cor);
    }
    expect(cores.size).toBeGreaterThan(1);
  });

  it('as cores possíveis são só tokens com contraste AA para texto branco', () => {
    expect(CORES_DO_AVATAR).toEqual(['carimbo', 'carimbo-2', 'tinta', 'tinta-2', 'ok', 'bad', 'warn']);
  });

  it('escondido do leitor de tela (o nome está ao lado), com tamanho e cor no atributo', () => {
    const { container } = render(<Avatar nome="Valdir Schmitt" tamanho="sm" />);
    const avatar = container.firstElementChild;

    expect(avatar).toHaveAttribute('aria-hidden', 'true');
    expect(avatar).toHaveTextContent('VS');
    expect(avatar).toHaveAttribute('data-tamanho', 'sm');
    expect(avatar).toHaveAttribute('data-cor', corDoAvatar('Valdir Schmitt'));
  });

  it('tamanho padrão md', () => {
    const { container } = render(<Avatar nome="Ana" />);

    expect(container.firstElementChild).toHaveAttribute('data-tamanho', 'md');
  });
});
