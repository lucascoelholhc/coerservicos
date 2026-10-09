import { chamar } from './cliente';

export interface NovoCliente {
  nome: string;
  celular: string;
  email: string;
  cep: string;
  senha: string;
  versaoTermosAceita: string;
}

/** POST /api/contas/cliente: cria a conta (não faz login; quem chama entra em seguida). */
export function criarCliente(dados: NovoCliente): Promise<{ id: string; nome: string }> {
  return chamar<{ id: string; nome: string }>('/contas/cliente', { metodo: 'POST', corpo: dados });
}
