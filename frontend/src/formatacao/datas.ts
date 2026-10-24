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

/** Data da API ("1980-05-10") = "10/05/1980" (sem fuso: é só a data). */
export function formatarData(data: string): string {
  const [ano, mes, dia] = data.split('-');
  return `${dia}/${mes}/${ano}`;
}
