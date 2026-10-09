import { Link } from 'react-router';

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
              <Link to="/#como-funciona">Como funciona</Link>
            </li>
            <li>
              <Link to="/#categorias">Categorias</Link>
            </li>
            <li>
              <Link to="/#para-profissionais">Para profissionais</Link>
            </li>
          </ul>
        </nav>
      </div>
    </footer>
  );
}
