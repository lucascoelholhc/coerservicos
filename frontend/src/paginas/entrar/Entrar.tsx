import { useRef, useState, type FormEvent } from 'react';
import { Link, Navigate, useLocation } from 'react-router';

import { entrar, entrarComCodigo, pedirCodigoParaEntrar, segundoPasso } from '../../api/autenticacao';
import { ErroDaApi } from '../../api/erros';
import { iniciarSessao } from '../../api/sessao';
import { useSessao } from '../../api/SessaoProvider';
import type { SessaoResposta } from '../../api/tipos';
import { CAMINHOS } from '../../caminhos';
import { Botao } from '../../componentes/Botao/Botao';
import { CampoCodigo } from '../../componentes/CampoCodigo/CampoCodigo';
import { CampoSenha } from '../../componentes/CampoSenha/CampoSenha';
import { CampoTexto } from '../../componentes/CampoTexto/CampoTexto';
import { Nota } from '../../componentes/Nota/Nota';
import { useTitulo } from '../../layout/titulo';
import { destinoDepoisDeEntrar } from './destino';
import estilos from './Entrar.module.css';

/** senha → (desafio do MFA); ou celular → código por SMS. Desafio e celular ficam só na memória. */
type Etapa =
  | { tipo: 'senha' }
  | { tipo: 'desafio'; desafioId: string }
  | { tipo: 'celular' }
  | { tipo: 'codigoSms'; celular: string; enviado: string };

interface Dados {
  login: string;
  senha: string;
  codigo: string;
}

function entrarNaEtapa(etapa: Exclude<Etapa, { tipo: 'celular' }>, dados: Dados): Promise<SessaoResposta> {
  switch (etapa.tipo) {
    case 'senha':
      return entrar({ login: dados.login, senha: dados.senha });
    case 'desafio':
      return segundoPasso({ desafioId: etapa.desafioId, codigo: dados.codigo });
    case 'codigoSms':
      return entrarComCodigo({ celular: etapa.celular, codigo: dados.codigo });
  }
}

/**
 * Entrar com celular ou e-mail e senha. Com MFA ligado (ou ADMIN), a mesma página pede o código por
 * SMS. Também dá para entrar só com o código por SMS. Ao entrar, vai para o "voltar" seguro ou para
 * Minha conta.
 */
export function Entrar() {
  useTitulo('Entrar');
  const usuario = useSessao();
  const local = useLocation();
  const [etapa, setEtapa] = useState<Etapa>({ tipo: 'senha' });
  const [login, setLogin] = useState('');
  const [senha, setSenha] = useState('');
  const [celular, setCelular] = useState('');
  const [codigo, setCodigo] = useState('');
  const [erroDoCelular, setErroDoCelular] = useState<string | undefined>();
  const [erro, setErro] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);
  const emEnvio = useRef(false);

  // Quem já entrou (ou acabou de entrar) segue para o destino.
  if (usuario) {
    return <Navigate replace to={destinoDepoisDeEntrar(local.search)} />;
  }
  const aviso = avisoDoEstado(local.state);

  function irPara(proxima: Etapa) {
    setEtapa(proxima);
    setCodigo('');
    setErro(null);
    setErroDoCelular(undefined);
  }

  async function executar() {
    if (etapa.tipo === 'celular') {
      const numero = celular.trim();
      irPara({ tipo: 'codigoSms', celular: numero, enviado: await pedirCodigoParaEntrar(numero) });
      return;
    }
    const sessao = await entrarNaEtapa(etapa, { login, senha, codigo });
    setSenha('');
    iniciarSessao(sessao);
  }

  async function enviar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    if (emEnvio.current) {
      return;
    }
    if (etapa.tipo === 'celular' && !celular.trim()) {
      setErroDoCelular('Informe o celular.');
      return;
    }
    emEnvio.current = true;
    setEnviando(true);
    setErro(null);
    setErroDoCelular(undefined);
    try {
      await executar();
    } catch (falha) {
      const problema = falha instanceof ErroDaApi ? falha : new ErroDaApi('servidor');
      if (etapa.tipo === 'senha' && problema.codigo === 'segundo-passo-necessario' && problema.desafioId) {
        setSenha('');
        irPara({ tipo: 'desafio', desafioId: problema.desafioId });
      } else {
        setErro(problema.message);
      }
    } finally {
      emEnvio.current = false;
      setEnviando(false);
    }
  }

  return (
    <section className={`largura ${estilos.pagina}`}>
      <div className={estilos.coluna}>
        <Topo etapa={etapa} />
        {aviso && (
          <Nota tipo="ok" anunciar>
            {aviso}
          </Nota>
        )}
        <form className={estilos.cartao} onSubmit={enviar} noValidate>
          {etapa.tipo === 'senha' && (
            <>
              <CampoTexto
                nome="login"
                rotulo="Celular ou e-mail"
                valor={login}
                aoMudar={setLogin}
                autoComplete="username"
              />
              <CampoSenha nome="senha" rotulo="Senha" valor={senha} aoMudar={setSenha} autoComplete="current-password" />
            </>
          )}
          {etapa.tipo === 'celular' && (
            <CampoTexto
              nome="celular"
              rotulo="Celular"
              tipo="tel"
              inputMode="tel"
              valor={celular}
              aoMudar={setCelular}
              autoComplete="tel"
              erro={erroDoCelular}
            />
          )}
          {etapa.tipo === 'desafio' && <CampoCodigo valor={codigo} aoMudar={setCodigo} />}
          {etapa.tipo === 'codigoSms' && (
            <>
              <Nota tipo="ok" anunciar>
                {etapa.enviado}
              </Nota>
              <CampoCodigo
                valor={codigo}
                aoMudar={setCodigo}
                aoReenviar={async () => {
                  await pedirCodigoParaEntrar(etapa.celular);
                }}
              />
            </>
          )}
          {erro && (
            <Nota tipo="erro" anunciar>
              {erro}
            </Nota>
          )}
          <Botao type="submit" tamanho="lg" desabilitado={enviando}>
            {textoDoBotao(etapa, enviando)}
          </Botao>
          {etapa.tipo === 'senha' && (
            <>
              <Link className={estilos.link} to={CAMINHOS.esqueciSenha}>
                Esqueci minha senha
              </Link>
              <Botao variante="suave" onClick={() => irPara({ tipo: 'celular' })}>
                Entrar com código por SMS
              </Botao>
            </>
          )}
          {etapa.tipo === 'desafio' && (
            <Botao variante="suave" onClick={() => irPara({ tipo: 'senha' })}>
              Voltar
            </Botao>
          )}
          {(etapa.tipo === 'celular' || etapa.tipo === 'codigoSms') && (
            <Botao variante="suave" onClick={() => irPara({ tipo: 'senha' })}>
              Entrar com senha
            </Botao>
          )}
        </form>
        <div className={estilos.criar}>
          <p>
            <b>Ainda não tem conta?</b> Para contratar ou para trabalhar.
          </p>
          <Botao para={CAMINHOS.criarConta} variante="escuro">
            Criar conta
          </Botao>
        </div>
      </div>
    </section>
  );
}

function Topo({ etapa }: { etapa: Etapa }) {
  if (etapa.tipo === 'senha') {
    return (
      <header className={estilos.topo}>
        <h1>Entrar</h1>
        <p className={estilos.apagado}>O mesmo login serve para quem contrata e para quem trabalha.</p>
      </header>
    );
  }
  if (etapa.tipo === 'celular') {
    return (
      <header className={estilos.topo}>
        <h1>Entrar com código por SMS</h1>
        <p className={estilos.apagado}>Mandamos um código para o celular da sua conta. Ele precisa estar confirmado.</p>
      </header>
    );
  }
  return (
    <header className={estilos.topo}>
      <h1>Digite o código que enviamos por SMS</h1>
      <p className={estilos.apagado}>O código chega no celular da sua conta e vale por alguns minutos.</p>
    </header>
  );
}

function textoDoBotao(etapa: Etapa, enviando: boolean): string {
  if (etapa.tipo === 'celular') {
    return enviando ? 'Mandando…' : 'Mandar código';
  }
  return enviando ? 'Entrando…' : 'Entrar';
}

function avisoDoEstado(estado: unknown): string | undefined {
  const aviso = (estado as { aviso?: unknown } | null)?.aviso;
  return typeof aviso === 'string' ? aviso : undefined;
}
