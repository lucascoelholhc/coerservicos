import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router';

import { sair, sairDeTodos } from '../../api/autenticacao';
import { buscarMinhaConta, reenviarConfirmacaoDeEmail } from '../../api/contas';
import { comoErroDaApi, ErroDaApi } from '../../api/erros';
import { encerrarSessao } from '../../api/sessao';
import type { MinhaConta as Conta, Papel } from '../../api/tipos';
import { useRecurso } from '../../api/useRecurso';
import { CAMINHOS } from '../../caminhos';
import { Botao } from '../../componentes/Botao/Botao';
import { FalhaAoCarregar } from '../../componentes/FalhaAoCarregar/FalhaAoCarregar';
import { Nota } from '../../componentes/Nota/Nota';
import { AvisoDeContatoPendente } from '../../layout/ContatoPendente';
import { useTitulo } from '../../layout/titulo';
import estilos from './MinhaConta.module.css';

const NOMES_DOS_PAPEIS: Record<Papel, string> = { CLIENTE: 'Cliente', PROFISSIONAL: 'Profissional', ADMIN: 'Equipe COE' };
const LISTA = new Intl.ListFormat('pt-BR', { style: 'long', type: 'conjunction' });

type Retorno = { tipo: 'ok' | 'erro'; texto: string } | null;

/** Minha conta (privada): dados da conta, confirmação do e-mail e sair deste ou de todos os aparelhos. */
export function MinhaConta() {
  useTitulo('Minha conta');
  const conta = useRecurso(buscarMinhaConta);
  const local = useLocation();
  const aviso = avisoDoEstado(local.state);
  const [retornoDoEmail, setRetornoDoEmail] = useState<Retorno>(null);

  return (
    <section className={`largura ${estilos.pagina}`}>
      <div className={estilos.coluna}>
        <h1>Minha conta</h1>
        {aviso && (
          <Nota tipo="ok" anunciar>
            {aviso}
          </Nota>
        )}
        {retornoDoEmail && (
          <Nota tipo={retornoDoEmail.tipo} anunciar>
            {retornoDoEmail.texto}
          </Nota>
        )}
        {conta.situacao === 'carregando' && <p role="status">Carregando…</p>}
        {conta.situacao === 'erro' && <FalhaAoCarregar erro={conta.erro} aoTentarDeNovo={conta.tentarDeNovo} />}
        {(conta.situacao === 'pronto' || conta.situacao === 'vazio') && (
          <>
            {conta.dados.contatoPendente && <AvisoDeContatoPendente />}
            <DadosDaConta conta={conta.dados} />
            {!conta.dados.emailConfirmado && (
              <ConfirmarEmail aoAtualizar={conta.tentarDeNovo} aoResponder={setRetornoDoEmail} />
            )}
            <Seguranca conta={conta.dados} />
            <Sair />
          </>
        )}
      </div>
    </section>
  );
}

function DadosDaConta({ conta }: { conta: Conta }) {
  return (
    <dl className={estilos.dados}>
      <div>
        <dt>Nome</dt>
        <dd>{conta.nome}</dd>
      </div>
      <div>
        <dt>Tipo de conta</dt>
        <dd>{LISTA.format(conta.papeis.map((papel) => NOMES_DOS_PAPEIS[papel]))}</dd>
      </div>
      <div>
        <dt>Celular</dt>
        <dd>{conta.celular ?? 'Não informado'}</dd>
      </div>
      <div>
        <dt>E-mail</dt>
        <dd>{conta.email ?? 'Não informado'}</dd>
      </div>
    </dl>
  );
}

function situacaoDoCelular(conta: Conta): string {
  if (conta.celular === null) {
    return 'Não informado';
  }
  return conta.celularConfirmado ? 'Confirmado' : 'Não confirmado';
}

function situacaoDoCodigo(conta: Conta): string {
  if (conta.papeis.includes('ADMIN')) {
    return 'Obrigatório';
  }
  return conta.mfaAtivo ? 'Ligado' : 'Desligado';
}

/** Celular confirmado e código por SMS no login, com o caminho para cada ajuste. */
function Seguranca({ conta }: { conta: Conta }) {
  return (
    <section className={estilos.secao} aria-labelledby="titulo-seguranca">
      <h2 id="titulo-seguranca">Segurança</h2>
      <ul className={estilos.itens}>
        <li className={estilos.item}>
          <span>
            Celular: <b>{situacaoDoCelular(conta)}</b>
          </span>
          {conta.celular !== null && !conta.celularConfirmado && (
            <Botao para={CAMINHOS.confirmarCelular} variante="suave" tamanho="sm">
              Confirmar celular
            </Botao>
          )}
        </li>
        <li className={estilos.item}>
          <Link className={estilos.linkDoItem} to={CAMINHOS.codigoNoLogin}>
            Código no login
          </Link>
          <b>{situacaoDoCodigo(conta)}</b>
        </li>
        <li className={estilos.item}>
          <Link className={estilos.linkDoItem} to={CAMINHOS.trocarSenha}>
            Trocar senha
          </Link>
        </li>
      </ul>
    </section>
  );
}

/** E-mail ainda não confirmado: sem ele, não dá para recuperar a senha pelo link. */
function ConfirmarEmail({ aoAtualizar, aoResponder }: { aoAtualizar: () => void; aoResponder: (retorno: Retorno) => void }) {
  const [enviando, setEnviando] = useState(false);
  const setRetorno = aoResponder;

  async function mandarDeNovo() {
    setEnviando(true);
    setRetorno(null);
    try {
      await reenviarConfirmacaoDeEmail();
      setRetorno({ tipo: 'ok', texto: 'Enviamos o link de novo. Confira seu e-mail.' });
    } catch (falha) {
      const erro = falha instanceof ErroDaApi ? falha : new ErroDaApi('servidor');
      if (erro.codigo === 'email-ja-confirmado') {
        setRetorno({ tipo: 'ok', texto: erro.message });
        aoAtualizar();
      } else {
        setRetorno({ tipo: 'erro', texto: erro.message });
      }
    } finally {
      setEnviando(false);
    }
  }

  return (
    <div className={estilos.bloco}>
      <Nota tipo="atencao">Confirme seu e-mail para conseguir recuperar a senha.</Nota>
      <Botao variante="suave" onClick={mandarDeNovo} desabilitado={enviando}>
        {enviando ? 'Mandando…' : 'Mandar o link de novo'}
      </Botao>
    </div>
  );
}

/** Sair deste aparelho ou de todos, com confirmação na própria página (nunca window.confirm). */
function Sair() {
  const navegar = useNavigate();
  const [pergunta, setPergunta] = useState<'este' | 'todos' | null>(null);
  const [saindo, setSaindo] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  async function confirmar() {
    setSaindo(true);
    setErro(null);
    try {
      await (pergunta === 'todos' ? sairDeTodos() : sair());
    } catch (falha) {
      const problema = comoErroDaApi(falha);
      // Sem a confirmação do servidor, o cookie da renovação continuaria valendo (um F5 voltaria a
      // entrar): não finge que saiu. Só a sessão já encerrada no servidor sai daqui também.
      if (problema.tipo !== 'sessao') {
        setErro(`Não foi possível sair agora. ${problema.message}`);
        setSaindo(false);
        return;
      }
    }
    // Saída voluntária: a página privada leva ao Início (o mesmo destino daqui), não a /entrar.
    encerrarSessao({ voluntaria: true });
    void navegar(CAMINHOS.inicio, { replace: true });
  }

  if (pergunta) {
    const titulo = pergunta === 'todos' ? 'Sair de todos os aparelhos?' : 'Sair deste aparelho?';
    return (
      <div className={estilos.bloco} role="group" aria-labelledby="pergunta-sair">
        <p id="pergunta-sair" className={estilos.pergunta}>
          {titulo}
        </p>
        {erro && (
          <Nota tipo="erro" anunciar>
            {erro}
          </Nota>
        )}
        <div className={estilos.botoes}>
          <Botao variante="perigo" onClick={confirmar} desabilitado={saindo}>
            {pergunta === 'todos' ? 'Sim, sair de todos' : 'Sim, sair'}
          </Botao>
          <Botao variante="suave" onClick={() => setPergunta(null)} desabilitado={saindo}>
            Cancelar
          </Botao>
        </div>
      </div>
    );
  }
  return (
    <div className={estilos.botoes}>
      <Botao variante="contorno" onClick={() => setPergunta('este')}>
        Sair
      </Botao>
      <Botao variante="suave" onClick={() => setPergunta('todos')}>
        Sair de todos os aparelhos
      </Botao>
    </div>
  );
}

function avisoDoEstado(estado: unknown): string | undefined {
  const aviso = (estado as { aviso?: unknown } | null)?.aviso;
  return typeof aviso === 'string' ? aviso : undefined;
}
