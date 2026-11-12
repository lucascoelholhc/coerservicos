import { chamar } from './cliente';

/** Foto nova de perfil já aprovado, oculta até o admin conferir (RN15a, RN57). */
export interface FotoPendente {
  id: string;
  servico: string | null;
  legenda: string | null;
}

export interface PendenciaDeFotos {
  profissionalId: string;
  nome: string;
  fotos: FotoPendente[];
}

/** Um parâmetro de negócio em vigor (tabela configuracao, só de inserção). */
export interface ParametroDaConfiguracao {
  chave: string;
  valor: string;
  tipo: string;
  descricao: string;
  vigenteDesde: string;
}

export function buscarFotosPendentes(sinal: AbortSignal): Promise<{ itens: PendenciaDeFotos[] }> {
  return chamar<{ itens: PendenciaDeFotos[] }>('/admin/profissionais/pendencias', { sinal });
}

export async function decidirFoto(profissional: string, foto: string, aprovar: boolean): Promise<void> {
  await chamar<unknown>(
    `/admin/profissionais/${encodeURIComponent(profissional)}/fotos/${encodeURIComponent(foto)}/${aprovar ? 'aprovar' : 'recusar'}`,
    { metodo: 'POST' },
  );
}

export function buscarConfiguracao(sinal: AbortSignal): Promise<{ itens: ParametroDaConfiguracao[] }> {
  return chamar<{ itens: ParametroDaConfiguracao[] }>('/admin/configuracao', { sinal });
}

/** O código da ação do admin chega por SMS no celular dele (o mesmo da Equipe). */
export async function pedirCodigoDaAcao(): Promise<void> {
  await chamar<unknown>('/admin/equipe/codigo', { metodo: 'POST' });
}

/** RN44g: mudar a configuração exige o código SMS na hora; a mudança vale para os contratos novos. */
export function mudarParametro(chave: string, valor: string, codigo: string): Promise<ParametroDaConfiguracao> {
  return chamar<ParametroDaConfiguracao>(`/admin/configuracao/${encodeURIComponent(chave)}`, {
    metodo: 'PUT',
    corpo: { valor, codigo },
  });
}
