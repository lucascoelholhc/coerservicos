import { useState } from 'react';
import { Link, useLocation, useParams } from 'react-router';

import { buscarPerfilPublico } from '../../api/busca';
import { buscarCatalogo, profissoesDoCatalogo } from '../../api/catalogo';
import { buscarRegras } from '../../api/regras';
import type { FotoDoPerfil, PerfilPublico as Perfil, Regras } from '../../api/tipos';
import { useRecurso } from '../../api/useRecurso';
import { CAMINHOS } from '../../caminhos';
import { Avatar } from '../../componentes/Avatar/Avatar';
import { Botao } from '../../componentes/Botao/Botao';
import { Chip } from '../../componentes/Chip/Chip';
import { FalhaAoCarregar } from '../../componentes/FalhaAoCarregar/FalhaAoCarregar';
import { Nota } from '../../componentes/Nota/Nota';
import { Reputacao, SeloDocumento, SeloNr10 } from '../../componentes/Selos/Selos';
import { hojeEmSaoPaulo } from '../../formatacao/datas';
import { formatarReais } from '../../formatacao/dinheiro';
import { formatarPercentual } from '../../formatacao/regras';
import { Icone } from '../../icones/Icone';
import { useTitulo } from '../../layout/titulo';
import {
  textoDaExperiencia,
  textoDasFerramentas,
  textoDasProfissoes,
  textoDoProximoLivre,
} from '../busca/textos';
import { CalendarioDeDias } from './CalendarioDeDias';
import estilos from './PerfilPublico.module.css';

/** Só a consulta da busca (sem barra nem esquema): o "voltar" nunca leva para fora da busca. */
const CONSULTA_DA_BUSCA = /^[\w%=&+.-]*$/;

function caminhoDeVolta(estado: unknown): string {
  const busca = (estado as { busca?: unknown } | null)?.busca;
  return typeof busca === 'string' && busca !== '' && CONSULTA_DA_BUSCA.test(busca)
    ? `${CAMINHOS.busca}?${busca}`
    : CAMINHOS.busca;
}

/** A foto é <img>: a CSP (img-src) limita os hosts; aqui só http(s), nunca outro esquema. */
const enderecoDeImagem = (url: string) => /^https?:\/\//i.test(url);

function textoDaTaxa(regras: Regras): string {
  return regras.taxaPagaPor === 'CLIENTE'
    ? `+ taxa de ${formatarPercentual(regras.comissao)} no pagamento`
    : 'Taxa já incluída';
}

/** Perfil público do profissional (protótipo: V.perfil). Nenhum contato antes de contratar (RN22). */
export function PerfilPublico() {
  const { id = '' } = useParams();
  const local = useLocation();
  const perfil = useRecurso((sinal) => buscarPerfilPublico(id, sinal), undefined, id);
  const catalogo = useRecurso(buscarCatalogo);
  const regras = useRecurso(buscarRegras);
  const [hoje] = useState(() => hojeEmSaoPaulo());
  useTitulo(perfil.dados?.nome ?? 'Perfil do profissional');
  const voltar = caminhoDeVolta(local.state);

  if (perfil.situacao === 'carregando') {
    return (
      <div className={`largura ${estilos.pagina}`}>
        <p role="status">Carregando o perfil…</p>
      </div>
    );
  }
  if (perfil.situacao === 'erro') {
    if (perfil.erro.status === 404) {
      return (
        <div className={`largura ${estilos.pagina} ${estilos.naoEncontrado}`}>
          <h1>Profissional não encontrado</h1>
          <p>Este perfil não existe ou não está disponível agora.</p>
          <Botao para={CAMINHOS.busca} variante="escuro">
            Ver outros profissionais
          </Botao>
        </div>
      );
    }
    return (
      <div className={`largura ${estilos.pagina}`}>
        <FalhaAoCarregar erro={perfil.erro} aoTentarDeNovo={perfil.tentarDeNovo} />
      </div>
    );
  }

  const dados = perfil.dados;
  const profissoes = catalogo.dados ? profissoesDoCatalogo(catalogo.dados) : [];
  const nomeDaProfissao = (codigo: string) => profissoes.find((p) => p.codigo === codigo)?.nome ?? codigo;
  const primeiroNome = dados.nome.split(' ')[0] ?? dados.nome;

  return (
    <>
      <section className={estilos.topo}>
        <div className="largura">
          <Link className={estilos.voltar} to={voltar}>
            <Icone nome="back" tamanho={18} />
            Voltar para a busca
          </Link>
          <div className={estilos.cabeca}>
            <Avatar nome={dados.nome} tamanho="lg" />
            <div className={estilos.identidade}>
              <h1>{dados.nome}</h1>
              <p className={estilos.profissoes}>
                {textoDasProfissoes([dados.profissao, ...dados.outrasProfissoes], nomeDaProfissao)}
              </p>
              <p className={estilos.selos}>
                <SeloDocumento />
                {dados.seloNr10 && <SeloNr10 />}
                <Reputacao
                  novoNaCoe={dados.avaliacoes.novoNaCoe}
                  nota={dados.avaliacoes.nota}
                  total={dados.avaliacoes.total}
                />
              </p>
            </div>
          </div>
        </div>
      </section>

      <div className={`largura ${estilos.grade}`}>
        <div className={estilos.conteudo}>
          <ul className={estilos.fatos} aria-label="Resumo">
            <Fato rotulo="Experiência" valor={textoDaExperiencia(dados.experienciaAnos)} />
            <Fato rotulo="Horário" valor={dados.horario} />
            <Fato rotulo="Ferramentas" valor={textoDasFerramentas(dados.ferramentas)} />
            <Fato rotulo="Atende até" valor={`${dados.raioKm} km`} />
          </ul>

          <section className={estilos.secao} aria-labelledby="perfil-fotos">
            <h2 id="perfil-fotos">Trabalhos feitos</h2>
            {dados.fotos.length === 0 ? (
              <p className={estilos.apoio}>Ainda sem fotos de trabalhos.</p>
            ) : (
              <div className={estilos.galeria}>
                {dados.fotos.map((foto) => (
                  <FotoDoTrabalho key={foto.id} foto={foto} nome={dados.nome} />
                ))}
              </div>
            )}
          </section>

          <section className={estilos.secao} aria-labelledby="perfil-sobre">
            <h2 id="perfil-sobre">Sobre</h2>
            <p className={estilos.sobre}>{dados.sobre}</p>
          </section>

          <section className={estilos.secao} aria-labelledby="perfil-servicos">
            <h2 id="perfil-servicos">O que faz</h2>
            <ul className={estilos.servicos}>
              {dados.servicos.map((servico) => (
                <li key={`${servico.profissao}-${servico.nome}`}>
                  <Icone nome="check" tamanho={20} />
                  {servico.nome}
                </li>
              ))}
            </ul>
          </section>

          <section className={estilos.secao} aria-labelledby="perfil-onde">
            <h2 id="perfil-onde">Onde atende</h2>
            <p>{`Mora em ${dados.cidade.nome} e vai até ${dados.raioKm} km.`}</p>
            {dados.outrasCidades.length > 0 && (
              <>
                <p className={estilos.apoio}>Também atende:</p>
                <p className={estilos.cidades}>
                  {dados.outrasCidades.map((cidade) => (
                    <Chip key={cidade.codigoIbge}>{cidade.nome}</Chip>
                  ))}
                </p>
              </>
            )}
          </section>

          <section className={estilos.secao} aria-labelledby="perfil-dias">
            <h2 id="perfil-dias">Dias livres</h2>
            <CalendarioDeDias hoje={hoje} diasDaSemana={dados.diasDaSemana} diasLivres={dados.diasLivres} />
          </section>

          <section className={estilos.secao} aria-labelledby="perfil-avaliacoes">
            <h2 id="perfil-avaliacoes">Avaliações</h2>
            {dados.avaliacoes.novoNaCoe ? (
              <div className={estilos.semAvaliacao}>
                <p>
                  <b>{primeiroNome} ainda não tem avaliações</b>
                </p>
                <p className={estilos.apoio}>
                  Toda avaliação na COE vem de uma diária paga pelo app. Contrate e seja o primeiro a avaliar.
                </p>
              </div>
            ) : (
              <Reputacao novoNaCoe={false} nota={dados.avaliacoes.nota} total={dados.avaliacoes.total} />
            )}
          </section>
        </div>

        <CaixaDePreco dados={dados} regras={regras.dados} hoje={hoje} primeiroNome={primeiroNome} />
      </div>
    </>
  );
}

function Fato({ rotulo, valor }: { rotulo: string; valor: string }) {
  return (
    <li>
      <span>{rotulo}</span>
      <b>{valor}</b>
    </li>
  );
}

function FotoDoTrabalho({ foto, nome }: { foto: FotoDoPerfil; nome: string }) {
  const [falhou, setFalhou] = useState(false);
  const descricao = foto.legenda ?? foto.servico ?? `Trabalho de ${nome}`;
  if (!enderecoDeImagem(foto.url) || falhou) {
    return (
      <figure className={`${estilos.foto} ${estilos.fotoFora}`}>
        <figcaption>Foto indisponível agora. Recarregue a página.</figcaption>
      </figure>
    );
  }
  return (
    <figure className={estilos.foto}>
      <img src={foto.url} alt={descricao} loading="lazy" onError={() => setFalhou(true)} />
      {foto.legenda && foto.servico && <figcaption>{foto.servico}</figcaption>}
    </figure>
  );
}

interface CaixaProps {
  dados: Perfil;
  regras?: Regras;
  hoje: string;
  primeiroNome: string;
}

/** Valor e contratação (protótipo: caixa-preco). A contratação pelo app chega no FE-05. */
function CaixaDePreco({ dados, regras, hoje, primeiroNome }: CaixaProps) {
  return (
    <aside className={estilos.caixa} aria-label="Valor e contratação">
      <div>
        <p className={estilos.rotulo}>Valor da diária</p>
        <p className={`valor ${estilos.valorGrande}`}>{formatarReais(dados.valorDiaria)}</p>
        {dados.valorMeiaDiaria && (
          <p>
            Meia diária: <b className="valor">{formatarReais(dados.valorMeiaDiaria)}</b>
          </p>
        )}
        {regras && <p className={estilos.apoio}>{textoDaTaxa(regras)}</p>}
      </div>
      <p className={dados.diasLivres.length > 0 ? estilos.livre : estilos.apoio}>
        <Icone nome="cal" tamanho={18} />
        {textoDoProximoLivre(dados.diasLivres[0] ?? null, hoje)}
      </p>
      <Nota>A contratação pelo app chega em breve.</Nota>
      <p className={estilos.oculto}>
        <Icone nome="eyeoff" />
        <span>Telefone e WhatsApp aparecem depois de contratar.</span>
      </p>
      <Nota tipo="ok">
        Você paga antes, mas o dinheiro só vai para {primeiroNome} depois que você aprova cada dia.
      </Nota>
    </aside>
  );
}
