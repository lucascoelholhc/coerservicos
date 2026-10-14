import { useEffect, useId, useRef, useState, type FormEvent } from 'react';

import { comoErroDaApi, type ErroDaApi } from '../../api/erros';
import { confirmarPosseDoCelular, pedirPosseDoCelular, pedirPosseDoEmail } from '../../api/posse';
import { Botao } from '../../componentes/Botao/Botao';
import { CampoCodigo, codigoCompleto } from '../../componentes/CampoCodigo/CampoCodigo';
import { FalhaAoCarregar } from '../../componentes/FalhaAoCarregar/FalhaAoCarregar';
import { Nota } from '../../componentes/Nota/Nota';
import { useEnvioUnico } from '../conta/useEnvioUnico';
import estilos from './PosseNoCadastro.module.css';

export type CampoDeContato = 'celular' | 'email';

interface PosseNoCadastroProps {
  campo: CampoDeContato;
  /** O contato como a pessoa digitou no cadastro (a API normaliza). */
  valor: string;
  aoComprovar: (comprovante: string) => void;
  aoCancelar: () => void;
}

type Envio = { tipo: 'enviando' } | { tipo: 'enviado'; mensagem: string } | { tipo: 'falhou'; erro: ErroDaApi };

/** Manda o código ou o link uma vez ao abrir (seguro no StrictMode) e deixa tentar de novo. */
function useEnvioAoAbrir(pedir: () => Promise<string>) {
  const [envio, setEnvio] = useState<Envio>({ tipo: 'enviando' });
  const pedido = useRef(pedir);
  const jaPediu = useRef(false);

  async function mandar() {
    try {
      setEnvio({ tipo: 'enviado', mensagem: await pedido.current() });
    } catch (falha) {
      setEnvio({ tipo: 'falhou', erro: comoErroDaApi(falha) });
    }
  }

  useEffect(() => {
    if (!jaPediu.current) {
      jaPediu.current = true;
      void mandar();
    }
  }, []);

  function tentarDeNovo() {
    setEnvio({ tipo: 'enviando' });
    void mandar();
  }

  return { envio, tentarDeNovo };
}

/** RN61 no cadastro: provar que o celular ou o e-mail em uso por outra conta é seu. */
export function PosseNoCadastro({ campo, valor, aoComprovar, aoCancelar }: PosseNoCadastroProps) {
  const idDoTitulo = useId();
  const refTitulo = useRef<HTMLHeadingElement>(null);

  useEffect(() => {
    refTitulo.current?.focus();
  }, []);

  return (
    <section className={estilos.posse} aria-labelledby={idDoTitulo}>
      <h2 id={idDoTitulo} ref={refTitulo} tabIndex={-1}>
        {campo === 'celular' ? 'Confirme que este celular é seu' : 'Confirme que este e-mail é seu'}
      </h2>
      {campo === 'celular' ? (
        <PeloCelular celular={valor} aoComprovar={aoComprovar} />
      ) : (
        <PeloEmail email={valor} />
      )}
      <Botao variante="suave" onClick={aoCancelar}>
        Cancelar
      </Botao>
    </section>
  );
}

function PeloCelular({ celular, aoComprovar }: { celular: string; aoComprovar: (comprovante: string) => void }) {
  const { envio, tentarDeNovo } = useEnvioAoAbrir(() => pedirPosseDoCelular(celular));
  const { enviando, executar } = useEnvioUnico();
  const [codigo, setCodigo] = useState('');
  const [erroDoCodigo, setErroDoCodigo] = useState<string | undefined>();
  const [erro, setErro] = useState<string | null>(null);

  function confirmar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    setErro(null);
    if (!codigoCompleto(codigo)) {
      setErroDoCodigo('Digite os 6 números do código.');
      return;
    }
    setErroDoCodigo(undefined);
    void executar(
      async () => aoComprovar(await confirmarPosseDoCelular({ celular, codigo })),
      (problema) => {
        if (problema.codigo === 'codigo-invalido') {
          setErroDoCodigo(problema.message);
        } else {
          setErro(problema.message);
        }
      },
    );
  }

  if (envio.tipo === 'enviando') {
    return <p role="status">Mandando o código…</p>;
  }
  if (envio.tipo === 'falhou') {
    return <FalhaAoCarregar erro={envio.erro} aoTentarDeNovo={tentarDeNovo} />;
  }
  return (
    <form className={estilos.formulario} onSubmit={confirmar} noValidate>
      <Nota tipo="ok" anunciar>
        {envio.mensagem}
      </Nota>
      <CampoCodigo
        valor={codigo}
        aoMudar={setCodigo}
        erro={erroDoCodigo}
        aoReenviar={async () => {
          await pedirPosseDoCelular(celular);
        }}
      />
      {erro && (
        <Nota tipo="erro" anunciar>
          {erro}
        </Nota>
      )}
      <Botao type="submit" variante="escuro" desabilitado={enviando}>
        {enviando ? 'Confirmando…' : 'Confirmar'}
      </Botao>
    </form>
  );
}

function PeloEmail({ email }: { email: string }) {
  const { envio, tentarDeNovo } = useEnvioAoAbrir(() => pedirPosseDoEmail(email));
  const [retorno, setRetorno] = useState<{ tipo: 'ok' | 'erro'; texto: string } | null>(null);

  async function mandarDeNovo() {
    setRetorno(null);
    try {
      await pedirPosseDoEmail(email);
      setRetorno({ tipo: 'ok', texto: 'Mandamos o link de novo.' });
    } catch (falha) {
      setRetorno({ tipo: 'erro', texto: comoErroDaApi(falha).message });
    }
  }

  if (envio.tipo === 'enviando') {
    return <p role="status">Mandando o link…</p>;
  }
  if (envio.tipo === 'falhou') {
    return <FalhaAoCarregar erro={envio.erro} aoTentarDeNovo={tentarDeNovo} />;
  }
  return (
    <>
      <Nota tipo="ok" anunciar>
        {`Enviamos um link para ${email}. Abra o link neste aparelho, no mesmo navegador. Quando você confirmar, o cadastro continua.`}
      </Nota>
      <Botao variante="contorno" onClick={() => void mandarDeNovo()}>
        Mandar o link de novo
      </Botao>
      {retorno && (
        <Nota tipo={retorno.tipo} anunciar>
          {retorno.texto}
        </Nota>
      )}
    </>
  );
}
