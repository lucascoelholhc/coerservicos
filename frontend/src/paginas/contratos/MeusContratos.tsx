import { Link } from 'react-router';

import { listarContratos } from '../../api/contratos';
import type { ResumoDoContrato } from '../../api/tipos';
import { useRecurso } from '../../api/useRecurso';
import { CAMINHOS, caminhoDoContrato } from '../../caminhos';
import { Botao } from '../../componentes/Botao/Botao';
import { FalhaAoCarregar } from '../../componentes/FalhaAoCarregar/FalhaAoCarregar';
import { formatarDiaCurto } from '../../formatacao/datas';
import { formatarReais } from '../../formatacao/dinheiro';
import { Icone } from '../../icones/Icone';
import { useTitulo } from '../../layout/titulo';
import estilos from './Contratos.module.css';
import { SITUACAO_DO_CONTRATO } from './textos';

function semContratos(dados: { itens: ResumoDoContrato[] }): boolean {
  return dados.itens.length === 0;
}

/** Minhas diárias (FE-05, protótipo V.cliente): os contratos do cliente, do mais novo. */
export function MeusContratos() {
  useTitulo('Minhas diárias');
  const recurso = useRecurso(listarContratos, semContratos);

  return (
    <section className={`largura ${estilos.pagina}`}>
      <header className={estilos.topo}>
        <h1>Minhas diárias</h1>
        <p className={estilos.apoio}>Acompanhe suas contratações e aprove os dias de trabalho.</p>
      </header>
      {recurso.situacao === 'carregando' && <p role="status">Carregando…</p>}
      {recurso.situacao === 'erro' && <FalhaAoCarregar erro={recurso.erro} aoTentarDeNovo={recurso.tentarDeNovo} />}
      {recurso.situacao === 'vazio' && (
        <div className={estilos.vazio}>
          <Icone nome="cal" tamanho={40} />
          <h2>Nenhuma contratação ainda</h2>
          <Botao para={CAMINHOS.busca}>Buscar profissional</Botao>
        </div>
      )}
      {recurso.situacao === 'pronto' && (
        <ul className={estilos.lista}>
          {recurso.dados.itens.map((contrato) => (
            <li key={contrato.id}>
              <CartaoDoContrato contrato={contrato} />
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function CartaoDoContrato({ contrato }: { contrato: ResumoDoContrato }) {
  const liberadas = contrato.dias.filter((d) => d.status === 'liberada').length;
  const proxima = contrato.dias.find((d) => ['agendada', 'paga', 'andamento'].includes(d.status));
  return (
    <Link className={estilos.cartao} to={caminhoDoContrato(contrato.id)}>
      <span className={estilos.cartaoTopo}>
        <b>{contrato.profissional.nome}</b>
        <span className={estilos.situacao} data-status={contrato.status}>
          {SITUACAO_DO_CONTRATO[contrato.status]}
        </span>
      </span>
      <span className={estilos.apoio}>
        {contrato.profissional.profissao} · pedido nº {contrato.numero}
      </span>
      <span className={estilos.cartaoBase}>
        <span>
          {liberadas} de {contrato.dias.length} {contrato.dias.length === 1 ? 'diária liberada' : 'diárias liberadas'}
          {proxima && ` · próxima ${formatarDiaCurto(proxima.data)}`}
        </span>
        <b className="valor">{formatarReais(contrato.valorTotal)}</b>
      </span>
    </Link>
  );
}
