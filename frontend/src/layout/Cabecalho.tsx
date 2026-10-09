import { Link, NavLink } from 'react-router';

import { CAMINHOS } from '../caminhos';
import { Botao } from '../componentes/Botao/Botao';
import { Logo } from '../componentes/Logo/Logo';
import { Icone } from '../icones/Icone';
import estilos from './Cabecalho.module.css';
import { MENU_TOPO_VISITANTE } from './navegacao';

export function Cabecalho() {
  return (
    <header className={estilos.cabecalho}>
      <div className={`largura ${estilos.dentro}`}>
        <Logo />
        <nav aria-label="Principal" className={estilos.topo}>
          <ul>
            {MENU_TOPO_VISITANTE.map((item) => (
              <li key={item.rotulo}>
                {item.para === undefined ? (
                  <Link className={estilos.link} to={item.ancora ?? CAMINHOS.inicio}>
                    <Icone nome={item.icone} tamanho={20} />
                    <span>{item.rotulo}</span>
                  </Link>
                ) : (
                  <NavLink className={estilos.link} to={item.para} end>
                    <Icone nome={item.icone} tamanho={20} />
                    <span>{item.rotulo}</span>
                  </NavLink>
                )}
              </li>
            ))}
          </ul>
        </nav>
        <div className={estilos.acoes}>
          <span className={estilos.entrar}>
            <Botao para={CAMINHOS.entrar} variante="contorno" tamanho="sm">
              Entrar
            </Botao>
          </span>
          <Botao para={CAMINHOS.criarConta} variante="escuro" tamanho="sm">
            Criar conta
          </Botao>
        </div>
      </div>
    </header>
  );
}
