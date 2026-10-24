import { useCallback, useEffect, useRef, useState, type ComponentType } from 'react';
import { useSearchParams } from 'react-router';

import { buscarCadastro } from '../../api/cadastroProfissional';
import { buscarCatalogo } from '../../api/catalogo';
import { buscarMinhaConta } from '../../api/contas';
import { buscarRegras } from '../../api/regras';
import type { CadastroDoProfissional, Catalogo, MinhaConta, Regras } from '../../api/tipos';
import { useRecurso } from '../../api/useRecurso';
import { FalhaAoCarregar } from '../../componentes/FalhaAoCarregar/FalhaAoCarregar';
import { useTitulo } from '../../layout/titulo';
import estilos from './Cadastro.module.css';
import { ETAPAS, TOTAL_DE_ETAPAS } from './conteudo';
import { TrocouDeEtapa, type PropsDaEtapa } from './etapas/comum';
import { EtapaFotos } from './etapas/EtapaFotos';
import { EtapaPix } from './etapas/EtapaPix';
import { EtapaSobre } from './etapas/EtapaSobre';
import { EtapaDados, EtapaDocumentos } from './etapas/EtapasDeVoce';
import { EtapaExperiencia, EtapaServicos, EtapaTrabalho, EtapaValor } from './etapas/EtapasDoTrabalho';
import { EtapaDias, EtapaOnde } from './etapas/EtapasOndeEQuando';
import { SituacaoDoCadastro } from './SituacaoDoCadastro';

/** As 11 telas na ordem de ETAPAS. */
const TELAS_DAS_ETAPAS: readonly ComponentType<PropsDaEtapa>[] = [
  EtapaTrabalho,
  EtapaDados,
  EtapaDocumentos,
  EtapaServicos,
  EtapaExperiencia,
  EtapaValor,
  EtapaOnde,
  EtapaDias,
  EtapaFotos,
  EtapaSobre,
  EtapaPix,
];

interface DadosDoCadastro {
  cadastro: CadastroDoProfissional | null;
  catalogo: Catalogo;
  regras: Regras;
  conta: MinhaConta;
}

async function carregar(sinal: AbortSignal): Promise<DadosDoCadastro> {
  const [cadastro, catalogo, regras, conta] = await Promise.all([
    buscarCadastro(sinal),
    buscarCatalogo(sinal),
    buscarRegras(sinal),
    buscarMinhaConta(sinal),
  ]);
  return { cadastro, catalogo, regras, conta };
}

/** Antes da primeira etapa não há rascunho na API: a tela começa com um vazio (o PUT do trabalho cria). */
function rascunhoNovo(nome: string): CadastroDoProfissional {
  return {
    status: 'rascunho',
    etapaAtual: 1,
    etapas: Object.fromEntries(ETAPAS.map((etapa) => [etapa.chave, false])) as CadastroDoProfissional['etapas'],
    nome,
    profissao: null,
    outrasProfissoes: [],
    cpf: null,
    dataNascimento: null,
    documentos: [],
    servicos: [],
    experienciaAnos: null,
    ferramentas: null,
    mei: false,
    valorDiaria: null,
    valorMeiaDiaria: null,
    horario: null,
    cidade: null,
    raioKm: null,
    outrasCidades: [],
    dias: [],
    fotos: [],
    frases: [],
    bio: null,
    chavePixTipo: null,
    chavePix: null,
    motivoCorrecao: null,
  };
}

/**
 * Cadastro do profissional (FE-04, protótipo "6d"): uma pergunta por tela, salva no servidor a cada
 * "Continuar" (DOM-02). A etapa vai no endereço (?etapa=N), então o botão de voltar do celular funciona;
 * sem ela, abre onde a pessoa parou.
 */
export function CadastroProfissional() {
  useTitulo('Cadastro de profissional');
  const recurso = useRecurso(carregar);

  if (recurso.situacao === 'carregando') {
    return (
      <section className={`largura ${estilos.pagina}`}>
        <p role="status">Carregando seu cadastro…</p>
      </section>
    );
  }
  if (recurso.situacao === 'erro') {
    return (
      <section className={`largura ${estilos.pagina}`}>
        <FalhaAoCarregar erro={recurso.erro} aoTentarDeNovo={recurso.tentarDeNovo} />
      </section>
    );
  }
  return <Assistente dados={recurso.dados} />;
}

function etapaDoEndereco(texto: string | null): number | null {
  const numero = Number(texto);
  return Number.isInteger(numero) && numero >= 1 && numero <= TOTAL_DE_ETAPAS ? numero : null;
}

function Assistente({ dados }: { dados: DadosDoCadastro }) {
  const [cadastro, setCadastro] = useState(dados.cadastro ?? rascunhoNovo(dados.conta.nome));
  const [busca, setBusca] = useSearchParams();
  const numero = etapaDoEndereco(busca.get('etapa')) ?? Math.min(Math.max(cadastro.etapaAtual, 1), TOTAL_DE_ETAPAS);

  const [trocou, setTrocou] = useState(false);
  // A etapa na tela agora (0 = saiu da página): um salvamento atrasado não leva a outra tela.
  const etapaNaTela = useRef(numero);
  useEffect(() => {
    etapaNaTela.current = numero;
  }, [numero]);
  useEffect(
    () => () => {
      etapaNaTela.current = 0;
    },
    [],
  );

  const irPara = useCallback(
    (destino: number) => {
      setTrocou(true);
      setBusca({ etapa: String(destino) });
    },
    [setBusca],
  );
  const aoSalvar = useCallback(
    (salvo: CadastroDoProfissional) => {
      setCadastro(salvo);
      const editavel = salvo.status === 'rascunho' || salvo.status === 'correcao_pedida';
      if (editavel && etapaNaTela.current === numero) {
        irPara(Math.min(numero + 1, TOTAL_DE_ETAPAS));
      }
    },
    [irPara, numero],
  );
  const aoVoltar = useCallback(() => irPara(Math.max(numero - 1, 1)), [irPara, numero]);

  if (cadastro.status !== 'rascunho' && cadastro.status !== 'correcao_pedida') {
    return <SituacaoDoCadastro situacao={cadastro.status} />;
  }
  const Tela = TELAS_DAS_ETAPAS[numero - 1] ?? EtapaTrabalho;
  return (
    <TrocouDeEtapa.Provider value={trocou}>
      <Tela
      // Cada etapa começa com o que está salvo: trocar de etapa monta a tela de novo.
      key={numero}
      numero={numero}
      cadastro={cadastro}
      catalogo={dados.catalogo}
      regras={dados.regras}
      conta={dados.conta}
      aoSalvar={aoSalvar}
      aoAtualizar={setCadastro}
      aoVoltar={aoVoltar}
      irPara={irPara}
      />
    </TrocouDeEtapa.Provider>
  );
}
