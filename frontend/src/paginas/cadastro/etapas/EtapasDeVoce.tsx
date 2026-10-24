import { useState } from 'react';
import { Link } from 'react-router';

import { buscarCadastro, enviarDocumento, salvarEtapa } from '../../../api/cadastroProfissional';
import type { TipoDeDocumento } from '../../../api/tipos';
import { CAMINHOS } from '../../../caminhos';
import { CampoArquivo } from '../../../componentes/CampoArquivo/CampoArquivo';
import { CampoTexto } from '../../../componentes/CampoTexto/CampoTexto';
import { Nota } from '../../../componentes/Nota/Nota';
import { Opcoes } from '../../../componentes/Opcoes/Opcoes';
import estilos from '../Cadastro.module.css';
import { QuadroDaEtapa } from '../QuadroDaEtapa';
import { mensagemDoEnvio, umaSo, usePrevias, useSalvarEtapa, type PropsDaEtapa } from './comum';

const CAMPOS_DOS_DADOS = {
  'cpf-invalido': 'cpf',
  'cpf-em-uso': 'cpf',
  // o servidor só aceita o CPF de quem já confirmou o celular (contra enumeração de CPF)
  'celular-nao-confirmado': 'cpf',
  'idade-minima': 'nascimento',
  'data-nascimento-invalida': 'nascimento',
};

/** Etapa 2: nome e celular são da conta (só leitura); aqui entram CPF e data de nascimento. */
export function EtapaDados({ numero, cadastro, conta, aoSalvar, aoVoltar }: PropsDaEtapa) {
  // O CPF nunca volta da API em claro: o campo começa vazio e só é mandado quando a pessoa digita.
  const [cpf, setCpf] = useState('');
  const [nascimento, setNascimento] = useState(cadastro.dataNascimento ?? '');
  const { enviando, erro, campos, salvar, avisar } = useSalvarEtapa(aoSalvar, CAMPOS_DOS_DADOS);

  function enviar() {
    const digitos = cpf.replace(/\D/g, '');
    if (digitos === '' && cadastro.cpf !== null && nascimento === cadastro.dataNascimento) {
      aoSalvar(cadastro);
      return;
    }
    const faltas: Record<string, string> = {};
    if (digitos === '') {
      faltas.cpf = cadastro.cpf === null ? 'Digite o seu CPF.' : 'Digite o CPF de novo para salvar a nova data.';
    } else if (digitos.length !== 11) {
      faltas.cpf = 'O CPF tem 11 números.';
    }
    if (nascimento === '') {
      faltas.nascimento = 'Digite a sua data de nascimento.';
    }
    if (Object.keys(faltas).length > 0) {
      avisar(faltas);
      return;
    }
    salvar(async () => {
      const salvo = await salvarEtapa('dados', { cpf: digitos, dataNascimento: nascimento });
      setCpf('');
      return salvo;
    });
  }

  return (
    <QuadroDaEtapa numero={numero} cadastro={cadastro} aoEnviar={enviar} aoVoltar={aoVoltar} enviando={enviando} erro={erro} campos={campos}>
      <CampoTexto nome="nome" rotulo="Nome completo" valor={cadastro.nome} aoMudar={() => undefined} somenteLeitura dica="É o nome da sua conta." />
      <CampoTexto
        nome="celular"
        rotulo="Celular"
        valor={conta.celular ?? ''}
        aoMudar={() => undefined}
        somenteLeitura
        dica="Seu número fica escondido até alguém contratar você."
      />
      {!conta.celularConfirmado && (
        <Nota tipo="atencao">
          Confirme seu celular antes de informar o CPF. <Link to={CAMINHOS.confirmarCelular}>Confirmar celular</Link>
        </Nota>
      )}
      <CampoTexto
        nome="cpf"
        rotulo="CPF"
        valor={cpf}
        aoMudar={setCpf}
        inputMode="numeric"
        autoComplete="off"
        maxLength={14}
        erro={campos.cpf}
        dica={
          cadastro.cpf === null
            ? 'Só a COE vê. Ele nunca aparece no seu perfil.'
            : `Já guardamos o CPF ${cadastro.cpf}. Para trocar, digite o CPF novo.`
        }
      />
      <CampoTexto
        nome="nascimento"
        rotulo="Data de nascimento"
        tipo="date"
        valor={nascimento}
        aoMudar={setNascimento}
        autoComplete="bday"
        erro={campos.nascimento}
      />
    </QuadroDaEtapa>
  );
}

const TIPOS_DO_DOCUMENTO = [
  { valor: 'rg_frente', texto: 'RG' },
  { valor: 'cnh', texto: 'CNH' },
] as const;

type TipoDoDocumento = (typeof TIPOS_DO_DOCUMENTO)[number]['valor'];

/** Etapa 3: foto do RG (frente) ou da CNH, selfie com o documento e, para eletricista, o certificado de NR-10. */
export function EtapaDocumentos({ numero, cadastro, aoSalvar, aoAtualizar, aoVoltar }: PropsDaEtapa) {
  const [documento, setDocumento] = useState<TipoDoDocumento>(
    cadastro.documentos.some((d) => d.tipo === 'cnh') ? 'cnh' : 'rg_frente',
  );
  const [mandando, setMandando] = useState<TipoDeDocumento | null>(null);
  const [errosDoEnvio, setErrosDoEnvio] = useState<Partial<Record<TipoDeDocumento, string>>>({});
  const { previas, guardar } = usePrevias();
  const { erro, avisar } = useSalvarEtapa(aoSalvar);
  const eletricista = cadastro.profissao === 'eletricista' || cadastro.outrasProfissoes.includes('eletricista');

  async function mandar(tipo: TipoDeDocumento, arquivos: File[]) {
    const arquivo = arquivos[0];
    if (mandando !== null || arquivo === undefined) {
      return;
    }
    setMandando(tipo);
    setErrosDoEnvio((anteriores) => ({ ...anteriores, [tipo]: undefined }));
    try {
      await enviarDocumento(tipo, arquivo);
      guardar(tipo, arquivo);
      const atualizado = await buscarCadastro(new AbortController().signal);
      if (atualizado) {
        aoAtualizar(atualizado);
      }
    } catch (falha) {
      setErrosDoEnvio((anteriores) => ({ ...anteriores, [tipo]: mensagemDoEnvio(falha) }));
    } finally {
      setMandando(null);
    }
  }

  function enviar() {
    if (!cadastro.etapas.documentos) {
      avisar({}, 'Mande a foto do documento e a selfie para continuar.');
      return;
    }
    aoSalvar(cadastro);
  }

  function situacao(tipo: TipoDeDocumento) {
    const enviado = cadastro.documentos.find((d) => d.tipo === tipo);
    if (mandando === tipo) {
      return <p role="status">Mandando a foto…</p>;
    }
    if (!enviado) {
      return null;
    }
    return (
      <div className={estilos.enviado}>
        {previas[tipo] && <img className={estilos.miniatura} src={previas[tipo]} alt="" />}
        {enviado.status === 'recusado' ? (
          <Nota tipo="atencao">A COE não aceitou esta foto. Mande outra.</Nota>
        ) : (
          <Nota tipo="ok">Foto enviada. Se quiser, mande outra no lugar.</Nota>
        )}
      </div>
    );
  }

  return (
    <QuadroDaEtapa numero={numero} cadastro={cadastro} aoEnviar={enviar} aoVoltar={aoVoltar} enviando={mandando !== null} erro={erro}>
      <Nota>
        Isso passa segurança para o cliente. Seus documentos <b>nunca</b> aparecem no perfil.
      </Nota>
      <Opcoes
        legenda="Qual documento você vai mandar?"
        nome="documento"
        tipo="unica"
        opcoes={TIPOS_DO_DOCUMENTO}
        selecionadas={[documento]}
        aoMudar={umaSo((tipo) => setDocumento(tipo as TipoDoDocumento))}
      />
      <CampoArquivo
        rotulo={documento === 'cnh' ? '1. Foto da CNH' : '1. Foto da frente do RG'}
        dica="Sem reflexo, dando para ler tudo."
        botao="Fotografar documento"
        captura="environment"
        grande
        desabilitado={mandando !== null}
        erro={errosDoEnvio[documento]}
        aoEscolher={(arquivos) => void mandar(documento, arquivos)}
      >
        {situacao(documento)}
      </CampoArquivo>
      <CampoArquivo
        rotulo="2. Selfie segurando o documento"
        dica="Seu rosto e o documento aparecendo juntos, num lugar claro."
        botao="Tirar selfie"
        captura="user"
        grande
        desabilitado={mandando !== null}
        erro={errosDoEnvio.selfie}
        aoEscolher={(arquivos) => void mandar('selfie', arquivos)}
      >
        {situacao('selfie')}
      </CampoArquivo>
      {eletricista && (
        <CampoArquivo
          rotulo="3. Certificado de NR-10 (se tiver)"
          dica='Se mandar, seu perfil ganha o selo "NR-10 conferido".'
          botao="Fotografar certificado"
          captura="environment"
          desabilitado={mandando !== null}
          erro={errosDoEnvio.certificado_nr10}
          aoEscolher={(arquivos) => void mandar('certificado_nr10', arquivos)}
        >
          {situacao('certificado_nr10')}
        </CampoArquivo>
      )}
    </QuadroDaEtapa>
  );
}
