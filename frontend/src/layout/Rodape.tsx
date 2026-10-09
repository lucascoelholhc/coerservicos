import { Link } from 'react-router';

import { SECOES_DO_INICIO } from '../caminhos';
import { Simbolo } from '../componentes/Logo/Logo';
import estilos from './Rodape.module.css';

export function Rodape() {
  return (
    <footer className={estilos.rodape}>
      <div className={`largura ${estilos.dentro}`}>
        <p className={estilos.marca}>
          <Simbolo claro tamanho={26} />
          COE Serviços, começando pelo Vale do Itajaí (SC)
        </p>
        <nav aria-label="Rodapé">
          <ul className={estilos.links}>
            <li>
              <Link to={SECOES_DO_INICIO.comoFunciona}>Como funciona</Link>
            </li>
            <li>
              <Link to={SECOES_DO_INICIO.categorias}>Categorias</Link>
            </li>
            <li>
              <Link to={SECOES_DO_INICIO.paraProfissionais}>Para profissionais</Link>
            </li>
          </ul>
        </nav>
      </div>
    </footer>
  );
}
