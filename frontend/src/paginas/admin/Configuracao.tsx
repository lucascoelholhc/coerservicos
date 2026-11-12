import { useState } from 'react';

import { comoErroDaApi } from '../../api/erros';
import {
  buscarConfiguracao,
  mudarParametro,
  pedirCodigoDaAcao,
  type ParametroDaConfiguracao,
} from '../../api/painelAdmin';
import { useRecurso } from '../../api/useRecurso';
import { Botao } from '../../componentes/Botao/Botao';
import { CampoCodigo, codigoCompleto } from '../../componentes/CampoCodigo/CampoCodigo';
import { CampoTexto } from '../../componentes/CampoTexto/CampoTexto';
import { FalhaAoCarregar } from '../../componentes/FalhaAoCarregar/FalhaAoCarregar';
import { Nota } from '../../componentes/Nota/Nota';
import { formatarDiaEHora } from '../../formatacao/datas';
import { useTitulo } from '../../layout/titulo';
import { useEnvioUnico } from '../conta/useEnvioUnico';
import estilos from './Admin.module.css';

async function carregar(sinal: AbortSignal): Promise<ParametroDaConfiguracao[]> {
  return (await buscarConfiguracao(sinal)).itens;
}

/**
 * Configuração dos parâmetros de negócio (FE-07, RN44g): cada mudança pede o código SMS da ação, na hora, e vale para
 * os contratos novos (contrato fechado mantém o que foi copiado). Os valores e os limites quem confere é a API.
 */
export function Configuracao() {
  useTitulo('Configuração');
  const recurso = useRecurso(carregar);

  return (
    <section className={`largura ${estilos.pagina}`}>
      <header className={estilos.topo}>
        <p className={estilos.chamada}>Equipe COE</p>
        <h1>Configuração</h1>
        <p className={estilos.apagado}>Mudar um valor pede um código por SMS no seu celular.</p>
      </header>
      {recurso.situacao === 'carregando' && <p role="status">Carregando a configuração…</p>}
      {recurso.situacao === 'erro' && <FalhaAoCarregar erro={recurso.erro} aoTentarDeNovo={recurso.tentarDeNovo} />}
      {(recurso.situacao === 'pronto' || recurso.situacao === 'vazio') && <Lista iniciais={recurso.dados} />}
    </section>
  );
}

function Lista({ iniciais }: { iniciais: ParametroDaConfiguracao[] }) {
  const [parametros, setParametros] = useState(iniciais);
  const [mudando, setMudando] = useState<string | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);

  function salvo(novo: ParametroDaConfiguracao) {
    setParametros((atuais) => atuais.map((p) => (p.chave === novo.chave ? novo : p)));
    setMudando(null);
    setAviso('Mudança salva. Vale para os contratos novos.');
  }

  return (
    <>
      {aviso && (
        <Nota tipo="ok" anunciar>
          {aviso}
        </Nota>
      )}
      <ul className={estilos.lista}>
        {parametros.map((parametro) => (
          <li key={parametro.chave}>
            <p>
              <strong>{parametro.descricao}</strong>
            </p>
            <p className={estilos.apagado}>
              {`${parametro.chave} · ${parametro.valor} · desde ${formatarDiaEHora(parametro.vigenteDesde)}`}
            </p>
            {mudando === parametro.chave ? (
              <Mudanca parametro={parametro} aoSalvar={salvo} aoCancelar={() => setMudando(null)} />
            ) : (
              <Botao
                variante="contorno"
                tamanho="sm"
                onClick={() => {
                  setAviso(null);
                  setMudando(parametro.chave);
                }}
              >
                {`Mudar ${parametro.chave}`}
              </Botao>
            )}
          </li>
        ))}
      </ul>
    </>
  );
}

function Mudanca({
  parametro,
  aoSalvar,
  aoCancelar,
}: {
  parametro: ParametroDaConfiguracao;
  aoSalvar: (novo: ParametroDaConfiguracao) => void;
  aoCancelar: () => void;
}) {
  const [valor, setValor] = useState(parametro.valor);
  const [codigo, setCodigo] = useState('');
  const [codigoPedido, setCodigoPedido] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const { enviando, executar } = useEnvioUnico();

  function pedirCodigo() {
    setErro(null);
    void executar(
      async () => {
        await pedirCodigoDaAcao();
        setCodigoPedido(true);
      },
      (falha) => setErro(comoErroDaApi(falha).message),
    );
  }

  function salvar() {
    setErro(null);
    void executar(
      async () => aoSalvar(await mudarParametro(parametro.chave, valor.trim(), codigo)),
      (falha) => {
        setCodigo('');
        setErro(comoErroDaApi(falha).message);
      },
    );
  }

  return (
    <div className={estilos.conferencia}>
      <CampoTexto nome={`valor-${parametro.chave}`} rotulo="Novo valor" valor={valor} aoMudar={setValor} maxLength={60} />
      {erro && (
        <Nota tipo="erro" anunciar>
          {erro}
        </Nota>
      )}
      {codigoPedido ? (
        <>
          <CampoCodigo valor={codigo} aoMudar={setCodigo} aoReenviar={pedirCodigoDaAcao} />
          <Botao variante="sucesso" tamanho="sm" onClick={salvar} desabilitado={enviando || !codigoCompleto(codigo)}>
            Salvar a mudança
          </Botao>
        </>
      ) : (
        <Botao variante="contorno" tamanho="sm" onClick={pedirCodigo} desabilitado={enviando || valor.trim() === ''}>
          Mandar código por SMS
        </Botao>
      )}
      <Botao variante="suave" tamanho="sm" onClick={aoCancelar}>
        Cancelar
      </Botao>
    </div>
  );
}
