import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';

import { buscarCatalogo } from '../../api/catalogo';
import { comoErroDaApi, type ErroDaApi } from '../../api/erros';
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

/** Conferir um cadastro e decidir (FE-07, protótipo ADM.verif; API do DOM-03). */
export function VerificacaoDoCadastro() {
  useTitulo('Verificar cadastro');
  const { id = '' } = useParams();
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
          <p className={estilos.apagado}>Cada abertura fica registrada. O link vale 2 minutos.</p>
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
        </section>
        <section className={estilos.bloco} aria-labelledby="h-dados">
          <h2 id="h-dados">Dados informados</h2>
          <dl className={estilos.dados}>
            <Linha rotulo="CPF" valor={detalhe.cpf} />
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
          <ul className={estilos.arquivos}>
            {cadastro.fotos.map((foto, indice) => (
              <li key={foto.id}>
                <ArquivoPorLink rotulo={`a foto ${indice + 1}`} buscar={() => urlDaFotoParaAdmin(detalhe.id, foto.id)} />
                <span className={estilos.apagado}>{[foto.servico, foto.legenda].filter(Boolean).join(' · ') || 'Sem descrição'}</span>
              </li>
            ))}
          </ul>
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

/** "Ver X" busca a URL assinada (curta) e vira um link "Abrir X" (nova aba): nada de imagem do bucket na CSP. */
function ArquivoPorLink({ rotulo, buscar }: { rotulo: string; buscar: () => Promise<UrlAssinada> }) {
  const [url, setUrl] = useState<string | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  const { enviando, executar } = useEnvioUnico();

  function ver() {
    setErro(null);
    void executar(
      async () => {
        const assinada = await buscar();
        if (!enderecoSeguro(assinada.url)) {
          throw new Error('URL fora do esperado');
        }
        setUrl(assinada.url);
      },
      (falha) => setErro(comoErroDaApi(falha).message),
    );
  }

  if (url) {
    return (
      <a className={estilos.abrir} href={url} target="_blank" rel="noreferrer">
        {`Abrir ${rotulo}`}
      </a>
    );
  }
  return (
    <>
      <button type="button" className={estilos.ver} onClick={ver} disabled={enviando}>
        {`Ver ${rotulo}`}
      </button>
      {erro && <span className={estilos.erro}>{erro}</span>}
    </>
  );
}

const CAMPOS_POR_CODIGO: Record<string, string> = {
  'texto-com-contato': 'motivo',
  'nr10-sem-decisao': 'nr10',
  'nr10-sem-motivo': 'motivoNr10',
};

type Acao = 'aprovar' | 'correcao' | 'recusar';

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
  const { enviando, executar } = useEnvioUnico();
  const temNr10 = detalhe.nr10 === 'em_analise';

  function decidir(acao: Acao, chamada: () => Promise<unknown>) {
    setErro(null);
    setCampos({});
    void executar(
      async () => {
        await chamada();
        void navegar(CAMINHOS.adminVerificacao, { replace: true, state: { aviso: AVISOS[acao] } });
      },
      (problema: ErroDaApi) => {
        setConfirmarRecusa(false);
        const campo = problema.codigo ? CAMPOS_POR_CODIGO[problema.codigo] : undefined;
        if (campo) {
          setCampos({ [campo]: problema.message });
        } else {
          setErro(problema.message);
        }
      },
    );
  }

  function aprovar() {
    setConfirmarRecusa(false);
    if (marcados.length < ITENS.length) {
      setCampos({});
      setErro('Marque todos os itens da conferência para aprovar.');
      return;
    }
    if (temNr10 && nr10 === '') {
      setErro(null);
      setCampos({ nr10: 'Decida o certificado de NR-10.' });
      return;
    }
    if (temNr10 && nr10 === 'recusado' && motivoNr10.trim() === '') {
      setErro(null);
      setCampos({ motivoNr10: 'Escreva por que o certificado não foi aceito.' });
      return;
    }
    const itens = Object.fromEntries(ITENS.map((item) => [item.chave, true])) as unknown as ItensDaConferencia;
    const pedido: PedidoDeAprovacao = { itens };
    if (temNr10) {
      pedido.nr10 = nr10 as 'verificado' | 'recusado';
      if (nr10 === 'recusado') {
        pedido.motivoNr10 = motivoNr10.trim();
      }
    }
    decidir('aprovar', () => aprovarCadastro(detalhe.id, pedido));
  }

  function exigirMotivo(falta: string): boolean {
    if (motivo.trim() === '') {
      setErro(null);
      setCampos({ motivo: falta });
      return false;
    }
    return true;
  }

  function corrigir() {
    setConfirmarRecusa(false);
    if (exigirMotivo('Escreva o que o profissional precisa corrigir.')) {
      decidir('correcao', () => pedirCorrecao(detalhe.id, motivo.trim()));
    }
  }

  function recusar() {
    if (exigirMotivo('Escreva o motivo da recusa.')) {
      setErro(null);
      setCampos({});
      setConfirmarRecusa(true);
    }
  }

  return (
    <aside className={estilos.decisao} aria-labelledby="h-conferencia">
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
        aoMudar={setMotivo}
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
          <Botao variante="perigo" onClick={() => decidir('recusar', () => recusarCadastro(detalhe.id, motivo.trim()))} desabilitado={enviando}>
            Sim, recusar
          </Botao>
          <Botao variante="suave" onClick={() => setConfirmarRecusa(false)} desabilitado={enviando}>
            Não
          </Botao>
        </div>
      ) : (
        <Botao variante="perigo" onClick={recusar} desabilitado={enviando}>
          Recusar cadastro
        </Botao>
      )}
    </aside>
  );
}
