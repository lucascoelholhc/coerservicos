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
              <a href="/#como-funciona">Como funciona</a>
            </li>
            <li>
              <a href="/#categorias">Categorias</a>
            </li>
            <li>
              <a href="/#para-profissionais">Para profissionais</a>
            </li>
          </ul>
        </nav>
      </div>
    </footer>
  );
}
