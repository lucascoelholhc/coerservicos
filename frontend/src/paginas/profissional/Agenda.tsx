import { useState } from 'react';
import { Link } from 'react-router';

import { buscarAgenda, listarMinhasDiarias, marcarFolga, tirarFolga } from '../../api/areaDoProfissional';
import type { AgendaDoProfissional, DiariaDoProfissional } from '../../api/tipos';
import { useRecurso } from '../../api/useRecurso';
import { caminhoDoContratoDoProfissional } from '../../caminhos';
import { FalhaAoCarregar } from '../../componentes/FalhaAoCarregar/FalhaAoCarregar';
import { Nota } from '../../componentes/Nota/Nota';
import { StatusDiaria } from '../../componentes/StatusDiaria/StatusDiaria';
import { diaDaSemana, formatarDiaCurto, formatarDiaLongo, hojeEmSaoPaulo, somarDias } from '../../formatacao/datas';
import { useTitulo } from '../../layout/titulo';
import { useEnvioUnico } from '../conta/useEnvioUnico';
import estilos from './Profissional.module.css';

interface DadosDaAgenda {
  agenda: AgendaDoProfissional;
  diarias: DiariaDoProfissional[];
}

async function carregar(sinal: AbortSignal): Promise<DadosDaAgenda> {
  const [agenda, diarias] = await Promise.all([buscarAgenda(sinal), listarMinhasDiarias(sinal)]);
  return { agenda, diarias: diarias.itens };
}

/** Quantos dias para frente a agenda mostra (as mesmas 4 semanas do perfil público). */
const DIAS_NA_TELA = 28;

/** Agenda (FE-06, protótipo proAgenda): folgas nos dias de trabalho e as diárias marcadas. */
export function Agenda() {
  useTitulo('Agenda');
  const recurso = useRecurso(carregar);
  return (
    <section className={`largura ${estilos.pagina}`}>
      <header className={estilos.topo}>
        <h1>Agenda</h1>
      </header>
      {recurso.situacao === 'carregando' && <p role="status">Carregando…</p>}
      {recurso.situacao === 'erro' && <FalhaAoCarregar erro={recurso.erro} aoTentarDeNovo={recurso.tentarDeNovo} />}
      {recurso.situacao === 'pronto' && <Conteudo dados={recurso.dados} />}
    </section>
  );
}

function Conteudo({ dados }: { dados: DadosDaAgenda }) {
  const hoje = hojeEmSaoPaulo();
  const marcadas = dados.diarias.filter((d) => d.data >= hoje && d.status !== 'reembolsada');
  return (
    <>
      <Folgas agenda={dados.agenda} ocupados={new Set(marcadas.map((d) => d.data))} hoje={hoje} />
      <section className={estilos.cartao} aria-labelledby="titulo-marcadas">
        <h2 id="titulo-marcadas">Diárias marcadas</h2>
        <ul className={estilos.lista}>
          {marcadas.length === 0 && <li className={estilos.apoio}>Nenhuma diária marcada ainda.</li>}
          {marcadas.map((d) => (
            <li key={d.id}>
              <Link className={estilos.linhaLink} to={caminhoDoContratoDoProfissional(d.contratoId)}>
                <b>
                  {formatarDiaLongo(d.data)}
                  {d.data === hoje ? ' · hoje' : ''}
                </b>
                <span className={estilos.apoio}>
                  {d.cliente} · {d.cidade.nome}
                </span>
                {d.servicos.length > 0 && <span className={estilos.apoio}>{d.servicos.join(', ')}</span>}
              </Link>
              <StatusDiaria estado={d.status} lado="profissional" />
            </li>
          ))}
        </ul>
      </section>
    </>
  );
}

function Folgas({ agenda, ocupados, hoje }: { agenda: AgendaDoProfissional; ocupados: Set<string>; hoje: string }) {
  const [folgas, setFolgas] = useState(() => new Set(agenda.folgas));
  const [erro, setErro] = useState<string | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);
  const { enviando, executar } = useEnvioUnico();
  const trabalho = new Set(agenda.diasDaSemana);
  const dias = Array.from({ length: DIAS_NA_TELA }, (_, i) => somarDias(hoje, i + 1)).filter((dia) =>
    trabalho.has(diaDaSemana(dia)),
  );

  function alternar(dia: string) {
    setErro(null);
    setAviso(null);
    const deFolga = folgas.has(dia);
    void executar(
      async () => {
        await (deFolga ? tirarFolga(dia) : marcarFolga(dia));
        setFolgas((antes) => {
          const novas = new Set(antes);
          if (deFolga) {
            novas.delete(dia);
          } else {
            novas.add(dia);
          }
          return novas;
        });
        setAviso(
          deFolga
            ? `${formatarDiaLongo(dia)} voltou para a agenda.`
            : `${formatarDiaLongo(dia)} é folga. Ninguém consegue contratar você nesse dia.`,
        );
      },
      (falha) => setErro(falha.message),
    );
  }

  return (
    <section className={estilos.cartao} aria-labelledby="titulo-folgas">
      <h2 id="titulo-folgas">Seus dias</h2>
      <p className={estilos.apoio}>Toque num dia para marcar folga. Toque de novo para voltar a trabalhar.</p>
      {dias.length === 0 ? (
        <p className={estilos.apoio}>Você não marcou dias da semana de trabalho no cadastro.</p>
      ) : (
        <ul className={estilos.dias}>
          {dias.map((dia) => {
            const ocupado = ocupados.has(dia);
            const deFolga = folgas.has(dia);
            const situacao = ocupado ? 'Diária' : deFolga ? 'Folga' : 'Livre';
            return (
              <li key={dia}>
                <button
                  type="button"
                  className={estilos.dia}
                  aria-pressed={deFolga}
                  aria-label={`${formatarDiaLongo(dia)}: ${situacao}`}
                  disabled={ocupado || enviando}
                  onClick={() => alternar(dia)}
                >
                  <b>{formatarDiaCurto(dia)}</b>
                  <span>{situacao}</span>
                </button>
              </li>
            );
          })}
        </ul>
      )}
      {aviso && (
        <Nota tipo="ok" anunciar>
          {aviso}
        </Nota>
      )}
      {erro && (
        <Nota tipo="erro" anunciar>
          {erro}
        </Nota>
      )}
    </section>
  );
}
