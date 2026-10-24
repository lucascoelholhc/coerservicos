import { useState } from 'react';

import { salvarEtapa } from '../../../api/cadastroProfissional';
import { CampoTextoLongo } from '../../../componentes/CampoTextoLongo/CampoTextoLongo';
import { Opcoes } from '../../../componentes/Opcoes/Opcoes';
import estilos from '../Cadastro.module.css';
import { FRASES_COMUNS, FRASES_POR_PROFISSAO, textoDoSobre } from '../conteudo';
import { QuadroDaEtapa } from '../QuadroDaEtapa';
import { useSalvarEtapa, type PropsDaEtapa } from './comum';

/** Limites do servidor (PedidosDoCadastro.Sobre): até 10 frases e 300 letras no texto livre. */
const FRASES_NO_MAXIMO = 10;
const TEXTO_NO_MAXIMO = 300;

const CAMPOS_DO_SOBRE = { 'texto-com-contato': 'texto', 'sobre-curto': 'texto' };

/** Etapa 10: frases prontas por toque, texto livre (se quiser) e a prévia de como fica no perfil. */
export function EtapaSobre({ numero, cadastro, aoSalvar, aoVoltar }: PropsDaEtapa) {
  const [frases, setFrases] = useState<string[]>(cadastro.frases);
  const [texto, setTexto] = useState(cadastro.bio ?? '');
  const { enviando, erro, campos, salvar } = useSalvarEtapa(aoSalvar, CAMPOS_DO_SOBRE);
  const prontas = [...FRASES_COMUNS, ...(cadastro.profissao ? (FRASES_POR_PROFISSAO[cadastro.profissao] ?? []) : [])];
  // Frase já salva que não está mais na lista continua aparecendo (e pode ser tirada).
  const opcoes = [...prontas, ...cadastro.frases.filter((frase) => !prontas.includes(frase))];
  const previa = textoDoSobre(frases, texto);

  function enviar() {
    salvar(() => salvarEtapa('sobre', { frases, texto: texto.trim() === '' ? null : texto.trim() }));
  }

  return (
    <QuadroDaEtapa numero={numero} cadastro={cadastro} aoEnviar={enviar} aoVoltar={aoVoltar} enviando={enviando} erro={erro} campos={campos}>
      <Opcoes
        legenda="Toque nas frases que combinam com você"
        dica="Não precisa escrever nada se não quiser."
        nome="frases"
        tipo="varias"
        forma="lista"
        opcoes={opcoes.map((frase) => ({ valor: frase, texto: frase }))}
        selecionadas={frases}
        aoMudar={setFrases}
        maximo={FRASES_NO_MAXIMO}
      />
      <CampoTextoLongo
        nome="texto"
        rotulo="Quer contar mais alguma coisa? (se quiser)"
        valor={texto}
        aoMudar={setTexto}
        maxLength={TEXTO_NO_MAXIMO}
        exemplo="Ex.: Trabalho com obra há 12 anos. Já fiz muito sobrado aqui no bairro."
        dica="Sem telefone, e-mail ou rede social: o contato aparece depois que o cliente paga."
        erro={campos.texto}
      />
      <div className={estilos.previa}>
        <span className={estilos.rotuloDaPrevia}>Assim vai aparecer no seu perfil</span>
        <p>{previa === '' ? <span className={estilos.apagado}>Toque em algumas frases acima.</span> : previa}</p>
      </div>
    </QuadroDaEtapa>
  );
}
