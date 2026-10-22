import { useId, useState } from 'react';

import { salvarEtapa } from '../../../api/cadastroProfissional';
import { profissoesDoCatalogo } from '../../../api/catalogo';
import type { Ferramentas, ProfissaoDoCatalogo } from '../../../api/tipos';
import { Botao } from '../../../componentes/Botao/Botao';
import { CampoSelecao } from '../../../componentes/CampoSelecao/CampoSelecao';
import { CampoTexto } from '../../../componentes/CampoTexto/CampoTexto';
import { Ilustracao, temIlustracao } from '../../../componentes/Ilustracao/Ilustracao';
import { Nota } from '../../../componentes/Nota/Nota';
import { Opcoes } from '../../../componentes/Opcoes/Opcoes';
import { centavosDoTexto, formatarReais, previaDaDiaria, textoDosCentavos } from '../../../formatacao/dinheiro';
import estilos from '../Cadastro.module.css';
import { EXPERIENCIA, FERRAMENTAS, HORARIO_PADRAO, HORARIOS } from '../conteudo';
import { QuadroDaEtapa } from '../QuadroDaEtapa';
import { umaSo, useSalvarEtapa, type PropsDaEtapa } from './comum';

/** O servidor aceita até 4 outras profissões (PedidosDoCadastro.Trabalho). */
const OUTRAS_NO_MAXIMO = 4;

const CAMPOS_DO_TRABALHO = { 'profissao-invalida': 'profissao' };

/** Etapa 1: a profissão principal (cartões com o desenho) e outras, se quiser. */
export function EtapaTrabalho({ numero, cadastro, catalogo, aoSalvar, aoVoltar }: PropsDaEtapa) {
  const profissoes = profissoesDoCatalogo(catalogo);
  const [profissao, setProfissao] = useState(cadastro.profissao ?? '');
  const [outras, setOutras] = useState<string[]>(cadastro.outrasProfissoes);
  const { enviando, erro, campos, salvar, avisar } = useSalvarEtapa(aoSalvar, CAMPOS_DO_TRABALHO);

  function escolher(codigo: string) {
    setProfissao(codigo);
    setOutras((anteriores) => anteriores.filter((outra) => outra !== codigo));
  }

  function enviar() {
    if (profissao === '') {
      avisar({ profissao: 'Escolha o seu trabalho.' });
      return;
    }
    salvar(() => salvarEtapa('trabalho', { profissao, outras }));
  }

  return (
    <QuadroDaEtapa numero={numero} cadastro={cadastro} aoEnviar={enviar} aoVoltar={aoVoltar} enviando={enviando} erro={erro} campos={campos}>
      <Nota>
        Este cadastro é para quem quer <b>oferecer serviços</b>. Para contratar alguém, use a busca com esta mesma
        conta.
      </Nota>
      <Opcoes
        legenda="Profissão"
        legendaOculta
        nome="profissao"
        tipo="unica"
        forma="cartao"
        opcoes={profissoes.map((p) => ({
          valor: p.codigo,
          texto: p.nome,
          imagem: temIlustracao(p.codigo) ? <Ilustracao profissao={p.codigo} /> : undefined,
        }))}
        selecionadas={profissao === '' ? [] : [profissao]}
        aoMudar={umaSo(escolher)}
        erro={campos.profissao}
      />
      {profissao !== '' && (
        <Opcoes
          legenda={
            <>
              Faz outro trabalho também? <span className={estilos.apagado}>(se quiser)</span>
            </>
          }
          nome="outras"
          tipo="varias"
          opcoes={profissoes.filter((p) => p.codigo !== profissao).map((p) => ({ valor: p.codigo, texto: p.nome }))}
          selecionadas={outras}
          aoMudar={setOutras}
          maximo={OUTRAS_NO_MAXIMO}
          erro={campos.outras}
        />
      )}
    </QuadroDaEtapa>
  );
}

const SEPARADOR = '::';

function chaveDoServico(profissao: string, nome: string): string {
  return `${profissao}${SEPARADOR}${nome}`;
}

/** As profissões escolhidas na etapa 1, na ordem: a principal primeiro. */
export function profissoesEscolhidas(cadastro: PropsDaEtapa['cadastro'], todas: ProfissaoDoCatalogo[]): ProfissaoDoCatalogo[] {
  const codigos = [cadastro.profissao, ...cadastro.outrasProfissoes].filter((codigo): codigo is string => codigo !== null);
  return codigos.map((codigo) => todas.find((p) => p.codigo === codigo)).filter((p): p is ProfissaoDoCatalogo => p !== undefined);
}

/** Sem a profissão principal, as etapas que dependem dela mandam de volta para a etapa 1. */
export function FaltaOTrabalho({ irPara }: { irPara: (numero: number) => void }) {
  return (
    <>
      <Nota tipo="atencao">Escolha primeiro o seu trabalho.</Nota>
      <Botao variante="contorno" onClick={() => irPara(1)}>
        Escolher o trabalho
      </Botao>
    </>
  );
}

const CAMPOS_DOS_SERVICOS = { 'servico-invalido': 'servicos' };

/** Etapa 4: os serviços de cada profissão escolhida (do catálogo). */
export function EtapaServicos({ numero, cadastro, catalogo, aoSalvar, aoVoltar, irPara }: PropsDaEtapa) {
  const profissoes = profissoesEscolhidas(cadastro, profissoesDoCatalogo(catalogo));
  const [escolhidos, setEscolhidos] = useState<string[]>(cadastro.servicos.map((s) => chaveDoServico(s.profissao, s.nome)));
  const { enviando, erro, campos, salvar, avisar } = useSalvarEtapa(aoSalvar, CAMPOS_DOS_SERVICOS);

  function trocar(profissao: string, daProfissao: string[]) {
    const prefixo = `${profissao}${SEPARADOR}`;
    setEscolhidos((anteriores) => [...anteriores.filter((chave) => !chave.startsWith(prefixo)), ...daProfissao]);
  }

  function enviar() {
    if (escolhidos.length === 0) {
      avisar({ servicos: 'Escolha pelo menos um serviço.' });
      return;
    }
    const servicos = escolhidos.map((chave) => {
      const [profissao = '', nome = ''] = chave.split(SEPARADOR);
      return { profissao, nome };
    });
    salvar(() => salvarEtapa('servicos', { servicos }));
  }

  return (
    <QuadroDaEtapa numero={numero} cadastro={cadastro} aoEnviar={enviar} aoVoltar={aoVoltar} enviando={enviando} erro={erro} campos={campos}>
      {profissoes.length === 0 ? (
        <FaltaOTrabalho irPara={irPara} />
      ) : (
        <>
          <p className={estilos.apagado}>Toque em tudo o que você faz bem. O cliente procura por esses serviços.</p>
          {profissoes.map((p) =>
            p.servicos.length === 0 ? (
              <p key={p.codigo}>{`${p.nome}: ainda sem serviços na COE.`}</p>
            ) : (
              <Opcoes
                key={p.codigo}
                legenda={p.nome}
                nome={`servicos-${p.codigo}`}
                tipo="varias"
                opcoes={p.servicos.map((s) => ({ valor: chaveDoServico(p.codigo, s.nome), texto: s.nome }))}
                selecionadas={escolhidos.filter((chave) => chave.startsWith(`${p.codigo}${SEPARADOR}`))}
                aoMudar={(daProfissao) => trocar(p.codigo, daProfissao)}
              />
            ),
          )}
          {campos.servicos && (
            <p className={estilos.erroDoGrupo} role="alert">
              {campos.servicos}
            </p>
          )}
        </>
      )}
    </QuadroDaEtapa>
  );
}

const CAMPOS_DA_EXPERIENCIA = { 'experiencia-invalida': 'anos' };

/** Etapa 5: há quanto tempo trabalha, ferramentas e MEI (se souber). */
export function EtapaExperiencia({ numero, cadastro, aoSalvar, aoVoltar }: PropsDaEtapa) {
  const [anos, setAnos] = useState(cadastro.experienciaAnos === null ? '' : String(cadastro.experienciaAnos));
  const [ferramentas, setFerramentas] = useState<string>(cadastro.ferramentas ?? '');
  const [mei, setMei] = useState(cadastro.mei ? 'sim' : cadastro.etapas.experiencia ? 'nao' : '');
  const { enviando, erro, campos, salvar, avisar } = useSalvarEtapa(aoSalvar, CAMPOS_DA_EXPERIENCIA);

  function enviar() {
    const faltas: Record<string, string> = {};
    if (anos === '') {
      faltas.anos = 'Escolha há quanto tempo você trabalha nisso.';
    }
    if (ferramentas === '') {
      faltas.ferramentas = 'Escolha uma opção sobre as ferramentas.';
    }
    if (Object.keys(faltas).length > 0) {
      avisar(faltas);
      return;
    }
    salvar(() =>
      salvarEtapa('experiencia', {
        anos: Number(anos),
        ferramentas: ferramentas as Ferramentas,
        mei: mei === '' ? null : mei === 'sim',
      }),
    );
  }

  return (
    <QuadroDaEtapa numero={numero} cadastro={cadastro} aoEnviar={enviar} aoVoltar={aoVoltar} enviando={enviando} erro={erro} campos={campos}>
      <Opcoes
        legenda="Há quanto tempo trabalha nisso?"
        nome="anos"
        tipo="unica"
        forma="cartao"
        opcoes={EXPERIENCIA.map((faixa) => ({ valor: String(faixa.anos), texto: faixa.texto }))}
        selecionadas={anos === '' ? [] : [anos]}
        aoMudar={umaSo(setAnos)}
        erro={campos.anos}
      />
      <Opcoes
        legenda="E as ferramentas?"
        nome="ferramentas"
        tipo="unica"
        forma="cartao"
        opcoes={Object.entries(FERRAMENTAS).map(([valor, texto]) => ({ valor, texto }))}
        selecionadas={ferramentas === '' ? [] : [ferramentas]}
        aoMudar={umaSo(setFerramentas)}
        erro={campos.ferramentas}
      />
      <Opcoes
        legenda={
          <>
            Você é MEI? <span className={estilos.apagado}>(se não souber, pode pular)</span>
          </>
        }
        nome="mei"
        tipo="unica"
        opcoes={[
          { valor: 'sim', texto: 'Sim, emito nota' },
          { valor: 'nao', texto: 'Não' },
        ]}
        selecionadas={mei === '' ? [] : [mei]}
        aoMudar={umaSo(setMei)}
      />
    </QuadroDaEtapa>
  );
}

/** "280.00" (da API) vira "280" no campo; "280.50" vira "280,50". */
function valorParaOCampo(texto: string | null): string {
  if (texto === null) {
    return '';
  }
  const [inteiros, centavos] = texto.split('.');
  return centavos === undefined || centavos === '00' ? (inteiros ?? '') : `${inteiros ?? ''},${centavos}`;
}

const PASSO_DO_VALOR = 1_000;

const CAMPOS_DO_VALOR = {
  'valor-abaixo-do-minimo': 'valor',
  'meia-diaria-invalida': 'meia',
  'texto-com-contato': 'horario',
};

/** Etapa 6: diária (com − e +), prévia do que recebe, meia diária e horário. */
export function EtapaValor({ numero, cadastro, regras, aoSalvar, aoVoltar }: PropsDaEtapa) {
  const [valor, setValor] = useState(valorParaOCampo(cadastro.valorDiaria));
  const [aceitaMeia, setAceitaMeia] = useState(cadastro.valorMeiaDiaria !== null);
  const [meia, setMeia] = useState(valorParaOCampo(cadastro.valorMeiaDiaria));
  const [horario, setHorario] = useState(cadastro.horario ?? HORARIO_PADRAO);
  const { enviando, erro, campos, salvar, avisar } = useSalvarEtapa(aoSalvar, CAMPOS_DO_VALOR);
  const horarios = HORARIOS.includes(horario) ? HORARIOS : [horario, ...HORARIOS];
  const idDaMeia = useId();

  function ajustar(diferenca: number) {
    const atual = centavosDoTexto(valor) ?? 0;
    setValor(valorParaOCampo(textoDosCentavos(Math.max(0, atual + diferenca))));
  }

  function enviar() {
    const diaria = centavosDoTexto(valor);
    const meiaDiaria = centavosDoTexto(meia);
    const faltas: Record<string, string> = {};
    if (diaria === null || diaria === 0) {
      faltas.valor = 'Digite o valor da diária, só com números (ex.: 280).';
    }
    if (aceitaMeia && (meiaDiaria === null || meiaDiaria === 0)) {
      faltas.meia = 'Digite o valor da meia diária, só com números (ex.: 160).';
    }
    if (diaria === null || diaria === 0 || Object.keys(faltas).length > 0) {
      avisar(faltas);
      return;
    }
    salvar(() =>
      salvarEtapa('valor', {
        valorDiaria: textoDosCentavos(diaria),
        valorMeiaDiaria: aceitaMeia && meiaDiaria !== null ? textoDosCentavos(meiaDiaria) : null,
        horario,
      }),
    );
  }

  return (
    <QuadroDaEtapa numero={numero} cadastro={cadastro} aoEnviar={enviar} aoVoltar={aoVoltar} enviando={enviando} erro={erro} campos={campos}>
      <p className={estilos.apagado}>Use − e + para ajustar, ou digite o valor.</p>
      <div className={estilos.ajuste}>
        <button type="button" className={estilos.passo} onClick={() => ajustar(-PASSO_DO_VALOR)} aria-label="Diminuir 10 reais">
          −
        </button>
        <CampoTexto
          nome="valorDiaria"
          rotulo="Valor da diária (R$)"
          valor={valor}
          aoMudar={setValor}
          inputMode="decimal"
          maxLength={10}
          erro={campos.valor}
        />
        <button type="button" className={estilos.passo} onClick={() => ajustar(PASSO_DO_VALOR)} aria-label="Aumentar 10 reais">
          +
        </button>
      </div>
      <PreviaDoValor centavos={centavosDoTexto(valor)} regras={regras} />
      <div className={estilos.interruptor}>
        <label htmlFor={idDaMeia}>
          <b>Aceito meia diária</b>
          <span className={estilos.apagado}>Serviço de até 4 horas</span>
        </label>
        <input
          id={idDaMeia}
          type="checkbox"
          checked={aceitaMeia}
          onChange={(evento) => setAceitaMeia(evento.target.checked)}
        />
      </div>
      {aceitaMeia && (
        <CampoTexto
          nome="valorMeiaDiaria"
          rotulo="Valor da meia diária (R$)"
          valor={meia}
          aoMudar={setMeia}
          inputMode="decimal"
          maxLength={10}
          erro={campos.meia}
        />
      )}
      <CampoSelecao
        nome="horario"
        rotulo="Seu horário"
        opcoes={horarios.map((h) => ({ valor: h, texto: h }))}
        valor={horario}
        aoMudar={setHorario}
        erro={campos.horario}
      />
    </QuadroDaEtapa>
  );
}

/** "Você recebe R$ X por dia": a comissão e quem paga vêm da API (o servidor decide o valor final). */
function PreviaDoValor({ centavos, regras }: { centavos: number | null; regras: PropsDaEtapa['regras'] }) {
  let previa: ReturnType<typeof previaDaDiaria> | null = null;
  if (centavos !== null && centavos > 0) {
    try {
      previa = previaDaDiaria(centavos, regras.comissao, regras.taxaPagaPor);
    } catch {
      previa = null;
    }
  }
  return (
    <div aria-live="polite">
      {previa && (
        <Nota tipo="ok">
          {regras.taxaPagaPor === 'CLIENTE' ? (
            <>
              Você recebe <b>{formatarReais(previa.recebe / 100)}</b> por dia. O cliente paga{' '}
              <b>{formatarReais(previa.clientePaga / 100)}</b> com a taxa da COE.
            </>
          ) : (
            <>
              Você recebe <b>{formatarReais(previa.recebe / 100)}</b> por dia, já com a taxa da COE descontada.
            </>
          )}
        </Nota>
      )}
    </div>
  );
}
