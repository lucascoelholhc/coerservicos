/* Textos e contas de exibição da busca e do perfil público. Quem decide o que é livre é a API. */
import { diaDaSemana, formatarDiaCurto, formatarDiaLongo, somarDias } from '../../formatacao/datas';

/** A API manda os dias livres das quatro semanas a partir de amanhã (BuscaPublicaService.JANELA_DE_DIAS). */
const DIAS_NA_JANELA = 28;

export function textoDoProximoLivre(dia: string | null, hoje: string): string {
  if (dia === null) {
    return 'Sem dia livre nas próximas 4 semanas';
  }
  return dia === somarDias(hoje, 1) ? 'Livre amanhã' : `Livre ${formatarDiaCurto(dia)}`;
}

export function textoDaExperiencia(anos: number): string {
  if (anos < 1) {
    return 'Menos de 1 ano';
  }
  return anos === 1 ? '1 ano' : `${anos} anos`;
}

const FERRAMENTAS: Record<string, string> = { sim: 'Leva as suas', parte: 'Leva parte', nao: 'Você fornece' };

export function textoDasFerramentas(ferramentas: string): string {
  return FERRAMENTAS[ferramentas] ?? 'A combinar';
}

export type EstadoDoDia = 'passado' | 'livre' | 'ocupado' | 'folga' | 'fora';

const TEXTO_DO_ESTADO: Record<EstadoDoDia, string> = {
  passado: 'já passou',
  livre: 'livre',
  ocupado: 'ocupado',
  folga: 'não trabalha neste dia',
  fora: 'agenda ainda não aberta',
};

export interface CasaDoCalendario {
  dia: string;
  estado: EstadoDoDia;
  hoje: boolean;
  /** Para o leitor de tela: "segunda-feira, 26 de outubro: livre". */
  rotulo: string;
}

/**
 * Semanas inteiras (segunda a domingo) que cobrem a janela da API, como o calendário do protótipo.
 * Dia de trabalho fora da lista de livres = ocupado.
 */
export function casasDoCalendario(
  hoje: string,
  diasDaSemana: readonly number[],
  diasLivres: readonly string[],
): CasaDoCalendario[] {
  const amanha = somarDias(hoje, 1);
  const ultimo = somarDias(amanha, DIAS_NA_JANELA - 1);
  const voltaAteSegunda = (diaDaSemana(amanha) + 6) % 7;
  const avancaAteDomingo = (7 - diaDaSemana(ultimo)) % 7;
  const primeiro = somarDias(amanha, -voltaAteSegunda);
  const total = voltaAteSegunda + DIAS_NA_JANELA + avancaAteDomingo;
  const livres = new Set(diasLivres);
  const trabalha = new Set(diasDaSemana);

  return Array.from({ length: total }, (_, indice) => {
    const dia = somarDias(primeiro, indice);
    let estado: EstadoDoDia;
    if (dia <= hoje) {
      estado = 'passado';
    } else if (dia > ultimo) {
      estado = 'fora';
    } else if (!trabalha.has(diaDaSemana(dia))) {
      estado = 'folga';
    } else {
      estado = livres.has(dia) ? 'livre' : 'ocupado';
    }
    const ehHoje = dia === hoje;
    return {
      dia,
      estado,
      hoje: ehHoje,
      rotulo: `${formatarDiaLongo(dia)}: ${TEXTO_DO_ESTADO[estado]}${ehHoje ? ' (hoje)' : ''}`,
    };
  });
}
