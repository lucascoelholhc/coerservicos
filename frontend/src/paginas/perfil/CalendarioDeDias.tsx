import { casasDoCalendario } from '../busca/textos';
import estilos from './PerfilPublico.module.css';

const SEMANA = ['seg', 'ter', 'qua', 'qui', 'sex', 'sáb', 'dom'];
const MESES = new Intl.DateTimeFormat('pt-BR', { month: 'long', timeZone: 'UTC' });

function nomeDoMes(dia: string): string {
  return MESES.format(new Date(`${dia}T12:00:00Z`));
}

interface CalendarioProps {
  hoje: string;
  diasDaSemana: readonly number[];
  diasLivres: readonly string[];
}

/** Dias livres das quatro semanas da API (protótipo: calendario em modo "view"). Sempre com legenda. */
export function CalendarioDeDias({ hoje, diasDaSemana, diasLivres }: CalendarioProps) {
  const casas = casasDoCalendario(hoje, diasDaSemana, diasLivres);
  const primeiro = casas[0]?.dia ?? hoje;
  const ultimo = casas.at(-1)?.dia ?? hoje;
  const meses =
    nomeDoMes(primeiro) === nomeDoMes(ultimo) ? nomeDoMes(primeiro) : `${nomeDoMes(primeiro)} – ${nomeDoMes(ultimo)}`;
  return (
    <div className={estilos.calendarioCaixa}>
      <p className={estilos.meses}>
        {meses} {ultimo.slice(0, 4)}
      </p>
      <div className={estilos.calendario} role="group" aria-label="Dias livres das próximas 4 semanas">
        {SEMANA.map((dia) => (
          <div key={dia} className={estilos.diaDaSemana} aria-hidden="true">
            {dia}
          </div>
        ))}
        {casas.map((casa) => (
          <div
            key={casa.dia}
            className={estilos.casa}
            data-estado={casa.estado}
            data-hoje={casa.hoje || undefined}
            role="img"
            aria-label={casa.rotulo}
          >
            {Number(casa.dia.slice(8))}
          </div>
        ))}
      </div>
      <p className={estilos.legenda}>
        <span>
          <i className={estilos.legendaLivre} aria-hidden="true" />
          Livre
        </span>
        <span>
          <i className={estilos.legendaOcupado} aria-hidden="true" />
          Ocupado
        </span>
        <span>
          <i className={estilos.legendaFolga} aria-hidden="true" />
          Não trabalha
        </span>
      </p>
    </div>
  );
}
