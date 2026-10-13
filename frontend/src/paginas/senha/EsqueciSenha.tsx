import { useRef, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router';

import { ErroDaApi } from '../../api/erros';
import { esqueciSenha } from '../../api/senha';
import { CAMINHOS } from '../../caminhos';
import { Botao } from '../../componentes/Botao/Botao';
import { CampoTexto } from '../../componentes/CampoTexto/CampoTexto';
import { Nota } from '../../componentes/Nota/Nota';
import { useTitulo } from '../../layout/titulo';
import estilos from './Senha.module.css';

const RESPOSTA = 'Se existir uma conta, enviamos um link para o e-mail confirmado ou um código por SMS.';

/** Recuperar a senha: a resposta é sempre a mesma, para não revelar se a conta existe. */
export function EsqueciSenha() {
  useTitulo('Esqueci minha senha');
  const navegar = useNavigate();
  const [login, setLogin] = useState('');
  const [erroDoCampo, setErroDoCampo] = useState<string | undefined>();
  const [erro, setErro] = useState<string | null>(null);
  const [enviado, setEnviado] = useState(false);
  const [enviando, setEnviando] = useState(false);
  const emEnvio = useRef(false);

  async function enviar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    if (emEnvio.current) {
      return;
    }
    if (!login.trim()) {
      setErroDoCampo('Informe o celular ou o e-mail da sua conta.');
      return;
    }
    emEnvio.current = true;
    setEnviando(true);
    setErroDoCampo(undefined);
    setErro(null);
    try {
      await esqueciSenha(login.trim());
      setEnviado(true);
    } catch (falha) {
      setErro((falha instanceof ErroDaApi ? falha : new ErroDaApi('servidor')).message);
    } finally {
      emEnvio.current = false;
      setEnviando(false);
    }
  }

  return (
    <section className={`largura ${estilos.pagina}`}>
      <div className={estilos.coluna}>
        <header className={estilos.topo}>
          <h1>Esqueci minha senha</h1>
          <p className={estilos.apagado}>
            Mandamos um link para o e-mail confirmado da sua conta ou, se ele não estiver confirmado, um código por SMS.
          </p>
        </header>
        <form className={estilos.cartao} onSubmit={enviar} noValidate>
          <CampoTexto
            nome="login"
            rotulo="Celular ou e-mail"
            valor={login}
            aoMudar={setLogin}
            autoComplete="username"
            erro={erroDoCampo}
          />
          {erro && (
            <Nota tipo="erro" anunciar>
              {erro}
            </Nota>
          )}
          {enviado && (
            <Nota tipo="ok" anunciar>
              {RESPOSTA}
            </Nota>
          )}
          <Botao type="submit" tamanho="lg" desabilitado={enviando}>
            {enviando ? 'Enviando…' : 'Recuperar senha'}
          </Botao>
          {enviado && (
            <Botao variante="suave" onClick={() => void navegar(CAMINHOS.redefinirSenha, { state: { login: login.trim() } })}>
              Recebi um código por SMS
            </Botao>
          )}
        </form>
        <Link className={estilos.link} to={CAMINHOS.entrar}>
          Voltar para entrar
        </Link>
      </div>
    </section>
  );
}
