import { buscarDinheiro, listarMinhasDiarias } from '../../api/areaDoProfissional';
import { buscarCadastro } from '../../api/cadastroProfissional';
import { buscarRegras } from '../../api/regras';
import type { CadastroDoProfissional, DiariaDoProfissional, DinheiroDoProfissional, Regras } from '../../api/tipos';
import { useRecurso } from '../../api/useRecurso';
import { FalhaAoCarregar } from '../../componentes/FalhaAoCarregar/FalhaAoCarregar';
import { StatusDiaria } from '../../componentes/StatusDiaria/StatusDiaria';
import { formatarDiaLongo } from '../../formatacao/datas';
import { formatarReais } from '../../formatacao/dinheiro';
import { Icone } from '../../icones/Icone';
import { useTitulo } from '../../layout/titulo';
import estilos from './Profissional.module.css';

interface DadosDoDinheiro {
  dinheiro: DinheiroDoProfissional;
  diarias: DiariaDoProfissional[];
  regras: Regras;
  cadastro: CadastroDoProfissional | null;
}

async function carregar(sinal: AbortSignal): Promise<DadosDoDinheiro> {
  const [dinheiro, diarias, regras, cadastro] = await Promise.all([
    buscarDinheiro(sinal),
    listarMinhasDiarias(sinal),
    buscarRegras(sinal),
    buscarCadastro(sinal),
  ]);
  return { dinheiro, diarias: diarias.itens, regras, cadastro };
}

/** Seu dinheiro (FE-06, protótipo proDinheiro): liberado (livro-caixa), o que vai cair e o dia a dia. */
export function Dinheiro() {
  useTitulo('Seu dinheiro');
  const recurso = useRecurso(carregar);
  return (
    <section className={`largura ${estilos.pagina}`}>
      <header className={estilos.topo}>
        <h1>Seu dinheiro</h1>
      </header>
      {recurso.situacao === 'carregando' && <p role="status">Carregando…</p>}
      {recurso.situacao === 'erro' && <FalhaAoCarregar erro={recurso.erro} aoTentarDeNovo={recurso.tentarDeNovo} />}
      {recurso.situacao === 'pronto' && <Conteudo dados={recurso.dados} />}
    </section>
  );
}

function Conteudo({ dados }: { dados: DadosDoDinheiro }) {
  const movimento = dados.diarias.filter((d) => d.status !== 'agendada').reverse();
  const emReclamacao = Number(dados.dinheiro.emReclamacao) > 0;
  return (
    <>
      <div className={estilos.grana}>
        <div data-tom="ok">
          <span className={estilos.rotulo}>
            <Icone nome="check" tamanho={18} /> Liberado para você
          </span>
          <b className={estilos.valorGrande}>{formatarReais(dados.dinheiro.liberado)}</b>
          <span className={estilos.apoio}>Dias aprovados pelo cliente ou liberados depois do prazo.</span>
        </div>
        <div>
          <span className={estilos.rotulo}>
            <Icone nome="lock" tamanho={18} /> Vai cair
          </span>
          <b className={estilos.valorGrande}>{formatarReais(dados.dinheiro.vaiCair)}</b>
          <span className={estilos.apoio}>O cliente já pagou. Cai quando cada dia for aprovado.</span>
        </div>
        {emReclamacao && (
          <div data-tom="bad">
            <span className={estilos.rotulo}>
              <Icone nome="alert" tamanho={18} /> Parado em reclamação
            </span>
            <b className={estilos.valorGrande}>{formatarReais(dados.dinheiro.emReclamacao)}</b>
            <span className={estilos.apoio}>A equipe da COE analisa e decide.</span>
          </div>
        )}
      </div>

      <p className={estilos.ajuda}>
        <Icone nome="wallet" tamanho={20} />{' '}
        {dados.cadastro?.chavePix ? (
          <>
            Seu Pix: <b>{dados.cadastro.chavePix}</b>.{' '}
          </>
        ) : null}
        {dados.regras.taxaPagaPor === 'CLIENTE'
          ? 'Você recebe o valor inteiro da diária. A taxa da COE é paga pelo cliente.'
          : 'A taxa da COE é descontada de cada diária.'}
      </p>

      <section className={estilos.cartao} aria-labelledby="titulo-dia-a-dia">
        <h2 id="titulo-dia-a-dia">Dia a dia</h2>
        <ul className={estilos.lista}>
          {movimento.length === 0 && <li className={estilos.apoio}>Nenhuma diária paga ainda.</li>}
          {movimento.map((d) => (
            <li key={d.id}>
              <span className={estilos.linhaLink}>
                <b>
                  {formatarDiaLongo(d.data)} · {d.cliente}
                </b>
                {d.servicos.length > 0 && <span className={estilos.apoio}>{d.servicos.join(', ')}</span>}
              </span>
              <span className={estilos.acoes}>
                <b className="valor">{formatarReais(d.repasse)}</b>
                <StatusDiaria estado={d.status} lado="profissional" />
              </span>
            </li>
          ))}
        </ul>
      </section>
    </>
  );
}
