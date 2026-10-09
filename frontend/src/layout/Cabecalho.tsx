import { Link, NavLink } from 'react-router';

import { useSessao, useSituacaoDaSessao } from '../api/SessaoProvider';
import { CAMINHOS } from '../caminhos';
import { Botao } from '../componentes/Botao/Botao';
import { Logo } from '../componentes/Logo/Logo';
import { Icone } from '../icones/Icone';
import estilos from './Cabecalho.module.css';
import { MENU_TOPO_VISITANTE } from './navegacao';

/** Visitante: Entrar e Criar conta. Logado: "Olá, <nome>" e Minha conta. Verificando: nada (não pisca). */
function AcoesDaConta() {
  const situacao = useSituacaoDaSessao();
  const usuario = useSessao();
  if (usuario) {
    return (
      <div className={estilos.acoes}>
        <span className={estilos.saudacao}>Olá, {primeiroNome(usuario.nome)}</span>
        <Botao para={CAMINHOS.conta} variante="contorno" tamanho="sm">
          Minha conta
        </Botao>
      </div>
    );
  }
  if (situacao === 'verificando') {
    return <div className={estilos.acoes} />;
  }
  return (
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
  );
}

function primeiroNome(nome: string): string {
  return nome.trim().split(/\s+/)[0] ?? nome;
}

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
        <AcoesDaConta />
      </div>
    </header>
  );
}
