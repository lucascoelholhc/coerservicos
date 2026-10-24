import { Icone } from '../../icones/Icone';
import estilos from './Selos.module.css';

/** Só aparece depois da verificação do admin: todo perfil público já passou por ela (DOM-03). */
export function SeloDocumento() {
  return (
    <span className={estilos.selo}>
      <Icone nome="shield" tamanho={18} />
      Documento conferido
    </span>
  );
}

export function SeloNr10() {
  return (
    <span className={estilos.selo}>
      <Icone nome="bolt" tamanho={17} />
      NR-10 conferido
    </span>
  );
}

interface ReputacaoProps {
  novoNaCoe: boolean;
  nota?: string | number | null;
  total?: number;
}

/** RN23: sem avaliações, "Novo na COE"; nunca nota inventada. */
export function Reputacao({ novoNaCoe, nota, total = 0 }: ReputacaoProps) {
  if (novoNaCoe || nota === null || nota === undefined) {
    return (
      <span className={estilos.novo}>
        <Icone nome="star" tamanho={15} />
        Novo na COE
      </span>
    );
  }
  const texto = Number(nota).toLocaleString('pt-BR', { minimumFractionDigits: 1, maximumFractionDigits: 1 });
  return (
    <span className={estilos.nota}>
      <Icone nome="star" tamanho={16} className={estilos.estrela} />
      {texto}
      <span className="sr-only"> de 5</span>
      <span className={estilos.total}>
        · {total} {total === 1 ? 'avaliação' : 'avaliações'}
      </span>
    </span>
  );
}
