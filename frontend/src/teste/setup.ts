import '@testing-library/jest-dom/vitest';
import { cleanup, configure } from '@testing-library/react';
import { afterEach, vi } from 'vitest';

// O jsdom não tem scrollIntoView (os testes de ambiente node não têm Element)
if (typeof Element !== 'undefined') {
  Element.prototype.scrollIntoView = vi.fn();
}

// Telas que carregam vários recursos juntos (ex.: o cadastro) passam de 1 s com a suíte inteira em paralelo.
configure({ asyncUtilTimeout: 3_000 });

afterEach(() => {
  cleanup();
});
