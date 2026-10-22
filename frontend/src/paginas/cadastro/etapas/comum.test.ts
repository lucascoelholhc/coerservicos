import { describe, expect, it } from 'vitest';

import { ErroDaApi } from '../../../api/erros';
import { enderecoSeguro, mensagemDoEnvio } from './comum';

describe('enderecoSeguro (link da URL assinada)', () => {
  it.each([
    ['https://coe-arquivos.s3.sa-east-1.amazonaws.com/portfolio/a.jpg?X-Amz-Signature=1', true],
    ['http://localhost:8333/coe/portfolio/a.jpg?assinatura', true],
    ['http://127.0.0.1:8333/coe/a.jpg', true],
    ['http://bucket.exemplo/a.jpg', false],
    ['javascript:alert(1)', false],
    ['data:image/png;base64,AAAA', false],
    ['//evil.exemplo/a.jpg', false],
    ['não é url', false],
  ])('%s = %s', (url, esperado) => {
    expect(enderecoSeguro(url)).toBe(esperado);
  });
});

describe('mensagemDoEnvio', () => {
  it('tempo esgotado pede para conferir antes de mandar de novo', () => {
    expect(mensagemDoEnvio(new ErroDaApi('tempo'))).toBe(
      'A COE demorou para responder. Tente de novo. Confira se a foto já aparece antes de mandar de novo.',
    );
  });

  it('outro erro: a mensagem da API; falha qualquer: a mensagem genérica', () => {
    expect(mensagemDoEnvio(new ErroDaApi('problema', { mensagem: 'Foto grande demais.' }))).toBe('Foto grande demais.');
    expect(mensagemDoEnvio(new Error('x'))).toBe('A COE está com um problema agora. Tente de novo daqui a pouco.');
  });
});
