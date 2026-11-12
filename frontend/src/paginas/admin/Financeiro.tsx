import { useState } from 'react';

import { comoErroDaApi } from '../../api/erros';
import {
  buscarAlertasAbertos,
  buscarPixParaConferir,
  buscarRepasses,
  buscarResumoFinanceiro,
  conferirPix,
  mandarRepasseDeNovo,
  resolverAlerta,
  type AlertaFinanceiro,
  type PixParaConferir,
  type RepasseDoPainel,
  type ResumoFinanceiro,
} from '../../api/financeiro';
import { useRecurso } from '../../api/useRecurso';
import { Botao } from '../../componentes/Botao/Botao';
import { FalhaAoCarregar } from '../../componentes/FalhaAoCarregar/FalhaAoCarregar';
import { Nota } from '../../componentes/Nota/Nota';
import { formatarData, formatarDiaEHora } from '../../formatacao/datas';
import { formatarReais } from '../../formatacao/dinheiro';
import { useTitulo } from '../../layout/titulo';
import { useEnvioUnico } from '../conta/useEnvioUnico';
import estilos from './Admin.module.css';

interface Painel {
  resumo: ResumoFinanceiro;
  alertas: AlertaFinanceiro[];
  repasses: RepasseDoPainel[];
}

async function carregar(sinal: AbortSignal): Promise<Painel> {
  const [resumo, alertas, repasses] = await Promise.all([
    buscarResumoFinanceiro(sinal),
    buscarAlertasAbertos(sinal),
    buscarRepasses(sinal),
  ]);
  return { resumo, alertas: alertas.itens, repasses: repasses.itens };
}

const TITULO_DO_ALERTA: Record<AlertaFinanceiro['tipo'], string> = {
  'repasse-falhou': 'Repasse com falha',
  'estorno-falhou': 'Estorno sem confirmação',
  'devolucao-falhou': 'Devolução sem confirmação',
  conciliacao: 'Diferença na conciliação com o gateway',
  'dinheiro-sem-pedido': 'Dinheiro recebido sem pedido (vai ser devolvido)',
  'cobranca-desconhecida': 'Pagamento de cobrança que a COE não conhece',
};

const MOTIVO: Record<string, string> = {
  'chave-invalida': 'chave Pix inválida',
  'titular-diferente': 'a chave está no nome de outra pessoa',
  incerto: 'o gateway não respondeu; confira no painel do gateway',
  'gateway-recusou': 'o gateway recusou',
  outro: 'motivo não informado pelo gateway',
};

const STATUS_DO_REPASSE: Record<RepasseDoPainel['status'], string> = {
  pendente: 'Esperando envio',
  enviado: 'Enviado, esperando o banco',
  confirmado: 'Pago',
  falhou: 'Falhou',
};

/** Financeiro do admin (FE-07, protótipo ADM.financeiro): saldos do livro-caixa, alertas e repasses por Pix. */
export function Financeiro() {
  useTitulo('Financeiro');
  const recurso = useRecurso(carregar);

  return (
    <section className={`largura ${estilos.pagina}`}>
      <header className={estilos.topo}>
        <p className={estilos.chamada}>Equipe COE</p>
        <h1>Financeiro</h1>
      </header>
      {recurso.situacao === 'carregando' && <p role="status">Carregando o financeiro…</p>}
      {recurso.situacao === 'erro' && <FalhaAoCarregar erro={recurso.erro} aoTentarDeNovo={recurso.tentarDeNovo} />}
      {(recurso.situacao === 'pronto' || recurso.situacao === 'vazio') && <Conteudo painel={recurso.dados} />}
    </section>
  );
}

function Conteudo({ painel }: { painel: Painel }) {
  const { saldos, periodo } = painel.resumo;
  return (
    <>
      <section aria-labelledby="fin-saldos" className={estilos.cartao}>
        <h2 id="fin-saldos">Saldos agora</h2>
        <dl className={estilos.numeros}>
          <Numero rotulo="No gateway" valor={saldos.noGateway} />
          <Numero rotulo="Guardado dos clientes" valor={saldos.guardado} />
          <Numero rotulo="A repassar aos profissionais" valor={saldos.aRepassar} />
          <Numero rotulo="Receita da COE (diárias liberadas)" valor={saldos.receita} />
          <Numero rotulo="Em reclamação" valor={saldos.emDisputa} />
          <Numero rotulo="A estornar aos clientes" valor={saldos.aEstornar} />
          <Numero rotulo="A devolver (sem pedido)" valor={saldos.aDevolver} />
          <Numero rotulo="Taxas do gateway pagas" valor={saldos.taxasDoGateway} />
        </dl>
      </section>
      <section aria-labelledby="fin-periodo" className={estilos.cartao}>
        <h2 id="fin-periodo">{`De ${formatarData(periodo.de)} a ${formatarData(periodo.ate)}`}</h2>
        <dl className={estilos.numeros}>
          <Numero rotulo="Recebido" valor={periodo.recebido} />
          <Numero rotulo="Liberado aos profissionais" valor={periodo.liberadoAoProfissional} />
          <Numero rotulo="Repassado por Pix" valor={periodo.repassado} />
          <Numero rotulo="Receita" valor={periodo.receita} />
          <Numero rotulo="Reembolsado" valor={periodo.reembolsado} />
        </dl>
      </section>
      <Alertas iniciais={painel.alertas} />
      <Repasses iniciais={painel.repasses} />
    </>
  );
}

function Numero({ rotulo, valor }: { rotulo: string; valor: string }) {
  return (
    <div>
      <dt className={estilos.apagado}>{rotulo}</dt>
      <dd>{formatarReais(valor)}</dd>
    </div>
  );
}

function Alertas({ iniciais }: { iniciais: AlertaFinanceiro[] }) {
  const [alertas, setAlertas] = useState(iniciais);
  const [erro, setErro] = useState<string | null>(null);
  const { enviando, executar } = useEnvioUnico();

  function resolver(alerta: AlertaFinanceiro) {
    setErro(null);
    void executar(
      async () => {
        await resolverAlerta(alerta.id);
        setAlertas((atuais) => atuais.filter((a) => a.id !== alerta.id));
      },
      (falha) => setErro(comoErroDaApi(falha).message),
    );
  }

  return (
    <section aria-labelledby="fin-alertas" className={estilos.cartao}>
      <h2 id="fin-alertas">Alertas</h2>
      {erro && (
        <Nota tipo="erro" anunciar>
          {erro}
        </Nota>
      )}
      {alertas.length === 0 ? (
        <p className={estilos.apagado}>Nenhum alerta aberto.</p>
      ) : (
        <ul className={estilos.lista}>
          {alertas.map((alerta) => (
            <li key={alerta.id}>
              <p>
                <strong>{TITULO_DO_ALERTA[alerta.tipo]}</strong>
                {typeof alerta.detalhe.motivo === 'string' && ` · ${MOTIVO[alerta.detalhe.motivo] ?? alerta.detalhe.motivo}`}
                {typeof alerta.detalhe.diferenca === 'string' && ` · diferença de ${formatarReais(alerta.detalhe.diferenca)}`}
              </p>
              <p className={estilos.apagado}>{formatarDiaEHora(alerta.criadoEm)}</p>
              <Botao variante="contorno" tamanho="sm" onClick={() => resolver(alerta)} desabilitado={enviando}>
                Marcar alerta como resolvido
              </Botao>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function Repasses({ iniciais }: { iniciais: RepasseDoPainel[] }) {
  const [repasses, setRepasses] = useState(iniciais);
  const [aviso, setAviso] = useState<string | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  const { enviando, executar } = useEnvioUnico();

  function mandarDeNovo(repasse: RepasseDoPainel) {
    setErro(null);
    void executar(
      async () => {
        await mandarRepasseDeNovo(repasse.id);
        setRepasses((atuais) =>
          atuais.map((r) => (r.id === repasse.id ? { ...r, status: 'pendente', erro: null } : r)),
        );
        setAviso(`Repasse de ${repasse.profissional} vai sair de novo na próxima rodada.`);
      },
      (falha) => setErro(comoErroDaApi(falha).message),
    );
  }

  function conferido(profissional: string) {
    setRepasses((atuais) => atuais.map((r) => (r.profissionalId === profissional ? { ...r, pixConferido: true } : r)));
    setAviso('Chave Pix conferida. Os repasses desse profissional saem na próxima rodada.');
  }

  return (
    <section aria-labelledby="fin-repasses" className={estilos.cartao}>
      <h2 id="fin-repasses">Repasses por Pix</h2>
      {aviso && (
        <Nota tipo="ok" anunciar>
          {aviso}
        </Nota>
      )}
      {erro && (
        <Nota tipo="erro" anunciar>
          {erro}
        </Nota>
      )}
      {repasses.length === 0 ? (
        <p className={estilos.apagado}>Nenhum repasse ainda.</p>
      ) : (
        <ul className={estilos.lista}>
          {repasses.map((repasse) => (
            <li key={repasse.id}>
              <p>
                <strong>{repasse.profissional}</strong>
                {` · ${formatarReais(repasse.valor)} · ${STATUS_DO_REPASSE[repasse.status]}`}
                {repasse.erro && ` · ${MOTIVO[repasse.erro] ?? repasse.erro}`}
              </p>
              {repasse.status === 'falhou' && (
                <Botao
                  variante="contorno"
                  tamanho="sm"
                  onClick={() => mandarDeNovo(repasse)}
                  desabilitado={enviando}
                >
                  {`Mandar de novo o repasse de ${repasse.profissional}`}
                </Botao>
              )}
              {!repasse.pixConferido && (
                <ConferenciaDoPix repasse={repasse} aoConferir={() => conferido(repasse.profissionalId)} />
              )}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

/** RN15: o admin vê a chave (auditado) e confirma que é do profissional; a API recusa se a chave mudou no meio. */
function ConferenciaDoPix({ repasse, aoConferir }: { repasse: RepasseDoPainel; aoConferir: () => void }) {
  const [pix, setPix] = useState<PixParaConferir | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  const { enviando, executar } = useEnvioUnico();

  function ver() {
    setErro(null);
    void executar(
      async () => setPix(await buscarPixParaConferir(repasse.profissionalId)),
      (falha) => setErro(comoErroDaApi(falha).message),
    );
  }

  function conferir(versao: string) {
    setErro(null);
    void executar(
      async () => {
        await conferirPix(repasse.profissionalId, versao);
        aoConferir();
      },
      (falha) => {
        setPix(null);
        setErro(comoErroDaApi(falha).message);
      },
    );
  }

  return (
    <div className={estilos.conferencia}>
      <p className={estilos.etiqueta}>Chave Pix ainda não conferida: o repasse espera.</p>
      {erro && (
        <Nota tipo="erro" anunciar>
          {erro}
        </Nota>
      )}
      {pix === null ? (
        <Botao
          variante="contorno"
          tamanho="sm"
          onClick={ver}
          desabilitado={enviando}
        >
          {`Ver a chave Pix de ${repasse.profissional}`}
        </Botao>
      ) : (
        <>
          <dl>
            <dt className={estilos.apagado}>Titular do cadastro</dt>
            <dd>{pix.nome}</dd>
            <dt className={estilos.apagado}>{`Chave (${pix.tipo})`}</dt>
            <dd>{pix.chave}</dd>
          </dl>
          <Botao variante="sucesso" tamanho="sm" onClick={() => conferir(pix.versao)} desabilitado={enviando}>
            A chave é dele: conferir
          </Botao>
        </>
      )}
    </div>
  );
}
