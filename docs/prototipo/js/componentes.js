/* =========================================================================
   5. COMPONENTES — funções puras que devolvem HTML.
   No front real, cada uma vira um componente com as mesmas props.
   ========================================================================= */

/* Marca: casinha enxaimel (telhado + vigas do Vale do Itajaí). claro = para fundo escuro */
const SIMBOLO = (tam=40, claro=false) => { const v = claro?'#FFFFFF':'#3B3DC4', t = claro?'#FFCF33':'#3B3DC4', f = claro?'#1C1F2E':'#FFFFFF';
  return `<svg viewBox="0 0 64 64" width="${tam}" height="${tam}" aria-hidden="true">
  <rect x="12" y="28" width="40" height="30" fill="${f}" stroke="${v}" stroke-width="4.5" stroke-linejoin="round"/>
  <path d="M12 42H52M32 28V42M12 28L23 42M52 28L41 42" stroke="${v}" stroke-width="3.6" fill="none" stroke-linecap="round"/>
  <rect x="25" y="42" width="14" height="16" fill="#FFCF33" stroke="${v}" stroke-width="3.6" stroke-linejoin="round"/>
  <path d="M5 30L32 7L59 30" fill="none" stroke="${t}" stroke-width="6.5" stroke-linejoin="round" stroke-linecap="round"/></svg>`; };
const logoHTML = () => `${SIMBOLO(38)}<span class="logo-txt"><b>COE</b><small>serviços</small></span>`;

const avatar = (p, sz='md') => `<span class="av av-${sz}" style="--c:${p.cor||'#51627A'}" aria-hidden="true">${iniciais(p.nome)}</span>`;
const ondeTxt = p => `Mora em ${p.cidade}, atende ${p.cidades.length>1?`${p.cidades.length} cidades`:'a cidade'} (até ${p.raio} km)`;
const verificado = () => `<span class="selo">${ic('shield',18)}Documento conferido</span>`;
const estrelas = n => `<span class="stars">${ic('star',16)}${nota(n)}<span class="sr-only"> de 5</span></span>`;
/* No lançamento não há avaliações: mostramos "Novo na COE", nunca nota inventada */
const reputacao = p => temHist() && p.hist && p.hist.nAval>0
  ? `${estrelas(p.hist.nota)}<span>${p.hist.nAval} avaliações</span><span>${p.hist.diarias} diárias</span>`
  : `<span class="novo">${ic('star',15)}Novo na COE</span>`;

/* Status da diária: nomes para o CLIENTE */
const ST = {
  agendada:{t:'Agendada', c:'mute', i:'cal', dot:'mute'},
  paga:{t:'Paga · guardada', c:'azul', i:'lock', dot:'azul'},
  andamento:{t:'Em andamento', c:'warn', i:'clock', dot:'warn'},
  aguardando:{t:'Aprove o dia', c:'telha', i:'hand', dot:'telha'},
  liberada:{t:'Liberada', c:'ok', i:'check', dot:'ok'},
  contestada:{t:'Em análise', c:'bad', i:'alert', dot:'bad'},
  reembolsada:{t:'Reembolsada', c:'mute', i:'repeat', dot:'mute'},
};
/* Os mesmos status em palavras do PROFISSIONAL (mais diretas) */
const ST_PRO = {agendada:'Marcado', paga:'Garantido', andamento:'Hoje', aguardando:'Esperando o cliente', liberada:'Pago', contestada:'Reclamação', reembolsada:'Devolvido ao cliente'};
const pill = (st, lado='cli') => `<span class="pill p-${ST[st].c}">${lado==='pro'?ST_PRO[st]:ST[st].t}</span>`;

/* Foto de trabalho (ilustração no protótipo) */
function foto(p, i, extra=''){
  const g = p.galeria[i % p.galeria.length];
  return `<figure class="cena ${extra}">${cena(p.prof, g[1], i)}<span class="tag-ilus">Ilustração</span><figcaption>${esc(g[0])}</figcaption></figure>`;
}

function disponib(p, d){
  if (d <= CONFIG.HOJE) return 'passado';
  if (!p.disp.includes(parseD(d).getDay())) return 'off';
  if (p.ocupados.includes(d)) return 'ocupado';
  if (S.contratos.some(c => c.proId===p.id && c.dias.some(x => x.data===d && x.status!=='reembolsada'))) return 'ocupado';
  return 'livre';
}
function proxLivre(p){
  for (let i=1;i<=14;i++){ const d = addDays(CONFIG.HOJE,i); if (disponib(p,d)==='livre') return i===1 ? 'Livre amanhã' : `Livre ${fmtDia(d)}`; }
  return 'Agenda cheia nas próximas 2 semanas';
}

function proCard(p){
  const pf = profissao(p.prof);
  const outras = p.outras.map(o => profissao(o).nome.toLowerCase()).join(', ');
  return `<article class="procard">
    <div class="pc-foto">${cena(p.prof, p.galeria[0][1], 0)}${avatar(p,'sm')}</div>
    <div class="pc-body">
      <h3><a href="#perfil-${p.id}">${esc(p.nome)}</a></h3>
      <p class="muted">${pf.nome}${outras?` e ${outras}`:''}, ${p.anos} anos de experiência</p>
      <div class="meta">${verificado()}${reputacao(p)}</div>
      <p class="meta"><span>${ic('pin',16)}${ondeTxt(p)}</span></p>
    </div>
    <div class="pc-lado"><p class="preco"><b>${brl(p.valor)}</b> <span>por dia</span></p><span class="livre">${ic('cal',17)}${proxLivre(p)}</span></div>
  </article>`;
}

const EST_TXT = {passado:'já passou', off:'não trabalha neste dia', ocupado:'ocupado', livre:'livre', sel:'escolhido', trab:'dia de trabalho'};
function calendario(p, {mode='view', sel=[]}={}){
  const start = weekKey(CONFIG.HOJE), total = CONFIG.SEMANAS_CAL*7, fim = addDays(start,total-1);
  const mi = MES[parseD(start).getMonth()], mf = MES[parseD(fim).getMonth()];
  let cells = ['seg','ter','qua','qui','sex','sáb','dom'].map(d => `<div class="dow" aria-hidden="true">${d}</div>`).join('');
  for (let i=0;i<total;i++){
    const d = addDays(start,i), dt = parseD(d);
    let st = disponib(p,d);
    if (mode==='agenda' && S.contratos.some(c => c.proId===p.id && c.dias.some(x => x.data===d && x.status!=='reembolsada'))) st = 'trab';
    if (mode==='select' && sel.includes(d)) st = 'sel';
    const cls = `d ${st}${d===CONFIG.HOJE?' hoje':''}`;
    const mini = dt.getDate()===1 ? `<small>${MES[dt.getMonth()].slice(0,3)}</small>` : '';
    const label = `${fmtDiaLongo(d)}: ${EST_TXT[st]}${d===CONFIG.HOJE?' (hoje)':''}`;
    cells += (mode==='select' && (st==='livre'||st==='sel'))
      ? `<button type="button" class="${cls}" id="cal-${d}" data-act="dia" data-d="${d}" aria-pressed="${st==='sel'}" aria-label="${label}">${mini}${dt.getDate()}</button>`
      : `<div class="${cls}" role="img" aria-label="${label}">${mini}${dt.getDate()}</div>`;
  }
  const legend = mode==='agenda'
    ? `<span><i class="lg-trab"></i>Dia de trabalho</span><span><i class="lg-livre"></i>Livre</span><span><i class="lg-off"></i>Folga</span>`
    : `<span><i class="lg-livre"></i>Livre</span>${mode==='select'?'<span><i class="lg-sel"></i>Escolhido</span>':''}<span><i class="lg-ocupado"></i>Ocupado</span><span><i class="lg-off"></i>Não trabalha</span>`;
  return `<div class="cal-wrap"><p class="label-sec">${mi===mf?mi:mi+' – '+mf} 2026</p>
    <div class="cal" role="group" aria-label="Calendário das próximas ${CONFIG.SEMANAS_CAL} semanas">${cells}</div><div class="legend">${legend}</div></div>`;
}

function qrSvg(seed){
  const n = 25; let s = seed;
  const rnd = () => (s = (s*1103515245+12345) & 0x7fffffff) / 0x7fffffff;
  const finder = (x,y) => `<rect x="${x}" y="${y}" width="7" height="7"/><rect x="${x+1}" y="${y+1}" width="5" height="5" fill="#fff"/><rect x="${x+2}" y="${y+2}" width="3" height="3"/>`;
  const inF = (x,y) => (x<8&&y<8)||(x>n-9&&y<8)||(x<8&&y>n-9);
  let r = '';
  for (let y=0;y<n;y++) for (let x=0;x<n;x++) if (!inF(x,y) && rnd()<.5) r += `<rect x="${x}" y="${y}" width="1" height="1"/>`;
  return `<svg viewBox="-2 -2 ${n+4} ${n+4}" class="qr" role="img" aria-label="QR Code Pix de exemplo"><rect x="-2" y="-2" width="${n+4}" height="${n+4}" fill="#fff"/><g fill="#1C1F2E">${finder(0,0)}${finder(n-7,0)}${finder(0,n-7)}${r}</g></svg>`;
}

function thumbs(key){
  return (S.upl[key]||[]).map((src,i) => `<figure class="thumb"><img src="${src}" alt="Foto ${i+1} enviada"><button type="button" class="thumb-x" data-act="rm-upl" data-k="${key}" data-i="${i}" aria-label="Remover foto ${i+1}">${ic('x',16)}</button></figure>`).join('');
}
function uplField(key, label, {multiple=false, hint='', capture='', err='', grande=false, botao=''}={}){
  const k = skey(key);
  return `<div class="field upl"><span class="lbl" id="lb-${k}">${label}</span>${hint?`<p class="hint" id="hi-${k}">${hint}</p>`:''}
    <label class="dropzone${grande?' dropzone-grande':''}" for="in-${k}">${ic('camera',grande?44:30)}<span><b style="font-size:${grande?'1.2rem':'1rem'}">${botao || (multiple?'Adicionar fotos':'Tirar ou escolher foto')}</b><small>Abre a câmera ou a galeria do celular</small></span></label>
    <input class="sr-only" type="file" id="in-${k}" accept="image/*" ${capture?`capture="${capture}"`:''} ${multiple?'multiple':''} data-upl="${key}" aria-labelledby="lb-${k}"${hint?` aria-describedby="hi-${k}"`:''}>
    <div class="thumbs" id="th-${k}">${thumbs(key)}</div>
    ${err?`<p class="err" id="er-${k}">${ic('alert',16)}${err}</p>`:''}</div>`;
}

/* Chat: `protegido` = antes da contratação → censura de contato ativa */
function chatHTML({id, msgs, lado, protegido, destino}){
  const quem = m => m.de==='sis' ? 'sis' : (m.de===lado ? 'eu' : 'outro');
  const aviso = S.avisoChat[id];
  return `<div class="chat" id="chat-${id}" aria-live="polite">${msgs.length ? msgs.map(m => m.de==='sis'
      ? `<p class="msg sis">${esc(m.txt)}</p>`
      : `<div class="msg ${quem(m)}"><span>${esc(m.txt)}</span>${m.h?`<time>${m.h}</time>`:''}</div>`).join('') : '<p class="msg sis">Nenhuma mensagem ainda. Mande a primeira.</p>'}</div>
    ${aviso ? `<div class="note note-bad" role="alert" style="margin-top:12px">${ic('eyeoff')}<div class="stack" style="--g:6px"><b>Mensagem não enviada</b><span>Tinha ${aviso.motivos.join(', ')}: <span class="small">“${aviso.marcado}”</span></span><span class="small">O telefone aparece sozinho quando a contratação é paga. Assim o dinheiro fica protegido. Tentativa ${aviso.n} de ${CONFIG.TENTATIVAS_ANTES_ANALISE}; depois disso a conta vai para análise.</span></div></div>` : ''}
    <form class="chatform" data-form="chat" data-id="${id}" data-lado="${lado}" data-protegido="${protegido?1:''}" data-destino="${esc(destino||'')}"><label class="sr-only" for="chat-in-${id}">Mensagem</label><input id="chat-in-${id}" placeholder="Escreva uma mensagem" autocomplete="off" value="${esc(aviso?aviso.txt:'')}"><button class="btn btn-azul" type="submit" aria-label="Enviar">${ic('send',20)}</button></form>
    ${protegido ? `<p class="small muted row start" style="margin-top:10px;gap:6px">${ic('lock',16)}Antes de contratar, não dá para trocar telefone, e-mail ou redes sociais.</p>` : ''}`;
}

/* ---------- Contratos ---------- */
function contratoResumo(c){
  const soma = sts => c.dias.filter(d => sts.includes(d.status)).length * c.valor;
  return {pago:soma(['paga','andamento','aguardando','liberada','contestada']), liberado:soma(['liberada']), retido:soma(['paga','andamento','aguardando']), disputa:soma(['contestada'])};
}
function contratoStatus(c){
  const s = c.dias.map(d => d.status);
  if (s.includes('contestada')) return ['Em análise','p-bad'];
  if (s.includes('aguardando')) return ['Aprove o dia','p-telha'];
  if (s.some(x => ['paga','andamento','agendada'].includes(x))) return ['Ativo','p-azul'];
  return ['Concluído','p-ok'];
}
const contratoAtivo = c => c.dias.some(d => ['paga','andamento','aguardando','contestada','agendada'].includes(d.status));
const meusContratos = () => S.contratos.filter(c => c.cliente===S.cliente.nome);
const diariasDoPro = pid => S.contratos.filter(c => c.proId===pid).flatMap(c => c.dias.map(d => ({c,d})));

function textoStatus(c, d, p, lado){
  const nome = primeiro(p.nome), cli = primeiro(c.cliente);
  const chegou = d.chegada ? ` Chegou às ${d.chegada}.` : '';
  switch(d.status){
    case 'agendada': return lado==='pro' ? 'Faz parte do plano. O cliente paga 2 dias antes.' : `Faz parte do plano ${c.modo}. Cobrança automática 2 dias antes.`;
    case 'paga': return lado==='pro' ? 'O cliente já pagou. O dinheiro está guardado com a COE.' : 'Pagamento confirmado. O valor fica guardado até você aprovar o dia.';
    case 'andamento': return lado==='pro' ? `Dia de trabalho hoje.${chegou}` : `Dia de trabalho hoje.${chegou} No fim do dia ${nome} manda uma foto do serviço.`;
    case 'aguardando': return lado==='pro' ? `Você terminou às ${String(d.concluidoEm).split('às ')[1]||''}. Se ${cli} não responder, o dinheiro cai no seu Pix em ${d.liberaEm}.` : `${nome} terminou o dia em ${d.concluidoEm}. Se você não responder, o valor é liberado em ${d.liberaEm}.`;
    case 'liberada': return lado==='pro' ? `${brl(c.valor)} enviado para o seu Pix em ${d.em}.` : `${brl(c.valor)} liberado para ${nome} em ${d.em}.`;
    case 'contestada': return lado==='pro' ? `${cli} fez uma reclamação deste dia. A equipe COE vê as fotos e a conversa e decide em até ${CONFIG.PRAZO_DISPUTA_HORAS} horas.` : `Só esta diária está travada. A equipe COE analisa fotos e conversa e responde em até ${CONFIG.PRAZO_DISPUTA_HORAS} horas.`;
    case 'reembolsada': return 'Valor devolvido ao cliente.';
  }
  return '';
}
function contarSemanaDomestico(proId, d, sel){
  const wk = weekKey(d);
  const exist = meusContratos().filter(c => c.proId===proId).flatMap(c => c.dias).filter(x => x.status!=='reembolsada' && weekKey(x.data)===wk).length;
  return exist + sel.filter(x => weekKey(x)===wk).length;
}

const dlgHead = t => `<div class="dlg-head"><h2 id="dlg-title" style="font-size:2rem">${t}</h2><button type="button" class="iconbtn" data-act="fechar" aria-label="Fechar">${ic('x',20)}</button></div>`;

/* Pedido do cliente (o que precisa ser feito, fotos do local, material) */
const MATERIAL_TXT = {tenho:'O cliente já tem o material', compro:'O cliente vai comprar: diga o que falta', combinar:'Material a combinar pelo chat'};
function pedidoHTML(c, p, lado){
  const pd = c.pedido; if (!pd) return '';
  const fotos = pd.fotos.map((src,i) => `<figure class="cena"><img src="${src}" alt="Foto ${i+1} do local"></figure>`).join('')
    + (pd.ilus||[]).map((sv,i) => `<figure class="cena">${cena(p.prof, sv, i+3)}<span class="tag-ilus">Ilustração</span><figcaption>Foto do local ${i+1}</figcaption></figure>`).join('');
  return `<section class="card stack" style="--g:12px" aria-label="Pedido"><h2 style="font-size:1.8rem">${lado==='pro'?'O que o cliente pediu':'Seu pedido'}</h2>
    ${pd.servicos.length?`<div class="chips">${pd.servicos.map(x => `<span class="chip">${esc(x)}</span>`).join('')}</div>`:''}
    ${pd.desc?`<p style="font-size:1.05rem">“${esc(pd.desc)}”</p>`:''}
    ${pd.material?`<p class="row" style="gap:8px">${ic('tools',18)}${MATERIAL_TXT[pd.material]}</p>`:''}
    ${fotos?`<div class="galeria" style="grid-template-columns:repeat(auto-fill,minmax(140px,1fr))">${fotos}</div>`:''}</section>`;
}
