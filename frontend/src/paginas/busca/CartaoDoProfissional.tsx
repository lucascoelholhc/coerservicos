import { Link } from 'react-router';

import type { ProfissionalNaBusca } from '../../api/tipos';
import { caminhoDoPerfil } from '../../caminhos';
import { Avatar } from '../../componentes/Avatar/Avatar';
import { Ilustracao, temIlustracao } from '../../componentes/Ilustracao/Ilustracao';
import { Reputacao, SeloDocumento, SeloNr10 } from '../../componentes/Selos/Selos';
import { formatarReais } from '../../formatacao/dinheiro';
import { Icone } from '../../icones/Icone';
import estilos from './Busca.module.css';
import { textoDaExperiencia, textoDoProximoLivre } from './textos';

interface CartaoProps {
  profissional: ProfissionalNaBusca;
  nomeDaProfissao: (codigo: string) => string;
  /** Nome da cidade escolhida no filtro (a distância é até ela). */
  cidadeBuscada?: string;
  hoje: string;
  /** A busca de onde veio (o perfil volta para ela). */
  busca: string;
}

function textoDoLugar(profissional: ProfissionalNaBusca, cidadeBuscada?: string): string {
  const mora = `Mora em ${profissional.cidade.nome}`;
  if (profissional.distanciaKm === null || cidadeBuscada === undefined) {
    return mora;
  }
  const km = Math.round(Number(profissional.distanciaKm));
  return km < 1 ? `${mora}, na cidade buscada` : `${mora}, a ${km} km de ${cidadeBuscada}`;
}

/** Cartão da lista (protótipo: proCard). O link do nome cobre o cartão inteiro. */
export function CartaoDoProfissional({ profissional, nomeDaProfissao, cidadeBuscada, hoje, busca }: CartaoProps) {
  const profissoes = [profissional.profissao, ...profissional.outrasProfissoes].map((codigo, indice) => {
    const nome = nomeDaProfissao(codigo);
    return indice === 0 ? nome : nome.toLowerCase();
  });
  const trabalho = `${profissoes.slice(0, -1).join(', ')}${profissoes.length > 1 ? ' e ' : ''}${profissoes.at(-1) ?? ''}`;
  return (
    <article className={estilos.cartao}>
      <div className={estilos.cartaoFoto}>
        {temIlustracao(profissional.profissao) ? (
          <Ilustracao profissao={profissional.profissao} formato="1:1" />
        ) : (
          <span className={estilos.semIlustracao} />
        )}
        <span className={estilos.cartaoAvatar}>
          <Avatar nome={profissional.nome} tamanho="sm" />
        </span>
      </div>
      <div className={estilos.cartaoCorpo}>
        <h2 className={estilos.cartaoNome}>
          <Link className={estilos.cartaoLink} to={caminhoDoPerfil(profissional.id)} state={{ busca }}>
            {profissional.nome}
          </Link>
        </h2>
        <p className={estilos.apoio}>
          {trabalho}, {textoDaExperiencia(profissional.experienciaAnos)} de experiência
        </p>
        <p className={estilos.meta}>
          <SeloDocumento />
          {profissional.seloNr10 && <SeloNr10 />}
          {profissional.novoNaCoe && <Reputacao novoNaCoe />}
        </p>
        <p className={estilos.meta}>
          <span className={estilos.comIcone}>
            <Icone nome="pin" tamanho={16} />
            {textoDoLugar(profissional, cidadeBuscada)}
          </span>
        </p>
      </div>
      <div className={estilos.cartaoLado}>
        <p className={estilos.preco}>
          <span className={`valor ${estilos.valorDoPreco}`}>{formatarReais(profissional.valorDiaria)}</span>{' '}
          <span>por dia</span>
        </p>
        <p className={profissional.proximoDiaLivre ? estilos.livre : estilos.semLivre}>
          <Icone nome="cal" tamanho={17} />
          {textoDoProximoLivre(profissional.proximoDiaLivre, hoje)}
        </p>
      </div>
    </article>
  );
}
