import type { CartelaDeExemplo } from '../../componentes/CartelaDias/CartelaDias';
import type { OpcaoDeSelecao } from '../../componentes/CampoSelecao/CampoSelecao';
import type { ProfissaoIlustrada } from '../../componentes/Ilustracao/Ilustracao';

/*
 * PROVISÓRIO (FE-01): textos e listas do Início, copiados do protótipo (ef7d397).
 * - Profissões e cidades: viram dados da API do catálogo no DOM-01/FE-03 (hoje, as mesmas do V3 e do V11).
 * - Prazo de liberação e comissão: voltam no dia 9/10 lidos de GET /api/publico/regras. Nenhum número de
 *   regra de negócio fica fixo no front; por isso os textos abaixo não citam horas nem porcentagem.
 */

export const PROFISSOES: readonly (OpcaoDeSelecao & { valor: ProfissaoIlustrada })[] = [
  { valor: 'pedreiro', texto: 'Pedreiro' },
  { valor: 'pintor', texto: 'Pintor' },
  { valor: 'eletricista', texto: 'Eletricista' },
  { valor: 'diarista', texto: 'Diarista' },
  { valor: 'jardineiro', texto: 'Jardineiro' },
];

export const CIDADES: readonly OpcaoDeSelecao[] = [
  'Blumenau',
  'Gaspar',
  'Pomerode',
  'Indaial',
  'Timbó',
  'Brusque',
  'Itajaí',
  'Balneário Camboriú',
  'Jaraguá do Sul',
  'Navegantes',
].map((cidade) => ({ valor: cidade, texto: cidade }));

export const HEROI = {
  titulo: 'Pague por dia. Libere quando o dia estiver feito.',
  texto:
    'Pedreiro, pintor, eletricista, jardineiro e diarista com documento conferido. O dinheiro fica guardado com a COE e só vai para o profissional depois que você aprova cada dia de trabalho.',
};

export const CARTELA_EXEMPLO: CartelaDeExemplo = {
  descricao:
    'Exemplo: obra de 3 diárias com o Valdir. Segunda já foi liberada para ele, terça você acabou de aprovar e quarta está paga e guardada.',
  profissional: 'Valdir Schmitt',
  titulo: 'Valdir Schmitt, pedreiro',
  subtitulo: 'Garagem no Garcia: 3 diárias de R$ 280',
  dias: [
    { semana: 'seg', numero: '28', estilo: 'liberado', carimbo: 'liberado', texto: 'Já está com o Valdir' },
    { semana: 'ter, hoje', numero: '29', estilo: 'aprovar', carimbo: 'aprovado', texto: 'Você viu a foto e aprovou' },
    { semana: 'qua', numero: '30', estilo: 'guardado', carimbo: 'guardado', texto: 'Pago e guardado na COE' },
  ],
  rodape: 'Você pagou as 3 diárias antes. Cada uma só vai para o Valdir depois do seu ok.',
};

export const PASSOS = [
  {
    titulo: 'Escolha e converse',
    texto: 'Veja fotos de trabalhos, experiência, valor da diária e os dias livres. Tire dúvidas pelo chat.',
  },
  {
    titulo: 'Pague as diárias antes',
    texto: 'Pix ou cartão. O dinheiro fica guardado e o profissional sabe que vai receber.',
  },
  {
    titulo: 'Aprove cada dia',
    texto: 'No fim do dia ele manda a foto do serviço. Você aprova e o dinheiro daquele dia vai para ele.',
  },
] as const;

export const FATOS = [
  {
    destaque: 'Você aprova',
    texto: 'Terminou o dia, você aprova. Se não responder, o valor é liberado ao profissional depois de um prazo.',
  },
  {
    destaque: '1 dia',
    texto: 'Se algo der errado, só aquele dia fica travado até a equipe COE decidir. Os outros seguem normais.',
  },
  {
    destaque: 'RG + selfie',
    texto: 'Todo profissional tem documento e selfie conferidos antes de aparecer na busca.',
  },
  {
    destaque: 'Nota real',
    texto: 'Só avalia quem contratou e pagou pelo app. Ninguém compra nem inventa avaliação.',
  },
] as const;

export const CONVITE_PROFISSIONAL = {
  chamada: 'Pedreiro, pintor, eletricista, jardineiro ou diarista?',
  titulo: 'Cadastre-se grátis e receba garantido',
  texto:
    'O cliente paga antes de você sair de casa. Você define o valor da diária e até onde vai trabalhar. O cadastro é feito pelo celular, uma pergunta de cada vez.',
  botao: 'Quero trabalhar com a COE',
  itens: [
    'Sem mensalidade',
    'Dinheiro no Pix quando o cliente aprova o dia',
    'Sem resposta do cliente, o dinheiro é liberado depois de um prazo',
    'A taxa da COE é paga pelo cliente.',
  ],
} as const;
