import type { FormEvent } from 'react';
import { Link, useNavigate } from 'react-router';

import { Botao } from '../../componentes/Botao/Botao';
import { CampoSelecao } from '../../componentes/CampoSelecao/CampoSelecao';
import { CartelaDias } from '../../componentes/CartelaDias/CartelaDias';
import { Ilustracao } from '../../componentes/Ilustracao/Ilustracao';
import { Icone } from '../../icones/Icone';
import { useTitulo } from '../../layout/titulo';
import { CARTELA_EXEMPLO, CIDADES, CONVITE_PROFISSIONAL, FATOS, HEROI, PASSOS, PROFISSOES } from './conteudo';
import estilos from './Inicio.module.css';

const FILTROS = ['profissao', 'cidade'] as const;

export function Inicio() {
  useTitulo();
  const navegar = useNavigate();

  function buscar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    const dados = new FormData(evento.currentTarget);
    const filtros = new URLSearchParams();
    for (const filtro of FILTROS) {
      const valor = dados.get(filtro);
      if (typeof valor === 'string' && valor !== '') {
        filtros.set(filtro, valor);
      }
    }
    const consulta = filtros.toString();
    navegar(consulta === '' ? '/buscar' : `/buscar?${consulta}`);
  }

  return (
    <>
      <section className={estilos.heroi}>
        <div className={`largura ${estilos.heroiGrade}`}>
          <div>
            <h1 className={estilos.heroiTitulo}>{HEROI.titulo}</h1>
            <p className={estilos.heroiTexto}>{HEROI.texto}</p>
            <form className={estilos.busca} role="search" aria-label="Buscar profissional" onSubmit={buscar}>
              <CampoSelecao
                id="h-prof"
                nome="profissao"
                rotulo="Do que você precisa?"
                opcoes={[{ valor: '', texto: 'Qualquer serviço' }, ...PROFISSOES]}
              />
              <CampoSelecao
                id="h-cidade"
                nome="cidade"
                rotulo="Em qual cidade?"
                opcoes={[{ valor: '', texto: 'Qualquer cidade' }, ...CIDADES]}
              />
              <Botao type="submit" tamanho="lg">
                <Icone nome="search" />
                Buscar
              </Botao>
            </form>
            <p className={estilos.heroiProfissional}>
              É profissional? <a href="#para-profissionais">Conheça a COE para quem trabalha</a>
            </p>
          </div>
          <CartelaDias cartela={CARTELA_EXEMPLO} />
        </div>
      </section>

      <section id="categorias" className={`${estilos.secao} ${estilos.secaoBranca}`} aria-labelledby="h-cats">
        <div className="largura">
          <div className={estilos.secaoTitulo}>
            <h2 id="h-cats">Quem você precisa?</h2>
            <Link className={estilos.linkTexto} to="/buscar">
              Ver todos os profissionais
            </Link>
          </div>
          <ul className={estilos.categorias}>
            {PROFISSOES.map((profissao) => (
              <li key={profissao.valor}>
                <Link className={estilos.categoria} to={`/buscar?profissao=${profissao.valor}`}>
                  <Ilustracao profissao={profissao.valor} formato="1:1" />
                  <b className={estilos.categoriaNome}>{profissao.texto}</b>
                </Link>
              </li>
            ))}
          </ul>
        </div>
      </section>

      <section id="como-funciona" className={estilos.secao} aria-labelledby="h-como">
        <div className="largura">
          <div className={estilos.secaoTitulo}>
            <h2 id="h-como">Como funciona</h2>
            <Link className={estilos.linkTexto} to="/como-funciona">
              Regras completas
            </Link>
          </div>
          <ol className={estilos.passos}>
            {PASSOS.map((passo, indice) => (
              <li key={passo.titulo}>
                <span className={estilos.passoNumero} aria-hidden="true">
                  {indice + 1}
                </span>
                <h3>{passo.titulo}</h3>
                <p className={estilos.apagado}>{passo.texto}</p>
              </li>
            ))}
          </ol>
        </div>
      </section>

      <section className={`${estilos.secao} ${estilos.secaoBranca}`} aria-labelledby="h-prot">
        <div className="largura">
          <div className={estilos.secaoTitulo}>
            <h2 id="h-prot">Seu dinheiro protegido</h2>
          </div>
          <dl className={estilos.fatos}>
            {FATOS.map((fato) => (
              <div key={fato.destaque}>
                <dt className={estilos.fatoDestaque}>{fato.destaque}</dt>
                <dd>{fato.texto}</dd>
              </div>
            ))}
          </dl>
        </div>
      </section>

      <section id="para-profissionais" className={estilos.convite} aria-labelledby="h-pro">
        <div className={`largura ${estilos.conviteGrade}`}>
          <div className={estilos.conviteTexto}>
            <p className={estilos.chamada}>{CONVITE_PROFISSIONAL.chamada}</p>
            <h2 id="h-pro">{CONVITE_PROFISSIONAL.titulo}</h2>
            <p>{CONVITE_PROFISSIONAL.texto}</p>
            <span>
              <Botao para="/para-profissionais" variante="escuro" tamanho="lg" sobreFundoEscuro>
                {CONVITE_PROFISSIONAL.botao}
              </Botao>
            </span>
          </div>
          <ul className={estilos.conviteItens}>
            {CONVITE_PROFISSIONAL.itens.map((item) => (
              <li key={item}>
                <Icone nome="check" className={estilos.conviteIcone} />
                {item}
              </li>
            ))}
          </ul>
        </div>
      </section>
    </>
  );
}
