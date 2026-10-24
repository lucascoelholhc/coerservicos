import type { PerfilPublico, ResultadoDaBusca } from '../api/tipos';

/** Host do armazenamento local (SeaweedFS), o mesmo do img-src da CSP. */
export const HOST_DO_BUCKET_LOCAL = 'http://localhost:8333';

const BLUMENAU = { codigoIbge: 4202404, nome: 'Blumenau', uf: 'SC' };

/** Lista como a API devolve (BuscaResposta.Busca): dados fictícios, nome "Primeiro S.". */
export const RESULTADO_DA_BUSCA: ResultadoDaBusca = {
  itens: [
    {
      id: '00000000-0000-0000-0000-0000000d0001',
      nome: 'Valdir S.',
      profissao: 'pedreiro',
      outrasProfissoes: ['pintor'],
      valorDiaria: '280.00',
      valorMeiaDiaria: '150.00',
      experienciaAnos: 20,
      cidade: BLUMENAU,
      distanciaKm: null,
      proximoDiaLivre: '2026-10-24',
      seloNr10: false,
      novoNaCoe: true,
    },
    {
      id: '00000000-0000-0000-0000-0000000d0006',
      nome: 'Cristiano W.',
      profissao: 'eletricista',
      outrasProfissoes: [],
      valorDiaria: '320.00',
      valorMeiaDiaria: null,
      experienciaAnos: 10,
      cidade: { codigoIbge: 4202909, nome: 'Brusque', uf: 'SC' },
      distanciaKm: 23.4,
      proximoDiaLivre: null,
      seloNr10: true,
      novoNaCoe: true,
    },
  ],
  temMais: false,
};

/** Perfil como a API devolve (BuscaResposta.Perfil); a foto vem do bucket local por URL assinada. */
export const PERFIL_DE_EXEMPLO: PerfilPublico = {
  id: '00000000-0000-0000-0000-0000000d0001',
  nome: 'Valdir S.',
  profissao: 'pedreiro',
  outrasProfissoes: ['pintor'],
  servicos: [
    { profissao: 'pedreiro', nome: 'Alvenaria' },
    { profissao: 'pedreiro', nome: 'Contrapiso' },
    { profissao: 'pintor', nome: 'Pintura interna' },
  ],
  experienciaAnos: 20,
  ferramentas: 'sim',
  horario: '7h às 17h',
  valorDiaria: '280.00',
  valorMeiaDiaria: '150.00',
  cidade: BLUMENAU,
  raioKm: 15,
  outrasCidades: [{ codigoIbge: 4205902, nome: 'Gaspar', uf: 'SC' }],
  diasDaSemana: [1, 2, 3, 4, 5],
  diasLivres: ['2026-10-26', '2026-10-27', '2026-10-29'],
  sobre: 'Trabalho com capricho. Faço muro e calçada.',
  seloNr10: false,
  avaliacoes: { total: 0, nota: null, novoNaCoe: true },
  fotos: [
    {
      id: 'f1',
      servico: 'Alvenaria',
      legenda: 'Muro de bloco',
      url: `${HOST_DO_BUCKET_LOCAL}/coe-arquivos-local/portfolio/123e4567-e89b-12d3-a456-426614174000.jpg?X-Amz-Signature=x`,
      expiraEm: '2099-01-01T00:00:00Z',
    },
  ],
};
