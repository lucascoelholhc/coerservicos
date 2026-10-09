import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router';

import { useSessao, useSituacaoDaSessao } from '../api/SessaoProvider';
import type { Papel } from '../api/tipos';
import { caminhoDeEntrar } from '../api/voltar';
import { SemAcesso } from '../paginas/sem-acesso/SemAcesso';
import estilos from './RotaPrivada.module.css';

interface RotaPrivadaProps {
  /** Papéis que podem ver a página; sem a lista, qualquer pessoa logada. */
  papeis?: readonly Papel[];
  children: ReactNode;
}

/**
 * Página só para quem entrou. Enquanto o app verifica a sessão (logo depois de abrir), mostra
 * "Carregando…" e nunca pisca a tela de entrar; sem sessão, leva a /entrar?voltar=<onde estava>.
 */
export function RotaPrivada({ papeis, children }: RotaPrivadaProps) {
  const situacao = useSituacaoDaSessao();
  const usuario = useSessao();
  const local = useLocation();

  if (situacao === 'verificando') {
    return (
      <section className={`largura ${estilos.carregando}`}>
        <p role="status">Carregando…</p>
      </section>
    );
  }
  if (!usuario) {
    return <Navigate replace to={caminhoDeEntrar(`${local.pathname}${local.search}${local.hash}`)} />;
  }
  if (papeis && !papeis.some((papel) => usuario.papeis.includes(papel))) {
    return <SemAcesso />;
  }
  return children;
}
