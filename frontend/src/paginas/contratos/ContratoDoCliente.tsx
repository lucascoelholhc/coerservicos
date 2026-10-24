import { useCallback, useState } from 'react';
import { Link, useParams } from 'react-router';

import { buscarContrato, pedirPagamento, simularPagamento } from '../../api/contratos';
import type { Contrato, Pagamento } from '../../api/tipos';
import { enderecoSeguro } from '../../api/urlAssinada';
import { useRecurso } from '../../api/useRecurso';
import { CAMINHOS, caminhoDeContratar, caminhoDeConversarCom } from '../../caminhos';
import { Botao } from '../../componentes/Botao/Botao';
import { CampoTexto } from '../../componentes/CampoTexto/CampoTexto';
import { FalhaAoCarregar } from '../../componentes/FalhaAoCarregar/FalhaAoCarregar';
import { Nota } from '../../componentes/Nota/Nota';
import { Opcoes } from '../../componentes/Opcoes/Opcoes';
import { StatusDiaria } from '../../componentes/StatusDiaria/StatusDiaria';
import { formatarCelular } from '../../formatacao/contato';
import { formatarDiaEHora, formatarDiaLongo } from '../../formatacao/datas';
import { formatarReais } from '../../formatacao/dinheiro';
import { Icone } from '../../icones/Icone';
import { useTitulo } from '../../layout/titulo';
import { umaSo } from '../cadastro/etapas/comum';
import { useEnvioUnico } from '../conta/useEnvioUnico';
import estilos from './Contratos.module.css';
import { MATERIAL, SITUACAO_DO_CONTRATO } from './textos';

/** Um contrato do cliente (FE-05, protótipo V.contrato): pagar, ver o contato liberado e as diárias. */
export function ContratoDoCliente() {
  const { id = '' } = useParams();
  const buscar = useCallback((sinal: AbortSignal) => buscarContrato(id, sinal), [id]);
  const recurso = useRecurso(buscar, undefined, id);
  useTitulo('Contrato');

  return (
    <section className={`largura ${estilos.pagina}`}>
      <Link className={estilos.voltar} to={CAMINHOS.contratos}>
        <Icone nome="back" tamanho={18} />
        Minhas diárias
      </Link>
      {recurso.situacao === 'carregando' && <p role="status">Carregando…</p>}
      {recurso.situacao === 'erro' && <FalhaAoCarregar erro={recurso.erro} aoTentarDeNovo={recurso.tentarDeNovo} />}
      {recurso.situacao === 'pronto' && <Detalhe contrato={recurso.dados} aoMudar={recurso.tentarDeNovo} />}
    </section>
  );
}

function Detalhe({ contrato, aoMudar }: { contrato: Contrato; aoMudar: () => void }) {
  const primeiro = contrato.profissional.nome.split(' ')[0];
  return (
    <div className={estilos.detalhe}>
      <header className={estilos.topo}>
        <h1>{contrato.profissional.nome}</h1>
        <p className={estilos.apoio}>
          {contrato.profissional.profissao} · pedido nº {contrato.numero}
        </p>
        <p className={estilos.situacao} data-status={contrato.status}>
          {SITUACAO_DO_CONTRATO[contrato.status]}
        </p>
      </header>

      {contrato.status === 'aguardando_pagamento' && <Pagar contrato={contrato} aoPagar={aoMudar} />}
      {contrato.status === 'expirado' && (
        <div className={estilos.bloco}>
          <Nota tipo="atencao">
            O prazo para pagar acabou e os dias voltaram para a agenda de {primeiro}. Nada foi cobrado.
          </Nota>
          <Botao para={caminhoDeContratar(contrato.profissional.id)}>Contratar de novo</Botao>
        </div>
      )}
      <ContatoLiberado contrato={contrato} />

      <Valores contrato={contrato} />

      <section className={estilos.bloco} aria-labelledby="titulo-dias">
        <h2 id="titulo-dias">Diárias</h2>
        <ul className={estilos.dias}>
          {contrato.dias.map((dia) => (
            <li key={dia.id}>
              <span>
                <b>{formatarDiaLongo(dia.data)}</b>
                <span className={estilos.apoio}>
                  {dia.tipo === 'meia' ? 'Meia diária' : 'Diária inteira'} · {formatarReais(dia.valor)}
                </span>
              </span>
              <StatusDiaria estado={dia.status} lado="cliente" />
            </li>
          ))}
        </ul>
      </section>

      <section className={estilos.bloco} aria-labelledby="titulo-pedido">
        <h2 id="titulo-pedido">O que você pediu</h2>
        {contrato.servicos.length > 0 && <p>{contrato.servicos.join(', ')}</p>}
        {contrato.descricao && <p>“{contrato.descricao}”</p>}
        <p className={estilos.apoio}>{MATERIAL[contrato.material]}</p>
        {contrato.endereco && (
          <p>
            <Icone nome="pin" tamanho={18} /> {contrato.endereco.logradouro}, {contrato.endereco.numero}
            {contrato.endereco.complemento ? ` · ${contrato.endereco.complemento}` : ''}
            {contrato.endereco.bairro ? ` · ${contrato.endereco.bairro}` : ''} · {contrato.cidade.nome}
          </p>
        )}
      </section>

      <Botao variante="contorno" para={caminhoDeConversarCom(contrato.profissional.id)}>
        <Icone nome="chat" tamanho={18} />
        Conversar com {primeiro}
      </Botao>
    </div>
  );
}

function Valores({ contrato }: { contrato: Contrato }) {
  return (
    <section className={estilos.bloco} aria-labelledby="titulo-valores">
      <h2 id="titulo-valores">Valores</h2>
      <dl className={estilos.soma}>
        <div>
          <dt>
            {contrato.dias.length} {contrato.dias.length === 1 ? 'diária' : 'diárias'}
          </dt>
          <dd className="valor">{formatarReais(contrato.valorServicos)}</dd>
        </div>
        <div>
          <dt>Taxa de serviço</dt>
          <dd className="valor">{formatarReais(contrato.valorComissao)}</dd>
        </div>
        <div className={estilos.somaTotal}>
          <dt>Total</dt>
          <dd className="valor">{formatarReais(contrato.valorTotal)}</dd>
        </div>
      </dl>
      <p className={estilos.apoio}>
        Você paga agora e o dinheiro fica guardado. Cada dia só é liberado depois que você aprovar.
      </p>
    </section>
  );
}

function ContatoLiberado({ contrato }: { contrato: Contrato }) {
  const contato = contrato.contato;
  if (!contato) {
    return null;
  }
  return (
    <section className={estilos.liberado} aria-labelledby="titulo-contato">
      <Nota tipo="ok">Pagamento confirmado</Nota>
      <h2 id="titulo-contato">Contato liberado</h2>
      <p>{contato.nome}</p>
      <p className={estilos.telefone}>
        <Icone nome="phone" tamanho={18} />
        <b className="valor">{formatarCelular(contato.celular)}</b>
      </p>
      <p className={estilos.apoio}>
        Mantenha os combinados no chat do app: é ele que vale se você precisar reclamar de um dia.
      </p>
    </section>
  );
}

function Pagar({ contrato, aoPagar }: { contrato: Contrato; aoPagar: () => void }) {
  const [metodo, setMetodo] = useState<Pagamento['metodo']>('pix');
  const [pagamento, setPagamento] = useState<Pagamento | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  const [copiado, setCopiado] = useState(false);
  const { enviando, executar } = useEnvioUnico();

  function gerar() {
    setErro(null);
    void executar(
      async () => setPagamento(await pedirPagamento(contrato.id, metodo)),
      (falha) => setErro(falha.message),
    );
  }

  function simular() {
    setErro(null);
    void executar(
      async () => {
        await simularPagamento(contrato.id);
        aoPagar();
      },
      (falha) => setErro(falha.message),
    );
  }

  async function copiar(codigo: string) {
    try {
      await navigator.clipboard.writeText(codigo);
      setCopiado(true);
    } catch {
      setCopiado(false);
    }
  }

  return (
    <section className={estilos.bloco} aria-labelledby="titulo-pagar">
      <h2 id="titulo-pagar">Pagamento</h2>
      <p className={estilos.apoio}>
        Pague até {formatarDiaEHora(contrato.pagamentoExpiraEm)}. Depois disso o pedido expira.
      </p>
      {pagamento === null || pagamento.metodo !== metodo ? (
        <>
          <Opcoes
            legenda="Como você quer pagar"
            nome="metodo"
            tipo="unica"
            forma="cartao"
            opcoes={[
              { valor: 'pix', texto: 'Pix', detalhe: 'Na hora, pelo app do seu banco' },
              { valor: 'cartao', texto: 'Cartão', detalhe: 'Na página segura do pagamento' },
            ]}
            selecionadas={[metodo]}
            aoMudar={umaSo((valor) => setMetodo(valor as Pagamento['metodo']))}
          />
          <Botao onClick={gerar} desabilitado={enviando}>
            {metodo === 'pix' ? 'Gerar o Pix' : 'Pagar com cartão'}
          </Botao>
        </>
      ) : (
        <div className={estilos.cobranca}>
          <p>
            Valor: <b className="valor">{formatarReais(pagamento.valor)}</b>
          </p>
          {pagamento.pixCopiaECola && (
            <>
              <CampoTexto
                nome="pix"
                rotulo="Código Pix copia e cola"
                valor={pagamento.pixCopiaECola}
                aoMudar={() => undefined}
                somenteLeitura
                dica="No app do banco, escolha Pix › Copia e cola e cole o código."
              />
              <Botao variante="contorno" onClick={() => void copiar(pagamento.pixCopiaECola ?? '')}>
                <Icone nome="copy" tamanho={18} />
                {copiado ? 'Copiado' : 'Copiar o código'}
              </Botao>
            </>
          )}
          {pagamento.linkPagamento && enderecoSeguro(pagamento.linkPagamento) && (
            <a
              className={estilos.linkExterno}
              href={pagamento.linkPagamento}
              target="_blank"
              rel="noopener noreferrer"
            >
              <Icone nome="card" tamanho={18} />
              Abrir a página de pagamento
            </a>
          )}
          <p className={estilos.apoio}>A tela avança quando o banco confirmar. Já pagou?</p>
          <div className={estilos.acoes}>
            <Botao variante="suave" onClick={aoPagar}>
              Atualizar
            </Botao>
            {pagamento.simulavel && (
              <Botao variante="suave" onClick={simular} desabilitado={enviando}>
                Simular pagamento (ambiente de teste)
              </Botao>
            )}
          </div>
        </div>
      )}
      {erro && (
        <Nota tipo="erro" anunciar>
          {erro}
        </Nota>
      )}
    </section>
  );
}
