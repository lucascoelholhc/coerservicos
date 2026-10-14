import { useState, type FormEvent } from 'react';
import { Link } from 'react-router';

import { buscarMinhaConta, definirMfa, pedirCodigoDoMfa } from '../../api/contas';
import type { ErroDaApi } from '../../api/erros';
import type { MinhaConta } from '../../api/tipos';
import { useRecurso } from '../../api/useRecurso';
import { CAMINHOS } from '../../caminhos';
import { Botao } from '../../componentes/Botao/Botao';
import { CampoCodigo, codigoCompleto } from '../../componentes/CampoCodigo/CampoCodigo';
import { FalhaAoCarregar } from '../../componentes/FalhaAoCarregar/FalhaAoCarregar';
import { Nota } from '../../componentes/Nota/Nota';
import estilos from '../../layout/PaginaDeFormulario.module.css';
import { useTitulo } from '../../layout/titulo';
import { useEnvioUnico } from './useEnvioUnico';

/** Código por SMS no login (MFA): opcional para cliente e profissional, obrigatório para a equipe (privada). */
export function CodigoNoLogin() {
  useTitulo('Código no login');
  const conta = useRecurso(buscarMinhaConta);
  const [feito, setFeito] = useState<string | null>(null);

  return (
    <section className={`largura ${estilos.pagina}`}>
      <div className={estilos.coluna}>
        <header className={estilos.topo}>
          <h1>Código no login</h1>
          <p className={estilos.apagado}>
            Com o código no login, além da senha, pedimos um código enviado por SMS sempre que você entrar.
          </p>
        </header>
        {feito && (
          <Nota tipo="ok" anunciar>
            {feito}
          </Nota>
        )}
        {conta.situacao === 'carregando' && <p role="status">Carregando…</p>}
        {conta.situacao === 'erro' && <FalhaAoCarregar erro={conta.erro} aoTentarDeNovo={conta.tentarDeNovo} />}
        {(conta.situacao === 'pronto' || conta.situacao === 'vazio') && (
          <Ajuste
            conta={conta.dados}
            aoComecar={() => setFeito(null)}
            aoTerminar={(texto) => {
              setFeito(texto);
              conta.tentarDeNovo();
            }}
          />
        )}
        <Link className={estilos.link} to={CAMINHOS.conta}>
          Voltar para minha conta
        </Link>
      </div>
    </section>
  );
}

interface AjusteProps {
  conta: MinhaConta;
  aoComecar: () => void;
  aoTerminar: (texto: string) => void;
}

function Ajuste({ conta, aoComecar, aoTerminar }: AjusteProps) {
  const admin = conta.papeis.includes('ADMIN');
  const ligado = admin || conta.mfaAtivo;
  const estado = <p className={estilos.estado}>{`Agora: ${ligado ? 'ligado' : 'desligado'}`}</p>;

  if (admin) {
    return (
      <div className={estilos.cartao}>
        {estado}
        <Nota>Para a equipe da COE, o código por SMS no login é obrigatório.</Nota>
      </div>
    );
  }
  if (!conta.celularConfirmado) {
    return (
      <div className={estilos.cartao}>
        {estado}
        <Nota tipo="atencao">Para usar o código no login, confirme seu celular primeiro.</Nota>
        <Botao para={CAMINHOS.confirmarCelular} variante="contorno">
          Confirmar celular
        </Botao>
      </div>
    );
  }
  return (
    <div className={estilos.cartao}>
      {estado}
      <Troca ligado={ligado} aoComecar={aoComecar} aoTerminar={aoTerminar} />
    </div>
  );
}

function Troca({ ligado, aoComecar, aoTerminar }: { ligado: boolean } & Omit<AjusteProps, 'conta'>) {
  const { enviando, executar } = useEnvioUnico();
  const [enviado, setEnviado] = useState<string | null>(null);
  const [codigo, setCodigo] = useState('');
  const [erroDoCodigo, setErroDoCodigo] = useState<string | undefined>();
  const [erro, setErro] = useState<string | null>(null);

  function tratarErro(problema: ErroDaApi) {
    if (problema.codigo === 'codigo-invalido') {
      setErroDoCodigo(problema.message);
    } else {
      setErro(problema.message);
    }
  }

  function pedir() {
    aoComecar();
    setErro(null);
    void executar(async () => setEnviado(await pedirCodigoDoMfa()), tratarErro);
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
      await definirMfa({ ativo: !ligado, codigo });
      aoTerminar(ligado ? 'Código no login desligado.' : 'Código no login ligado.');
    }, tratarErro);
  }

  function cancelar() {
    setEnviado(null);
    setCodigo('');
    setErro(null);
    setErroDoCodigo(undefined);
  }

  const mensagemDeErro = erro && (
    <Nota tipo="erro" anunciar>
      {erro}
    </Nota>
  );

  if (enviado === null) {
    return (
      <>
        {mensagemDeErro}
        <Botao variante={ligado ? 'contorno' : 'principal'} onClick={pedir} desabilitado={enviando}>
          {ligado ? 'Desligar código no login' : 'Ligar código no login'}
        </Botao>
      </>
    );
  }
  return (
    <form className={estilos.formulario} onSubmit={confirmar} noValidate>
      <Nota tipo="ok" anunciar>
        {enviado}
      </Nota>
      <CampoCodigo
        valor={codigo}
        aoMudar={setCodigo}
        erro={erroDoCodigo}
        aoReenviar={async () => {
          await pedirCodigoDoMfa();
        }}
      />
      {mensagemDeErro}
      <div className={estilos.botoes}>
        <Botao type="submit" desabilitado={enviando}>
          {enviando ? 'Confirmando…' : 'Confirmar'}
        </Botao>
        <Botao variante="suave" onClick={cancelar} desabilitado={enviando}>
          Cancelar
        </Botao>
      </div>
    </form>
  );
}
