/* =========================================================================
   1. CONFIGURAÇÃO — regras de negócio que o admin pode alterar
   ========================================================================= */
const CONFIG = {
  COMISSAO: 0.10,             // 10% sobre cada diária
  TAXA_PAGA_POR: 'cliente',   // 'cliente' soma no total | 'profissional' desconta do repasse
  AUTO_LIBERA_HORAS: 12,      // sem resposta do cliente → libera
  PRAZO_DISPUTA_HORAS: 48,    // prazo da equipe para mediar
  LIMITE_DOMESTICO_SEMANA: 2, // LC 150/2015 — diarista com o mesmo cliente
  TENTATIVAS_ANTES_ANALISE: 3,
  HOJE: '2026-09-28',         // "hoje" fixo do protótipo
  SEMANAS_CAL: 4,
};

/* Cidades atendidas — começa pelo Vale do Itajaí, mas o sistema não é preso a uma cidade.
   No sistema real: cidade vem do CEP (ViaCEP) e a distância pela geolocalização. */
const CIDADES = ['Blumenau','Gaspar','Pomerode','Indaial','Timbó','Brusque','Itajaí','Balneário Camboriú','Jaraguá do Sul'];
const RAIOS = [[5,'Só perto de casa','até 5 km'],[10,'Minha cidade','até 10 km'],[20,'Minha cidade e vizinhas','até 20 km'],[40,'Vou longe se precisar','até 40 km']];

/* Categorias: ÁREA → PROFISSÃO → SERVIÇOS.  lc150: sujeita ao limite semanal da LC 150/2015 */
const AREAS = [
  {id:'obra', nome:'Obra e reforma', profissoes:[
    {id:'pedreiro', nome:'Pedreiro', plural:'Pedreiros', ic:'brick', ativo:true, faixa:[250,330],
     servicos:['Alvenaria','Reboco','Contrapiso','Assentamento de piso','Muro e calçada','Telhado e calhas','Conserto de trincas','Pequenos reparos']},
    {id:'pintor', nome:'Pintor', plural:'Pintores', ic:'roller', ativo:true, faixa:[220,300],
     servicos:['Pintura interna','Pintura externa','Massa corrida','Textura e grafiato','Portas e janelas','Pintura de muro']},
    {id:'eletricista', nome:'Eletricista', plural:'Eletricistas', ic:'bolt', ativo:true, faixa:[280,360], nr10:true,
     servicos:['Tomadas e interruptores','Chuveiro e resistência','Quadro de luz e disjuntor','Luminárias e ventilador','Fiação nova','Padrão de entrada']},
    {id:'encanador', nome:'Encanador', plural:'Encanadores', ic:'drop', ativo:false, faixa:[250,320], servicos:['Vazamentos','Desentupimento','Instalação de louças']},
  ]},
  {id:'casa', nome:'Casa e jardim', profissoes:[
    {id:'diarista', nome:'Diarista', plural:'Diaristas', ic:'broom', ativo:true, lc150:true, faixa:[160,220],
     servicos:['Limpeza completa','Limpeza pesada','Pós-obra','Passar roupa','Organização','Vidros e janelas']},
    {id:'jardineiro', nome:'Jardineiro', plural:'Jardineiros', ic:'leaf', ativo:true, faixa:[170,240],
     servicos:['Corte de grama','Poda de árvores e cercas','Limpeza de terreno','Plantio','Manutenção mensal','Recolher entulho verde']},
    {id:'montador', nome:'Montador de móveis', plural:'Montadores', ic:'tools', ativo:false, faixa:[180,260], servicos:['Montagem','Desmontagem','Instalação de prateleiras']},
  ]},
];

const EXPERIENCIA = [[0,'Menos de 1 ano'],[1,'1 a 3 anos'],[3,'3 a 5 anos'],[5,'5 a 10 anos'],[10,'10 a 20 anos'],[20,'Mais de 20 anos']];
const FERRAMENTAS = {sim:'Levo as minhas ferramentas', parte:'Levo parte das ferramentas', nao:'O cliente fornece as ferramentas'};
const HORARIOS = ['6h às 15h','7h às 16h','7h às 17h','7h30 às 17h','8h às 16h','8h às 17h'];

/* Frases prontas para o "Sobre você": o profissional toca em vez de escrever */
const FRASES = {
  comum:['Chego no horário combinado','Deixo o local limpo no fim do dia','Aviso antes o material que precisa comprar','Tiro dúvidas pelo chat antes de começar','Atendo também aos sábados','Trabalho com um ajudante'],
  pedreiro:['Faço obra do começo ao fim','Faço pequenos reparos também','Confiro o nível e o prumo em tudo'],
  pintor:['Protejo móveis e piso com lona','Faço massa corrida e lixa antes de pintar','Ajudo a escolher a tinta'],
  eletricista:['Tenho curso de NR-10','Sigo a norma de instalações elétricas','Deixo tudo testado antes de ir embora'],
  jardineiro:['Levo o entulho verde embora','Tenho roçadeira e cortador próprios','Faço manutenção mensal'],
  diarista:['Levo meus panos','O cliente fornece os produtos','Passo roupa também','Tenho cuidado com móveis e objetos'],
};
