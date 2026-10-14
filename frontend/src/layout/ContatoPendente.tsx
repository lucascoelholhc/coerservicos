import { CAMINHOS } from '../caminhos';
import { Botao, type VarianteDoBotao } from '../componentes/Botao/Botao';
import { Nota } from '../componentes/Nota/Nota';
import estilos from './ContatoPendente.module.css';
import { useTitulo } from './titulo';

/** O mesmo texto do 403 contato-pendente da API (BloqueioDeContatoPendente). */
export const MENSAGEM_DE_CONTATO_PENDENTE =
  'Cadastre e confirme um novo celular ou e-mail para continuar usando a COE.';

/** A nota da RN61 com o caminho para recolocar o contato. */
export function AvisoDeContatoPendente({ variante = 'contorno' }: { variante?: VarianteDoBotao }) {
  return (
    <div className={estilos.aviso}>
      <Nota tipo="atencao">{MENSAGEM_DE_CONTATO_PENDENTE}</Nota>
      <Botao para={CAMINHOS.recolocarContato} variante={variante}>
        Recolocar contato
      </Botao>
    </div>
  );
}

/** No lugar de uma tela privada que o backend não libera com contato pendente. */
export function TelaDeContatoPendente() {
  useTitulo('Falta um contato');
  return (
    <section className={`largura ${estilos.tela}`}>
      <h1>Falta um contato na sua conta</h1>
      <AvisoDeContatoPendente variante="principal" />
    </section>
  );
}
