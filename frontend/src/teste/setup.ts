import '@testing-library/jest-dom/vitest';
import { cleanup, configure } from '@testing-library/react';
import { afterEach, vi } from 'vitest';

// O jsdom não tem scrollIntoView (os testes de ambiente node não têm Element)
if (typeof Element !== 'undefined') {
  Element.prototype.scrollIntoView = vi.fn();
}

// O jsdom não tem showModal/close do <dialog>: só abre e fecha (o navegador cuida do foco e do fundo).
// Como no navegador, o evento "close" chega depois (numa tarefa), e só quando estava aberto.
if (typeof HTMLDialogElement !== 'undefined' && !HTMLDialogElement.prototype.showModal) {
  HTMLDialogElement.prototype.showModal = function showModal(this: HTMLDialogElement) {
    this.setAttribute('open', '');
  };
  HTMLDialogElement.prototype.close = function close(this: HTMLDialogElement) {
    if (!this.hasAttribute('open')) {
      return;
    }
    this.removeAttribute('open');
    setTimeout(() => this.dispatchEvent(new Event('close')), 0);
  };
}

// Telas que carregam vários recursos juntos (ex.: o cadastro) passam de 1 s com a suíte inteira em paralelo.
configure({ asyncUtilTimeout: 3_000 });

afterEach(() => {
  cleanup();
});
