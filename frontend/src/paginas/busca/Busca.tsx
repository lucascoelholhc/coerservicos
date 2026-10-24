import { useEffect, useRef, useState, type ReactNode } from 'react';
import { useSearchParams } from 'react-router';

import { buscarProfissionais } from '../../api/busca';
import { buscarCatalogo, profissoesDoCatalogo } from '../../api/catalogo';
import { comoErroDaApi } from '../../api/erros';
import type { ProfissionalNaBusca, ResultadoDaBusca } from '../../api/tipos';
import { useRecurso } from '../../api/useRecurso';
import { Botao } from '../../componentes/Botao/Botao';
import { CampoSelecao } from '../../componentes/CampoSelecao/CampoSelecao';
import { Chip } from '../../componentes/Chip/Chip';
import { FalhaAoCarregar } from '../../componentes/FalhaAoCarregar/FalhaAoCarregar';
import { Nota } from '../../componentes/Nota/Nota';
import { hojeEmSaoPaulo } from '../../formatacao/datas';
import { Icone } from '../../icones/Icone';
import { useTitulo } from '../../layout/titulo';
import estilos from './Busca.module.css';
import { CartaoDoProfissional } from './CartaoDoProfissional';
import {
  FILTROS_VAZIOS,
  ORDENS,
  comFiltro,
  consultaDosFiltros,
  etiquetasDosFiltros,
  filtrosDaUrl,
  semFiltro,
  type FiltrosDaBusca,
  type Ordem,
} from './filtros';
import { PainelDeFiltros } from './PainelDeFiltros';

const semNinguem = (resultado: ResultadoDaBusca) => resultado.itens.length === 0;

interface Mais {
  consulta: string;
  pagina: number;
  itens: ProfissionalNaBusca[];
  temMais: boolean;
}

function textoDaContagem(quantos: number, temMais: boolean): string {
  const palavra = quantos === 1 ? 'profissional' : 'profissionais';
  return temMais ? `Mais de ${quantos} ${palavra}` : `${quantos} ${palavra}`;
}

/** Busca pública (protótipo: V.busca), com os filtros do DOM-04 na URL. */
export function Busca() {
  useTitulo('Buscar profissionais');
  const [parametros, setParametros] = useSearchParams();
  const filtros = filtrosDaUrl(parametros);
  const consulta = consultaDosFiltros(filtros);
  const [hoje] = useState(() => hojeEmSaoPaulo());
  const catalogo = useRecurso(buscarCatalogo);
  // A chave (a consulta) faz buscar de novo quando os filtros mudam.
  const busca = useRecurso((sinal) => buscarProfissionais(consulta, 0, sinal), semNinguem, consulta);
  const [mais, setMais] = useState<Mais | null>(null);
  const [trazendoMais, setTrazendoMais] = useState(false);
  const [erroDoMais, setErroDoMais] = useState<string | null>(null);
  const [folhaAberta, setFolhaAberta] = useState(false);

  const profissoes = catalogo.dados ? profissoesDoCatalogo(catalogo.dados) : [];
  const cidades = catalogo.dados?.cidades ?? [];
  const profissao = profissoes.find((p) => p.codigo === filtros.profissao);
  const cidade = cidades.find((c) => String(c.codigoIbge) === filtros.cidade);
  const nomeDaProfissao = (codigo: string) => profissoes.find((p) => p.codigo === codigo)?.nome ?? codigo;
  const etiquetas = etiquetasDosFiltros(filtros, cidades);

  const extras = mais?.consulta === consulta ? mais : null;
  const itens = [...(busca.dados?.itens ?? []), ...(extras?.itens ?? [])];
  const temMais = extras ? extras.temMais : (busca.dados?.temMais ?? false);

  function trocarFiltros(novos: FiltrosDaBusca) {
    setErroDoMais(null);
    setParametros(consultaDosFiltros(novos), { replace: true });
  }
  const mudar = (troca: Partial<FiltrosDaBusca>) => trocarFiltros(comFiltro(filtros, troca));
  const limpar = () => trocarFiltros({ ...FILTROS_VAZIOS, profissao: filtros.profissao, ordem: filtros.ordem });

  async function verMais() {
    const pagina = (extras?.pagina ?? 0) + 1;
    setTrazendoMais(true);
    setErroDoMais(null);
    try {
      const proxima = await buscarProfissionais(consulta, pagina, new AbortController().signal);
      const vistos = new Set(itens.map((item) => item.id));
      setMais({
        consulta,
        pagina,
        itens: [...(extras?.itens ?? []), ...proxima.itens.filter((item) => !vistos.has(item.id))],
        temMais: proxima.temMais,
      });
    } catch (falha) {
      setErroDoMais(comoErroDaApi(falha).message);
    } finally {
      setTrazendoMais(false);
    }
  }

  const titulo = `${profissao ? profissao.nomePlural : 'Profissionais'}${cidade ? ` em ${cidade.nome}` : ''}`;
  const contagem = busca.situacao === 'pronto' ? textoDaContagem(itens.length, temMais) : null;
  const textoDoVer = contagem && !temMais ? `Ver ${contagem}` : 'Ver os profissionais';
  const painel = (prefixo: string) => (
    <PainelDeFiltros
      prefixo={prefixo}
      filtros={filtros}
      aoMudar={mudar}
      profissao={profissao}
      cidades={cidades}
      hoje={hoje}
    />
  );

  return (
    <div className={`largura ${estilos.pagina}`}>
      <h1 className={estilos.titulo}>{titulo}</h1>

      {profissoes.length > 0 && (
        <div className={estilos.categorias} role="group" aria-label="Profissão">
          <button type="button" aria-pressed={!filtros.profissao} onClick={() => mudar({ profissao: '' })}>
            Todos
          </button>
          {profissoes.map((p) => (
            <button
              key={p.codigo}
              type="button"
              aria-pressed={filtros.profissao === p.codigo}
              onClick={() => mudar({ profissao: p.codigo })}
            >
              {p.nome}
            </button>
          ))}
        </div>
      )}

      <div className={estilos.topo}>
        <CampoSelecao
          nome="ordem"
          rotulo="Mostrar primeiro"
          opcoes={ORDENS}
          valor={filtros.ordem}
          aoMudar={(ordem) => mudar({ ordem: ordem as Ordem })}
        />
        <span className={estilos.soCelular}>
          <Botao variante="contorno" onClick={() => setFolhaAberta(true)}>
            <Icone nome="filter" />
            Filtros{etiquetas.length > 0 ? ` (${etiquetas.length})` : ''}
          </Botao>
        </span>
      </div>

      <div className={estilos.grade}>
        <aside className={estilos.lateral} aria-label="Filtros">
          {painel('lateral')}
          <Botao variante="suave" tamanho="sm" onClick={limpar}>
            Limpar filtros
          </Botao>
        </aside>

        <div className={estilos.resultados}>
          <div className={estilos.ativos}>
            <p className={estilos.contagem} aria-live="polite">
              {contagem}
            </p>
            {etiquetas.map((etiqueta) => (
              <Chip
                key={etiqueta.chave}
                aoRemover={() => trocarFiltros(semFiltro(filtros, etiqueta.chave))}
                rotuloDoRemover={`Remover filtro ${etiqueta.texto}`}
              >
                {etiqueta.texto}
              </Chip>
            ))}
          </div>

          {busca.situacao === 'carregando' && (
            <p className={estilos.carregando} role="status">
              Procurando profissionais…
            </p>
          )}
          {busca.situacao === 'erro' && <FalhaAoCarregar erro={busca.erro} aoTentarDeNovo={busca.tentarDeNovo} />}
          {busca.situacao === 'vazio' && (
            <div className={estilos.vazio}>
              <Icone nome="search" tamanho={40} />
              <h2>Ninguém com esses filtros</h2>
              <p>Tente outra cidade, outro dia ou aumente o valor máximo.</p>
              <Botao variante="escuro" onClick={limpar}>
                Limpar filtros
              </Botao>
            </div>
          )}
          {busca.situacao === 'pronto' && (
            <>
              {itens.every((item) => item.novoNaCoe) && (
                <Nota>
                  A COE está começando, então ninguém aqui tem avaliação ainda. Todos passaram pela conferência de
                  documento e selfie.
                </Nota>
              )}
              <div className={estilos.lista}>
                {itens.map((item) => (
                  <CartaoDoProfissional
                    key={item.id}
                    profissional={item}
                    nomeDaProfissao={nomeDaProfissao}
                    cidadeBuscada={cidade?.nome}
                    hoje={hoje}
                    busca={consulta}
                  />
                ))}
              </div>
              {erroDoMais && (
                <Nota tipo="erro" anunciar>
                  {erroDoMais}
                </Nota>
              )}
              {temMais && (
                <Botao variante="contorno" onClick={() => void verMais()} desabilitado={trazendoMais}>
                  {trazendoMais ? 'Trazendo mais…' : 'Ver mais'}
                </Botao>
              )}
            </>
          )}
        </div>
      </div>

      {folhaAberta && (
        <FolhaDeFiltros aoFechar={() => setFolhaAberta(false)} aoLimpar={limpar} textoDoVer={textoDoVer}>
          {painel('folha')}
        </FolhaDeFiltros>
      )}
    </div>
  );
}

interface FolhaProps {
  children: ReactNode;
  aoFechar: () => void;
  aoLimpar: () => void;
  textoDoVer: string;
}

/** Folha de filtros do celular (protótipo: dlgFiltros): <dialog> nativo, Esc fecha, foco preso nela. */
function FolhaDeFiltros({ children, aoFechar, aoLimpar, textoDoVer }: FolhaProps) {
  const dialogo = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const elemento = dialogo.current;
    elemento?.showModal();
    return () => elemento?.close();
  }, []);
  return (
    <dialog ref={dialogo} className={estilos.folha} aria-labelledby="titulo-da-folha" onClose={aoFechar}>
      <div className={estilos.folhaTopo}>
        <h2 id="titulo-da-folha">Filtros</h2>
        <button type="button" className={estilos.fechar} aria-label="Fechar" onClick={() => dialogo.current?.close()}>
          <Icone nome="x" />
        </button>
      </div>
      <div className={estilos.folhaCorpo}>{children}</div>
      <div className={estilos.folhaPe}>
        <Botao variante="contorno" onClick={aoLimpar}>
          Limpar
        </Botao>
        <Botao onClick={() => dialogo.current?.close()}>{textoDoVer}</Botao>
      </div>
    </dialog>
  );
}
