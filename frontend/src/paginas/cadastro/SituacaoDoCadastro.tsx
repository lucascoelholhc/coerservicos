import type { SituacaoDoCadastro as Situacao } from '../../api/tipos';
import { CAMINHOS } from '../../caminhos';
import { Botao } from '../../componentes/Botao/Botao';
import { Icone, type NomeDoIcone } from '../../icones/Icone';
import estilos from './Cadastro.module.css';

interface SituacaoDoCadastroProps {
  situacao: Exclude<Situacao, 'rascunho' | 'correcao_pedida'>;
}

const TELAS: Record<SituacaoDoCadastroProps['situacao'], { icone: NomeDoIcone; titulo: string; texto: string }> = {
  em_analise: {
    icone: 'shield',
    titulo: 'Cadastro enviado',
    texto: 'Agora a COE confere seus documentos. Você recebe um SMS quando seu perfil estiver no ar.',
  },
  ativo: {
    icone: 'check',
    titulo: 'Seu cadastro foi aprovado',
    texto: 'Seu perfil já aparece na busca para os clientes da sua região.',
  },
  recusado: {
    icone: 'xcircle',
    titulo: 'Cadastro não aprovado',
    texto: 'A COE não aprovou este cadastro. O motivo foi enviado por SMS para o seu celular.',
  },
  suspenso: {
    icone: 'alert',
    titulo: 'Seu perfil está suspenso',
    texto: 'Enquanto isso, ele não aparece na busca. A COE avisou o motivo por SMS.',
  },
};

/** Depois do envio o assistente some: o cadastro está com a COE (protótipo: "Cadastro enviado"). */
export function SituacaoDoCadastro({ situacao }: SituacaoDoCadastroProps) {
  const tela = TELAS[situacao];
  return (
    <section className={`largura ${estilos.pagina}`}>
      <div className={`${estilos.assistente} ${estilos.situacao}`} data-situacao={situacao}>
        <span className={estilos.selo}>
          <Icone nome={tela.icone} tamanho={44} />
        </span>
        <h1>{tela.titulo}</h1>
        <p className={estilos.textoDaSituacao}>{tela.texto}</p>
        {situacao === 'em_analise' && (
          <ol className={estilos.linhaDoTempo}>
            <li data-estado="feito">
              <Icone nome="check" />
              <b>Cadastro recebido</b>
            </li>
            <li data-estado="agora">
              <Icone nome="clock" />
              <b>Conferência do documento e da selfie</b>
            </li>
            <li data-estado="depois">
              <Icone nome="user" />
              <b>Seu perfil aparece na busca</b>
            </li>
          </ol>
        )}
        <Botao para={CAMINHOS.conta} tamanho="lg">
          Ir para minha conta
        </Botao>
      </div>
    </section>
  );
}
