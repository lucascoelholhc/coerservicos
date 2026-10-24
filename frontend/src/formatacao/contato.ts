/** "47900000099" = "(47) 90000-0099"; outro formato volta como veio. */
export function formatarCelular(celular: string): string {
  const digitos = celular.replace(/\D/g, '');
  if (digitos.length === 11) {
    return `(${digitos.slice(0, 2)}) ${digitos.slice(2, 7)}-${digitos.slice(7)}`;
  }
  if (digitos.length === 10) {
    return `(${digitos.slice(0, 2)}) ${digitos.slice(2, 6)}-${digitos.slice(6)}`;
  }
  return celular;
}
