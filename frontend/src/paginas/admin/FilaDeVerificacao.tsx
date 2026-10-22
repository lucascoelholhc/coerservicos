import { useState } from 'react';
import { Link, useLocation } from 'react-router';

import { buscarCatalogo, profissoesDoCatalogo } from '../../api/catalogo';
import { comoErroDaApi } from '../../api/erros';
import type { Catalogo, ItemDaFila } from '../../api/tipos';
import { useRecurso } from '../../api/useRecurso';
import { buscarFila } from '../../api/verificacao';
import { caminhoDaVerificacao } from '../../caminhos';
import { Botao } from '../../componentes/Botao/Botao';
import { FalhaAoCarregar } from '../../componentes/FalhaAoCarregar/FalhaAoCarregar';
import { Nota } from '../../componentes/Nota/Nota';
import { formatarDiaEHora } from '../../formatacao/datas';
import { useTitulo } from '../../layout/titulo';
import { useEnvioUnico } from '../conta/useEnvioUnico';
import estilos from './Admin.module.css';

interface Inicio {
  itens: ItemDaFila[];
  temMais: boolean;
  catalogo: Catalogo;
}

async function carregar(sinal: AbortSignal): Promise<Inicio> {
  const [fila, catalogo] = await Promise.all([buscarFila(0, sinal), buscarCatalogo(sinal)]);
  return { ...fila, catalogo };
}

/** Fila de verificação do admin (FE-07, protótipo ADM.verificacoes): mais antigo primeiro. */
export function FilaDeVerificacao() {
  useTitulo('Verificação de cadastros');
  const recurso = useRecurso(carregar);
  const aviso = (useLocation().state as { aviso?: string } | null)?.aviso;

  return (
    <section className={`largura ${estilos.pagina}`}>
      <header className={estilos.topo}>
        <p className={estilos.chamada}>Equipe COE</p>
        <h1>Cadastros para verificar</h1>
      </header>
      {aviso && (
        <Nota tipo="ok" anunciar>
          {aviso}
        </Nota>
      )}
      {recurso.situacao === 'carregando' && <p role="status">Carregando a fila…</p>}
      {recurso.situacao === 'erro' && <FalhaAoCarregar erro={recurso.erro} aoTentarDeNovo={recurso.tentarDeNovo} />}
      {(recurso.situacao === 'pronto' || recurso.situacao === 'vazio') && <Lista inicio={recurso.dados} />}
    </section>
  );
}

function Lista({ inicio }: { inicio: Inicio }) {
  const [itens, setItens] = useState(inicio.itens);
  const [temMais, setTemMais] = useState(inicio.temMais);
  const [pagina, setPagina] = useState(0);
  const [erro, setErro] = useState<string | null>(null);
  const { enviando, executar } = useEnvioUnico();
  const profissoes = profissoesDoCatalogo(inicio.catalogo);
  const nomeDaProfissao = (codigo: string) => profissoes.find((p) => p.codigo === codigo)?.nome ?? codigo;

  function verMais() {
    setErro(null);
    void executar(
      async () => {
        const proxima = await buscarFila(pagina + 1, new AbortController().signal);
        setItens((anteriores) => [...anteriores, ...proxima.itens.filter((novo) => !anteriores.some((i) => i.id === novo.id))]);
        setTemMais(proxima.temMais);
        setPagina(pagina + 1);
      },
      (falha) => setErro(comoErroDaApi(falha).message),
    );
  }

  if (itens.length === 0) {
    return (
      <div className={estilos.vazio}>
        <h2>Fila vazia</h2>
        <p>Nenhum cadastro esperando verificação.</p>
      </div>
    );
  }
  return (
    <>
      <ul className={estilos.cartoes}>
        {itens.map((item) => (
          <li key={item.id}>
            <article className={estilos.cartao} aria-labelledby={`fila-${item.id}`}>
              <h2 id={`fila-${item.id}`}>{item.nome}</h2>
              <p className={estilos.apagado}>{`${nomeDaProfissao(item.profissao)} · enviado em ${formatarDiaEHora(item.enviadoEm)}`}</p>
              {item.nr10 && <p className={estilos.etiqueta}>Tem certificado de NR-10</p>}
              <Link
                className={estilos.analisar}
                to={caminhoDaVerificacao(item.id)}
                aria-label={`Analisar o cadastro de ${item.nome}`}
              >
                Analisar o cadastro
              </Link>
            </article>
          </li>
        ))}
      </ul>
      {erro && (
        <Nota tipo="erro" anunciar>
          {erro}
        </Nota>
      )}
      {temMais && (
        <Botao variante="contorno" onClick={verMais} desabilitado={enviando}>
          {enviando ? 'Carregando…' : 'Ver mais'}
        </Botao>
      )}
    </>
  );
}
