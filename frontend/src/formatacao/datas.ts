/** Datas da COE sempre no fuso de negócio (São Paulo), como o backend (FusoDeNegocio). */
const ZONA = 'America/Sao_Paulo';

const DIA_E_HORA = new Intl.DateTimeFormat('pt-BR', {
  timeZone: ZONA,
  day: '2-digit',
  month: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
});

/** Instante da API ("2026-10-20T13:00:00Z") = "20/10 às 10:00". */
export function formatarDiaEHora(instante: string): string {
  const partes = Object.fromEntries(DIA_E_HORA.formatToParts(new Date(instante)).map((p) => [p.type, p.value]));
  return `${partes.day}/${partes.month} às ${partes.hour}:${partes.minute}`;
}

const DIAS_CURTOS = ['dom', 'seg', 'ter', 'qua', 'qui', 'sex', 'sáb'];
const DIAS_LONGOS = ['domingo', 'segunda-feira', 'terça-feira', 'quarta-feira', 'quinta-feira', 'sexta-feira', 'sábado'];
const MESES = [
  'janeiro',
  'fevereiro',
  'março',
  'abril',
  'maio',
  'junho',
  'julho',
  'agosto',
  'setembro',
  'outubro',
  'novembro',
  'dezembro',
];
const DIA_EM_MS = 86_400_000;

/** Dia sem hora ("2026-10-27") como meia-noite UTC: contas de dia que não dependem do fuso do aparelho. */
function emUtc(dia: string): Date {
  const [ano, mes, numero] = dia.split('-').map(Number);
  return new Date(Date.UTC(ano ?? 0, (mes ?? 1) - 1, numero ?? 1));
}

/** 0 = domingo ... 6 = sábado, como o EXTRACT(DOW) do backend. */
export function diaDaSemana(dia: string): number {
  return emUtc(dia).getUTCDay();
}

export function somarDias(dia: string, quantos: number): string {
  return new Date(emUtc(dia).getTime() + quantos * DIA_EM_MS).toISOString().slice(0, 10);
}

/** "2026-10-27" = "ter, 27/10" (protótipo: fmtDia). */
export function formatarDiaCurto(dia: string): string {
  const [, mes, numero] = dia.split('-');
  return `${DIAS_CURTOS[diaDaSemana(dia)]}, ${numero}/${mes}`;
}

/** "2026-10-27" = "terça-feira, 27 de outubro" (para o leitor de tela). */
export function formatarDiaLongo(dia: string): string {
  const data = emUtc(dia);
  return `${DIAS_LONGOS[data.getUTCDay()]}, ${data.getUTCDate()} de ${MESES[data.getUTCMonth()]}`;
}

const SO_A_DATA = new Intl.DateTimeFormat('en-CA', { timeZone: ZONA, year: 'numeric', month: '2-digit', day: '2-digit' });

/** O dia de hoje no fuso de negócio ("AAAA-MM-DD"); quem decide os dias livres é a API. */
export function hojeEmSaoPaulo(agora: Date = new Date()): string {
  return SO_A_DATA.format(agora);
}

/** Data da API ("1980-05-10") = "10/05/1980" (sem fuso: é só a data). */
export function formatarData(data: string): string {
  const [ano, mes, dia] = data.split('-');
  return `${dia}/${mes}/${ano}`;
}
