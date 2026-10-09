import { useEffect, type ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router';

import { esquecerSaidaVoluntaria, foiSaidaVoluntaria } from '../api/sessao';
import { useSessao, useSituacaoDaSessao } from '../api/SessaoProvider';
import type { Papel } from '../api/tipos';
import { caminhoDeEntrar } from '../api/voltar';
import { CAMINHOS } from '../caminhos';
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
    if (foiSaidaVoluntaria()) {
      return <IrParaOInicio />;
    }
    return <Navigate replace to={caminhoDeEntrar(`${local.pathname}${local.search}${local.hash}`)} />;
  }
  if (papeis && !papeis.some((papel) => usuario.papeis.includes(papel))) {
    return <SemAcesso />;
  }
  return children;
}

/** Depois de "Sair": o Início, como o botão pediu; e a próxima página privada volta a mandar para /entrar. */
function IrParaOInicio() {
  useEffect(() => {
    esquecerSaidaVoluntaria();
  }, []);
  return <Navigate replace to={CAMINHOS.inicio} />;
}
