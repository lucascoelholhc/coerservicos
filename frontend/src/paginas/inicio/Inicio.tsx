import { useRef, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router';

import { buscarCatalogo, profissoesDoCatalogo } from '../../api/catalogo';
import { buscarRegras } from '../../api/regras';
import type { Catalogo, Regras } from '../../api/tipos';
import { useRecurso, type EstadoDoRecurso } from '../../api/useRecurso';
import { CAMINHOS, caminhoDaBusca, SECOES_DO_INICIO } from '../../caminhos';
import { Botao } from '../../componentes/Botao/Botao';
import { CampoSelecao, type OpcaoDeSelecao } from '../../componentes/CampoSelecao/CampoSelecao';
import { CartelaDias } from '../../componentes/CartelaDias/CartelaDias';
import { FalhaAoCarregar } from '../../componentes/FalhaAoCarregar/FalhaAoCarregar';
import { Ilustracao, temIlustracao } from '../../componentes/Ilustracao/Ilustracao';
import { Icone } from '../../icones/Icone';
import { useTitulo } from '../../layout/titulo';
import {
  CARTELA_EXEMPLO,
  CONVITE_PROFISSIONAL,
  DESTAQUE_DA_APROVACAO,
  HEROI,
  OUTROS_FATOS,
  PASSOS,
} from './conteudo';
import { fraseDaAprovacao, fraseDaLiberacaoAoProfissional, fraseDaTaxa } from './frases';
import estilos from './Inicio.module.css';

type EstadoDoCatalogo = EstadoDoRecurso<Catalogo> & { tentarDeNovo: () => void };

const QUANTIDADE_DE_ESQUELETOS = 5;

function semProfissoes(catalogo: Catalogo): boolean {
  return profissoesDoCatalogo(catalogo).length === 0;
}

export function Inicio() {
  useTitulo();
  const catalogo = useRecurso(buscarCatalogo, semProfissoes);
  const regras = useRecurso(buscarRegras);
  const regrasProntas = regras.situacao === 'pronto' ? regras.dados : undefined;

  return (
    <>
      <section className={estilos.heroi}>
        <div className={`largura ${estilos.heroiGrade}`}>
          <div>
            <h1 className={estilos.heroiTitulo}>{HEROI.titulo}</h1>
            <p className={estilos.heroiTexto}>{HEROI.texto}</p>
            <FormularioDeBusca catalogo={catalogo} />
            <p className={estilos.heroiProfissional}>
              É profissional? <Link to={SECOES_DO_INICIO.paraProfissionais}>Conheça a COE para quem trabalha</Link>
            </p>
          </div>
          <CartelaDias cartela={CARTELA_EXEMPLO} />
        </div>
      </section>

      <Categorias catalogo={catalogo} />

      <section id="como-funciona" className={estilos.secao} aria-labelledby="h-como">
        <div className="largura">
          <div className={estilos.secaoTitulo}>
            <h2 id="h-como">Como funciona</h2>
            <Link className={estilos.linkTexto} to={CAMINHOS.comoFunciona}>
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
            <div>
              <dt className={estilos.fatoDestaque}>{DESTAQUE_DA_APROVACAO}</dt>
              <dd>{fraseDaAprovacao(regrasProntas)}</dd>
            </div>
            {OUTROS_FATOS.map((fato) => (
              <div key={fato.destaque}>
                <dt className={estilos.fatoDestaque}>{fato.destaque}</dt>
                <dd>{fato.texto}</dd>
              </div>
            ))}
          </dl>
        </div>
      </section>

      <ConviteAoProfissional regras={regrasProntas} carregando={regras.situacao === 'carregando'} />
    </>
  );
}

/** Profissão e cidade vêm da API; enquanto carrega (ou se falhar), os campos ficam desabilitados. */
function FormularioDeBusca({ catalogo }: { catalogo: EstadoDoCatalogo }) {
  const navegar = useNavigate();
  const aviso = useRef<HTMLParagraphElement>(null);
  const dados = catalogo.dados;
  const carregando = catalogo.situacao === 'carregando';

  function buscar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    const campos = new FormData(evento.currentTarget);
    void navegar(caminhoDaBusca({ profissao: texto(campos.get('profissao')), cidade: texto(campos.get('cidade')) }));
  }

  return (
    <>
      <form className={estilos.busca} role="search" aria-label="Buscar profissional" onSubmit={buscar}>
        <CampoSelecao
          nome="profissao"
          rotulo="Do que você precisa?"
          opcoes={dados ? opcoesDeProfissao(dados) : [opcaoEnquanto(carregando)]}
          desabilitado={!dados}
        />
        <CampoSelecao
          nome="cidade"
          rotulo="Em qual cidade?"
          opcoes={dados ? opcoesDeCidade(dados) : [opcaoEnquanto(carregando)]}
          desabilitado={!dados}
        />
        <Botao type="submit" tamanho="lg" desabilitado={!dados}>
          <Icone nome="search" />
          Buscar
        </Botao>
      </form>
      <p className="sr-only" role="status" tabIndex={-1} ref={aviso}>
        {carregando ? 'Carregando profissões e cidades…' : ''}
      </p>
      {catalogo.situacao === 'erro' && (
        <div className={estilos.falhaNaBusca}>
          <FalhaAoCarregar
            erro={catalogo.erro}
            aoTentarDeNovo={() => {
              // O botão some ao tentar de novo: o foco vai para o aviso, não se perde no <body>.
              catalogo.tentarDeNovo();
              aviso.current?.focus();
            }}
          />
        </div>
      )}
    </>
  );
}

function Categorias({ catalogo }: { catalogo: EstadoDoCatalogo }) {
  return (
    <section id="categorias" className={`${estilos.secao} ${estilos.secaoBranca}`} aria-labelledby="h-cats">
      <div className="largura">
        <div className={estilos.secaoTitulo}>
          <h2 id="h-cats">Quem você precisa?</h2>
          <Link className={estilos.linkTexto} to={CAMINHOS.busca}>
            Ver todos os profissionais
          </Link>
        </div>
        {catalogo.situacao === 'carregando' && (
          <ul className={estilos.categorias} aria-hidden="true">
            {Array.from({ length: QUANTIDADE_DE_ESQUELETOS }, (_, indice) => (
              <li key={indice}>
                <span className={estilos.esqueleto} />
              </li>
            ))}
          </ul>
        )}
        {catalogo.situacao === 'vazio' && (
          <p className={estilos.apagado}>Ainda não há profissionais para mostrar. Volte daqui a pouco.</p>
        )}
        {catalogo.situacao === 'pronto' && (
          <ul className={estilos.categorias}>
            {profissoesDoCatalogo(catalogo.dados).map((profissao) => (
              <li key={profissao.codigo}>
                <Link className={estilos.categoria} to={caminhoDaBusca({ profissao: profissao.codigo })}>
                  {temIlustracao(profissao.codigo) ? (
                    <Ilustracao profissao={profissao.codigo} formato="1:1" />
                  ) : (
                    <span className={estilos.semIlustracao} aria-hidden="true" />
                  )}
                  <b className={estilos.categoriaNome}>{profissao.nome}</b>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  );
}

/** Convite ao profissional: a frase da taxa só aparece com as regras da API (nunca um número fixo). */
function ConviteAoProfissional({ regras, carregando }: { regras: Regras | undefined; carregando: boolean }) {
  return (
    <section id="para-profissionais" className={estilos.convite} aria-labelledby="h-pro">
      <div className={`largura ${estilos.conviteGrade}`}>
        <div className={estilos.conviteTexto}>
          <p className={estilos.chamada}>{CONVITE_PROFISSIONAL.chamada}</p>
          <h2 id="h-pro">{CONVITE_PROFISSIONAL.titulo}</h2>
          <p>{CONVITE_PROFISSIONAL.texto}</p>
          <span>
            <Botao para={CAMINHOS.paraProfissionais} variante="escuro" tamanho="lg" sobreFundoEscuro>
              {CONVITE_PROFISSIONAL.botao}
            </Botao>
          </span>
        </div>
        <ul className={estilos.conviteItens}>
          {[...CONVITE_PROFISSIONAL.itensFixos, fraseDaLiberacaoAoProfissional(regras)].map((item) => (
            <li key={item}>
              <Icone nome="check" className={estilos.conviteIcone} />
              {item}
            </li>
          ))}
          {regras && (
            <li>
              <Icone nome="check" className={estilos.conviteIcone} />
              {fraseDaTaxa(regras)}
            </li>
          )}
          {carregando && (
            <li aria-hidden="true">
              <span className={estilos.esqueletoLinha} />
            </li>
          )}
        </ul>
      </div>
    </section>
  );
}

function texto(valor: FormDataEntryValue | null): string {
  return typeof valor === 'string' ? valor : '';
}

function opcaoEnquanto(carregando: boolean): OpcaoDeSelecao {
  return { valor: '', texto: carregando ? 'Carregando…' : 'Não foi possível carregar' };
}

function opcoesDeProfissao(catalogo: Catalogo): OpcaoDeSelecao[] {
  return [
    { valor: '', texto: 'Qualquer serviço' },
    ...profissoesDoCatalogo(catalogo).map((profissao) => ({ valor: profissao.codigo, texto: profissao.nome })),
  ];
}

/** Com cidades de mais de um estado, o nome leva a UF ("Curitiba - PR"). */
function opcoesDeCidade(catalogo: Catalogo): OpcaoDeSelecao[] {
  const variosEstados = new Set(catalogo.cidades.map((cidade) => cidade.uf)).size > 1;
  return [
    { valor: '', texto: 'Qualquer cidade' },
    ...catalogo.cidades.map((cidade) => ({
      valor: String(cidade.codigoIbge),
      texto: variosEstados ? `${cidade.nome} - ${cidade.uf}` : cidade.nome,
    })),
  ];
}
