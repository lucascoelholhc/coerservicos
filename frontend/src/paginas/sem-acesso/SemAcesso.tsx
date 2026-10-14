import { CAMINHOS } from '../../caminhos';
import { Botao } from '../../componentes/Botao/Botao';
import { useTitulo } from '../../layout/titulo';
import estilos from './SemAcesso.module.css';

/** Logado, mas sem o papel da página (diferente de "não encontrada"). */
export function SemAcesso() {
  useTitulo('Sem acesso');
  return (
    <section className={`largura ${estilos.pagina}`}>
      <div className={estilos.cartao}>
        <h1>Você não tem acesso a esta página</h1>
        <p>Esta página é de outro tipo de conta. Se acha que é um engano, fale com a equipe COE.</p>
        <Botao para={CAMINHOS.conta}>Ir para minha conta</Botao>
      </div>
    </section>
  );
}
