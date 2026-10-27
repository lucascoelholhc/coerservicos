import { describe, expect, it } from 'vitest';

import {
  CAMINHOS,
  caminhoDaBusca,
  caminhoDaConversa,
  caminhoDeContratar,
  caminhoDeConversarCom,
  caminhoDoContrato,
  caminhoDoPerfil,
  SECOES_DO_INICIO,
} from './caminhos';

describe('caminhos do front (um lugar só para menu, herói, rodapé e testes)', () => {
  it('rotas fixas', () => {
    expect(CAMINHOS).toEqual({
      inicio: '/',
      busca: '/busca',
      perfil: '/profissionais',
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
      contratar: '/contratar',
      contratos: '/contratos',
      mensagens: '/mensagens',
      conversar: '/conversar',
      profissionalHoje: '/profissional',
      profissionalAgenda: '/profissional/agenda',
      profissionalDinheiro: '/profissional/dinheiro',
      profissionalPerfil: '/profissional/perfil',
      profissionalContratos: '/profissional/contratos',
      profissionalTerminar: '/profissional/terminar',
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

  it('perfil público pelo id, codificado', () => {
    expect(caminhoDoPerfil('00000000-0000-0000-0000-0000000d0001')).toBe(
      '/profissionais/00000000-0000-0000-0000-0000000d0001',
    );
    expect(caminhoDoPerfil('a/b')).toBe('/profissionais/a%2Fb');
  });
  it('caminhos da área do cliente (FE-05) com o id escapado', () => {
    expect(caminhoDeContratar('p 1')).toBe('/contratar/p%201');
    expect(caminhoDoContrato('c1')).toBe('/contratos/c1');
    expect(caminhoDaConversa('m/1')).toBe('/mensagens/m%2F1');
    expect(caminhoDeConversarCom('p1')).toBe('/conversar/p1');
  });
});
