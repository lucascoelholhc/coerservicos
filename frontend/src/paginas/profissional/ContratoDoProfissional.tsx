import { useCallback } from 'react';
import { Link, useParams } from 'react-router';

import { buscarContrato } from '../../api/contratos';
import type { Contrato } from '../../api/tipos';
import { useRecurso } from '../../api/useRecurso';
import { CAMINHOS, caminhoDeTerminar } from '../../caminhos';
import { Botao } from '../../componentes/Botao/Botao';
import { FalhaAoCarregar } from '../../componentes/FalhaAoCarregar/FalhaAoCarregar';
import { Nota } from '../../componentes/Nota/Nota';
import { StatusDiaria } from '../../componentes/StatusDiaria/StatusDiaria';
import { formatarCelular } from '../../formatacao/contato';
import { formatarDiaLongo } from '../../formatacao/datas';
import { Icone } from '../../icones/Icone';
import { useTitulo } from '../../layout/titulo';
import { MATERIAL } from '../contratos/textos';
import estilos from './Profissional.module.css';

/** Um contrato visto pelo profissional (FE-06, protótipo proContrato): pedido, contato depois do pagamento e dias. */
export function ContratoDoProfissional() {
  useTitulo('Pedido');
  const { id = '' } = useParams();
  const buscar = useCallback((sinal: AbortSignal) => buscarContrato(id, sinal), [id]);
  const recurso = useRecurso(buscar, undefined, id);
  return (
    <section className={`largura ${estilos.pagina}`}>
      <Link className={estilos.voltar} to={CAMINHOS.profissionalAgenda}>
        <Icone nome="back" tamanho={18} />
        Agenda
      </Link>
      {recurso.situacao === 'carregando' && <p role="status">Carregando…</p>}
      {recurso.situacao === 'erro' && <FalhaAoCarregar erro={recurso.erro} aoTentarDeNovo={recurso.tentarDeNovo} />}
      {recurso.situacao === 'pronto' && <Detalhe contrato={recurso.dados} />}
    </section>
  );
}

function Detalhe({ contrato }: { contrato: Contrato }) {
  return (
    <>
      <header className={estilos.topo}>
        <h1>{contrato.contato?.nome ?? 'Pedido'}</h1>
        <p className={estilos.apoio}>
          Pedido nº {contrato.numero} · {contrato.cidade.nome}
        </p>
      </header>

      {contrato.contato ? (
        <section className={estilos.cartao} aria-labelledby="titulo-contato">
          <h2 id="titulo-contato">Contato do cliente</h2>
          <p className={estilos.telefone}>
            <Icone nome="phone" tamanho={20} /> <b>{formatarCelular(contrato.contato.celular)}</b>
          </p>
          {contrato.endereco && (
            <p>
              <Icone nome="pin" tamanho={20} /> {contrato.endereco.logradouro}, {contrato.endereco.numero}
              {contrato.endereco.complemento ? ` · ${contrato.endereco.complemento}` : ''}
              {contrato.endereco.bairro ? ` · ${contrato.endereco.bairro}` : ''} · {contrato.cidade.nome}
            </p>
          )}
        </section>
      ) : (
        <Nota tipo="info">O endereço e o telefone do cliente aparecem aqui depois que ele pagar.</Nota>
      )}

      <section className={estilos.cartao} aria-labelledby="titulo-pedido">
        <h2 id="titulo-pedido">O que o cliente pediu</h2>
        {contrato.servicos.length > 0 && <p>{contrato.servicos.join(', ')}</p>}
        {contrato.descricao && <p>“{contrato.descricao}”</p>}
        <p className={estilos.apoio}>{MATERIAL[contrato.material]}</p>
      </section>

      <section className={estilos.cartao} aria-labelledby="titulo-dias">
        <h2 id="titulo-dias">Dias</h2>
        <ul className={estilos.lista}>
          {contrato.dias.map((dia, posicao) => (
            <li key={dia.id}>
              <span className={estilos.linhaLink}>
                <b>
                  Dia {posicao + 1} · {formatarDiaLongo(dia.data)}
                </b>
                <span className={estilos.apoio}>{dia.tipo === 'meia' ? 'Meia diária' : 'Diária inteira'}</span>
              </span>
              <StatusDiaria estado={dia.status} lado="profissional" />
              {dia.status === 'andamento' && (
                <Botao variante="contorno" para={caminhoDeTerminar(dia.id)}>
                  Terminei o dia
                </Botao>
              )}
            </li>
          ))}
        </ul>
      </section>

      <Botao variante="contorno" para={CAMINHOS.mensagens}>
        <Icone nome="chat" tamanho={18} />
        Conversas
      </Botao>
    </>
  );
}
