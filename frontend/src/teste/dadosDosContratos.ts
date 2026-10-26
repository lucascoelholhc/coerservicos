import type { Contrato, EnvioDaMensagem, MensagemDoChat, Pagamento, ResumoDaConversa, ResumoDoContrato } from '../api/tipos';
import { PERFIL_DE_EXEMPLO } from './dadosDaBusca';

/** Dados da área do cliente (FE-05) para o Vitest e o Playwright. Celular sempre fictício (479000000NN). */
export const CONTRATO_AGUARDANDO: Contrato = {
  id: 'c0000000-0000-0000-0000-000000000001',
  numero: 1042,
  status: 'aguardando_pagamento',
  profissionalId: PERFIL_DE_EXEMPLO.id,
  profissional: { id: PERFIL_DE_EXEMPLO.id, nome: 'Valdir S.', profissao: 'Pedreiro' },
  cidade: { codigoIbge: 4202404, nome: 'Blumenau', uf: 'SC' },
  cep: '89010000',
  dias: [
    {
      id: 'd1',
      data: '2026-10-26',
      tipo: 'inteira',
      status: 'agendada',
      valor: '280.00',
      autoLiberaEm: null,
    },
    {
      id: 'd2',
      data: '2026-10-27',
      tipo: 'inteira',
      status: 'agendada',
      valor: '280.00',
      autoLiberaEm: null,
    },
  ],
  servicos: ['Alvenaria'],
  descricao: 'Levantar um muro de 6 metros no fundo do terreno.',
  material: 'cliente_tem',
  valorServicos: '560.00',
  valorComissao: '56.00',
  valorTotal: '616.00',
  pagamentoExpiraEm: '2026-10-25T13:00:00Z',
  endereco: { logradouro: 'Rua das Palmeiras', numero: '120', bairro: 'Centro', complemento: null },
  contato: null,
};

export const CONTRATO_PAGO: Contrato = {
  ...CONTRATO_AGUARDANDO,
  status: 'pago',
  dias: CONTRATO_AGUARDANDO.dias.map((dia) => ({ ...dia, status: 'paga' })),
  contato: { nome: 'Valdir Souza', celular: '47900000099' },
};

export const PAGAMENTO_PIX: Pagamento = {
  cobrancaId: 'b1',
  metodo: 'pix',
  status: 'pendente',
  valor: '616.00',
  pixCopiaECola: '00020101COEFALSOpay_0000000000000001',
  linkPagamento: 'http://localhost:5173/pagamento-falso/pay_0000000000000001',
  expiraEm: '2026-10-25T13:00:00Z',
  simulavel: true,
};

export const LISTA_DE_CONTRATOS: { itens: ResumoDoContrato[] } = {
  itens: [
    {
      id: CONTRATO_AGUARDANDO.id,
      numero: CONTRATO_AGUARDANDO.numero,
      status: 'aguardando_pagamento',
      profissional: CONTRATO_AGUARDANDO.profissional,
      dias: CONTRATO_AGUARDANDO.dias.map(({ id, data, status }) => ({ id, data, status })),
      valorTotal: '616.00',
      criadoEm: '2026-10-25T12:00:00Z',
    },
  ],
};

export const CONVERSA: ResumoDaConversa = {
  id: 'm1',
  profissionalId: PERFIL_DE_EXEMPLO.id,
  outroNome: 'Valdir S.',
  souProfissional: false,
  ultimaMensagemEm: '2026-10-25T12:00:00Z',
  naoLidas: 1,
  contatoLiberado: false,
};

export const MENSAGENS: MensagemDoChat[] = [
  {
    id: 'g1',
    minha: true,
    texto: 'Bom dia! O muro tem uns 6 metros.',
    bloqueada: false,
    motivos: [],
    lida: true,
    criadoEm: '2026-10-25T11:00:00Z',
  },
  {
    id: 'g2',
    minha: false,
    texto: 'Bom dia! Dá para fazer em dois dias.',
    bloqueada: false,
    motivos: [],
    lida: false,
    criadoEm: '2026-10-25T12:00:00Z',
  },
];

export const ENVIO_BLOQUEADO: EnvioDaMensagem = {
  mensagem: {
    id: 'g3',
    minha: true,
    texto: 'me liga no •• •••••-••••',
    bloqueada: true,
    motivos: ['telefone', 'pedido'],
    lida: false,
    criadoEm: '2026-10-25T12:05:00Z',
  },
  aviso: { tentativa: 1, limite: 3, contaEmAnalise: false },
};
