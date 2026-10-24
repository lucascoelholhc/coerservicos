/*
 * Textos das telas do cadastro, do protótipo (docs/prototipo/js/cadastro.js e config.js). São opções de
 * tela: o servidor confere cada valor (faixas de experiência, raios, tipos de Pix) e decide os mínimos.
 */
import type { EtapaDoCadastro, Ferramentas, TipoDeChavePix } from '../../api/tipos';

export interface DescricaoDaEtapa {
  chave: EtapaDoCadastro;
  titulo: string;
  grupo: string;
}

/** As 11 etapas na ordem do protótipo e da API (MontadorDoCadastro.ETAPAS). */
export const ETAPAS: readonly DescricaoDaEtapa[] = [
  { chave: 'trabalho', titulo: 'Qual é o seu trabalho?', grupo: 'Seu trabalho' },
  { chave: 'dados', titulo: 'Seus dados', grupo: 'Você' },
  { chave: 'documentos', titulo: 'Documento e selfie', grupo: 'Você' },
  { chave: 'servicos', titulo: 'O que você faz?', grupo: 'Seu trabalho' },
  { chave: 'experiencia', titulo: 'Sua experiência', grupo: 'Seu trabalho' },
  { chave: 'valor', titulo: 'Quanto você cobra por dia?', grupo: 'Seu trabalho' },
  { chave: 'onde', titulo: 'Onde você trabalha?', grupo: 'Onde e quando' },
  { chave: 'dias', titulo: 'Em quais dias você trabalha?', grupo: 'Onde e quando' },
  { chave: 'fotos', titulo: 'Fotos dos seus trabalhos', grupo: 'Fotos' },
  { chave: 'sobre', titulo: 'Conte sobre você', grupo: 'Seu perfil' },
  { chave: 'pix', titulo: 'Onde receber o dinheiro', grupo: 'Pagamento' },
];

export const TOTAL_DE_ETAPAS = ETAPAS.length;

/** Início de cada faixa (o servidor aceita só estes: FAIXAS_DE_EXPERIENCIA). */
export const EXPERIENCIA: readonly { anos: number; texto: string }[] = [
  { anos: 0, texto: 'Menos de 1 ano' },
  { anos: 1, texto: '1 a 3 anos' },
  { anos: 3, texto: '3 a 5 anos' },
  { anos: 5, texto: '5 a 10 anos' },
  { anos: 10, texto: '10 a 20 anos' },
  { anos: 20, texto: 'Mais de 20 anos' },
];

export const FERRAMENTAS: Readonly<Record<Ferramentas, string>> = {
  sim: 'Levo as minhas ferramentas',
  parte: 'Levo parte das ferramentas',
  nao: 'O cliente fornece as ferramentas',
};

export const HORARIOS: readonly string[] = ['6h às 15h', '7h às 16h', '7h às 17h', '7h30 às 17h', '8h às 16h', '8h às 17h'];

export const HORARIO_PADRAO = '7h às 17h';

/** Até onde vai (o servidor aceita 5, 10, 20 ou 40 km). */
export const RAIOS: readonly { km: number; texto: string }[] = [
  { km: 5, texto: 'Só perto de casa' },
  { km: 10, texto: 'Minha cidade' },
  { km: 20, texto: 'Minha cidade e vizinhas' },
  { km: 40, texto: 'Vou longe se precisar' },
];

/** Dias na ordem da semana de trabalho (segunda primeiro); 0 = domingo, como na API. */
export const DIAS_DA_SEMANA: readonly { dia: number; curto: string; nome: string }[] = [
  { dia: 1, curto: 'seg', nome: 'Segunda-feira' },
  { dia: 2, curto: 'ter', nome: 'Terça-feira' },
  { dia: 3, curto: 'qua', nome: 'Quarta-feira' },
  { dia: 4, curto: 'qui', nome: 'Quinta-feira' },
  { dia: 5, curto: 'sex', nome: 'Sexta-feira' },
  { dia: 6, curto: 'sáb', nome: 'Sábado' },
  { dia: 0, curto: 'dom', nome: 'Domingo' },
];

/** Frases prontas do "Sobre você": a pessoa toca em vez de escrever (o servidor passa cada uma pela censura). */
export const FRASES_COMUNS: readonly string[] = [
  'Chego no horário combinado',
  'Deixo o local limpo no fim do dia',
  'Aviso antes o material que precisa comprar',
  'Tiro dúvidas pelo chat antes de começar',
  'Atendo também aos sábados',
  'Trabalho com um ajudante',
];

export const FRASES_POR_PROFISSAO: Readonly<Record<string, readonly string[]>> = {
  pedreiro: ['Faço obra do começo ao fim', 'Faço pequenos reparos também', 'Confiro o nível e o prumo em tudo'],
  pintor: ['Protejo móveis e piso com lona', 'Faço massa corrida e lixa antes de pintar', 'Ajudo a escolher a tinta'],
  eletricista: ['Tenho curso de NR-10', 'Sigo a norma de instalações elétricas', 'Deixo tudo testado antes de ir embora'],
  jardineiro: ['Levo o entulho verde embora', 'Tenho roçadeira e cortador próprios', 'Faço manutenção mensal'],
  diarista: ['Levo meus panos', 'O cliente fornece os produtos', 'Passo roupa também', 'Tenho cuidado com móveis e objetos'],
};

export const TIPOS_DE_PIX: readonly { tipo: TipoDeChavePix; texto: string }[] = [
  { tipo: 'cpf', texto: 'CPF' },
  { tipo: 'celular', texto: 'Celular' },
  { tipo: 'email', texto: 'E-mail' },
  { tipo: 'aleatoria', texto: 'Chave aleatória' },
];

/** Texto do "Sobre" como o perfil público mostra (BuscaPublicaService.textoDoSobre): frases com ponto, depois o texto. */
export function textoDoSobre(frases: readonly string[], texto: string): string {
  const comPonto = frases.map((frase) => frase.trim()).map((frase) => (/[.!?]$/.test(frase) ? frase : `${frase}.`));
  return [...comPonto, texto.trim()].filter(Boolean).join(' ');
}
