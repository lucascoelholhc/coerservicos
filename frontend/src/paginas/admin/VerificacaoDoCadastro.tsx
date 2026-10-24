import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';

import { buscarCatalogo } from '../../api/catalogo';
import { comoErroDaApi, ErroDaApi } from '../../api/erros';
import type { Catalogo, DetalheDaVerificacao, ItensDaConferencia, TipoDeDocumento, UrlAssinada } from '../../api/tipos';
import { enderecoSeguro } from '../../api/urlAssinada';
import { useRecurso } from '../../api/useRecurso';
import {
  aprovarCadastro,
  buscarDetalheDaVerificacao,
  pedirCorrecao,
  recusarCadastro,
  urlDaFotoParaAdmin,
  urlDoDocumentoParaAdmin,
  type PedidoDeAprovacao,
} from '../../api/verificacao';
import { CAMINHOS } from '../../caminhos';
import { Botao } from '../../componentes/Botao/Botao';
import { CampoTexto } from '../../componentes/CampoTexto/CampoTexto';
import { CampoTextoLongo } from '../../componentes/CampoTextoLongo/CampoTextoLongo';
import { FalhaAoCarregar } from '../../componentes/FalhaAoCarregar/FalhaAoCarregar';
import { Nota } from '../../componentes/Nota/Nota';
import { Opcoes } from '../../componentes/Opcoes/Opcoes';
import { formatarData, formatarDiaEHora } from '../../formatacao/datas';
import { useTitulo } from '../../layout/titulo';
import { FERRAMENTAS, textoDoSobre } from '../cadastro/conteudo';
import { umaSo } from '../cadastro/etapas/comum';
import { resumo } from '../cadastro/resumo';
import { useEnvioUnico } from '../conta/useEnvioUnico';
import estilos from './Admin.module.css';

/** O motivo vai por SMS (PedidosDaVerificacao.MOTIVO_MAXIMO). */
const MOTIVO_MAXIMO = 160;

const VALIDADE_MAXIMA_DA_URL_MS = 15 * 60_000;

/** O CPF completo some da tela sozinho depois de um tempo (aba esquecida aberta). */
const CPF_VISIVEL_MS = 2 * 60_000;

/** O checklist da RN12, na ordem do protótipo (ADM.verif). */
const ITENS: readonly { chave: keyof ItensDaConferencia; texto: string }[] = [
  { chave: 'cpf', texto: 'CPF válido e no nome da pessoa' },
  { chave: 'documento', texto: 'Documento legível e dentro da validade' },
  { chave: 'selfie', texto: 'Selfie confere com o documento' },
  { chave: 'fotos', texto: 'Fotos parecem trabalhos reais (não tiradas da internet)' },
  { chave: 'texto', texto: 'Texto do perfil sem contato' },
];

const NOMES_DOS_DOCUMENTOS: Record<TipoDeDocumento, string> = {
  rg_frente: 'RG (frente)',
  rg_verso: 'RG (verso)',
  cnh: 'CNH',
  selfie: 'Selfie com o documento',
  certificado_nr10: 'Certificado de NR-10',
};

interface Dados {
  detalhe: DetalheDaVerificacao;
  catalogo: Catalogo;
}

/** O SMS é uma linha só (o servidor recusa quebra de linha): espaços e quebras viram um espaço. */
function umaLinha(texto: string): string {
  return texto.replace(/\s+/g, ' ').trim();
}

/** Conferir um cadastro e decidir (FE-07, protótipo ADM.verif; API do DOM-03). */
export function VerificacaoDoCadastro() {
  useTitulo('Verificar cadastro');
  const { id = '' } = useParams();
  // Outro id na mesma rota = outra conferência: nada do cadastro anterior (checklist, mensagem) fica.
  return <Verificacao key={id} id={id} />;
}

function Verificacao({ id }: { id: string }) {
  const recurso = useRecurso(
    async (sinal): Promise<Dados> => {
      const [detalhe, catalogo] = await Promise.all([buscarDetalheDaVerificacao(id, sinal), buscarCatalogo(sinal)]);
      return { detalhe, catalogo };
    },
  );

  return (
    <section className={`largura ${estilos.pagina}`}>
      <Link className={estilos.voltar} to={CAMINHOS.adminVerificacao}>
        Fila de verificação
      </Link>
      {recurso.situacao === 'carregando' && <p role="status">Carregando o cadastro…</p>}
      {recurso.situacao === 'erro' && <FalhaAoCarregar erro={recurso.erro} aoTentarDeNovo={recurso.tentarDeNovo} />}
      {(recurso.situacao === 'pronto' || recurso.situacao === 'vazio') && <Conferencia dados={recurso.dados} />}
    </section>
  );
}

function Conferencia({ dados }: { dados: Dados }) {
  const { detalhe, catalogo } = dados;
  const { cadastro } = detalhe;
  const linhas = resumo(cadastro, catalogo);
  const sobre = textoDoSobre(cadastro.frases, cadastro.bio ?? '');
  return (
    <div className={estilos.duasColunas}>
      <div className={estilos.coluna}>
        <header className={estilos.topo}>
          <h1>{cadastro.nome}</h1>
          <p className={estilos.apagado}>{`${linhas.trabalho} · enviado em ${formatarDiaEHora(detalhe.enviadoEm)}`}</p>
        </header>
        <section className={estilos.bloco} aria-labelledby="h-documentos">
          <h2 id="h-documentos">Documentos</h2>
          <p className={estilos.apagado}>Cada abertura fica registrada. O link vale poucos minutos.</p>
          {cadastro.documentos.length === 0 ? (
            <p>Nenhum documento enviado.</p>
          ) : (
            <ul className={estilos.arquivos}>
              {cadastro.documentos.map((documento) => (
                <li key={documento.id}>
                  <ArquivoPorLink
                    rotulo={NOMES_DOS_DOCUMENTOS[documento.tipo]}
                    buscar={() => urlDoDocumentoParaAdmin(detalhe.id, documento.id)}
                  />
                </li>
              ))}
            </ul>
          )}
        </section>
        <section className={estilos.bloco} aria-labelledby="h-dados">
          <h2 id="h-dados">Dados informados</h2>
          <dl className={estilos.dados}>
            <div>
              <dt>CPF</dt>
              <dd>
                <CpfParaConferir cpf={detalhe.cpf} />
              </dd>
            </div>
            <Linha rotulo="Nascimento" valor={cadastro.dataNascimento ? formatarData(cadastro.dataNascimento) : '—'} />
            <Linha rotulo="Celular" valor={detalhe.celularConfirmado ? 'Confirmado' : 'Não confirmado'} />
            <Linha rotulo="Trabalho" valor={linhas.trabalho} />
            <Linha rotulo="Serviços" valor={linhas.servicos} />
            <Linha rotulo="Experiência" valor={linhas.experiencia} />
            <Linha rotulo="Ferramentas" valor={cadastro.ferramentas ? FERRAMENTAS[cadastro.ferramentas] : '—'} />
            <Linha rotulo="MEI" valor={cadastro.mei ? 'Sim' : 'Não informado'} />
            <Linha rotulo="Diária" valor={linhas.valor} />
            <Linha rotulo="Horário" valor={cadastro.horario ?? '—'} />
            <Linha rotulo="Onde atende" valor={linhas.onde} />
            <Linha rotulo="Dias" valor={linhas.dias} />
            <Linha rotulo="Chave Pix" valor={linhas.pix} />
          </dl>
        </section>
        <section className={estilos.bloco} aria-labelledby="h-sobre">
          <h2 id="h-sobre">Sobre (texto do perfil)</h2>
          <p>{sobre === '' ? '—' : sobre}</p>
        </section>
        <section className={estilos.bloco} aria-labelledby="h-fotos">
          <h2 id="h-fotos">{`Fotos de trabalhos (${cadastro.fotos.length})`}</h2>
          {cadastro.fotos.length === 0 ? (
            <p>Nenhuma foto enviada.</p>
          ) : (
            <ul className={estilos.arquivos}>
              {cadastro.fotos.map((foto, indice) => (
                <li key={foto.id}>
                  <ArquivoPorLink rotulo={`a foto ${indice + 1}`} buscar={() => urlDaFotoParaAdmin(detalhe.id, foto.id)} />
                  <span className={estilos.apagado}>
                    {[foto.servico, foto.legenda].filter(Boolean).join(' · ') || 'Sem descrição'}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
      <Decisao detalhe={detalhe} />
    </div>
  );
}

function Linha({ rotulo, valor }: { rotulo: string; valor: string }) {
  return (
    <div>
      <dt>{rotulo}</dt>
      <dd>{valor === '' ? '—' : valor}</dd>
    </div>
  );
}

/** "***.***.***-35" (os 2 últimos dígitos, como a Mascara do backend). */
function mascararCpf(cpf: string): string {
  return `***.***.***-${cpf.replace(/\D/g, '').slice(-2)}`;
}

/** CPF completo só quando o admin pede (para conferir com o documento) e por pouco tempo. */
function CpfParaConferir({ cpf }: { cpf: string }) {
  const [visivel, setVisivel] = useState(false);
  useEffect(() => {
    if (!visivel) {
      return undefined;
    }
    const relogio = setTimeout(() => setVisivel(false), CPF_VISIVEL_MS);
    return () => clearTimeout(relogio);
  }, [visivel]);
  return (
    <span className={estilos.cpf}>
      <span>{visivel ? cpf : mascararCpf(cpf)}</span>
      <button type="button" className={estilos.ver} onClick={() => setVisivel(!visivel)}>
        {visivel ? 'Esconder CPF' : 'Mostrar CPF'}
      </button>
    </span>
  );
}

/**
 * "Ver X" busca a URL assinada (curta) e vira o link "Abrir X" (nova aba), que recebe o foco; quando a
 * URL vence, volta a ser "Ver X". Nada de imagem do bucket na tela (nem na CSP).
 */
function ArquivoPorLink({ rotulo, buscar }: { rotulo: string; buscar: () => Promise<UrlAssinada> }) {
  const [assinada, setAssinada] = useState<UrlAssinada | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  const link = useRef<HTMLAnchorElement>(null);
  const botao = useRef<HTMLButtonElement>(null);
  const venceu = useRef(false);
  const { enviando, executar } = useEnvioUnico();

  useEffect(() => {
    if (assinada === null) {
      if (venceu.current) {
        venceu.current = false;
        botao.current?.focus();
      }
      return undefined;
    }
    link.current?.focus();
    // A URL assinada vale no máximo 15 min (coe.armazenamento); o teto também evita estourar o setTimeout.
    const restante = Math.min(Math.max(0, new Date(assinada.expiraEm).getTime() - Date.now()), VALIDADE_MAXIMA_DA_URL_MS);
    const relogio = setTimeout(() => {
      venceu.current = document.activeElement === link.current;
      setAssinada(null);
    }, restante);
    return () => clearTimeout(relogio);
  }, [assinada]);

  function ver() {
    setErro(null);
    void executar(
      async () => {
        const resposta = await buscar();
        if (!enderecoSeguro(resposta.url)) {
          throw new ErroDaApi('resposta');
        }
        setAssinada(resposta);
      },
      (falha) => setErro(comoErroDaApi(falha).message),
    );
  }

  if (assinada) {
    return (
      <a ref={link} className={estilos.abrir} href={assinada.url} target="_blank" rel="noreferrer">
        {`Abrir ${rotulo}`}
        <span className="sr-only"> (abre em nova aba)</span>
      </a>
    );
  }
  return (
    <>
      <button ref={botao} type="button" className={estilos.ver} onClick={ver} disabled={enviando}>
        {`Ver ${rotulo}`}
      </button>
      {erro && (
        <span className={estilos.erro} role="alert">
          {erro}
        </span>
      )}
    </>
  );
}

type Acao = 'aprovar' | 'correcao' | 'recusar';

/** Onde cada código da API aparece: o texto com contato é o motivo da ação (o do NR-10, na aprovação). */
const CAMPOS_POR_CODIGO: Record<Acao, Record<string, string>> = {
  aprovar: {
    'texto-com-contato': 'motivoNr10',
    'nr10-sem-decisao': 'nr10',
    'nr10-sem-motivo': 'motivoNr10',
    'checklist-incompleto': 'itens',
  },
  correcao: { 'texto-com-contato': 'motivo' },
  recusar: { 'texto-com-contato': 'motivo' },
};

const AVISOS: Record<Acao, string> = {
  aprovar: 'Cadastro aprovado. O profissional já aparece na busca.',
  correcao: 'Correção pedida. O profissional recebe a mensagem por SMS.',
  recusar: 'Cadastro recusado. O profissional recebe o motivo por SMS.',
};

/** Checklist, NR-10 e a decisão; recusar pede confirmação (o cadastro não muda mais depois). */
function Decisao({ detalhe }: { detalhe: DetalheDaVerificacao }) {
  const navegar = useNavigate();
  const [marcados, setMarcados] = useState<string[]>([]);
  const [nr10, setNr10] = useState('');
  const [motivoNr10, setMotivoNr10] = useState('');
  const [motivo, setMotivo] = useState('');
  const [confirmarRecusa, setConfirmarRecusa] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const [campos, setCampos] = useState<Record<string, string>>({});
  const painel = useRef<HTMLElement>(null);
  const nao = useRef<HTMLButtonElement>(null);
  const recusarBotao = useRef<HTMLButtonElement>(null);
  const voltarParaRecusar = useRef(false);
  const { enviando, executar } = useEnvioUnico();
  const temNr10 = detalhe.nr10 === 'em_analise';

  // Erro de campo: o foco vai para o primeiro campo inválido (o leitor de tela lê o erro dele).
  useEffect(() => {
    if (Object.keys(campos).length > 0) {
      painel.current?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus();
    }
  }, [campos]);

  // A confirmação recebe o foco ao abrir; "Não" devolve o foco a "Recusar cadastro".
  useEffect(() => {
    if (confirmarRecusa) {
      nao.current?.focus();
    } else if (voltarParaRecusar.current) {
      voltarParaRecusar.current = false;
      recusarBotao.current?.focus();
    }
  }, [confirmarRecusa]);

  function mostrar(porCampo: Record<string, string>, geral: string | null = null) {
    setCampos(porCampo);
    setErro(geral);
  }

  function decidir(acao: Acao, chamada: () => Promise<unknown>) {
    mostrar({});
    void executar(
      async () => {
        await chamada();
        void navegar(CAMINHOS.adminVerificacao, { replace: true, state: { aviso: AVISOS[acao] } });
      },
      (problema: ErroDaApi) => {
        setConfirmarRecusa(false);
        const campo = problema.codigo ? CAMPOS_POR_CODIGO[acao][problema.codigo] : undefined;
        if (campo) {
          mostrar({ [campo]: problema.message });
        } else if (Object.keys(problema.campos).length > 0) {
          mostrar({ ...problema.campos }, problema.message);
        } else {
          mostrar({}, problema.message);
        }
      },
    );
  }

  function aprovar() {
    setConfirmarRecusa(false);
    if (marcados.length < ITENS.length) {
      mostrar({ itens: 'Marque todos os itens da conferência para aprovar.' });
      return;
    }
    if (temNr10 && nr10 === '') {
      mostrar({ nr10: 'Decida o certificado de NR-10.' });
      return;
    }
    if (temNr10 && nr10 === 'recusado' && umaLinha(motivoNr10) === '') {
      mostrar({ motivoNr10: 'Escreva por que o certificado não foi aceito.' });
      return;
    }
    const itens = Object.fromEntries(ITENS.map((item) => [item.chave, true])) as unknown as ItensDaConferencia;
    const pedido: PedidoDeAprovacao = { itens };
    if (temNr10) {
      pedido.nr10 = nr10 as 'verificado' | 'recusado';
      if (nr10 === 'recusado') {
        pedido.motivoNr10 = umaLinha(motivoNr10);
      }
    }
    decidir('aprovar', () => aprovarCadastro(detalhe.id, pedido));
  }

  function exigirMotivo(falta: string): boolean {
    if (umaLinha(motivo) === '') {
      mostrar({ motivo: falta });
      return false;
    }
    return true;
  }

  function corrigir() {
    setConfirmarRecusa(false);
    if (exigirMotivo('Escreva o que o profissional precisa corrigir.')) {
      decidir('correcao', () => pedirCorrecao(detalhe.id, umaLinha(motivo)));
    }
  }

  function recusar() {
    if (exigirMotivo('Escreva o motivo da recusa.')) {
      mostrar({});
      setConfirmarRecusa(true);
    }
  }

  function desistirDaRecusa() {
    voltarParaRecusar.current = true;
    setConfirmarRecusa(false);
  }

  function mudarMotivo(texto: string) {
    // O que vai por SMS é o que foi confirmado: mudar a mensagem fecha a confirmação.
    setMotivo(texto);
    setConfirmarRecusa(false);
  }

  return (
    <aside ref={painel} className={estilos.decisao} aria-labelledby="h-conferencia">
      <h2 id="h-conferencia">Conferência</h2>
      <Opcoes
        legenda="Itens conferidos"
        legendaOculta
        nome="itens"
        tipo="varias"
        forma="lista"
        opcoes={ITENS.map((item) => ({ valor: item.chave, texto: item.texto }))}
        selecionadas={marcados}
        aoMudar={setMarcados}
        erro={campos.itens}
      />
      {temNr10 && (
        <>
          <Opcoes
            legenda="Certificado de NR-10"
            nome="nr10"
            tipo="unica"
            opcoes={[
              { valor: 'verificado', texto: 'Conferido (dá o selo NR-10)' },
              { valor: 'recusado', texto: 'Não aceito' },
            ]}
            selecionadas={nr10 === '' ? [] : [nr10]}
            aoMudar={umaSo(setNr10)}
            erro={campos.nr10}
          />
          {nr10 === 'recusado' && (
            <CampoTexto
              nome="motivoNr10"
              rotulo="Por que o certificado não foi aceito? (vai por SMS)"
              valor={motivoNr10}
              aoMudar={setMotivoNr10}
              maxLength={MOTIVO_MAXIMO}
              erro={campos.motivoNr10}
            />
          )}
        </>
      )}
      <CampoTextoLongo
        nome="motivo"
        rotulo="Mensagem para o profissional (se pedir correção ou recusar)"
        valor={motivo}
        aoMudar={mudarMotivo}
        maxLength={MOTIVO_MAXIMO}
        dica="Vai por SMS: uma frase curta, sem telefone."
        erro={campos.motivo}
      />
      {erro && (
        <Nota tipo="erro" anunciar>
          {erro}
        </Nota>
      )}
      <Botao variante="sucesso" tamanho="lg" onClick={aprovar} desabilitado={enviando}>
        Aprovar e publicar
      </Botao>
      <Botao variante="contorno" onClick={corrigir} desabilitado={enviando}>
        Pedir correção
      </Botao>
      {confirmarRecusa ? (
        <div className={estilos.confirmar} role="group" aria-labelledby="pergunta-recusa">
          <p id="pergunta-recusa">Recusar de vez? Depois disso o cadastro não pode mais ser mudado.</p>
          <Botao
            variante="perigo"
            onClick={() => decidir('recusar', () => recusarCadastro(detalhe.id, umaLinha(motivo)))}
            desabilitado={enviando}
          >
            Sim, recusar
          </Botao>
          <button ref={nao} type="button" className={estilos.nao} onClick={desistirDaRecusa} disabled={enviando}>
            Não
          </button>
        </div>
      ) : (
        <button ref={recusarBotao} type="button" className={estilos.recusar} onClick={recusar} disabled={enviando}>
          Recusar cadastro
        </button>
      )}
    </aside>
  );
}
