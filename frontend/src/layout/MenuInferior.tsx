import { NavLink } from 'react-router';

import { useSessao, useSituacaoDaSessao } from '../api/SessaoProvider';
import { Icone } from '../icones/Icone';
import estilos from './MenuInferior.module.css';
import { MENU_CLIENTE, MENU_LOGADO, MENU_VISITANTE } from './navegacao';

/** Menu fixo embaixo, só no celular (some a partir de 900 px e o menu sobe para o cabeçalho). */
export function MenuInferior() {
  const situacao = useSituacaoDaSessao();
  const usuario = useSessao();
  // Enquanto verifica a sessão, o último item fica de fora (nem "Entrar" nem "Minha conta" piscam).
  const logado = usuario?.papeis.includes('CLIENTE') ? MENU_CLIENTE : MENU_LOGADO;
  const itens = usuario ? logado : situacao === 'verificando' ? MENU_VISITANTE.slice(0, 2) : MENU_VISITANTE;
  return (
    <nav aria-label="Menu" className={estilos.menu}>
      <ul>
        {itens.map((item) => (
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
