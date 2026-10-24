import type { DetalheDaVerificacao, FilaDeVerificacao } from '../api/tipos';
import { CADASTRO_COMPLETO } from './dadosDoCadastro';

/** Fila do admin (GET /api/admin/verificacao), mais antigo primeiro. */
export const FILA_DA_VERIFICACAO: FilaDeVerificacao = {
  itens: [
    { id: 'p1', nome: 'Valdir Schmitt', profissao: 'pedreiro', enviadoEm: '2026-10-20T13:00:00Z', nr10: false },
    { id: 'p2', nome: 'Cristiano Weber', profissao: 'eletricista', enviadoEm: '2026-10-21T09:30:00Z', nr10: true },
  ],
  temMais: false,
};

/** Detalhe (GET /api/admin/verificacao/{id}): CPF completo para conferir com o documento. */
export const DETALHE_DA_VERIFICACAO: DetalheDaVerificacao = {
  id: 'p1',
  cpf: '111.444.777-35',
  celularConfirmado: true,
  nr10: 'nao_tem',
  enviadoEm: '2026-10-20T13:00:00Z',
  cadastro: { ...CADASTRO_COMPLETO, status: 'em_analise', nome: 'Valdir Schmitt' },
};
