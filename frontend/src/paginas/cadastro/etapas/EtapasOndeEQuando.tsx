import { useState } from 'react';

import { salvarEtapa } from '../../../api/cadastroProfissional';
import { CampoSelecao } from '../../../componentes/CampoSelecao/CampoSelecao';
import { Opcoes } from '../../../componentes/Opcoes/Opcoes';
import estilos from '../Cadastro.module.css';
import { DIAS_DA_SEMANA, RAIOS } from '../conteudo';
import { QuadroDaEtapa } from '../QuadroDaEtapa';
import { umaSo, useSalvarEtapa, type PropsDaEtapa } from './comum';

/** O servidor aceita até 20 outras cidades (PedidosDoCadastro.Onde). */
const OUTRAS_CIDADES_NO_MAXIMO = 20;

const CAMPOS_DO_ONDE = { 'cidade-invalida': 'cidade' };

/** Etapa 7: cidade onde mora (do catálogo), até onde vai e outras cidades que atende. */
export function EtapaOnde({ numero, cadastro, catalogo, aoSalvar, aoVoltar }: PropsDaEtapa) {
  const [cidade, setCidade] = useState(cadastro.cidade === null ? '' : String(cadastro.cidade));
  const [raio, setRaio] = useState(cadastro.raioKm === null ? '' : String(cadastro.raioKm));
  const [outras, setOutras] = useState<string[]>(cadastro.outrasCidades.map(String));
  const { enviando, erro, campos, salvar, avisar } = useSalvarEtapa(aoSalvar, CAMPOS_DO_ONDE);

  function enviar() {
    const faltas: Record<string, string> = {};
    if (cidade === '') {
      faltas.cidade = 'Escolha a cidade onde você mora.';
    }
    if (raio === '') {
      faltas.raio = 'Escolha até onde você vai para trabalhar.';
    }
    if (Object.keys(faltas).length > 0) {
      avisar(faltas);
      return;
    }
    salvar(() =>
      salvarEtapa('onde', {
        cidade: Number(cidade),
        raioKm: Number(raio),
        outrasCidades: outras.filter((outra) => outra !== cidade).map(Number),
      }),
    );
  }

  return (
    <QuadroDaEtapa numero={numero} cadastro={cadastro} aoEnviar={enviar} aoVoltar={aoVoltar} enviando={enviando} erro={erro} campos={campos}>
      <CampoSelecao
        nome="cidade"
        rotulo="Em que cidade você mora?"
        opcoes={[
          { valor: '', texto: 'Escolha…' },
          ...catalogo.cidades.map((c) => ({ valor: String(c.codigoIbge), texto: c.nome })),
        ]}
        valor={cidade}
        aoMudar={setCidade}
        erro={campos.cidade}
      />
      <p className={estilos.apagado}>Não achou sua cidade? Por enquanto a COE atende só estas.</p>
      <Opcoes
        legenda="Até onde você vai para trabalhar?"
        nome="raio"
        tipo="unica"
        forma="cartao"
        opcoes={RAIOS.map((r) => ({ valor: String(r.km), texto: r.texto, detalhe: `até ${r.km} km de onde você mora` }))}
        selecionadas={raio === '' ? [] : [raio]}
        aoMudar={umaSo(setRaio)}
        erro={campos.raio}
      />
      {cidade !== '' && (
        <Opcoes
          legenda={
            <>
              Outras cidades que você atende <span className={estilos.apagado}>(se quiser)</span>
            </>
          }
          nome="outrasCidades"
          tipo="varias"
          opcoes={catalogo.cidades
            .filter((c) => String(c.codigoIbge) !== cidade)
            .map((c) => ({ valor: String(c.codigoIbge), texto: c.nome }))}
          selecionadas={outras.filter((outra) => outra !== cidade)}
          aoMudar={setOutras}
          maximo={OUTRAS_CIDADES_NO_MAXIMO}
        />
      )}
    </QuadroDaEtapa>
  );
}

/** Etapa 8: os dias da semana em que trabalha (dias específicos se bloqueiam depois, na agenda). */
export function EtapaDias({ numero, cadastro, aoSalvar, aoVoltar }: PropsDaEtapa) {
  const [dias, setDias] = useState<string[]>(cadastro.dias.map(String));
  const { enviando, erro, campos, salvar, avisar } = useSalvarEtapa(aoSalvar);

  function enviar() {
    if (dias.length === 0) {
      avisar({ dias: 'Escolha pelo menos um dia.' });
      return;
    }
    salvar(() => salvarEtapa('dias', { dias: dias.map(Number).sort((a, b) => a - b) }));
  }

  return (
    <QuadroDaEtapa numero={numero} cadastro={cadastro} aoEnviar={enviar} aoVoltar={aoVoltar} enviando={enviando} erro={erro} campos={campos}>
      <Opcoes
        legenda="Dias da semana"
        legendaOculta
        nome="dias"
        tipo="varias"
        forma="dia"
        opcoes={DIAS_DA_SEMANA.map((d) => ({ valor: String(d.dia), texto: d.nome, curto: d.curto }))}
        selecionadas={dias}
        aoMudar={setDias}
        erro={campos.dias}
      />
      <p className={estilos.apagado}>Você pode bloquear dias específicos depois, na sua agenda.</p>
    </QuadroDaEtapa>
  );
}
