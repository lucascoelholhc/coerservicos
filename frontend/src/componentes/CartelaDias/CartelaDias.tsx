import { Avatar } from '../Avatar/Avatar';
import { Carimbo, type TipoDeCarimbo } from '../Carimbo/Carimbo';
import estilos from './CartelaDias.module.css';

export interface DiaDaCartela {
  semana: string;
  numero: string;
  /** liberado: número verde; aprovar: dia amarelo, carimbo batendo; guardado: tracejado. */
  estilo: 'liberado' | 'aprovar' | 'guardado';
  carimbo: TipoDeCarimbo;
  texto: string;
}

export interface CartelaDeExemplo {
  /** Nome acessível da cartela inteira; o tipo obriga a começar por "Exemplo:" (não é um contrato real). */
  descricao: `Exemplo:${string}`;
  profissional: string;
  titulo: string;
  subtitulo: string;
  dias: readonly DiaDaCartela[];
  rodape: string;
}

/**
 * Cartela de dias do "Dia carimbado" (herói do Início). É um exemplo, não um contrato: "Exemplo"
 * e "Ilustração" ficam visíveis e o leitor de tela ouve a descrição inteira.
 */
export function CartelaDias({ cartela }: { cartela: CartelaDeExemplo }) {
  return (
    <div className={estilos.cartela} role="img" aria-label={cartela.descricao}>
      <div className={estilos.marcas}>
        <span className={estilos.exemplo}>Exemplo</span>
        <span className={estilos.ilustracao}>Ilustração</span>
      </div>
      <div className={estilos.cabeca}>
        <Avatar nome={cartela.profissional} tamanho="sm" />
        <div>
          <b>{cartela.titulo}</b>
          <span className={estilos.subtitulo}>{cartela.subtitulo}</span>
        </div>
      </div>
      <div className={estilos.dias}>
        {cartela.dias.map((dia) => (
          <div key={`${dia.semana}-${dia.numero}`} className={estilos.dia} data-estilo={dia.estilo}>
            <span className={estilos.semana}>{dia.semana}</span>
            <span className={estilos.numero}>{dia.numero}</span>
            <span className={estilos.lugarDoCarimbo}>
              <Carimbo tipo={dia.carimbo} compacto animar={dia.estilo === 'aprovar'} />
            </span>
            <span className={estilos.texto}>{dia.texto}</span>
          </div>
        ))}
      </div>
      <p className={estilos.rodape}>{cartela.rodape}</p>
    </div>
  );
}
