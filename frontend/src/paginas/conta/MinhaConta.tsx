import { useState } from 'react';
import { useLocation, useNavigate } from 'react-router';

import { sair, sairDeTodos } from '../../api/autenticacao';
import { buscarMinhaConta, reenviarConfirmacaoDeEmail } from '../../api/contas';
import { ErroDaApi } from '../../api/erros';
import { encerrarSessao } from '../../api/sessao';
import type { MinhaConta as Conta, Papel } from '../../api/tipos';
import { useRecurso } from '../../api/useRecurso';
import { CAMINHOS } from '../../caminhos';
import { Botao } from '../../componentes/Botao/Botao';
import { FalhaAoCarregar } from '../../componentes/FalhaAoCarregar/FalhaAoCarregar';
import { Nota } from '../../componentes/Nota/Nota';
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
            <DadosDaConta conta={conta.dados} />
            {!conta.dados.emailConfirmado && (
              <ConfirmarEmail aoAtualizar={conta.tentarDeNovo} aoResponder={setRetornoDoEmail} />
            )}
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

  async function confirmar() {
    setSaindo(true);
    try {
      await (pergunta === 'todos' ? sairDeTodos() : sair());
    } catch {
      // Mesmo sem resposta da API, a sessão deste aparelho sai: o token só existia na memória.
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
