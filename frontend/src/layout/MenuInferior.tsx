import { NavLink } from 'react-router';

import { Icone } from '../icones/Icone';
import estilos from './MenuInferior.module.css';
import { MENU_VISITANTE } from './navegacao';

/** Menu fixo embaixo, só no celular (some a partir de 900 px e o menu sobe para o cabeçalho). */
export function MenuInferior() {
  return (
    <nav aria-label="Menu" className={estilos.menu}>
      <ul>
        {MENU_VISITANTE.map((item) => (
          <li key={item.rotulo}>
            <NavLink className={estilos.link} to={item.para} end>
              <span className={estilos.icone}>
                <Icone nome={item.icone} />
              </span>
              {item.rotulo}
            </NavLink>
          </li>
        ))}
      </ul>
    </nav>
  );
}
