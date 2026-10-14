import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, describe, expect, it } from 'vitest';

import { encerrarSessao, iniciarSessao } from '../api/sessao';
import { CAMINHOS } from '../caminhos';
import { semViolacoes } from '../teste/axe';
import { SESSAO_PADRAO } from '../teste/dadosDaApi';
import { liberadaComContatoPendente, ROTAS_LIBERADAS_COM_CONTATO_PENDENTE } from './liberadasComContatoPendente';
import { RotaPrivada } from './RotaPrivada';

const PENDENTE = { ...SESSAO_PADRAO, usuario: { ...SESSAO_PADRAO.usuario, contatoPendente: true } };

function abrir(caminho: string) {
  return render(
    <MemoryRouter initialEntries={[caminho]}>
      <Routes>
        <Route
          path="*"
          element={
            <RotaPrivada>
              <h1>Tela privada</h1>
            </RotaPrivada>
          }
        />
      </Routes>
    </MemoryRouter>,
  );
}

describe('RotaPrivada com contato pendente (RN61)', () => {
  afterEach(() => encerrarSessao());

  it('a lista de liberadas é igual aos endpoints liberados no backend', () => {
    expect(ROTAS_LIBERADAS_COM_CONTATO_PENDENTE).toEqual([
      '/conta',
      '/conta/recolocar-contato',
      '/conta/trocar-senha',
      '/conta/confirmar-celular',
    ]);
  });

  it.each(['/conta', '/conta/recolocar-contato', '/conta/trocar-senha', '/conta/confirmar-celular', '/conta/'])(
    'liberada (%s): a tela abre',
    (caminho) => {
      iniciarSessao(PENDENTE);
      abrir(caminho);

      expect(screen.getByRole('heading', { level: 1, name: 'Tela privada' })).toBeInTheDocument();
    },
  );

  it('código no login (MFA): bloqueada com a nota da API e o caminho para recolocar', async () => {
    iniciarSessao(PENDENTE);
    const { container } = abrir(CAMINHOS.codigoNoLogin);

    expect(screen.queryByRole('heading', { name: 'Tela privada' })).not.toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 1, name: 'Falta um contato na sua conta' })).toBeInTheDocument();
    expect(screen.getByText('Cadastre e confirme um novo celular ou e-mail para continuar usando a COE.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Recolocar contato' })).toHaveAttribute('href', '/conta/recolocar-contato');
    await semViolacoes(container);
  });

  it('negar por padrão: uma rota privada nova, fora da lista, nasce bloqueada', () => {
    iniciarSessao(PENDENTE);
    abrir('/contratos/novo');

    expect(liberadaComContatoPendente('/contratos/novo')).toBe(false);
    expect(screen.getByRole('heading', { level: 1, name: 'Falta um contato na sua conta' })).toBeInTheDocument();
  });

  it('sem contato pendente: nada é bloqueado', () => {
    iniciarSessao(SESSAO_PADRAO);
    abrir(CAMINHOS.codigoNoLogin);

    expect(screen.getByRole('heading', { level: 1, name: 'Tela privada' })).toBeInTheDocument();
  });
});
