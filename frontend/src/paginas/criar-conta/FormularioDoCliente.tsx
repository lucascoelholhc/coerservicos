import { useEffect, useId, useRef, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router';

import { entrar } from '../../api/autenticacao';
import { criarCliente } from '../../api/contas';
import { ErroDaApi } from '../../api/erros';
import { buscarRegras } from '../../api/regras';
import { iniciarSessao } from '../../api/sessao';
import { useRecurso } from '../../api/useRecurso';
import { CAMINHOS } from '../../caminhos';
import { Botao } from '../../componentes/Botao/Botao';
import { CampoSenha } from '../../componentes/CampoSenha/CampoSenha';
import { CampoTexto } from '../../componentes/CampoTexto/CampoTexto';
import { FalhaAoCarregar } from '../../componentes/FalhaAoCarregar/FalhaAoCarregar';
import { Nota } from '../../componentes/Nota/Nota';
import estilos from './CriarConta.module.css';
import { PosseNoCadastro, type CampoDeContato } from './PosseNoCadastro';

type Campo = 'nome' | 'celular' | 'email' | 'cep' | 'senha' | 'aceite';
type Erros = Partial<Record<Campo, string>>;

/** Ordem da tela: o foco vai para o primeiro campo com erro. */
const ORDEM: readonly Campo[] = ['nome', 'celular', 'email', 'cep', 'senha', 'aceite'];
const SENHA_MINIMA = 8;
const AVISO_DO_EMAIL = 'Enviamos um link para o seu e-mail. Confirme para conseguir recuperar a senha.';

/** Comprovantes da prova de posse (RN61): só nesta tela, em memória. */
type Comprovantes = Partial<Record<CampoDeContato, string>>;
type Conflito = { mensagem: string; campo: CampoDeContato | null };

function campoDeContato(campos: Readonly<Record<string, string>>): CampoDeContato | null {
  return (['celular', 'email'] as const).find((campo) => campo in campos) ?? null;
}

function campoComComprovante(comprovantes: Comprovantes): CampoDeContato | null {
  return (['celular', 'email'] as const).find((campo) => comprovantes[campo] !== undefined) ?? null;
}

function semOComprovante(comprovantes: Comprovantes, campo: CampoDeContato): Comprovantes {
  return campo === 'celular' ? { email: comprovantes.email } : { celular: comprovantes.celular };
}

function corpoDosComprovantes(comprovantes: Comprovantes) {
  return { comprovanteCelular: comprovantes.celular, comprovanteEmail: comprovantes.email };
}

/** Como a API chama cada campo (o aceite dos termos vem como versaoTermosAceita). */
function campoDaApi(nome: string): Campo | undefined {
  if (nome === 'versaoTermosAceita') {
    return 'aceite';
  }
  return (ORDEM as readonly string[]).includes(nome) ? (nome as Campo) : undefined;
}

interface FormularioDoClienteProps {
  /** E-mail já provado na /provar-email: vem preenchido e somente leitura. */
  emailFixo?: string;
  /** Comprovante desse e-mail (só em memória). */
  comprovanteEmail?: string;
}

/**
 * Formulário do cadastro de cliente (RF03): nome, celular, e-mail, CEP e senha, com aceite dos termos
 * na versão que a API informa. A validação daqui só avisa antes; quem decide é a API. Com o contato em
 * outra conta (409), oferece provar a posse (RN61). Depois de criar, entra com o celular e a senha
 * digitados (a senha é descartada logo em seguida) e vai para Minha conta.
 */
export function FormularioDoCliente({ emailFixo, comprovanteEmail }: FormularioDoClienteProps) {
  const navegar = useNavigate();
  const regras = useRecurso(buscarRegras);
  const [valores, setValores] = useState({ nome: '', celular: '', email: emailFixo ?? '', cep: '', senha: '' });
  const [aceite, setAceite] = useState(false);
  const [erros, setErros] = useState<Erros>({});
  const [conflito, setConflito] = useState<Conflito | null>(null);
  const [posse, setPosse] = useState<CampoDeContato | null>(null);
  const [comprovantes, setComprovantes] = useState<Comprovantes>({ email: comprovanteEmail });
  const [erroGeral, setErroGeral] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);
  const emEnvio = useRef(false);
  const [foco, setFoco] = useState<{ campo: Campo; vez: number } | null>(null);
  const refNome = useRef<HTMLInputElement>(null);
  const refCelular = useRef<HTMLInputElement>(null);
  const refEmail = useRef<HTMLInputElement>(null);
  const refCep = useRef<HTMLInputElement>(null);
  const refSenha = useRef<HTMLInputElement>(null);
  const refAceite = useRef<HTMLInputElement>(null);
  const idDoAceite = useId();

  useEffect(() => {
    if (!foco) {
      return;
    }
    const campos = { nome: refNome, celular: refCelular, email: refEmail, cep: refCep, senha: refSenha, aceite: refAceite };
    campos[foco.campo].current?.focus();
  }, [foco]);

  function mudar(campo: keyof typeof valores) {
    return (valor: string) => {
      setValores((anteriores) => ({ ...anteriores, [campo]: valor }));
      // O comprovante vale para o contato provado; mudou o contato, ele não serve mais.
      if (campo === 'celular' || campo === 'email') {
        setComprovantes((anteriores) => semOComprovante(anteriores, campo));
      }
    };
  }

  function mostrarErros(novos: Erros) {
    setErros(novos);
    const primeiro = ORDEM.find((campo) => novos[campo]);
    if (primeiro) {
      setFoco((anterior) => ({ campo: primeiro, vez: (anterior?.vez ?? 0) + 1 }));
    }
  }

  function avisosAntesDeEnviar(): Erros {
    const avisos: Erros = {};
    if (!valores.nome.trim()) {
      avisos.nome = 'Informe seu nome.';
    }
    if (!valores.celular.trim()) {
      avisos.celular = 'Informe seu celular.';
    }
    if (!valores.email.trim()) {
      avisos.email = 'Informe seu e-mail.';
    }
    if (!valores.cep.trim()) {
      avisos.cep = 'Informe seu CEP.';
    }
    if (valores.senha.length < SENHA_MINIMA) {
      avisos.senha = 'A senha precisa ter pelo menos 8 caracteres.';
    }
    if (!aceite) {
      avisos.aceite = 'Para criar a conta, aceite os termos de uso e a política de privacidade.';
    }
    return avisos;
  }

  async function enviar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    if (emEnvio.current || posse !== null) {
      return;
    }
    setConflito(null);
    setErroGeral(null);
    const avisos = avisosAntesDeEnviar();
    if (Object.keys(avisos).length > 0) {
      mostrarErros(avisos);
      return;
    }
    await cadastrar(comprovantes);
  }

  async function cadastrar(provas: Comprovantes) {
    if (regras.situacao !== 'pronto') {
      return;
    }
    emEnvio.current = true;
    setEnviando(true);
    setErros({});
    setConflito(null);
    setErroGeral(null);
    try {
      await criarCliente({ ...valores, versaoTermosAceita: regras.dados.versaoTermos, ...corpoDosComprovantes(provas) });
    } catch (falha) {
      tratarErroDoCadastro(falha instanceof ErroDaApi ? falha : new ErroDaApi('servidor'), provas);
      emEnvio.current = false;
      setEnviando(false);
      return;
    }
    await entrarDepoisDeCriar(provas);
  }

  function aoComprovar(campo: CampoDeContato, comprovante: string) {
    const provas = { ...comprovantes, [campo]: comprovante };
    setComprovantes(provas);
    setPosse(null);
    void cadastrar(provas);
  }

  async function entrarDepoisDeCriar(provas: Comprovantes) {
    const { celular, senha } = valores;
    setValores((anteriores) => ({ ...anteriores, senha: '' }));
    try {
      iniciarSessao(await entrar({ login: celular, senha }));
      // Com o e-mail provado (RN61) ele já nasce confirmado e nenhum link é mandado.
      const estado = provas.email === undefined ? { aviso: AVISO_DO_EMAIL } : undefined;
      void navegar(CAMINHOS.conta, { replace: true, state: estado });
    } catch {
      void navegar(CAMINHOS.entrar, {
        replace: true,
        state: { aviso: 'Sua conta foi criada. Entre com seu celular e sua senha.' },
      });
    }
  }

  function tratarErroDoCadastro(erro: ErroDaApi, provas: Comprovantes) {
    if (erro.codigo === 'termos-desatualizados') {
      setAceite(false);
      regras.tentarDeNovo();
      mostrarErros({ aceite: 'Os termos mudaram. Leia e aceite de novo para continuar.' });
      return;
    }
    const porCampo: Erros = {};
    for (const [nome, mensagem] of Object.entries(erro.campos)) {
      const campo = campoDaApi(nome);
      if (campo) {
        porCampo[campo] = mensagem;
      }
    }
    if (erro.codigo === 'comprovante-invalido') {
      // Venceu (30 min) ou não vale: descarta e oferece provar de novo o contato que tinha comprovante.
      setComprovantes({});
      setConflito({ mensagem: erro.message, campo: campoComComprovante(provas) });
      return;
    }
    if (erro.codigo === 'contato-em-uso') {
      setConflito({ mensagem: erro.message, campo: campoDeContato(erro.campos) });
    } else if (Object.keys(porCampo).length === 0) {
      setErroGeral(erro.message);
    }
    mostrarErros(porCampo);
  }

  const podeEnviar = regras.situacao === 'pronto' && !enviando && posse === null;

  return (
    <>
      <form className={estilos.cartao} onSubmit={enviar} noValidate>
        <CampoTexto
          ref={refNome}
          nome="nome"
          rotulo="Seu nome"
          valor={valores.nome}
          aoMudar={mudar('nome')}
          autoComplete="name"
          erro={erros.nome}
        />
        <CampoTexto
          ref={refCelular}
          nome="celular"
          rotulo="Celular"
          tipo="tel"
          inputMode="tel"
          valor={valores.celular}
          aoMudar={mudar('celular')}
          autoComplete="tel"
          somenteLeitura={posse !== null}
          erro={erros.celular}
        />
        <CampoTexto
          ref={refEmail}
          nome="email"
          rotulo="E-mail"
          tipo="email"
          inputMode="email"
          valor={valores.email}
          aoMudar={mudar('email')}
          autoComplete="email"
          somenteLeitura={emailFixo !== undefined || posse !== null}
          erro={erros.email}
        />
        <CampoTexto
          ref={refCep}
          nome="cep"
          rotulo="Seu CEP"
          inputMode="numeric"
          valor={valores.cep}
          aoMudar={mudar('cep')}
          autoComplete="postal-code"
          erro={erros.cep}
        />
        <CampoSenha
          ref={refSenha}
          nome="senha"
          rotulo="Crie uma senha"
          valor={valores.senha}
          aoMudar={mudar('senha')}
          autoComplete="new-password"
          dica="Use pelo menos 8 caracteres. Não use seu celular nem seu e-mail."
          erro={erros.senha}
        />
        <div className={estilos.aceite}>
          <input
            ref={refAceite}
            type="checkbox"
            id={idDoAceite}
            checked={aceite}
            onChange={(evento) => setAceite(evento.target.checked)}
            aria-invalid={erros.aceite ? true : undefined}
            aria-describedby={erros.aceite ? `${idDoAceite}-erro` : undefined}
          />
          <label htmlFor={idDoAceite}>
            Li e aceito os <Link to={CAMINHOS.termos}>termos de uso</Link> e a{' '}
            <Link to={CAMINHOS.privacidade}>política de privacidade</Link>, inclusive a regra de só trocar contato depois
            de contratar.
          </label>
          {erros.aceite && (
            <p className={estilos.erroDoAceite} id={`${idDoAceite}-erro`}>
              {erros.aceite}
            </p>
          )}
        </div>
        {regras.situacao === 'erro' && <FalhaAoCarregar erro={regras.erro} aoTentarDeNovo={regras.tentarDeNovo} />}
        {conflito && posse === null && (
          <Nota tipo="erro" anunciar>
            <p>{conflito.mensagem}</p>
            <p className={estilos.links}>
              <Link to={CAMINHOS.entrar}>Entrar</Link>
              <Link to={CAMINHOS.esqueciSenha}>Esqueci minha senha</Link>
            </p>
            {conflito.campo && (
              <Botao variante="contorno" onClick={() => setPosse(conflito.campo)}>
                {conflito.campo === 'celular' ? 'Esse celular é meu' : 'Esse e-mail é meu'}
              </Botao>
            )}
          </Nota>
        )}
        {erroGeral && (
          <Nota tipo="erro" anunciar>
            {erroGeral}
          </Nota>
        )}
        <Botao type="submit" tamanho="lg" desabilitado={!podeEnviar}>
          {enviando ? 'Criando sua conta…' : 'Criar conta'}
        </Botao>
      </form>
      {posse && (
        <PosseNoCadastro
          campo={posse}
          valor={valores[posse]}
          aoComprovar={(comprovante) => aoComprovar(posse, comprovante)}
          aoCancelar={() => setPosse(null)}
        />
      )}
    </>
  );
}
