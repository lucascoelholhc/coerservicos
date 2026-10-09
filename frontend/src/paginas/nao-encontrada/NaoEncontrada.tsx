import { Botao } from '../../componentes/Botao/Botao';
import { Icone } from '../../icones/Icone';
import { useTitulo } from '../../layout/titulo';
import estilos from './NaoEncontrada.module.css';

export function NaoEncontrada() {
  useTitulo('Página não encontrada');
  return (
    <section className={`largura ${estilos.pagina}`}>
      <div className={estilos.cartao}>
        <Icone nome="search" tamanho={40} className={estilos.icone} />
        <h1>Página não encontrada</h1>
        <Botao para="/">Ir para o início</Botao>
      </div>
    </section>
  );
}
