import { Outlet } from 'react-router';

import { Cabecalho } from './Cabecalho';
import { MenuInferior } from './MenuInferior';
import estilos from './Moldura.module.css';
import { Rodape } from './Rodape';

/** Esqueleto de toda tela pública: pular para o conteúdo, cabeçalho, conteúdo, rodapé e menu. */
export function Moldura() {
  return (
    <div className={estilos.moldura}>
      <a className={estilos.pular} href="#conteudo">
        Pular para o conteúdo
      </a>
      <Cabecalho />
      <main id="conteudo" tabIndex={-1} className={estilos.principal}>
        <Outlet />
      </main>
      <Rodape />
      <MenuInferior />
    </div>
  );
}
