import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router';

import { confirmarCelular, pedirCodigoDoCelular } from '../../api/contas';
import type { ErroDaApi } from '../../api/erros';
import { CAMINHOS } from '../../caminhos';
import { Botao } from '../../componentes/Botao/Botao';
import { CampoCodigo, codigoCompleto } from '../../componentes/CampoCodigo/CampoCodigo';
import { Nota } from '../../componentes/Nota/Nota';
import estilos from '../../layout/PaginaDeFormulario.module.css';
import { useTitulo } from '../../layout/titulo';
import { useEnvioUnico } from './useEnvioUnico';

/** Confirmar o celular da conta com o código por SMS (privada). */
export function ConfirmarCelular() {
  useTitulo('Confirmar celular');
  const navegar = useNavigate();
  const { enviando, executar } = useEnvioUnico();
  const [enviado, setEnviado] = useState<string | null>(null);
  const [codigo, setCodigo] = useState('');
  const [erroDoCodigo, setErroDoCodigo] = useState<string | undefined>();
  const [erro, setErro] = useState<string | null>(null);

  function voltarComAviso(aviso: string) {
    void navegar(CAMINHOS.conta, { replace: true, state: { aviso } });
  }

  function tratarErro(problema: ErroDaApi) {
    if (problema.codigo === 'celular-ja-confirmado') {
      voltarComAviso(problema.message);
    } else if (problema.codigo === 'codigo-invalido') {
      setErroDoCodigo(problema.message);
    } else {
      setErro(problema.message);
    }
  }

  function pedir() {
    setErro(null);
    void executar(async () => setEnviado(await pedirCodigoDoCelular()), tratarErro);
  }

  function confirmar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    setErro(null);
    if (!codigoCompleto(codigo)) {
      setErroDoCodigo('Digite os 6 números do código.');
      return;
    }
    setErroDoCodigo(undefined);
    void executar(async () => {
      await confirmarCelular(codigo);
      voltarComAviso('Celular confirmado.');
    }, tratarErro);
  }

  return (
    <section className={`largura ${estilos.pagina}`}>
      <div className={estilos.coluna}>
        <header className={estilos.topo}>
          <h1>Confirmar celular</h1>
          <p className={estilos.apagado}>
            Vamos mandar um código por SMS para o celular da sua conta. Com ele confirmado, você também pode entrar só com
            o código.
          </p>
        </header>
        {enviado === null ? (
          <div className={estilos.cartao}>
            {erro && (
              <Nota tipo="erro" anunciar>
                {erro}
              </Nota>
            )}
            <Botao tamanho="lg" onClick={pedir} desabilitado={enviando}>
              {enviando ? 'Mandando…' : 'Mandar código'}
            </Botao>
          </div>
        ) : (
          <form className={estilos.cartao} onSubmit={confirmar} noValidate>
            <Nota tipo="ok" anunciar>
              {enviado}
            </Nota>
            <CampoCodigo
              valor={codigo}
              aoMudar={setCodigo}
              erro={erroDoCodigo}
              aoReenviar={async () => {
                await pedirCodigoDoCelular();
              }}
            />
            {erro && (
              <Nota tipo="erro" anunciar>
                {erro}
              </Nota>
            )}
            <Botao type="submit" tamanho="lg" desabilitado={enviando}>
              {enviando ? 'Confirmando…' : 'Confirmar'}
            </Botao>
          </form>
        )}
        <Link className={estilos.link} to={CAMINHOS.conta}>
          Voltar para minha conta
        </Link>
      </div>
    </section>
  );
}
