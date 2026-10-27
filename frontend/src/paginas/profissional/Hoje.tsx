import { useCallback, useState } from 'react';
import { Link } from 'react-router';

import { useSessao } from '../../api/SessaoProvider';
import { buscarDinheiro, listarMinhasDiarias } from '../../api/areaDoProfissional';
import { buscarContrato } from '../../api/contratos';
import { cheguei } from '../../api/diarias';
import type { DiariaDoProfissional, DinheiroDoProfissional } from '../../api/tipos';
import { useRecurso } from '../../api/useRecurso';
import { CAMINHOS, caminhoDeTerminar, caminhoDoContratoDoProfissional } from '../../caminhos';
import { Botao } from '../../componentes/Botao/Botao';
import { FalhaAoCarregar } from '../../componentes/FalhaAoCarregar/FalhaAoCarregar';
import { Nota } from '../../componentes/Nota/Nota';
import { StatusDiaria } from '../../componentes/StatusDiaria/StatusDiaria';
import { formatarCelular } from '../../formatacao/contato';
import { formatarDiaEHora, formatarDiaLongo } from '../../formatacao/datas';
import { formatarReais } from '../../formatacao/dinheiro';
import { Icone } from '../../icones/Icone';
import { useTitulo } from '../../layout/titulo';
import { useEnvioUnico } from '../conta/useEnvioUnico';
import estilos from './Profissional.module.css';
import { horaEmSaoPaulo, saudacao } from './textos';

interface DadosDeHoje {
  hoje: string;
  diarias: DiariaDoProfissional[];
  dinheiro: DinheiroDoProfissional;
}

async function carregar(sinal: AbortSignal): Promise<DadosDeHoje> {
  const [diarias, dinheiro] = await Promise.all([listarMinhasDiarias(sinal), buscarDinheiro(sinal)]);
  return { hoje: diarias.hoje, diarias: diarias.itens, dinheiro };
}

const PROXIMOS = 4;

/** Hoje (FE-06, protótipo proHoje): o trabalho do dia, quem falta aprovar, os próximos dias e o que vai cair. */
export function Hoje() {
  useTitulo('Hoje');
  const usuario = useSessao();
  const recurso = useRecurso(carregar);
  const primeiroNome = (usuario?.nome ?? '').trim().split(/\s+/)[0];

  return (
    <section className={`largura ${estilos.pagina}`}>
      <header className={estilos.topo}>
        <p className={estilos.saudacao}>{saudacao(horaEmSaoPaulo())}</p>
        <h1>{primeiroNome}</h1>
      </header>
      {recurso.situacao === 'carregando' && <p role="status">Carregando…</p>}
      {recurso.situacao === 'erro' && <FalhaAoCarregar erro={recurso.erro} aoTentarDeNovo={recurso.tentarDeNovo} />}
      {recurso.situacao === 'pronto' && <Painel dados={recurso.dados} aoMudar={recurso.tentarDeNovo} />}
    </section>
  );
}

function Painel({ dados, aoMudar }: { dados: DadosDeHoje; aoMudar: () => void }) {
  const hoje = dados.hoje;
  const doDia = dados.diarias.find((d) => d.data === hoje && (d.status === 'paga' || d.status === 'andamento'));
  const esperando = dados.diarias.filter((d) => d.status === 'aguardando');
  const proximos = dados.diarias
    .filter((d) => d.data > hoje && d.status !== 'liberada' && d.status !== 'reembolsada')
    .slice(0, PROXIMOS);

  return (
    <>
      {doDia ? (
        <CartaoDeHoje diaria={doDia} aoMudar={aoMudar} />
      ) : (
        <div className={estilos.cartao}>
          <h2>Nenhuma obra hoje</h2>
          <p className={estilos.apoio}>
            {proximos[0]
              ? `Sua próxima diária é ${formatarDiaLongo(proximos[0].data)}.`
              : 'Quando alguém contratar você, aparece aqui.'}
          </p>
        </div>
      )}

      {esperando.length > 0 && (
        <section className={estilos.cartao} aria-labelledby="titulo-esperando">
          <h2 id="titulo-esperando">Esperando o cliente</h2>
          <ul className={estilos.lista}>
            {esperando.map((d) => (
              <li key={d.id}>
                <span className={estilos.linhaLink}>
                  <b>
                    {formatarDiaLongo(d.data)} · {d.cliente}
                  </b>
                  {d.autoLiberaEm && (
                    <span className={estilos.apoio}>
                      Se o cliente não responder, cai sozinho em {formatarDiaEHora(d.autoLiberaEm)}.
                    </span>
                  )}
                </span>
                <b className="valor">{formatarReais(d.repasse)}</b>
              </li>
            ))}
          </ul>
        </section>
      )}

      <section className={estilos.cartao} aria-labelledby="titulo-proximos">
        <div className={estilos.secaoTopo}>
          <h2 id="titulo-proximos">Próximos dias</h2>
          <Link className={estilos.link} to={CAMINHOS.profissionalAgenda}>
            Ver agenda
          </Link>
        </div>
        <ul className={estilos.lista}>
          {proximos.length === 0 && <li className={estilos.apoio}>Nenhuma diária marcada.</li>}
          {proximos.map((d) => (
            <li key={d.id}>
              <Link className={estilos.linhaLink} to={caminhoDoContratoDoProfissional(d.contratoId)}>
                <b>{formatarDiaLongo(d.data)}</b>
                <span className={estilos.apoio}>
                  {d.cliente} · {d.cidade.nome}
                </span>
              </Link>
              <StatusDiaria estado={d.status} lado="profissional" />
            </li>
          ))}
        </ul>
      </section>

      <Link className={estilos.vaiCair} to={CAMINHOS.profissionalDinheiro}>
        <span>
          <span className={estilos.rotulo}>Vai cair no seu Pix</span>
          <span className={estilos.valorGrande}>{formatarReais(dados.dinheiro.vaiCair)}</span>
        </span>
        <Icone nome="next" tamanho={28} />
      </Link>
    </>
  );
}

function CartaoDeHoje({ diaria, aoMudar }: { diaria: DiariaDoProfissional; aoMudar: () => void }) {
  const buscar = useCallback((sinal: AbortSignal) => buscarContrato(diaria.contratoId, sinal), [diaria.contratoId]);
  const contrato = useRecurso(buscar, undefined, diaria.contratoId);
  const [erro, setErro] = useState<string | null>(null);
  const { enviando, executar } = useEnvioUnico();

  function marcarChegada() {
    setErro(null);
    void executar(
      async () => {
        await cheguei(diaria.id);
        aoMudar();
      },
      (falha) => setErro(falha.message),
    );
  }

  const dados = contrato.situacao === 'pronto' ? contrato.dados : null;
  return (
    <article className={estilos.hoje} aria-labelledby="titulo-hoje">
      <div className={estilos.hojeTopo}>
        <b>Hoje · {formatarDiaLongo(diaria.data)}</b>
        <StatusDiaria estado={diaria.status} lado="profissional" />
      </div>
      <h2 id="titulo-hoje">Obra de {diaria.cliente}</h2>
      {diaria.servicos.length > 0 && <p className={estilos.apoio}>{diaria.servicos.join(', ')}</p>}
      {contrato.situacao === 'carregando' && <p role="status">Carregando o endereço…</p>}
      {contrato.situacao === 'erro' && (
        <FalhaAoCarregar erro={contrato.erro} aoTentarDeNovo={contrato.tentarDeNovo} />
      )}
      {dados && (
        <div className={estilos.onde}>
          {dados.endereco && (
            <p>
              <Icone nome="pin" tamanho={22} />
              <span>
                <b>
                  {dados.endereco.logradouro}, {dados.endereco.numero}
                  {dados.endereco.complemento ? ` · ${dados.endereco.complemento}` : ''}
                </b>
                <br />
                {dados.endereco.bairro ? `${dados.endereco.bairro} · ` : ''}
                {dados.cidade.nome}
              </span>
            </p>
          )}
          {dados.contato && (
            <p>
              <Icone nome="phone" tamanho={22} />
              <span>
                <b className={estilos.telefone}>{formatarCelular(dados.contato.celular)}</b> · {dados.contato.nome}
              </span>
            </p>
          )}
          <Link className={estilos.link} to={caminhoDoContratoDoProfissional(diaria.contratoId)}>
            Ver o pedido
          </Link>
        </div>
      )}
      {diaria.status === 'paga' ? (
        <>
          <Botao tamanho="xl" onClick={marcarChegada} desabilitado={enviando}>
            <Icone nome="pin" tamanho={26} />
            Cheguei na obra
          </Botao>
          <p className={estilos.apoio}>Avisa o cliente que você chegou. Ajuda se tiver alguma reclamação depois.</p>
        </>
      ) : (
        <>
          {diaria.chegouEm && (
            <Nota tipo="ok">Você chegou às {formatarDiaEHora(diaria.chegouEm).split(' às ')[1]}.</Nota>
          )}
          <Botao tamanho="xl" para={caminhoDeTerminar(diaria.id)}>
            <Icone nome="camera" tamanho={26} />
            Terminei o dia
          </Botao>
          <p className={estilos.apoio}>Você tira uma foto do serviço e o cliente aprova o dia.</p>
        </>
      )}
      {erro && (
        <Nota tipo="erro" anunciar>
          {erro}
        </Nota>
      )}
    </article>
  );
}
