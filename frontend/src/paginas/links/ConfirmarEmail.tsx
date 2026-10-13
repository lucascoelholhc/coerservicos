import { useEffect, useRef, useState } from 'react';

import { confirmarEmail, reenviarConfirmacaoDeEmail } from '../../api/contas';
import { ErroDaApi } from '../../api/erros';
import { useSessao } from '../../api/SessaoProvider';
import { lerTokenDoFragmento, limparFragmento } from '../../api/tokenDoFragmento';
import { CAMINHOS } from '../../caminhos';
import { Botao } from '../../componentes/Botao/Botao';
import { FalhaAoCarregar } from '../../componentes/FalhaAoCarregar/FalhaAoCarregar';
import { Nota } from '../../componentes/Nota/Nota';
import { useTitulo } from '../../layout/titulo';
import estilos from './Links.module.css';

type Estado =
  | { tipo: 'confirmando' }
  | { tipo: 'confirmado' }
  | { tipo: 'invalido'; mensagem?: string }
  | { tipo: 'erro'; erro: ErroDaApi };

function comoErroDaApi(falha: unknown): ErroDaApi {
  return falha instanceof ErroDaApi ? falha : new ErroDaApi('servidor');
}

/** Link do e-mail de confirmação (/confirmar-email#token=...). */
export function ConfirmarEmail() {
  useTitulo('Confirmar e-mail');
  const usuario = useSessao();
  const [token] = useState(lerTokenDoFragmento);
  const [estado, setEstado] = useState<Estado>(token ? { tipo: 'confirmando' } : { tipo: 'invalido' });
  const enviado = useRef(false);

  async function enviar(valor: string) {
    try {
      await confirmarEmail(valor);
      setEstado({ tipo: 'confirmado' });
    } catch (falha) {
      const erro = comoErroDaApi(falha);
      setEstado(erro.codigo === 'link-invalido' ? { tipo: 'invalido', mensagem: erro.message } : { tipo: 'erro', erro });
    }
  }

  useEffect(() => {
    // O fragmento sai da barra de endereço antes de qualquer chamada.
    limparFragmento();
    if (token && !enviado.current) {
      enviado.current = true;
      void enviar(token);
    }
  }, [token]);

  return (
    <section className={`largura ${estilos.pagina}`}>
      <div className={estilos.coluna}>
        {estado.tipo === 'confirmando' && <p role="status">Confirmando seu e-mail…</p>}
        {estado.tipo === 'confirmado' && (
          <>
            <h1>E-mail confirmado</h1>
            <p>Agora você consegue recuperar a senha pelo e-mail, se precisar.</p>
            <div className={estilos.botoes}>
              <Botao para={CAMINHOS.inicio}>Ir para o início</Botao>
              {usuario && (
                <Botao para={CAMINHOS.conta} variante="contorno">
                  Minha conta
                </Botao>
              )}
            </div>
          </>
        )}
        {estado.tipo === 'invalido' && <LinkInvalido mensagem={estado.mensagem} logado={usuario !== null} />}
        {estado.tipo === 'erro' && token && (
          <>
            <h1>Confirmar e-mail</h1>
            <FalhaAoCarregar
              erro={estado.erro}
              aoTentarDeNovo={() => {
                setEstado({ tipo: 'confirmando' });
                void enviar(token);
              }}
            />
          </>
        )}
      </div>
    </section>
  );
}

function LinkInvalido({ mensagem, logado }: { mensagem?: string; logado: boolean }) {
  const [retorno, setRetorno] = useState<{ tipo: 'ok' | 'erro'; texto: string } | null>(null);

  async function pedirOutro() {
    try {
      await reenviarConfirmacaoDeEmail();
      setRetorno({ tipo: 'ok', texto: 'Enviamos um link novo. Confira seu e-mail.' });
    } catch (falha) {
      setRetorno({ tipo: 'erro', texto: comoErroDaApi(falha).message });
    }
  }

  return (
    <>
      <h1>Este link não vale mais</h1>
      {mensagem ? (
        <Nota tipo="erro" anunciar>
          {mensagem}
        </Nota>
      ) : (
        <p>O link está incompleto, já foi usado ou venceu.</p>
      )}
      {logado ? (
        <Botao variante="contorno" onClick={() => void pedirOutro()}>
          Pedir outro link
        </Botao>
      ) : (
        <>
          <p>Entre na sua conta para pedir outro link.</p>
          <Botao para={CAMINHOS.entrar} variante="contorno">
            Entrar
          </Botao>
        </>
      )}
      {retorno && (
        <Nota tipo={retorno.tipo} anunciar>
          {retorno.texto}
        </Nota>
      )}
    </>
  );
}
