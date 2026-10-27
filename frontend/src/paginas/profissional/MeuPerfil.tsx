import { buscarAgenda } from '../../api/areaDoProfissional';
import { buscarCadastro } from '../../api/cadastroProfissional';
import { buscarCatalogo } from '../../api/catalogo';
import type { AgendaDoProfissional, CadastroDoProfissional, Catalogo, EtapaDoCadastro } from '../../api/tipos';
import { useRecurso } from '../../api/useRecurso';
import { CAMINHOS, caminhoDoPerfil } from '../../caminhos';
import { Botao } from '../../componentes/Botao/Botao';
import { FalhaAoCarregar } from '../../componentes/FalhaAoCarregar/FalhaAoCarregar';
import { Nota } from '../../componentes/Nota/Nota';
import { useTitulo } from '../../layout/titulo';
import { resumo } from '../cadastro/resumo';
import estilos from './Profissional.module.css';

interface DadosDoPerfil {
  cadastro: CadastroDoProfissional | null;
  catalogo: Catalogo;
  agenda: AgendaDoProfissional;
}

async function carregar(sinal: AbortSignal): Promise<DadosDoPerfil> {
  const [cadastro, catalogo, agenda] = await Promise.all([
    buscarCadastro(sinal),
    buscarCatalogo(sinal),
    buscarAgenda(sinal),
  ]);
  return { cadastro, catalogo, agenda };
}

/** O que aparece para o cliente, na ordem do perfil público. */
const LINHAS: readonly { etapa: EtapaDoCadastro; rotulo: string }[] = [
  { etapa: 'trabalho', rotulo: 'Trabalho' },
  { etapa: 'servicos', rotulo: 'O que eu faço' },
  { etapa: 'experiencia', rotulo: 'Experiência' },
  { etapa: 'valor', rotulo: 'Quanto cobro por dia' },
  { etapa: 'onde', rotulo: 'Onde trabalho' },
  { etapa: 'dias', rotulo: 'Quando trabalho' },
  { etapa: 'sobre', rotulo: 'Sobre mim' },
  { etapa: 'pix', rotulo: 'Onde recebo' },
];

/**
 * Meu perfil (FE-06, protótipo proPerfil), só leitura: o perfil aprovado não muda por aqui ainda (mudar depois da
 * verificação é uma decisão em aberto, ver o plano). Folgas pela Agenda.
 */
export function MeuPerfil() {
  useTitulo('Meu perfil');
  const recurso = useRecurso(carregar);
  return (
    <section className={`largura ${estilos.pagina}`}>
      <header className={estilos.topo}>
        <h1>Meu perfil</h1>
      </header>
      {recurso.situacao === 'carregando' && <p role="status">Carregando…</p>}
      {recurso.situacao === 'erro' && <FalhaAoCarregar erro={recurso.erro} aoTentarDeNovo={recurso.tentarDeNovo} />}
      {recurso.situacao === 'pronto' && <Conteudo dados={recurso.dados} />}
    </section>
  );
}

function Conteudo({ dados }: { dados: DadosDoPerfil }) {
  const linhas = dados.cadastro ? resumo(dados.cadastro, dados.catalogo) : null;
  return (
    <>
      <div className={estilos.acoes}>
        <Botao variante="contorno" para={caminhoDoPerfil(dados.agenda.perfilId)}>
          Ver como o cliente vê
        </Botao>
        <Botao variante="suave" para={CAMINHOS.profissionalAgenda}>
          Marcar folga
        </Botao>
      </div>
      {linhas && (
        <section className={estilos.cartao} aria-labelledby="titulo-dados">
          <h2 id="titulo-dados">O que o cliente vê</h2>
          <dl className={estilos.lista}>
            {LINHAS.map(({ etapa, rotulo }) => (
              <div key={etapa} className={estilos.linhaLink}>
                <dt className={estilos.rotulo}>{rotulo}</dt>
                <dd className={estilos.apoio}>{linhas[etapa] || '—'}</dd>
              </div>
            ))}
          </dl>
        </section>
      )}
      <Nota tipo="info">
        Para mudar o valor, os serviços, as cidades ou as fotos, fale com a equipe da COE. Em breve você muda por aqui.
      </Nota>
      <div className={estilos.acoes}>
        <Botao variante="suave" para={CAMINHOS.contratos}>
          Minhas contratações (como cliente)
        </Botao>
        <Botao variante="suave" para={CAMINHOS.conta}>
          Minha conta (senha, celular e sair)
        </Botao>
      </div>
    </>
  );
}
