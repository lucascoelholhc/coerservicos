/*
 * Formatação dos números de regra que vêm da API (GET /api/publico/regras). Nenhum valor fica fixo
 * no front: estas funções só transformam o que a API mandou em texto.
 */

/** "PT12H" = "12 h"; "PT90M" = "1 h 30 min" (Duration do Java em ISO-8601, só horas e minutos). */
export function formatarPrazo(iso: string): string {
  const partes = /^PT(?:(\d+)H)?(?:(\d+)M)?$/.exec(iso);
  const totalEmMinutos = partes ? Number(partes[1] ?? 0) * 60 + Number(partes[2] ?? 0) : 0;
  if (totalEmMinutos <= 0) {
    throw new Error(`Prazo fora do formato: ${iso}`);
  }
  const horas = Math.floor(totalEmMinutos / 60);
  const minutos = totalEmMinutos % 60;
  return [horas > 0 ? `${horas} h` : '', minutos > 0 ? `${minutos} min` : ''].filter(Boolean).join(' ');
}

/** "0.1000" = "10%"; "0.1250" = "12,5%" (fração com até 4 casas, como o Percentual do backend). */
export function formatarPercentual(fracao: string): string {
  if (!/^0\.\d{1,4}$/.test(fracao)) {
    throw new Error(`Percentual fora do formato: ${fracao}`);
  }
  const centesimosDePorcento = Number(fracao.slice(2).padEnd(4, '0'));
  return `${(centesimosDePorcento / 100).toLocaleString('pt-BR', { maximumFractionDigits: 2 })}%`;
}
