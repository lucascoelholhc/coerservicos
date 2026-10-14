import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router';

import { atualizarSessao } from '../../api/cliente';
import { buscarMinhaConta, cadastrarCelularNovo, cadastrarEmailNovo, confirmarCelular } from '../../api/contas';
import type { ErroDaApi } from '../../api/erros';
import { usuarioAtual } from '../../api/sessao';
import { useRecurso } from '../../api/useRecurso';
import { CAMINHOS } from '../../caminhos';
import { Botao } from '../../componentes/Botao/Botao';
import { CampoCodigo, codigoCompleto } from '../../componentes/CampoCodigo/CampoCodigo';
import { MandarDeNovo } from '../../componentes/CampoCodigo/MandarDeNovo';
import { CampoTexto } from '../../componentes/CampoTexto/CampoTexto';
import { FalhaAoCarregar } from '../../componentes/FalhaAoCarregar/FalhaAoCarregar';
import { Nota } from '../../componentes/Nota/Nota';
import estilos from '../../layout/PaginaDeFormulario.module.css';
import { useTitulo } from '../../layout/titulo';
import { useEnvioUnico } from './useEnvioUnico';

const VOLTOU_AO_NORMAL = 'Contato confirmado. Sua conta voltou ao normal.';

/** Depois de confirmar: a sessão é renovada (o contatoPendente sai do token) e volta para Minha conta. */
function useVoltarParaAConta() {
  const navegar = useNavigate();
  return () => void navegar(CAMINHOS.conta, { replace: true, state: { aviso: VOLTOU_AO_NORMAL } });
}

/**
 * RN61 (privada, liberada com contato pendente): a conta perdeu o celular ou o e-mail para o dono
 * verdadeiro e cadastra outro. O PUT só manda o código ou o link; a confirmação grava. Reenviar é
 * repetir o PUT com o mesmo valor (o backend segura 60 s entre envios).
 */
export function RecolocarContato() {
  useTitulo('Recolocar contato');
  const conta = useRecurso(buscarMinhaConta);

  return (
    <section className={`largura ${estilos.pagina}`}>
      <div className={estilos.coluna}>
        <header className={estilos.topo}>
          <h1>Recolocar contato</h1>
          <p className={estilos.apagado}>
            Outra pessoa confirmou que um contato da sua conta é dela. Cadastre um novo para continuar usando a COE.
          </p>
        </header>
        {conta.situacao === 'carregando' && <p role="status">Carregando…</p>}
        {conta.situacao === 'erro' && <FalhaAoCarregar erro={conta.erro} aoTentarDeNovo={conta.tentarDeNovo} />}
        {(conta.situacao === 'pronto' || conta.situacao === 'vazio') &&
          (conta.dados.contatoPendente ? (
            conta.dados.celular === null ? (
              <NovoCelular />
            ) : (
              <NovoEmail />
            )
          ) : (
            <Nota tipo="ok">Sua conta já tem celular e e-mail.</Nota>
          ))}
        <Link className={estilos.link} to={CAMINHOS.conta}>
          Voltar para minha conta
        </Link>
      </div>
    </section>
  );
}

function NovoEmail() {
  const voltar = useVoltarParaAConta();
  const { enviando, executar } = useEnvioUnico();
  const [email, setEmail] = useState('');
  const [enviadoPara, setEnviadoPara] = useState<string | null>(null);
  const [erroDoCampo, setErroDoCampo] = useState<string | undefined>();
  const [erro, setErro] = useState<string | null>(null);

  function tratarErro(problema: ErroDaApi) {
    const doCampo = problema.campos.email;
    if (doCampo) {
      setErroDoCampo(doCampo);
    } else {
      setErro(problema.message);
    }
  }

  function enviar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    setErro(null);
    const valor = email.trim();
    if (!valor) {
      setErroDoCampo('Informe o novo e-mail.');
      return;
    }
    setErroDoCampo(undefined);
    void executar(async () => {
      await cadastrarEmailNovo(valor);
      setEnviadoPara(valor);
    }, tratarErro);
  }

  function jaConfirmei(endereco: string) {
    setErro(null);
    void executar(
      async () => {
        await atualizarSessao();
        if (usuarioAtual()?.contatoPendente) {
          setErro(`Ainda não recebemos a confirmação. Abra o link que enviamos para ${endereco}.`);
        } else {
          voltar();
        }
      },
      (problema) => setErro(problema.message),
    );
  }

  const mensagemDeErro = erro && (
    <Nota tipo="erro" anunciar>
      {erro}
    </Nota>
  );

  if (enviadoPara !== null) {
    return (
      <div className={estilos.cartao}>
        <Nota tipo="ok" anunciar>
          {`Enviamos um link para ${enviadoPara}. Abra o link para confirmar.`}
        </Nota>
        <MandarDeNovo
          item="link"
          aoReenviar={async () => {
            await cadastrarEmailNovo(enviadoPara);
          }}
        />
        {mensagemDeErro}
        <Botao tamanho="lg" onClick={() => jaConfirmei(enviadoPara)} desabilitado={enviando}>
          {enviando ? 'Conferindo…' : 'Já confirmei pelo link'}
        </Botao>
        <Botao variante="suave" onClick={() => setEnviadoPara(null)}>
          Usar outro e-mail
        </Botao>
      </div>
    );
  }
  return (
    <form className={estilos.cartao} onSubmit={enviar} noValidate>
      <CampoTexto
        nome="email"
        rotulo="Novo e-mail"
        tipo="email"
        inputMode="email"
        valor={email}
        aoMudar={setEmail}
        autoComplete="email"
        erro={erroDoCampo}
      />
      {mensagemDeErro}
      <Botao type="submit" tamanho="lg" desabilitado={enviando}>
        {enviando ? 'Mandando…' : 'Mandar link'}
      </Botao>
    </form>
  );
}

function NovoCelular() {
  const voltar = useVoltarParaAConta();
  const { enviando, executar } = useEnvioUnico();
  const [celular, setCelular] = useState('');
  const [enviado, setEnviado] = useState<{ numero: string; mensagem: string } | null>(null);
  const [codigo, setCodigo] = useState('');
  const [erroDoCelular, setErroDoCelular] = useState<string | undefined>();
  const [erroDoCodigo, setErroDoCodigo] = useState<string | undefined>();
  const [erro, setErro] = useState<string | null>(null);

  function enviar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    setErro(null);
    const numero = celular.trim();
    if (!numero) {
      setErroDoCelular('Informe o novo celular.');
      return;
    }
    setErroDoCelular(undefined);
    void executar(
      async () => setEnviado({ numero, mensagem: await cadastrarCelularNovo(numero) }),
      (problema) => {
        const doCampo = problema.campos.celular;
        if (doCampo) {
          setErroDoCelular(doCampo);
        } else {
          setErro(problema.message);
        }
      },
    );
  }

  function confirmar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    setErro(null);
    if (!codigoCompleto(codigo)) {
      setErroDoCodigo('Digite os 6 números do código.');
      return;
    }
    setErroDoCodigo(undefined);
    void executar(
      async () => {
        await confirmarCelular(codigo);
        await atualizarSessao();
        voltar();
      },
      (problema) => {
        if (problema.codigo === 'codigo-invalido') {
          setErroDoCodigo(problema.message);
        } else {
          setErro(problema.message);
        }
      },
    );
  }

  const mensagemDeErro = erro && (
    <Nota tipo="erro" anunciar>
      {erro}
    </Nota>
  );

  if (enviado !== null) {
    return (
      <form className={estilos.cartao} onSubmit={confirmar} noValidate>
        <Nota tipo="ok" anunciar>
          {enviado.mensagem}
        </Nota>
        <CampoCodigo
          valor={codigo}
          aoMudar={setCodigo}
          erro={erroDoCodigo}
          aoReenviar={async () => {
            await cadastrarCelularNovo(enviado.numero);
          }}
        />
        {mensagemDeErro}
        <Botao type="submit" tamanho="lg" desabilitado={enviando}>
          {enviando ? 'Confirmando…' : 'Confirmar'}
        </Botao>
        <Botao
          variante="suave"
          onClick={() => {
            setEnviado(null);
            setCodigo('');
          }}
        >
          Usar outro número
        </Botao>
      </form>
    );
  }
  return (
    <form className={estilos.cartao} onSubmit={enviar} noValidate>
      <CampoTexto
        nome="celular"
        rotulo="Novo celular"
        tipo="tel"
        inputMode="tel"
        valor={celular}
        aoMudar={setCelular}
        autoComplete="tel"
        erro={erroDoCelular}
      />
      {mensagemDeErro}
      <Botao type="submit" tamanho="lg" desabilitado={enviando}>
        {enviando ? 'Mandando…' : 'Mandar código'}
      </Botao>
    </form>
  );
}
