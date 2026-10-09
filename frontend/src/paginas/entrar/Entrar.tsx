import { useRef, useState, type FormEvent } from 'react';
import { Link, Navigate, useLocation } from 'react-router';

import { entrar, segundoPasso } from '../../api/autenticacao';
import { ErroDaApi } from '../../api/erros';
import { iniciarSessao } from '../../api/sessao';
import { useSessao } from '../../api/SessaoProvider';
import { CAMINHOS } from '../../caminhos';
import { Botao } from '../../componentes/Botao/Botao';
import { CampoSenha } from '../../componentes/CampoSenha/CampoSenha';
import { CampoTexto } from '../../componentes/CampoTexto/CampoTexto';
import { Nota } from '../../componentes/Nota/Nota';
import { useTitulo } from '../../layout/titulo';
import { destinoDepoisDeEntrar } from './destino';
import estilos from './Entrar.module.css';

type Etapa = { tipo: 'senha' } | { tipo: 'codigo'; desafioId: string };

/**
 * Entrar com celular ou e-mail e senha. Com MFA ligado (ou ADMIN), a mesma página pede o código por
 * SMS (o desafio fica só na memória). Ao entrar, vai para o "voltar" seguro ou para Minha conta.
 */
export function Entrar() {
  useTitulo('Entrar');
  const usuario = useSessao();
  const local = useLocation();
  const [etapa, setEtapa] = useState<Etapa>({ tipo: 'senha' });
  const [login, setLogin] = useState('');
  const [senha, setSenha] = useState('');
  const [codigo, setCodigo] = useState('');
  const [erro, setErro] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);
  const emEnvio = useRef(false);

  // Quem já entrou (ou acabou de entrar) segue para o destino.
  if (usuario) {
    return <Navigate replace to={destinoDepoisDeEntrar(local.search)} />;
  }
  const aviso = avisoDoEstado(local.state);

  async function enviar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    if (emEnvio.current) {
      return;
    }
    emEnvio.current = true;
    setEnviando(true);
    setErro(null);
    try {
      const sessao =
        etapa.tipo === 'senha' ? await entrar({ login, senha }) : await segundoPasso({ desafioId: etapa.desafioId, codigo });
      setSenha('');
      iniciarSessao(sessao);
    } catch (falha) {
      const problema = falha instanceof ErroDaApi ? falha : new ErroDaApi('servidor');
      if (etapa.tipo === 'senha' && problema.codigo === 'segundo-passo-necessario' && problema.desafioId) {
        setSenha('');
        setEtapa({ tipo: 'codigo', desafioId: problema.desafioId });
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
        {etapa.tipo === 'senha' ? (
          <header className={estilos.topo}>
            <h1>Entrar</h1>
            <p className={estilos.apagado}>O mesmo login serve para quem contrata e para quem trabalha.</p>
          </header>
        ) : (
          <header className={estilos.topo}>
            <h1>Digite o código que enviamos por SMS</h1>
            <p className={estilos.apagado}>O código chega no celular da sua conta e vale por alguns minutos.</p>
          </header>
        )}
        {aviso && (
          <Nota tipo="ok" anunciar>
            {aviso}
          </Nota>
        )}
        <form className={estilos.cartao} onSubmit={enviar} noValidate>
          {etapa.tipo === 'senha' ? (
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
          ) : (
            <CampoTexto
              nome="codigo"
              rotulo="Código"
              valor={codigo}
              aoMudar={setCodigo}
              inputMode="numeric"
              autoComplete="one-time-code"
              maxLength={6}
            />
          )}
          {erro && (
            <Nota tipo="erro" anunciar>
              {erro}
            </Nota>
          )}
          <Botao type="submit" tamanho="lg" desabilitado={enviando}>
            {enviando ? 'Entrando…' : 'Entrar'}
          </Botao>
          {etapa.tipo === 'senha' ? (
            <Link className={estilos.link} to={CAMINHOS.esqueciSenha}>
              Esqueci minha senha
            </Link>
          ) : (
            <Botao
              variante="suave"
              onClick={() => {
                setEtapa({ tipo: 'senha' });
                setCodigo('');
                setErro(null);
              }}
            >
              Voltar
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

function avisoDoEstado(estado: unknown): string | undefined {
  const aviso = (estado as { aviso?: unknown } | null)?.aviso;
  return typeof aviso === 'string' ? aviso : undefined;
}
