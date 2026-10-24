import { useId } from 'react';

import type { CidadeDoCatalogo, ProfissaoDoCatalogo } from '../../api/tipos';
import { CampoSelecao, type OpcaoDeSelecao } from '../../componentes/CampoSelecao/CampoSelecao';
import { Opcoes } from '../../componentes/Opcoes/Opcoes';
import { formatarDiaCurto, somarDias } from '../../formatacao/datas';
import { formatarReais } from '../../formatacao/dinheiro';
import estilos from './Busca.module.css';
import type { FiltrosDaBusca } from './filtros';

/** Faixas de valor e de experiência da tela (o filtro do protótipo); o servidor só compara números. */
const VALORES = ['150.00', '200.00', '250.00', '300.00', '350.00', '400.00'];
const EXPERIENCIAS = [
  { valor: '', texto: 'Qualquer' },
  { valor: '5', texto: '5 anos ou mais' },
  { valor: '10', texto: '10 anos ou mais' },
];
/** Dias da semana seguinte para o filtro rápido (o protótipo mostra 7). */
const DIAS_NO_FILTRO = 7;

interface PainelProps {
  /** Prefixo dos nomes dos grupos: o painel aparece duas vezes (lateral e folha). */
  prefixo: string;
  filtros: FiltrosDaBusca;
  aoMudar: (troca: Partial<FiltrosDaBusca>) => void;
  profissao?: ProfissaoDoCatalogo;
  cidades: readonly CidadeDoCatalogo[];
  hoje: string;
}

function opcoesDeValor(atual: string): OpcaoDeSelecao[] {
  const valores = atual && !VALORES.includes(atual) ? [...VALORES, atual] : VALORES;
  return [
    { valor: '', texto: 'Qualquer valor' },
    ...valores.map((valor) => ({ valor, texto: formatarReais(valor) })),
  ];
}

/** Filtros da busca (protótipo: filtrosHTML). Cada troca vale na hora (a URL muda e a lista também). */
export function PainelDeFiltros({ prefixo, filtros, aoMudar, profissao, cidades, hoje }: PainelProps) {
  const id = useId();
  const dias = Array.from({ length: DIAS_NO_FILTRO }, (_, indice) => somarDias(hoje, indice + 1));
  const primeira = (selecionadas: string[]) => selecionadas.at(-1) ?? '';
  return (
    <div className={estilos.painel}>
      {profissao && profissao.servicos.length > 0 && (
        <Opcoes
          legenda="O que precisa ser feito"
          nome={`${prefixo}-servico`}
          tipo="unica"
          opcoes={[
            { valor: '', texto: 'Qualquer serviço' },
            ...profissao.servicos.map((servico) => ({ valor: servico.nome, texto: servico.nome })),
          ]}
          selecionadas={[filtros.servico]}
          aoMudar={(selecionadas) => aoMudar({ servico: primeira(selecionadas) })}
        />
      )}
      <CampoSelecao
        nome={`${prefixo}-cidade`}
        rotulo="Cidade do serviço"
        opcoes={[
          { valor: '', texto: 'Todas as cidades' },
          ...cidades.map((cidade) => ({ valor: String(cidade.codigoIbge), texto: `${cidade.nome} (${cidade.uf})` })),
        ]}
        valor={filtros.cidade}
        aoMudar={(cidade) => aoMudar({ cidade })}
        desabilitado={cidades.length === 0}
      />
      <Opcoes
        legenda="Livre no dia"
        nome={`${prefixo}-dia`}
        tipo="unica"
        opcoes={[
          { valor: '', texto: 'Qualquer dia' },
          ...dias.map((dia) => ({ valor: dia, texto: formatarDiaCurto(dia) })),
          ...(filtros.dia && !dias.includes(filtros.dia)
            ? [{ valor: filtros.dia, texto: formatarDiaCurto(filtros.dia) }]
            : []),
        ]}
        selecionadas={[filtros.dia]}
        aoMudar={(selecionadas) => aoMudar({ dia: primeira(selecionadas) })}
      />
      <CampoSelecao
        nome={`${prefixo}-valor`}
        rotulo="Diária até"
        opcoes={opcoesDeValor(filtros.valorMaximo)}
        valor={filtros.valorMaximo}
        aoMudar={(valorMaximo) => aoMudar({ valorMaximo })}
      />
      <Opcoes
        legenda="Experiência"
        nome={`${prefixo}-experiencia`}
        tipo="unica"
        opcoes={EXPERIENCIAS}
        selecionadas={[filtros.experienciaMinima]}
        aoMudar={(selecionadas) => aoMudar({ experienciaMinima: primeira(selecionadas) })}
      />
      <fieldset className={estilos.condicoes}>
        <legend className={estilos.legenda}>Condições</legend>
        <label className={estilos.interruptor} htmlFor={`${id}-ferramentas`}>
          <span>Leva as próprias ferramentas</span>
          <input
            id={`${id}-ferramentas`}
            type="checkbox"
            checked={filtros.ferramentas === 'sim'}
            onChange={(evento) => aoMudar({ ferramentas: evento.target.checked ? 'sim' : '' })}
          />
        </label>
        <label className={estilos.interruptor} htmlFor={`${id}-meia`}>
          <span>Aceita meia diária</span>
          <input
            id={`${id}-meia`}
            type="checkbox"
            checked={filtros.meiaDiaria}
            onChange={(evento) => aoMudar({ meiaDiaria: evento.target.checked })}
          />
        </label>
      </fieldset>
    </div>
  );
}
