import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router';

import { ErroDaApi } from '../../api/erros';
import { redefinirSenha } from '../../api/senha';
import { encerrarSessao } from '../../api/sessao';
import { lerTokenDoFragmento, limparFragmento } from '../../api/tokenDoFragmento';
import { CAMINHOS } from '../../caminhos';
import { Botao } from '../../componentes/Botao/Botao';
import { CampoSenha } from '../../componentes/CampoSenha/CampoSenha';
import { CampoTexto } from '../../componentes/CampoTexto/CampoTexto';
import { Nota } from '../../componentes/Nota/Nota';
import { useTitulo } from '../../layout/titulo';
import estilos from './Senha.module.css';

type Campo = 'celular' | 'codigo' | 'senha';
type Erros = Partial<Record<Campo, string>>;

const SENHA_MINIMA = 8;
const DICA_DA_SENHA = 'Use pelo menos 8 caracteres. Não use seu celular nem seu e-mail.';

/** Do campo da API para o da tela. */
function campoDaTela(nome: string): Campo | undefined {
  if (nome === 'novaSenha') {
    return 'senha';
  }
  return nome === 'celular' || nome === 'codigo' ? nome : undefined;
}

/** O "esqueci" passa o que a pessoa digitou; só vira celular se parecer número de telefone. */
function celularDoEstado(estado: unknown): string {
  const login = (estado as { login?: unknown } | null)?.login;
  return typeof login === 'string' && /^[\d\s()+-]+$/.test(login) ? login : '';
}

/**
 * Nova senha pelo link do e-mail (#token=, limpo antes de qualquer chamada) ou pelo código do SMS.
 * Redefinir não faz login e derruba as outras sessões: depois, /entrar com o aviso.
 */
export function RedefinirSenha() {
  useTitulo('Criar nova senha');
  const navegar = useNavigate();
  const local = useLocation();
  const [token] = useState(lerTokenDoFragmento);
  const [celular, setCelular] = useState(() => celularDoEstado(local.state));
  const [codigo, setCodigo] = useState('');
  const [senha, setSenha] = useState('');
  const [erros, setErros] = useState<Erros>({});
  const [erro, setErro] = useState<{ texto: string; linkInvalido: boolean } | null>(null);
  const [enviando, setEnviando] = useState(false);
  const emEnvio = useRef(false);

  useEffect(() => {
    limparFragmento();
  }, []);

  function avisos(): Erros {
    const encontrados: Erros = {};
    if (!token && !celular.trim()) {
      encontrados.celular = 'Informe o celular.';
    }
    if (!token && !codigo.trim()) {
      encontrados.codigo = 'Informe o código que chegou por SMS.';
    }
    if (senha.length < SENHA_MINIMA) {
      encontrados.senha = 'A senha precisa ter pelo menos 8 caracteres.';
    }
    return encontrados;
  }

  function tratarErro(problema: ErroDaApi) {
    if (problema.codigo === 'senha-nao-permitida') {
      setErros({ senha: problema.message });
      return;
    }
    const porCampo: Erros = {};
    for (const [nome, mensagem] of Object.entries(problema.campos)) {
      const campo = campoDaTela(nome);
      if (campo) {
        porCampo[campo] = mensagem;
      }
    }
    setErros(porCampo);
    if (Object.keys(porCampo).length === 0) {
      setErro({ texto: problema.message, linkInvalido: problema.codigo === 'link-invalido' });
    }
  }

  async function enviar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    if (emEnvio.current) {
      return;
    }
    setErro(null);
    const encontrados = avisos();
    setErros(encontrados);
    if (Object.keys(encontrados).length > 0) {
      return;
    }
    emEnvio.current = true;
    setEnviando(true);
    try {
      await redefinirSenha(
        token ? { token, novaSenha: senha } : { celular: celular.trim(), codigo: codigo.trim(), novaSenha: senha },
      );
      // A API derrubou as sessões: a deste aparelho sai antes de ir para /entrar.
      encerrarSessao();
      void navegar(CAMINHOS.entrar, { replace: true, state: { aviso: 'Senha alterada. Entre com a nova senha.' } });
    } catch (falha) {
      tratarErro(falha instanceof ErroDaApi ? falha : new ErroDaApi('servidor'));
    } finally {
      emEnvio.current = false;
      setEnviando(false);
    }
  }

  return (
    <section className={`largura ${estilos.pagina}`}>
      <div className={estilos.coluna}>
        <header className={estilos.topo}>
          <h1>Criar nova senha</h1>
          <p className={estilos.apagado}>
            {token ? 'Escolha a nova senha da sua conta.' : 'Digite o celular, o código que chegou por SMS e a nova senha.'}
          </p>
        </header>
        <form className={estilos.cartao} onSubmit={enviar} noValidate>
          {!token && (
            <>
              <CampoTexto
                nome="celular"
                rotulo="Celular"
                tipo="tel"
                inputMode="tel"
                valor={celular}
                aoMudar={setCelular}
                autoComplete="tel"
                erro={erros.celular}
              />
              <CampoTexto
                nome="codigo"
                rotulo="Código"
                inputMode="numeric"
                valor={codigo}
                aoMudar={setCodigo}
                autoComplete="one-time-code"
                maxLength={6}
                erro={erros.codigo}
              />
            </>
          )}
          <CampoSenha
            nome="novaSenha"
            rotulo="Nova senha"
            valor={senha}
            aoMudar={setSenha}
            autoComplete="new-password"
            dica={DICA_DA_SENHA}
            erro={erros.senha}
          />
          {erro && (
            <Nota tipo="erro" anunciar>
              <p>{erro.texto}</p>
              {erro.linkInvalido && (
                <p>
                  <Link to={CAMINHOS.esqueciSenha}>Pedir outro link</Link>
                </p>
              )}
            </Nota>
          )}
          <Botao type="submit" tamanho="lg" desabilitado={enviando}>
            {enviando ? 'Salvando…' : 'Salvar nova senha'}
          </Botao>
        </form>
      </div>
    </section>
  );
}
