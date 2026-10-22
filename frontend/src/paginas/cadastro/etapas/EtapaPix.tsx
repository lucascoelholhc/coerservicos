import { useState } from 'react';
import { Link } from 'react-router';

import { enviarCadastro, salvarEtapa } from '../../../api/cadastroProfissional';
import type { CadastroDoProfissional, Catalogo, TipoDeChavePix } from '../../../api/tipos';
import { CAMINHOS } from '../../../caminhos';
import { CampoTexto } from '../../../componentes/CampoTexto/CampoTexto';
import { Nota } from '../../../componentes/Nota/Nota';
import { Opcoes } from '../../../componentes/Opcoes/Opcoes';
import estilos from '../Cadastro.module.css';
import { ETAPAS, TIPOS_DE_PIX } from '../conteudo';
import { QuadroDaEtapa } from '../QuadroDaEtapa';
import { resumo } from '../resumo';
import { umaSo, useSalvarEtapa, type PropsDaEtapa } from './comum';

const MODO_DO_TECLADO: Record<TipoDeChavePix, 'numeric' | 'tel' | 'email' | 'text'> = {
  cpf: 'numeric',
  celular: 'tel',
  email: 'email',
  aleatoria: 'text',
};

const CAMPOS_DO_PIX = { 'pix-invalido': 'chave' };

/** Etapa 11: chave Pix, revisão de tudo, as duas confirmações e o envio para análise. */
export function EtapaPix({ numero, cadastro, catalogo, regras, conta, aoSalvar, aoAtualizar, aoVoltar, irPara }: PropsDaEtapa) {
  const [tipo, setTipo] = useState<TipoDeChavePix | ''>(cadastro.chavePixTipo ?? '');
  const [chave, setChave] = useState('');
  const [termos, setTermos] = useState(false);
  const [regra, setRegra] = useState(false);
  const { enviando, erro, campos, salvar, avisar } = useSalvarEtapa(aoSalvar, CAMPOS_DO_PIX);
  // A chave guardada só volta mascarada: sem digitar de novo, fica a que está salva (se o tipo for o mesmo).
  const mantemAGuardada = chave.trim() === '' && cadastro.etapas.pix && tipo === cadastro.chavePixTipo;

  function enviar() {
    const faltas: Record<string, string> = {};
    if (tipo === '') {
      faltas.tipo = 'Escolha o tipo da chave Pix.';
    } else if (!mantemAGuardada && chave.trim() === '') {
      faltas.chave = 'Digite a sua chave Pix.';
    }
    if (!termos || !regra) {
      faltas.aceite = 'Para enviar, marque as duas confirmações.';
    }
    if (tipo === '' || Object.keys(faltas).length > 0) {
      avisar(faltas);
      return;
    }
    salvar(async () => {
      if (!mantemAGuardada) {
        aoAtualizar(await salvarEtapa('pix', { tipo, chave: chave.trim() }));
        setChave('');
      }
      return enviarCadastro({ versaoTermosAceita: regras.versaoTermos, aceitoRegraDeContato: true });
    });
  }

  return (
    <QuadroDaEtapa
      numero={numero}
      cadastro={cadastro}
      aoEnviar={enviar}
      aoVoltar={aoVoltar}
      enviando={enviando}
      erro={erro}
      campos={campos}
      acao="Enviar cadastro"
    >
      {!conta.celularConfirmado && (
        <Nota tipo="atencao">
          Confirme seu celular antes de enviar. <Link to={CAMINHOS.confirmarCelular}>Confirmar celular</Link>
        </Nota>
      )}
      <Opcoes
        legenda="Tipo de chave Pix"
        nome="tipoPix"
        tipo="unica"
        opcoes={TIPOS_DE_PIX.map((t) => ({ valor: t.tipo, texto: t.texto }))}
        selecionadas={tipo === '' ? [] : [tipo]}
        aoMudar={umaSo((valor) => setTipo(valor as TipoDeChavePix))}
        erro={campos.tipo}
      />
      <CampoTexto
        nome="chavePix"
        rotulo="Sua chave Pix"
        valor={chave}
        aoMudar={setChave}
        inputMode={tipo === '' ? 'text' : MODO_DO_TECLADO[tipo]}
        autoComplete="off"
        maxLength={120}
        erro={campos.chave}
        dica={
          cadastro.chavePix !== null
            ? `Já guardamos a chave ${cadastro.chavePix}. Para trocar, digite a nova.`
            : 'A chave precisa estar no seu nome. A COE confere antes do primeiro pagamento.'
        }
      />
      <h2 className={estilos.subtitulo}>Confira antes de enviar</h2>
      <Revisao cadastro={cadastro} catalogo={catalogo} irPara={irPara} />
      <div className={estilos.aceites}>
        <label className={estilos.aceite}>
          <input type="checkbox" checked={termos} onChange={(evento) => setTermos(evento.target.checked)} />
          <span>
            Li e aceito os <Link to={CAMINHOS.termos}>termos de uso</Link> e a{' '}
            <Link to={CAMINHOS.privacidade}>política de privacidade</Link>.
          </span>
        </label>
        <label className={estilos.aceite}>
          <input type="checkbox" checked={regra} onChange={(evento) => setRegra(evento.target.checked)} />
          <span>
            Entendi que não posso passar meu telefone antes de o cliente pagar, e que receber por fora tira a garantia
            do pagamento.
          </span>
        </label>
        {campos.aceite && (
          <p className={estilos.erroDoGrupo} role="alert">
            {campos.aceite}
          </p>
        )}
      </div>
    </QuadroDaEtapa>
  );
}

/** Cada etapa com o que foi salvo e "Mudar"; etapa incompleta aparece como "Falta completar". */
function Revisao({ cadastro, catalogo, irPara }: { cadastro: CadastroDoProfissional; catalogo: Catalogo; irPara: (n: number) => void }) {
  const valores = resumo(cadastro, catalogo);
  return (
    <dl className={estilos.revisao}>
      {ETAPAS.slice(0, -1).map((etapa, indice) => (
        <div key={etapa.chave}>
          <dt>{etapa.titulo}</dt>
          <dd>
            {cadastro.etapas[etapa.chave] && valores[etapa.chave] !== '' ? (
              valores[etapa.chave]
            ) : (
              <b className={estilos.falta}>Falta completar</b>
            )}
          </dd>
          <dd>
            <button type="button" className={estilos.linkBotao} onClick={() => irPara(indice + 1)}>
              Mudar<span className="sr-only">{`: ${etapa.titulo}`}</span>
            </button>
          </dd>
        </div>
      ))}
    </dl>
  );
}
