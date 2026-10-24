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
  /** A primeira página de onde as extras saíram. */
  base: ResultadoDaBusca;
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
  const [folhaAberta, setFolhaAberta] = useState(false);

  const profissoes = catalogo.dados ? profissoesDoCatalogo(catalogo.dados) : [];
  const cidades = catalogo.dados?.cidades ?? [];
  const profissao = profissoes.find((p) => p.codigo === filtros.profissao);
  const cidade = cidades.find((c) => String(c.codigoIbge) === filtros.cidade);
  const nomeDaProfissao = (codigo: string) => profissoes.find((p) => p.codigo === codigo)?.nome ?? codigo;
  const etiquetas = etiquetasDosFiltros(filtros, cidades);

  const [quantos, setQuantos] = useState<{ primeira: ResultadoDaBusca; total: number; temMais: boolean } | null>(
    null,
  );

  function trocarFiltros(novos: FiltrosDaBusca) {
    setParametros(consultaDosFiltros(novos), { replace: true });
  }
  const mudar = (troca: Partial<FiltrosDaBusca>) => trocarFiltros(comFiltro(filtros, troca));
  const limpar = () => trocarFiltros({ ...FILTROS_VAZIOS, profissao: filtros.profissao, ordem: filtros.ordem });

  const titulo = `${profissao ? profissao.nomePlural : 'Profissionais'}${cidade ? ` em ${cidade.nome}` : ''}`;
  // A contagem é da lista mostrada (primeira página + as que o "Ver mais" trouxe).
  const primeiraPagina = busca.situacao === 'pronto' ? busca.dados : null;
  const lista = primeiraPagina && quantos?.primeira === primeiraPagina ? quantos : null;
  const temMaisNaTela = lista ? lista.temMais : (primeiraPagina?.temMais ?? false);
  const contagem = primeiraPagina
    ? textoDaContagem(lista ? lista.total : primeiraPagina.itens.length, temMaisNaTela)
    : null;
  const textoDoVer = contagem && !temMaisNaTela ? `Ver ${contagem}` : 'Ver os profissionais';
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
            {!filtros.profissao && <Icone nome="check" tamanho={18} />}
            Todos
          </button>
          {profissoes.map((p) => (
            <button
              key={p.codigo}
              type="button"
              aria-pressed={filtros.profissao === p.codigo}
              onClick={() => mudar({ profissao: p.codigo })}
            >
              {filtros.profissao === p.codigo && <Icone nome="check" tamanho={18} />}
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
            <ListaDeResultados
              key={consulta}
              consulta={consulta}
              primeira={busca.dados}
              aoContar={(total, temMais) => setQuantos({ primeira: busca.dados, total, temMais })}
              cartao={(item) => (
                <CartaoDoProfissional
                  key={item.id}
                  profissional={item}
                  nomeDaProfissao={nomeDaProfissao}
                  cidadeBuscada={cidade?.nome}
                  hoje={hoje}
                  busca={consulta}
                />
              )}
            />
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

interface ListaProps {
  consulta: string;
  primeira: ResultadoDaBusca;
  aoContar: (total: number, temMais: boolean) => void;
  cartao: (item: ProfissionalNaBusca) => ReactNode;
}

/**
 * Lista e "Ver mais" de uma consulta (a página a monta de novo a cada consulta, com key). As páginas extras
 * valem só para a primeira página de onde saíram: buscou de novo, começa do zero. Sair cancela o pedido.
 */
function ListaDeResultados({ consulta, primeira, aoContar, cartao }: ListaProps) {
  const [mais, setMais] = useState<Mais | null>(null);
  const [trazendo, setTrazendo] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const controle = useRef<AbortController | null>(null);
  useEffect(() => () => controle.current?.abort(), []);

  const extras = mais?.base === primeira ? mais : null;
  const itens = [...primeira.itens, ...(extras?.itens ?? [])];
  const temMais = extras ? extras.temMais : primeira.temMais;

  async function verMais() {
    controle.current?.abort();
    const atual = new AbortController();
    controle.current = atual;
    const pagina = (extras?.pagina ?? 0) + 1;
    setTrazendo(true);
    setErro(null);
    try {
      const proxima = await buscarProfissionais(consulta, pagina, atual.signal);
      const vistos = new Set(itens.map((item) => item.id));
      const novos = [...(extras?.itens ?? []), ...proxima.itens.filter((item) => !vistos.has(item.id))];
      setMais({ base: primeira, pagina, itens: novos, temMais: proxima.temMais });
      aoContar(primeira.itens.length + novos.length, proxima.temMais);
    } catch (falha) {
      if (!atual.signal.aborted) {
        setErro(comoErroDaApi(falha).message);
      }
    } finally {
      if (!atual.signal.aborted) {
        setTrazendo(false);
      }
    }
  }

  return (
    <>
      {itens.every((item) => item.novoNaCoe) && (
        <Nota>
          A COE está começando, então ninguém aqui tem avaliação ainda. Todos passaram pela conferência de documento e
          selfie.
        </Nota>
      )}
      <div className={estilos.lista}>{itens.map(cartao)}</div>
      {erro && (
        <Nota tipo="erro" anunciar>
          {erro}
        </Nota>
      )}
      {temMais && (
        <Botao variante="contorno" onClick={() => void verMais()} desabilitado={trazendo}>
          {trazendo ? 'Trazendo mais…' : 'Ver mais'}
        </Botao>
      )}
    </>
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
    if (elemento && !elemento.open) {
      elemento.showModal();
    }
    return () => {
      if (elemento?.open) {
        elemento.close();
      }
    };
  }, []);
  return (
    // Clique no fundo é atalho de mouse; no teclado, o Esc nativo do <dialog> faz o mesmo.
    // eslint-disable-next-line jsx-a11y-x/click-events-have-key-events, jsx-a11y-x/no-noninteractive-element-interactions
    <dialog
      ref={dialogo}
      className={estilos.folha}
      aria-labelledby="titulo-da-folha"
      // O "close" chega depois: numa montagem dupla (StrictMode) a folha já abriu de novo e continua aberta.
      onClose={(evento) => {
        if (!evento.currentTarget.open) {
          aoFechar();
        }
      }}
      // Clique fora do conteúdo (no fundo escuro) cai no próprio <dialog>: fecha.
      onClick={(evento) => {
        if (evento.target === evento.currentTarget) {
          evento.currentTarget.close();
        }
      }}
    >
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
