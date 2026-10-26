import { Link } from 'react-router';

import { listarConversas } from '../../api/conversas';
import type { ResumoDaConversa } from '../../api/tipos';
import { useRecurso } from '../../api/useRecurso';
import { CAMINHOS, caminhoDaConversa } from '../../caminhos';
import { Botao } from '../../componentes/Botao/Botao';
import { FalhaAoCarregar } from '../../componentes/FalhaAoCarregar/FalhaAoCarregar';
import { Icone } from '../../icones/Icone';
import { useTitulo } from '../../layout/titulo';
import estilos from './Mensagens.module.css';

function semConversas(dados: { itens: ResumoDaConversa[] }): boolean {
  return dados.itens.length === 0;
}

/** Mensagens (FE-05, protótipo V.mensagens): antes de contratar, a conversa fica só no app. */
export function Mensagens() {
  useTitulo('Mensagens');
  const recurso = useRecurso(listarConversas, semConversas);
  return (
    <section className={`largura ${estilos.pagina}`}>
      <header className={estilos.topo}>
        <h1>Mensagens</h1>
        <p className={estilos.apoio}>Antes de contratar, a conversa fica só no app. Depois, o telefone é liberado.</p>
      </header>
      {recurso.situacao === 'carregando' && <p role="status">Carregando…</p>}
      {recurso.situacao === 'erro' && <FalhaAoCarregar erro={recurso.erro} aoTentarDeNovo={recurso.tentarDeNovo} />}
      {recurso.situacao === 'vazio' && (
        <div className={estilos.vazio}>
          <Icone nome="chat" tamanho={40} />
          <h2>Nenhuma conversa ainda</h2>
          <Botao para={CAMINHOS.busca}>Buscar profissional</Botao>
        </div>
      )}
      {recurso.situacao === 'pronto' && (
        <ul className={estilos.lista}>
          {recurso.dados.itens.map((conversa) => (
            <li key={conversa.id}>
              <Link className={estilos.item} to={caminhoDaConversa(conversa.id)}>
                <b>{conversa.outroNome}</b>
                <span className={estilos.apoio}>
                  {conversa.contatoLiberado ? 'Contato liberado' : 'Contato oculto até o pagamento'}
                </span>
                {conversa.naoLidas > 0 && (
                  <span className={estilos.novas}>
                    {conversa.naoLidas} {conversa.naoLidas === 1 ? 'nova' : 'novas'}
                  </span>
                )}
              </Link>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
