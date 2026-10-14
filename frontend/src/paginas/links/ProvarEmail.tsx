import { useEffect, useRef, useState } from 'react';

import { entregarComprovanteDoEmail } from '../../api/canalDaPosse';
import { comoErroDaApi, type ErroDaApi } from '../../api/erros';
import { confirmarPosseDoEmail, type PosseDoEmail } from '../../api/posse';
import { lerTokenDoFragmento, limparFragmento } from '../../api/tokenDoFragmento';
import { CAMINHOS } from '../../caminhos';
import { Botao } from '../../componentes/Botao/Botao';
import { FalhaAoCarregar } from '../../componentes/FalhaAoCarregar/FalhaAoCarregar';
import { Nota } from '../../componentes/Nota/Nota';
import estilos from '../../layout/PaginaDeFormulario.module.css';
import { useTitulo } from '../../layout/titulo';
import { FormularioDoCliente } from '../criar-conta/FormularioDoCliente';

type Estado =
  | { tipo: 'confirmando' }
  | { tipo: 'entregue' }
  | { tipo: 'continuar'; posse: PosseDoEmail }
  | { tipo: 'invalido'; mensagem?: string }
  | { tipo: 'erro'; erro: ErroDaApi };

/**
 * Link da prova de posse do e-mail (/provar-email#token=..., RN61). O fragmento sai da barra de
 * endereço antes de qualquer chamada; o comprovante vai para a aba do cadastro pelo canal da posse.
 * Sem resposta, o cadastro continua aqui, com o e-mail fixo e o resto em branco.
 */
export function ProvarEmail() {
  useTitulo('Confirmar que o e-mail é seu');
  const [token] = useState(lerTokenDoFragmento);
  const [estado, setEstado] = useState<Estado>(token ? { tipo: 'confirmando' } : { tipo: 'invalido' });
  const enviado = useRef(false);

  async function confirmar(valor: string) {
    try {
      const posse = await confirmarPosseDoEmail(valor);
      const entregue = await entregarComprovanteDoEmail(posse.email, posse.comprovante);
      setEstado(entregue ? { tipo: 'entregue' } : { tipo: 'continuar', posse });
    } catch (falha) {
      const erro = comoErroDaApi(falha);
      setEstado(erro.codigo === 'link-invalido' ? { tipo: 'invalido', mensagem: erro.message } : { tipo: 'erro', erro });
    }
  }

  useEffect(() => {
    limparFragmento();
    if (token && !enviado.current) {
      enviado.current = true;
      void confirmar(token);
    }
  }, [token]);

  return (
    <section className={`largura ${estilos.pagina}`}>
      <div className={estilos.coluna}>
        {estado.tipo === 'confirmando' && <p role="status">Confirmando seu e-mail…</p>}
        {estado.tipo === 'entregue' && (
          <header className={estilos.topo}>
            <h1>E-mail confirmado</h1>
            <p>Pronto. Volte para a aba do cadastro para terminar.</p>
          </header>
        )}
        {estado.tipo === 'continuar' && (
          <>
            <header className={estilos.topo}>
              <h1>Termine seu cadastro</h1>
              <p className={estilos.apagado}>Seu e-mail está confirmado. Preencha o resto para criar a conta.</p>
            </header>
            <FormularioDoCliente emailFixo={estado.posse.email} comprovanteEmail={estado.posse.comprovante} />
          </>
        )}
        {estado.tipo === 'invalido' && (
          <>
            <header className={estilos.topo}>
              <h1>Este link não vale mais</h1>
            </header>
            {estado.mensagem ? (
              <Nota tipo="erro" anunciar>
                {estado.mensagem}
              </Nota>
            ) : (
              <p>O link está incompleto, já foi usado ou venceu.</p>
            )}
            <p>Volte ao cadastro e toque em “Esse e-mail é meu” para receber outro link.</p>
            <Botao para={CAMINHOS.criarContaCliente} variante="contorno">
              Voltar ao cadastro
            </Botao>
          </>
        )}
        {estado.tipo === 'erro' && token && (
          <>
            <header className={estilos.topo}>
              <h1>Confirmar que o e-mail é seu</h1>
            </header>
            <FalhaAoCarregar
              erro={estado.erro}
              aoTentarDeNovo={() => {
                setEstado({ tipo: 'confirmando' });
                void confirmar(token);
              }}
            />
          </>
        )}
      </div>
    </section>
  );
}
