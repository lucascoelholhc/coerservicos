/* =========================================================================
   2. DADOS DE EXEMPLO — todos fictícios. No sistema real, vêm da API.
   galeria: [legenda, serviço]
   ========================================================================= */
const PROS = [
  {id:'p1',nome:'Valdir Schmitt',prof:'pedreiro',outras:[],servicos:['Alvenaria','Reboco','Contrapiso','Assentamento de piso','Muro e calçada'],anos:22,ferramentas:'sim',valor:280,meia:160,
   cidade:'Blumenau',raio:20,cidades:['Blumenau','Gaspar','Pomerode','Indaial'],disp:[1,2,3,4,5,6],horario:'7h às 17h',mei:true,fone:'(47) 99812-4471',cor:'#2B5C8A',ocupados:['2026-10-02','2026-10-08','2026-10-09'],desde:'set/2026',status:'ativo',
   bio:'Trabalho com construção há 22 anos. Faço obra do começo ao fim e pequenos reparos também. Levo minhas ferramentas e deixo o local limpo no fim do dia.',
   galeria:[['Muro de arrimo no Garcia','Muro e calçada'],['Contrapiso nivelado','Contrapiso'],['Reboco da fachada','Reboco'],['Piso da cozinha','Assentamento de piso'],['Calçada nova','Muro e calçada'],['Churrasqueira de tijolo','Alvenaria']],
   hist:{nota:4.9,nAval:38,diarias:142}},
  {id:'p3',nome:'Anderson Pereira',prof:'pedreiro',outras:['pintor'],servicos:['Reboco','Conserto de trincas','Muro e calçada','Pequenos reparos','Pintura de muro'],anos:6,ferramentas:'parte',valor:260,meia:null,
   cidade:'Blumenau',raio:10,cidades:['Blumenau','Gaspar'],disp:[1,2,3,4,5,6],horario:'7h às 16h',mei:false,fone:'(47) 98820-3316',cor:'#2F7A6B',ocupados:['2026-10-01'],desde:'set/2026',status:'ativo',
   bio:'Faço reboco, conserto de trincas e muros. Pinto o muro depois de pronto. Atendo a região sul da cidade.',
   galeria:[['Muro lateral','Muro e calçada'],['Trinca consertada','Conserto de trincas'],['Reboco interno','Reboco'],['Muro pintado','Pintura de muro'],['Pilar recuperado','Pequenos reparos'],['Fachada','Reboco']],
   hist:{nota:4.5,nAval:17,diarias:54}},
  {id:'p4',nome:'Jair Hoffmann',prof:'pedreiro',outras:[],servicos:['Telhado e calhas','Alvenaria','Muro e calçada','Pequenos reparos'],anos:18,ferramentas:'sim',valor:320,meia:180,
   cidade:'Blumenau',raio:40,cidades:['Blumenau','Gaspar','Pomerode','Indaial','Brusque'],disp:[1,2,3,4,5],horario:'7h às 17h',mei:true,fone:'(47) 99670-1188',cor:'#8A4B2F',ocupados:['2026-09-30','2026-10-13'],desde:'set/2026',status:'ativo',
   bio:'Telhados, calhas e alvenaria. Tenho equipamento de segurança para trabalho em altura.',
   galeria:[['Parede de bloco','Alvenaria'],['Troca de telhas','Telhado e calhas'],['Muro com portão','Muro e calçada'],['Calha nova','Telhado e calhas'],['Beiral','Pequenos reparos'],['Parede da garagem','Alvenaria']],
   hist:{nota:4.7,nAval:31,diarias:118}},
  {id:'p11',nome:'Marcos Lenzi',prof:'pintor',outras:[],servicos:['Pintura interna','Pintura externa','Massa corrida','Textura e grafiato','Portas e janelas'],anos:15,ferramentas:'sim',valor:250,meia:140,
   cidade:'Blumenau',raio:20,cidades:['Blumenau','Gaspar','Pomerode'],disp:[1,2,3,4,5,6],horario:'7h30 às 17h',mei:true,fone:'(47) 99401-2290',cor:'#7A3E5C',ocupados:['2026-10-05'],desde:'set/2026',status:'ativo',
   bio:'Pintor há 15 anos. Protejo móveis e piso com lona, faço massa corrida e lixa antes de pintar. Ajudo a escolher a tinta.',
   galeria:[['Sala em verde-oliva','Pintura interna'],['Fachada de sobrado','Pintura externa'],['Quarto com massa corrida','Massa corrida'],['Parede com grafiato','Textura e grafiato'],['Portas envernizadas','Portas e janelas'],['Cozinha clara','Pintura interna']],
   hist:{nota:4.8,nAval:27,diarias:101}},
  {id:'p12',nome:'Fábio Rosa',prof:'pintor',outras:[],servicos:['Pintura interna','Pintura de muro','Massa corrida'],anos:5,ferramentas:'parte',valor:230,meia:130,
   cidade:'Gaspar',raio:20,cidades:['Gaspar','Blumenau'],disp:[1,2,3,4,5],horario:'8h às 17h',mei:false,fone:'(47) 98877-1450',cor:'#3D5A99',ocupados:[],desde:'set/2026',status:'ativo',
   bio:'Pinto casas e apartamentos na região norte. Trabalho caprichado, com fita e lona.',
   galeria:[['Apartamento inteiro','Pintura interna'],['Muro da frente','Pintura de muro'],['Teto do banheiro','Pintura interna'],['Parede lixada','Massa corrida'],['Sala e corredor','Pintura interna'],['Muro lateral','Pintura de muro']],
   hist:{nota:4.6,nAval:11,diarias:40}},
  {id:'p13',nome:'Cristiano Wagner',prof:'eletricista',outras:[],servicos:['Tomadas e interruptores','Chuveiro e resistência','Quadro de luz e disjuntor','Luminárias e ventilador'],anos:12,ferramentas:'sim',valor:300,meia:170,nr10:true,
   cidade:'Blumenau',raio:40,cidades:['Blumenau','Indaial','Timbó','Pomerode'],disp:[1,2,3,4,5,6],horario:'8h às 17h',mei:true,fone:'(47) 99533-8012',cor:'#4B5F2A',ocupados:['2026-10-01'],desde:'set/2026',status:'ativo',
   bio:'Eletricista com curso de NR-10. Troca de quadro, disjuntores, tomadas e chuveiro. Deixo tudo testado antes de ir embora.',
   galeria:[['Quadro de luz novo','Quadro de luz e disjuntor'],['Tomadas da cozinha','Tomadas e interruptores'],['Ventilador de teto','Luminárias e ventilador'],['Chuveiro trocado','Chuveiro e resistência'],['Luminárias da sala','Luminárias e ventilador'],['Disjuntores identificados','Quadro de luz e disjuntor']],
   hist:{nota:4.9,nAval:21,diarias:77}},
  {id:'p14',nome:'Edson Tomio',prof:'eletricista',outras:[],servicos:['Fiação nova','Padrão de entrada','Quadro de luz e disjuntor','Tomadas e interruptores'],anos:25,ferramentas:'sim',valor:340,meia:190,nr10:true,
   cidade:'Blumenau',raio:20,cidades:['Blumenau','Gaspar'],disp:[1,2,3,4,5],horario:'7h às 16h',mei:true,fone:'(47) 99720-6634',cor:'#5B4A8A',ocupados:['2026-09-30'],desde:'set/2026',status:'ativo',
   bio:'25 anos de profissão. Faço fiação completa de casa nova, padrão de entrada e reforma de instalação antiga.',
   galeria:[['Fiação de casa nova','Fiação nova'],['Padrão de entrada','Padrão de entrada'],['Quadro organizado','Quadro de luz e disjuntor'],['Tomadas novas','Tomadas e interruptores'],['Eletroduto aparente','Fiação nova'],['Troca de disjuntores','Quadro de luz e disjuntor']],
   hist:{nota:5.0,nAval:34,diarias:160}},
  {id:'p15',nome:'Osni Beduschi',prof:'jardineiro',outras:[],servicos:['Corte de grama','Poda de árvores e cercas','Limpeza de terreno','Recolher entulho verde'],anos:30,ferramentas:'sim',valor:200,meia:110,
   cidade:'Blumenau',raio:20,cidades:['Blumenau','Gaspar'],disp:[1,2,3,4,5,6],horario:'7h às 16h',mei:false,fone:'(47) 99102-7788',cor:'#2E6B3A',ocupados:['2026-10-02'],desde:'set/2026',status:'ativo',
   bio:'Cuido de jardins há 30 anos. Corte de grama, poda e limpeza de terreno. Levo o entulho verde embora.',
   galeria:[['Gramado cortado','Corte de grama'],['Cerca viva podada','Poda de árvores e cercas'],['Terreno limpo','Limpeza de terreno'],['Jardim da frente','Corte de grama'],['Árvore podada','Poda de árvores e cercas'],['Entulho recolhido','Recolher entulho verde']],
   hist:{nota:4.8,nAval:25,diarias:130}},
  {id:'p16',nome:'Gilmar Theiss',prof:'jardineiro',outras:[],servicos:['Corte de grama','Plantio','Manutenção mensal'],anos:4,ferramentas:'parte',valor:180,meia:100,
   cidade:'Pomerode',raio:20,cidades:['Pomerode','Blumenau'],disp:[2,4,6],horario:'7h às 16h',mei:false,fone:'(47) 98260-4431',cor:'#6B7A2E',ocupados:[],desde:'set/2026',status:'ativo',
   bio:'Faço corte de grama, plantio e manutenção mensal de jardim. Atendo terças, quintas e sábados.',
   galeria:[['Canteiro plantado','Plantio'],['Grama aparada','Corte de grama'],['Jardim mantido','Manutenção mensal'],['Mudas novas','Plantio'],['Quintal','Corte de grama'],['Canteiro de flores','Plantio']],
   hist:{nota:4.5,nAval:8,diarias:29}},
  {id:'p5',nome:'Rosângela Werner',prof:'diarista',outras:[],servicos:['Limpeza completa','Passar roupa','Pós-obra','Organização'],anos:15,ferramentas:'parte',valor:190,meia:110,
   cidade:'Blumenau',raio:10,cidades:['Blumenau'],disp:[1,2,3,4,5],horario:'8h às 17h',mei:true,fone:'(47) 99231-5570',cor:'#B0476A',ocupados:['2026-09-30','2026-10-07','2026-10-14'],desde:'set/2026',status:'ativo',
   bio:'Diarista há 15 anos. Limpeza caprichada, passo roupa e faço pós-obra. Levo meus panos; o cliente fornece os produtos.',
   galeria:[['Cozinha brilhando','Limpeza completa'],['Banheiro','Limpeza completa'],['Pós-obra','Pós-obra'],['Armários organizados','Organização'],['Sala','Limpeza completa'],['Roupa passada','Passar roupa']],
   hist:{nota:5.0,nAval:44,diarias:236}},
  {id:'p6',nome:'Márcia dos Santos',prof:'diarista',outras:[],servicos:['Limpeza completa','Organização'],anos:4,ferramentas:'nao',valor:170,meia:null,
   cidade:'Gaspar',raio:20,cidades:['Gaspar','Blumenau'],disp:[2,4,6],horario:'8h às 16h',mei:false,fone:'(47) 98455-0921',cor:'#3E6FA8',ocupados:[],desde:'set/2026',status:'ativo',
   bio:'Limpeza completa e organização de armários. Atendo terças, quintas e sábados.',
   galeria:[['Armários organizados','Organização'],['Cozinha','Limpeza completa'],['Sala','Limpeza completa'],['Banheiro','Limpeza completa'],['Lavanderia','Organização'],['Quarto','Limpeza completa']],
   hist:{nota:4.4,nAval:12,diarias:61}},
  {id:'p7',nome:'Ivone Kreutzfeld',prof:'diarista',outras:[],servicos:['Limpeza pesada','Pós-obra','Vidros e janelas'],anos:11,ferramentas:'sim',valor:210,meia:120,
   cidade:'Blumenau',raio:20,cidades:['Blumenau','Pomerode'],disp:[1,2,3,4,5,6],horario:'8h às 17h',mei:true,fone:'(47) 99702-4413',cor:'#7A5C2E',ocupados:['2026-10-03'],desde:'set/2026',status:'ativo',
   bio:'Limpeza pesada, pós-obra e vidros de sacada. Experiência com apartamentos e salas comerciais.',
   galeria:[['Pós-obra','Pós-obra'],['Vidros da sacada','Vidros e janelas'],['Escritório','Limpeza pesada'],['Janelas','Vidros e janelas'],['Cozinha','Limpeza pesada'],['Box do banheiro','Limpeza pesada']],
   hist:{nota:4.8,nAval:29,diarias:173}},
];

const REV = {
  obra:[
    ['Carla B.','Garcia',5,'Chegou no horário e deixou tudo limpo no fim do dia. Serviço bem feito.','21/09/2026'],
    ['Osmar K.','Velha',5,'Explicou o que precisava comprar antes de começar.','12/09/2026'],
    ['Patrícia L.','Centro',4,'Bom trabalho. Atrasou meia hora no segundo dia, mas avisou pelo chat.','30/08/2026'],
  ],
  casa:[
    ['Juliana M.','Garcia',5,'Impecável. Organizou até a despensa sem eu pedir.','22/09/2026'],
    ['Renata S.','Velha',5,'Pontual e muito cuidadosa.','15/09/2026'],
    ['Marcos T.','Centro',4,'Muito bom. Faltou um detalhe, mas resolveu na semana seguinte.','01/09/2026'],
  ],
};

const CONTRATOS_INICIAIS = [
  {id:'c1',cliente:'Juliana Martins',local:'Garcia, Blumenau',endereco:'Rua Amazonas, 1200 · Garcia · Blumenau',proId:'p3',servico:'Banheiro: reboco e contrapiso',modo:'avulsa',valor:260,criado:'20/09/2026',
   dias:[{data:'2026-09-24',status:'liberada',em:'24/09 às 18:40'},{data:'2026-09-25',status:'liberada',em:'26/09 às 06:05 (automática)'},
         {data:'2026-09-28',status:'aguardando',chegada:'07:34',concluidoEm:'28/09 às 17:10',liberaEm:'29/09 às 05:10',foto:true,obs:'Contrapiso nivelado. Não pisar por 48 horas.'},
         {data:'2026-09-29',status:'paga'},{data:'2026-09-30',status:'paga'}],
   chat:[{de:'pro',txt:'Boa tarde, Juliana! Confirmo quarta às 7h30. Precisa comprar 6 sacos de argamassa.',h:'22/09 17:02'},
         {de:'cli',txt:'Combinado, já pedi na loja. Deixo o portão aberto.',h:'22/09 17:15'},
         {de:'pro',txt:'Terminei o contrapiso hoje. Mandei a foto pelo app.',h:'28/09 17:11'}]},
  {id:'c2',cliente:'Juliana Martins',local:'Garcia, Blumenau',endereco:'Rua Amazonas, 1200 · Garcia · Blumenau',proId:'p5',servico:'Limpeza completa: casa de 3 quartos',modo:'quinzenal',valor:190,criado:'10/09/2026',
   dias:[{data:'2026-09-14',status:'liberada',em:'14/09 às 17:30'},{data:'2026-09-28',status:'andamento',chegada:'07:52'},{data:'2026-10-12',status:'agendada'}],
   chat:[{de:'sis',txt:'Plano quinzenal ativo. Se a profissional faltar, a COE envia outra no mesmo dia ou devolve o valor.'},{de:'pro',txt:'Bom dia! Já estou a caminho.',h:'28/09 07:48'}]},
  {id:'c3',cliente:'Juliana Martins',local:'Garcia, Blumenau',endereco:'Rua Amazonas, 1200 · Garcia · Blumenau',proId:'p4',servico:'Muro lateral: alvenaria e reboco',modo:'avulsa',valor:320,criado:'10/09/2026',
   dias:[{data:'2026-09-15',status:'liberada',em:'15/09 às 19:10'},{data:'2026-09-16',status:'liberada',em:'16/09 às 18:02'},
         {data:'2026-09-17',status:'contestada',contest:{motivo:'Serviço incompleto',desc:'O reboco ficou com trincas e a última parte do muro não foi feita.',aberta:'17/09 às 19:02',fotos:2,resposta:'Choveu à tarde e o reboco não secou. Me ofereci para voltar e terminar sem custo.'}}],
   chat:[{de:'pro',txt:'Choveu forte depois das 14h, vou precisar voltar.',h:'17/09 15:20'}]},
  {id:'c4',cliente:'Carlos Reinert',local:'Itoupava Norte, Blumenau',endereco:'Rua Dois de Setembro, 3150 · Itoupava Norte · Blumenau',proId:'p1',servico:'Garagem: contrapiso e piso (32 m²)',modo:'avulsa',valor:280,criado:'24/09/2026',
   pedido:{servicos:['Contrapiso','Assentamento de piso'],desc:'Garagem de 32 m². O porcelanato 60×60 já está comprado. O chão tem um desnível perto do portão.',material:'tenho',fotos:[],ilus:['Contrapiso','Assentamento de piso']},
   dias:[{data:'2026-09-28',status:'andamento'},{data:'2026-09-29',status:'paga'},{data:'2026-10-01',status:'paga'}],
   chat:[{de:'cli',txt:'Valdir, o piso chega às 8h. O portão da garagem fica aberto.',h:'27/09 20:10'},{de:'pro',txt:'Beleza, chego 7h30 e já adianto o contrapiso.',h:'27/09 20:14'}]},
  {id:'c5',cliente:'Patrícia Lenz',local:'Centro, Gaspar',endereco:'Rua Hermann Huscher, 88 · Centro · Gaspar',proId:'p6',servico:'Limpeza completa: apartamento',modo:'avulsa',valor:170,criado:'15/09/2026',
   dias:[{data:'2026-09-25',status:'contestada',contest:{motivo:'Profissional não compareceu',desc:'Esperei até 10h e ela não apareceu nem respondeu o chat.',aberta:'25/09 às 10:12',fotos:0,resposta:'Tive um problema na família e fiquei sem bateria. Peço desculpas.'}}],chat:[]},
  {id:'c6',cliente:'Eduardo Voigt',local:'Velha, Blumenau',endereco:'Rua Pomerode, 410 · Velha · Blumenau',proId:'p1',servico:'Churrasqueira de tijolo',modo:'avulsa',valor:280,criado:'15/09/2026',
   dias:[{data:'2026-09-22',status:'liberada',em:'22/09 às 18:30'},{data:'2026-09-23',status:'liberada',em:'24/09 às 06:20 (automática)'},{data:'2026-09-24',status:'liberada',em:'24/09 às 19:05'}],
   chat:[{de:'cli',txt:'Ficou muito boa, obrigado!',h:'24/09 19:06'}]},
  {id:'c7',cliente:'Mariana Hess',local:'Victor Konder, Blumenau',endereco:'Rua Alberto Stein, 77 · Victor Konder · Blumenau',proId:'p13',servico:'Troca do quadro de luz',modo:'avulsa',valor:300,criado:'22/09/2026',
   dias:[{data:'2026-09-26',status:'liberada',em:'26/09 às 17:45'}],chat:[]},
  {id:'c8',cliente:'Roberto Klug',local:'Glória, Blumenau',endereco:'Rua Progresso, 902 · Glória · Blumenau',proId:'p15',servico:'Limpeza de terreno (400 m²)',modo:'avulsa',valor:200,criado:'21/09/2026',
   dias:[{data:'2026-09-25',status:'liberada',em:'25/09 às 16:50'},{data:'2026-10-02',status:'paga'}],chat:[]},
];

/* Conversas ANTES da contratação — contato censurado */
const CONVERSAS_INICIAIS = [
  {id:'m1',cliente:'Juliana Martins',proId:'p11',msgs:[
    {de:'cli',txt:'Oi Marcos! Quanto tempo leva para pintar uma sala de 20 m² com massa corrida?',h:'27/09 10:02'},
    {de:'pro',txt:'Uns 2 dias: um de massa e lixa, outro de tinta. Quinta e sexta estou livre.',h:'27/09 10:31'}]},
  {id:'m2',cliente:'Sandra Kienen',proId:'p1',msgs:[
    {de:'cli',txt:'Boa tarde! Quanto tempo leva um contrapiso de 18 m²?',h:'28/09 14:10'},
    {de:'pro',txt:'Uma diária, se o chão já estiver limpo.',h:'28/09 14:18'},
    {de:'sis',txt:'Uma mensagem de Sandra não foi entregue porque tinha número de telefone.',h:'28/09 14:22'}]},
];

const MODERACAO_INICIAL = [
  {id:'mo1',quando:'28/09 às 14:22',autor:'Sandra Kienen',tipo:'Cliente',onde:'Chat com Valdir Schmitt (antes da contratação)',trecho:'me passa teu zap que é mais fácil, o meu é 47 9 9812-3344',motivos:['telefone','WhatsApp'],status:'bloqueada'},
  {id:'mo2',quando:'27/09 às 19:05',autor:'Márcia dos Santos',tipo:'Profissional',onde:'Texto "Sobre você" do perfil',trecho:'Também atendo pelo insta @marcia.limpeza',motivos:['rede social'],status:'bloqueada'},
  {id:'mo3',quando:'26/09 às 09:40',autor:'Anderson Pereira',tipo:'Profissional',onde:'Legenda de foto',trecho:'orçamento pelo anderson.reformas@gmail.com',motivos:['e-mail'],status:'bloqueada'},
  {id:'mo4',quando:'26/09 às 08:55',autor:'Anderson Pereira',tipo:'Profissional',onde:'Chat com Juliana Martins (antes da contratação)',trecho:'se quiser me liga, nove oito oito dois zero três três um seis',motivos:['telefone por extenso'],status:'bloqueada'},
];

const CLIENTES_INICIAIS = [
  {nome:'Juliana Martins',cidade:'Blumenau',desde:'09/2026',status:'ativo'},
  {nome:'Carlos Reinert',cidade:'Blumenau',desde:'09/2026',status:'ativo'},
  {nome:'Patrícia Lenz',cidade:'Gaspar',desde:'09/2026',status:'ativo'},
  {nome:'Eduardo Voigt',cidade:'Blumenau',desde:'09/2026',status:'ativo'},
  {nome:'Mariana Hess',cidade:'Blumenau',desde:'09/2026',status:'ativo'},
  {nome:'Roberto Klug',cidade:'Blumenau',desde:'09/2026',status:'ativo'},
  {nome:'Sandra Kienen',cidade:'Pomerode',desde:'09/2026',status:'ativo'},
];

const VERIFICACOES_INICIAIS = [
  {id:'v1',nome:'Cleusa Rodrigues',prof:'diarista',outras:[],anos:9,enviado:'27/09 às 21:14',cel:'(47) 9••••-3390',cpf:'•••.482.•••-10',cidade:'Blumenau',raio:20,cidades:['Blumenau','Gaspar'],valor:175,servicos:['Limpeza completa','Organização'],ferramentas:'nao',fotos:4,
   bio:'Faço limpeza completa e organização. Chego no horário combinado.',checks:{cpf:true,doc:true,selfie:null,fotos:null,bio:true}},
  {id:'v2',nome:'Ricardo Deschamps',prof:'pedreiro',outras:[],anos:12,enviado:'28/09 às 08:40',cel:'(47) 9••••-1172',cpf:'•••.116.•••-43',cidade:'Blumenau',raio:20,cidades:['Blumenau','Indaial'],valor:290,servicos:['Alvenaria','Reboco','Contrapiso'],ferramentas:'sim',fotos:6,
   bio:'Pedreiro há 12 anos, trabalho com alvenaria e reboco. Tenho betoneira própria.',checks:{cpf:true,doc:true,selfie:true,fotos:true,bio:true}},
  {id:'v3',nome:'Nelson Kraus',prof:'eletricista',outras:[],anos:8,enviado:'28/09 às 11:02',cel:'(47) 9••••-8814',cpf:'•••.903.•••-27',cidade:'Brusque',raio:20,cidades:['Brusque','Gaspar'],valor:290,servicos:['Tomadas e interruptores','Chuveiro e resistência'],ferramentas:'sim',fotos:2,nr10:'enviado',
   bio:'Eletricista residencial. Chama no zap 47 99••• pra combinar.',checks:{cpf:true,doc:null,selfie:null,fotos:null,bio:false,nr10:null}},
];
