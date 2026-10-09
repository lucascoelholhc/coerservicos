import diarista from '../../assets/ilustracoes/diarista.svg';
import eletricista from '../../assets/ilustracoes/eletricista.svg';
import jardineiro from '../../assets/ilustracoes/jardineiro.svg';
import pedreiro from '../../assets/ilustracoes/pedreiro.svg';
import pintor from '../../assets/ilustracoes/pintor.svg';
import estilos from './Ilustracao.module.css';

/**
 * Desenhos gerados uma vez do protótipo (scripts/gerar-ilustracoes.mjs). Substituem fotos enquanto
 * não há fotos reais; por isso a etiqueta "Ilustração" fica sempre visível.
 */
const DESENHOS = { pedreiro, pintor, eletricista, jardineiro, diarista };

export type ProfissaoIlustrada = keyof typeof DESENHOS;

export const PROFISSOES_ILUSTRADAS = Object.keys(DESENHOS) as ProfissaoIlustrada[];

interface IlustracaoProps {
  profissao: ProfissaoIlustrada;
  formato?: '4:3' | '1:1';
}

/** Decorativa: o nome da profissão sempre aparece em texto ao lado. */
export function Ilustracao({ profissao, formato = '4:3' }: IlustracaoProps) {
  return (
    <span className={estilos.ilustracao} data-formato={formato}>
      <img className={estilos.imagem} src={DESENHOS[profissao]} alt="" />
      <span className={estilos.etiqueta} aria-hidden="true">
        Ilustração
      </span>
    </span>
  );
}
