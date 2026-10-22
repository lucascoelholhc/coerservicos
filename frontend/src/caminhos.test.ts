import { describe, expect, it } from 'vitest';

import { CAMINHOS, caminhoDaBusca, SECOES_DO_INICIO } from './caminhos';

describe('caminhos do front (um lugar só para menu, herói, rodapé e testes)', () => {
  it('rotas fixas', () => {
    expect(CAMINHOS).toEqual({
      inicio: '/',
      busca: '/busca',
      entrar: '/entrar',
      criarConta: '/criar-conta',
      criarContaCliente: '/criar-conta/cliente',
      criarContaProfissional: '/criar-conta/profissional',
      cadastro: '/cadastro',
      adminVerificacao: '/admin/verificacao',
      conta: '/conta',
      confirmarCelular: '/conta/confirmar-celular',
      codigoNoLogin: '/conta/codigo-no-login',
      trocarSenha: '/conta/trocar-senha',
      recolocarContato: '/conta/recolocar-contato',
      esqueciSenha: '/esqueci-senha',
      redefinirSenha: '/redefinir-senha',
      confirmarEmail: '/confirmar-email',
      provarEmail: '/provar-email',
      termos: '/termos',
      privacidade: '/privacidade',
      comoFunciona: '/como-funciona',
      paraProfissionais: '/para-profissionais',
    });
    expect(SECOES_DO_INICIO).toEqual({
      categorias: '/#categorias',
      comoFunciona: '/#como-funciona',
      paraProfissionais: '/#para-profissionais',
    });
  });

  it.each([
    [{}, '/busca'],
    [{ profissao: 'pintor' }, '/busca?profissao=pintor'],
    [{ cidade: '4202404' }, '/busca?cidade=4202404'],
    [{ profissao: 'pintor', cidade: '4202404' }, '/busca?profissao=pintor&cidade=4202404'],
    [{ profissao: '', cidade: '' }, '/busca'],
  ])('busca com %o = %s', (filtros, esperado) => {
    expect(caminhoDaBusca(filtros)).toBe(esperado);
  });
});
