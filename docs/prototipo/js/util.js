/* =========================================================================
   3. ESTADO DA SESSÃO — no front real vira store (Zustand/Redux/Context)
   ========================================================================= */
const FILTROS_PADRAO = () => ({prof:'', servicos:[], cidade:'', dia:'', precoMax:400, expMin:0, ferramentas:false, meia:false, ordem:'recomendados'});
const S = {
  role:'visitante',
  cenario:'lancamento',               // 'lancamento' = sem avaliações | 'historico'
  cliente:{nome:'Juliana Martins', exibir:'Juliana', cidade:'Blumenau', enderecos:[{id:'casa', nome:'Casa', linha:'Rua Amazonas, 1200 · Garcia', cidade:'Blumenau', cep:'89030-000'}]},
  proLogadoId:'p1',
  filtros:FILTROS_PADRAO(),
  busca:{texto:''},
  ct:null, paneContrato:'diarias',
  authTab:'entrar', redirect:null,
  cad:{etapa:1, dados:{outras:[], servicos:[], cidade:'', raio:20, cidades:[], dias:[1,2,3,4,5,6], ferramentas:'', frases:[], valor:250, horario:'7h às 17h', fotosInfo:[]}, err:{}},
  fim:{}, // estado da tela "Terminei o dia"
  perfilEdit:null, proTab:'dados',
  upl:{}, err:{}, avisoChat:{}, strikes:{},
  contratos:JSON.parse(JSON.stringify(CONTRATOS_INICIAIS)),
  conversas:JSON.parse(JSON.stringify(CONVERSAS_INICIAIS)),
  moderacao:JSON.parse(JSON.stringify(MODERACAO_INICIAL)),
  clientes:JSON.parse(JSON.stringify(CLIENTES_INICIAIS)),
  verificacoes:JSON.parse(JSON.stringify(VERIFICACOES_INICIAIS)),
  admBusca:'', admStatus:'todos', novaProf:{},
};

/* =========================================================================
   4. UTILITÁRIOS
   ========================================================================= */
const $ = s => document.querySelector(s);
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const pad = n => String(n).padStart(2,'0');
const brl = v => Number(v).toLocaleString('pt-BR',{style:'currency',currency:'BRL'});
const nota = n => n.toFixed(1).replace('.',',');
const parseD = s => { const [y,m,d] = s.split('-').map(Number); return new Date(y,m-1,d); };
const iso = d => `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())}`;
const addDays = (s,n) => { const d = parseD(s); d.setDate(d.getDate()+n); return iso(d); };
const DOW = ['dom','seg','ter','qua','qui','sex','sáb'];
const DOWL = ['domingo','segunda-feira','terça-feira','quarta-feira','quinta-feira','sexta-feira','sábado'];
const MES = ['janeiro','fevereiro','março','abril','maio','junho','julho','agosto','setembro','outubro','novembro','dezembro'];
const dm = s => { const d = parseD(s); return `${pad(d.getDate())}/${pad(d.getMonth()+1)}`; };
const fmtDia = s => `${DOW[parseD(s).getDay()]}, ${dm(s)}`;
const fmtDiaLongo = s => { const d = parseD(s); return `${DOWL[d.getDay()]}, ${d.getDate()} de ${MES[d.getMonth()]}`; };
const weekKey = s => { const d = parseD(s); d.setDate(d.getDate()-((d.getDay()+6)%7)); return iso(d); };
const primeiro = n => String(n).split(' ')[0];
const iniciais = n => String(n).split(' ').map(x => x[0]).slice(0,2).join('');
const skey = k => k.replace(/[^a-z0-9]/gi,'_');
const temHist = () => S.cenario === 'historico';

const pro = id => PROS.find(p => p.id === id);
const todasProfs = () => AREAS.flatMap(a => a.profissoes.map(p => ({...p, area:a.id, areaNome:a.nome})));
const profissao = id => todasProfs().find(p => p.id === id) || {nome:id, plural:id, servicos:[], area:'obra', faixa:[150,350]};
const areaDe = profId => profissao(profId).area;
const ehDomestico = profId => !!profissao(profId).lc150;
const anosTxt = n => n>=1 ? `${n} ${n===1?'ano':'anos'} de experiência` : 'Menos de 1 ano de experiência';

function calc(valor, n=1){
  const base = valor*n, taxa = Math.round(base*CONFIG.COMISSAO*100)/100;
  return CONFIG.TAXA_PAGA_POR === 'cliente' ? {base, taxa, total:base+taxa, recebe:base} : {base, taxa, total:base, recebe:base-taxa};
}
function stamp(addH=0){
  const n = new Date(), d = parseD(CONFIG.HOJE);
  d.setHours(n.getHours()+addH, n.getMinutes());
  return `${pad(d.getDate())}/${pad(d.getMonth()+1)} às ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}
const horaCurta = () => stamp().replace(' às',' ');
function sampleImg(label, cor){
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="320" height="240"><rect width="320" height="240" fill="${cor}"/><text x="160" y="128" font-family="Arial" font-size="20" fill="#fff" text-anchor="middle">${label}</text></svg>`;
  return 'data:image/svg+xml;utf8,' + encodeURIComponent(svg);
}

/* ---------- Censura de contato ----------
   Bloqueia telefone (com ou sem separadores, ou por extenso), e-mail, links,
   @perfis e palavras que pedem contato fora do app. No backend real a mesma
   regra roda no servidor (o front só dá o aviso). */
const NUM_PALAVRA = '(zero|um|uma|dois|duas|tr[eê]s|quatro|cinco|seis|sete|oito|nove|meia)';
const REGRAS_CONTATO = [
  ['telefone', /(?:\d[\s().\-]*){8,}/],
  ['telefone por extenso', new RegExp(`\\b${NUM_PALAVRA}\\b(?:[\\s,.\\-]+\\b${NUM_PALAVRA}\\b){5,}`,'i')],
  ['e-mail', /[\w.+\-]+@[\w\-]+\.[a-z]{2,}|\b\w+\s*[(\[]?arroba[)\]]?\s*\w+/i],
  ['link', /(https?:\/\/|www\.)\S+|\b[\w\-]+\.(com|com\.br|net|app|me|link|site)\b/i],
  ['rede social', /(^|\s)@[\w.]{3,}/],
  ['WhatsApp', /\b(whats|whatsapp|wpp|zap|zapzap|watts)\b/i],
  ['rede social', /\b(insta|instagram|facebook|face|telegram|tiktok)\b/i],
  ['pedido de contato', /\b(me liga|liga pra mim|meu n[uú]mero|meu contato|meu cel|chama no|me chama)\b/i],
];
function censura(txt){
  const motivos = [];
  REGRAS_CONTATO.forEach(([nome, re]) => { if (re.test(txt) && !motivos.includes(nome)) motivos.push(nome); });
  let marcado = esc(txt);
  REGRAS_CONTATO.forEach(([, re]) => { marcado = marcado.replace(new RegExp(re.source, re.flags.includes('g')?re.flags:re.flags+'g'), m => `<mark class="censura">${m}</mark>`); });
  return {tem: motivos.length>0, motivos, marcado};
}
function registraBloqueio(autor, tipo, onde, trecho, motivos){
  S.moderacao.unshift({id:'mo'+Date.now(), quando:stamp(), autor, tipo, onde, trecho, motivos, status:'bloqueada'});
  S.strikes[autor] = (S.strikes[autor]||0) + 1;
  return S.strikes[autor];
}
const tentativas = autor => S.moderacao.filter(m => m.autor===autor).length;

/* ---------- Ícones (traço 2px, 24×24) ---------- */
const P = {
  home:'<path d="M3 11l9-8 9 8"/><path d="M5 10v10h14V10"/>',
  search:'<circle cx="11" cy="11" r="7"/><path d="M21 21l-4.3-4.3"/>',
  cal:'<rect x="3" y="5" width="18" height="16" rx="2"/><path d="M3 10h18M8 3v4M16 3v4"/>',
  wallet:'<rect x="3" y="6" width="18" height="14" rx="2"/><path d="M3 10h18"/><circle cx="16.5" cy="15" r="1.2"/>',
  user:'<circle cx="12" cy="8" r="4"/><path d="M4 21c1.5-4 4.5-6 8-6s6.5 2 8 6"/>',
  users:'<circle cx="9" cy="8" r="3.5"/><path d="M2.5 20c1-3.5 3.5-5.5 6.5-5.5s5.5 2 6.5 5.5"/><path d="M16 4.5a3.5 3.5 0 010 7M18 14.8c1.8.8 3 2.6 3.5 5.2"/>',
  shield:'<path d="M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6z"/><path d="M9 12l2 2 4-4"/>',
  check:'<path d="M5 12l5 5 9-10"/>',
  x:'<path d="M6 6l12 12M18 6L6 18"/>',
  star:'<path d="M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.8l6.1-.9z" fill="currentColor"/>',
  chat:'<path d="M4 5h16v11H9l-5 4z"/>',
  camera:'<path d="M4 8h3l2-3h6l2 3h3v11H4z"/><circle cx="12" cy="13" r="3.5"/>',
  clock:'<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
  pin:'<path d="M12 21s-7-6.5-7-12a7 7 0 0114 0c0 5.5-7 12-7 12z"/><circle cx="12" cy="9" r="2.5"/>',
  back:'<path d="M15 5l-7 7 7 7"/>',
  next:'<path d="M9 5l7 7-7 7"/>',
  brick:'<rect x="3" y="5" width="18" height="14" rx="1"/><path d="M3 9.7h18M3 14.3h18M9 5v4.7M15 5v4.7M6 9.7v4.6M12 9.7v4.6M18 9.7v4.6M9 14.3V19M15 14.3V19"/>',
  grid:'<rect x="3" y="3" width="18" height="18" rx="1"/><path d="M3 9h18M3 15h18M9 3v18M15 3v18"/>',
  bucket:'<path d="M5 8h14l-1.5 12h-11z"/><path d="M8 8a4 4 0 018 0"/><path d="M9 12h6"/>',
  broom:'<path d="M15 3l-4 9"/><path d="M6 12h9l2 9H4z"/><path d="M8.5 16v5M12.5 16v5"/>',
  shirt:'<path d="M8 3l4 3 4-3 5 4-3 3-2-1v12H8V9l-2 1-3-3z"/>',
  roller:'<rect x="4" y="3" width="14" height="6" rx="1"/><path d="M18 6h2v5h-8v3"/><rect x="10.5" y="14" width="3" height="7" rx="1"/>',
  bolt:'<path d="M13 2L4 14h7l-1 8 9-12h-7z"/>',
  leaf:'<path d="M5 19c0-9 6-14 15-14 0 9-5 15-14 15"/><path d="M5 19l8-8"/>',
  drop:'<path d="M12 3s6 7 6 11a6 6 0 01-12 0c0-4 6-11 6-11z"/>',
  tools:'<path d="M14 7l3-3 3 3-3 3"/><path d="M17 7L7 17"/><path d="M4 20l3-3"/><path d="M6 4a3 3 0 004 4l2 2"/>',
  lock:'<rect x="5" y="11" width="14" height="10" rx="2"/><path d="M8 11V7a4 4 0 018 0v4"/>',
  card:'<rect x="3" y="5" width="18" height="14" rx="2"/><path d="M3 10h18M7 15h4"/>',
  qr:'<rect x="4" y="4" width="6" height="6"/><rect x="14" y="4" width="6" height="6"/><rect x="4" y="14" width="6" height="6"/><path d="M14 14h2v2h-2zM18 18h2v2h-2zM14 18h1M18 14h2"/>',
  alert:'<path d="M12 3l10 18H2z"/><path d="M12 10v5M12 18v.5"/>',
  info:'<circle cx="12" cy="12" r="9"/><path d="M12 11v6M12 7.5v.5"/>',
  chart:'<path d="M4 20V4M4 20h16"/><path d="M8 16v-5M12 16V8M16 16v-8"/>',
  repeat:'<path d="M4 12a8 8 0 0114-5l2 2M20 12a8 8 0 01-14 5l-2-2"/><path d="M20 4v5h-5M4 20v-5h5"/>',
  phone:'<path d="M5 4h4l2 5-3 2a11 11 0 005 5l2-3 5 2v4a2 2 0 01-2 2A16 16 0 013 6a2 2 0 012-2z"/>',
  idcard:'<rect x="3" y="5" width="18" height="14" rx="2"/><circle cx="9" cy="11" r="2.2"/><path d="M5.5 16c.7-1.5 2-2.3 3.5-2.3s2.8.8 3.5 2.3M14 10h4M14 13h4"/>',
  copy:'<rect x="8" y="8" width="12" height="12" rx="2"/><path d="M16 8V5a1 1 0 00-1-1H5a1 1 0 00-1 1v10a1 1 0 001 1h3"/>',
  send:'<path d="M4 12l16-8-6 16-3-6z"/>',
  logout:'<path d="M15 4h4v16h-4M10 16l-4-4 4-4M6 12h10"/>',
  filter:'<path d="M4 5h16l-6 7v6l-4 2v-8z"/>',
  hand:'<path d="M7 11V6a1.5 1.5 0 013 0v4M10 10V4.5a1.5 1.5 0 013 0V10M13 10V5.5a1.5 1.5 0 013 0V12M16 9.5a1.5 1.5 0 013 0V14a7 7 0 01-7 7h-1a6 6 0 01-5-3l-2.5-4.5a1.5 1.5 0 012.5-1.6L7 13"/>',
  law:'<path d="M12 3v18M6 21h12M4 7h16M7 7l-3 7a3 3 0 006 0zM17 7l-3 7a3 3 0 006 0z"/>',
  eyeoff:'<path d="M3 3l18 18"/><path d="M10.6 5.1A10 10 0 0112 5c5 0 9 4.5 10 7-.4 1-1.2 2.3-2.4 3.5M6.6 6.6C4.3 8 2.7 10.2 2 12c1 2.5 5 7 10 7 1.8 0 3.4-.5 4.8-1.3"/><path d="M9.9 9.9a3 3 0 004.2 4.2"/>',
  flag:'<path d="M5 21V4M5 4h11l-2 4 2 4H5"/>',
  ban:'<circle cx="12" cy="12" r="9"/><path d="M5.6 5.6l12.8 12.8"/>',
  plus:'<path d="M12 5v14M5 12h14"/>',
  trash:'<path d="M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13"/>',
  gear:'<circle cx="12" cy="12" r="3"/><path d="M12 2v3M12 19v3M4.2 4.2l2.1 2.1M17.7 17.7l2.1 2.1M2 12h3M19 12h3M4.2 19.8l2.1-2.1M17.7 6.3l2.1-2.1"/>',
  list:'<path d="M9 6h11M9 12h11M9 18h11"/><circle cx="4.5" cy="6" r="1"/><circle cx="4.5" cy="12" r="1"/><circle cx="4.5" cy="18" r="1"/>',
  layers:'<path d="M12 3l9 5-9 5-9-5z"/><path d="M3 13l9 5 9-5"/>',
  money:'<rect x="2" y="6" width="20" height="12" rx="2"/><circle cx="12" cy="12" r="3"/><path d="M6 12h.5M17.5 12h.5"/>',
  image:'<rect x="3" y="4" width="18" height="16" rx="2"/><circle cx="9" cy="10" r="2"/><path d="M21 17l-5-5-9 8"/>',
  help:'<circle cx="12" cy="12" r="9"/><path d="M9.5 9.5a2.5 2.5 0 015 .5c0 1.5-2.5 2-2.5 3.5M12 17v.5"/>',
  bell:'<path d="M6 16V11a6 6 0 0112 0v5l2 2H4z"/><path d="M10 20a2 2 0 004 0"/>',
};
const ic = (n, s=22) => `<svg class="ic" width="${s}" height="${s}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${P[n]||''}</svg>`;
