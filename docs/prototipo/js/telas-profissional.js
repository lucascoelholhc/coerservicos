/* =========================================================================
   6c. APP DO PROFISSIONAL — pensado para quem usa pouco o celular:
   uma tarefa por tela, botões grandes, palavras do dia a dia.
   Rotas: #pro-hoje · #pro-terminar-<contrato>-<data> · #pro-agenda ·
          #pro-dinheiro · #pro-conversas · #pro-conversa-<id> ·
          #pro-contrato-<id> · #pro-perfil · #pro-avaliacoes
   ========================================================================= */
const TEL_COE = '(47) 3000-0000';
const mapaLink = end => `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(end+', SC')}`;
const horaAgora = () => { const n = new Date(), h = n.getHours(); return (h<6||h>18) ? '07:32' : `${pad(h)}:${pad(n.getMinutes())}`; };

function somaPro(todas, sts){ return todas.filter(x => sts.includes(x.d.status)).reduce((a,x) => a+calc(x.c.valor).recebe, 0); }

V.pro = arg => {
  S.role = 'profissional';
  const p = pro(S.proLogadoId), a = arg || 'hoje';
  const [tab, ...rest] = a.split('-'), sub = rest.join('-');
  const todas = diariasDoPro(p.id);
  const R = {hoje:proHoje, terminar:proTerminar, agenda:proAgenda, dinheiro:proDinheiro, conversas:proConversas, conversa:proConversa, contrato:proContrato, perfil:proPerfil, avaliacoes:proAvaliacoes}[tab];
  return R ? R(p, todas, sub) : V.naoEncontrado();
};

function proHoje(p, todas){
  const hoje = todas.find(x => x.d.data===CONFIG.HOJE && ['andamento','paga'].includes(x.d.status));
  const esperando = todas.filter(x => x.d.status==='aguardando');
  const prox = todas.filter(x => x.d.data>CONFIG.HOJE && !['reembolsada','liberada'].includes(x.d.status)).sort((a,b) => a.d.data.localeCompare(b.d.data)).slice(0,4);
  const conv = S.conversas.filter(m => m.proId===p.id);
  const hora = new Date().getHours(), saud = hora<12?'Bom dia':hora<18?'Boa tarde':'Boa noite';
  let cartao = '';
  if (hoje) {
    const {c,d} = hoje;
    cartao = `<article class="hoje" aria-labelledby="h-hoje">
      <div class="hoje-top"><b>Hoje · ${fmtDia(d.data)}</b>${pill(d.status==='paga'?'andamento':d.status,'pro')}</div>
      <div class="hoje-body">
        <div class="stack" style="--g:6px"><h2 id="h-hoje">Obra do ${esc(primeiro(c.cliente))}</h2><p class="muted" style="font-size:1.08rem">${esc(c.servico)}</p>${c.pedido?`<a class="linkbtn" href="#pro-contrato-${c.id}" style="justify-self:start">Ver o pedido e as fotos</a>`:''}</div>
        <div class="onde">
          <div>${ic('pin',22)}<span><b>${esc(c.endereco)}</b><br><a href="${mapaLink(c.endereco)}" target="_blank" rel="noopener">Abrir no mapa</a></span></div>
          <div>${ic('clock',22)}<span>${p.horario}</span></div>
          <div>${ic('phone',22)}<span><b class="num">${TEL_CLIENTE_DE(c.cliente)}</b> · ${esc(c.cliente)}<br><button type="button" class="linkbtn" data-act="copiar-txt" data-txt="${TEL_CLIENTE_DE(c.cliente)}">Copiar número</button></span></div>
        </div>
        ${d.chegada
          ? `<div class="note note-ok">${ic('check')}<span>Você chegou às <b>${d.chegada}</b>. O cliente foi avisado.</span></div>
             <a class="btn btn-telha btn-xl" href="#pro-terminar-${c.id}-${d.data}">${ic('camera',26)}Terminei o dia</a>
             <p class="small muted">Você tira uma foto do serviço e o cliente aprova. O dinheiro deste dia cai no seu Pix.</p>`
          : `<button type="button" class="btn btn-azul btn-xl" data-act="cheguei" data-c="${c.id}" data-d="${d.data}">${ic('pin',26)}Cheguei na obra</button>
             <p class="small muted">Avisa o cliente que você chegou. Ajuda se tiver alguma reclamação depois.</p>`}
      </div></article>`;
  } else {
    cartao = `<div class="card stack" style="--g:8px"><h2>Nenhuma obra hoje</h2><p class="muted">${prox.length?`Sua próxima diária é ${fmtDia(prox[0].d.data)}.`:'Quando alguém contratar você, aparece aqui.'}</p></div>`;
  }
  return `<div class="wrap"><div class="stack" style="--g:22px;max-width:720px">
    <header class="stack" style="--g:4px"><p class="eyebrow">${saud}</p><h1>${esc(primeiro(p.nome))}</h1></header>
    ${cartao}
    ${esperando.length ? `<section class="card stack" style="--g:6px"><h2 style="font-size:1.8rem">Esperando o cliente</h2><ul class="lista">${esperando.map(({c,d}) => `<li><div><b>${fmtDia(d.data)} · ${esc(c.cliente)}</b><p class="small muted">${textoStatus(c,d,p,'pro')}</p></div><b class="num">${brl(calc(c.valor).recebe)}</b></li>`).join('')}</ul></section>` : ''}
    <section class="stack" style="--g:10px" aria-labelledby="h-prox"><div class="row between"><h2 id="h-prox" style="font-size:1.8rem">Próximos dias</h2><a class="linkbtn" href="#pro-agenda">Ver agenda</a></div>
      <div class="painel"><ul class="lista" style="padding-inline:16px">${prox.length ? prox.map(({c,d}) => `<li><a href="#pro-contrato-${c.id}" class="row" style="flex:1"><span class="num-grande" style="font-size:2.2rem;min-width:62px">${dm(d.data).slice(0,2)}</span><span><b>${DOWL[parseD(d.data).getDay()]}</b><br><span class="muted small">${esc(c.cliente)} · ${esc(c.local)}</span></span></a>${pill(d.status,'pro')}</li>`).join('') : '<li class="muted">Nenhuma diária marcada.</li>'}</ul></div></section>
    <a class="card row between" href="#pro-dinheiro" style="text-decoration:none"><span class="stack" style="--g:4px"><span class="label-sec">Vai cair no seu Pix</span><span class="valor-grande">${brl(somaPro(todas,['paga','andamento','aguardando']))}</span></span>${ic('next',28)}</a>
    ${conv.length ? `<a class="alerta alerta-link" href="#pro-conversas" style="text-decoration:none"><span class="row"><span class="ai">${ic('chat')}</span><b>${conv.length} ${conv.length===1?'cliente quer':'clientes querem'} falar com você</b></span>${ic('next',24)}</a>` : ''}
    <div class="ajuda">${ic('help',28)}<span><b>Precisa de ajuda?</b><br>Fale com a COE: <b class="num">${TEL_COE}</b> (também WhatsApp)</span></div>
  </div></div>`;
}
const TEL_CLIENTE_DE = nome => ({'Carlos Reinert':'(47) 99140-2231','Juliana Martins':'(47) 99655-3021','Eduardo Voigt':'(47) 98812-5540'}[nome] || '(47) 99000-0000');

function proTerminar(p, todas, sub){
  const [cid, ...dd] = sub.split('-'), data = dd.join('-');
  const x = todas.find(t => t.c.id===cid && t.d.data===data); if (!x) return V.naoEncontrado();
  const {c,d} = x, k = `fim_${cid}_${data}`, st = S.fim[k] || (S.fim[k] = {frases:[], texto:''});
  if (st.ok || d.status==='aguardando') return `<div class="wrap"><div class="card sucesso" style="max-width:620px;margin-inline:auto">
    <span class="grande">${ic('check',44)}</span><h1>Dia enviado</h1>
    <p style="max-width:40ch;font-size:1.1rem">${esc(primeiro(c.cliente))} recebeu a foto e tem até <b>${d.liberaEm}</b> para aprovar. Se não responder, os <b>${brl(calc(c.valor).recebe)}</b> caem no seu Pix do mesmo jeito.</p>
    <a class="btn btn-telha btn-xl" href="#pro-hoje" style="max-width:420px">Voltar para Hoje</a></div></div>`;
  const RECADOS = ['Não pisar por 24 horas','Não pisar por 48 horas','Falta material para amanhã','Volto amanhã às 7h','Deixei tudo limpo'];
  return `<div class="wrap"><div class="stack" style="--g:22px;max-width:640px;margin-inline:auto">
    <a class="back" href="#pro-hoje">${ic('back',18)}Voltar</a>
    <header class="stack" style="--g:6px"><h1>Terminei o dia</h1><p class="muted" style="font-size:1.08rem">${fmtDia(data)} · obra do ${esc(primeiro(c.cliente))}</p></header>
    <section class="card stack" style="--g:14px"><div class="row"><span class="passo-num">1</span><h2 style="font-size:1.9rem">Tire uma foto do serviço</h2></div>
      ${uplField(k,'Foto do que você fez hoje',{multiple:true,capture:'environment',grande:true,botao:'Tirar foto',err:S.err[k]})}
      <button type="button" class="linkbtn" data-act="foto-exemplo" data-k="${k}" style="justify-self:start">Usar foto de exemplo (protótipo)</button></section>
    <section class="card stack" style="--g:14px"><div class="row"><span class="passo-num">2</span><h2 style="font-size:1.9rem">Algum recado? <span class="muted" style="font-size:1.2rem">(se quiser)</span></h2></div>
      <div class="frases-rap" role="group" aria-label="Recados prontos">${RECADOS.map(r => `<button type="button" data-act="fim-frase" data-k="${k}" data-v="${esc(r)}" aria-pressed="${st.frases.includes(r)}">${esc(r)}</button>`).join('')}</div>
      <div class="field"><label for="fim-txt-${k}">Ou escreva</label><input id="fim-txt-${k}" value="${esc(st.texto)}" data-inp="fim-txt" data-k="${k}"></div></section>
    <section class="stack" style="--g:10px"><div class="row"><span class="passo-num">3</span><h2 style="font-size:1.9rem">Enviar para o cliente</h2></div>
      <button type="button" class="btn btn-telha btn-xl" data-act="terminar" data-c="${cid}" data-d="${data}">${ic('send',24)}Enviar e terminar o dia</button>
      <p class="small muted">O cliente tem ${CONFIG.AUTO_LIBERA_HORAS} horas para aprovar. Se não responder, o dinheiro é liberado para você.</p></section>
  </div></div>`;
}

function proAgenda(p, todas){
  const prox = todas.filter(x => x.d.data>=CONFIG.HOJE && x.d.status!=='reembolsada').sort((a,b) => a.d.data.localeCompare(b.d.data));
  return `<div class="wrap"><header class="pagehead"><h1>Agenda</h1></header>
    ${p.status==='pausado'?`<div class="note note-warn" style="margin-bottom:16px">${ic('alert')}<span>Seu perfil está pausado e não aparece na busca. <a href="#pro-perfil">Voltar a aparecer</a></span></div>`:''}
    <div class="split"><section class="card" aria-labelledby="h-cal"><h2 id="h-cal" style="font-size:1.8rem;margin-bottom:12px">Seus dias</h2>${calendario(p,{mode:'agenda'})}<a class="linkbtn" href="#pro-perfil" style="display:inline-block;margin-top:10px">Mudar os dias em que trabalho</a></section>
    <section class="card" aria-labelledby="h-lista"><h2 id="h-lista" style="font-size:1.8rem;margin-bottom:6px">Diárias marcadas</h2>
      <ul class="lista">${prox.length ? prox.map(({c,d}) => `<li><a href="#pro-contrato-${c.id}" class="stack" style="--g:2px;flex:1"><b>${fmtDia(d.data)}${d.data===CONFIG.HOJE?' · hoje':''}</b><span>${esc(c.cliente)} · ${esc(c.local)}</span><span class="small muted">${esc(c.servico)}</span></a>${pill(d.status,'pro')}</li>`).join('') : '<li class="muted">Nenhuma diária marcada ainda.</li>'}</ul></section></div></div>`;
}

function proDinheiro(p, todas){
  const mov = todas.filter(x => x.d.status!=='agendada').sort((a,b) => b.d.data.localeCompare(a.d.data));
  return `<div class="wrap"><div class="stack" style="--g:22px;max-width:760px">
    <header class="pagehead" style="margin-bottom:0"><h1>Seu dinheiro</h1></header>
    <div class="grana">
      <div class="g1"><span class="gtitulo">${ic('check')}Já caiu no seu Pix</span><b>${brl(somaPro(todas,['liberada']))}</b><span class="muted small">Dias aprovados pelo cliente ou liberados depois de ${CONFIG.AUTO_LIBERA_HORAS} h</span></div>
      <div class="g2"><span class="gtitulo">${ic('lock')}Vai cair</span><b>${brl(somaPro(todas,['paga','andamento','aguardando']))}</b><span class="muted small">O cliente já pagou. Cai quando ele aprovar cada dia.</span></div>
      ${somaPro(todas,['contestada'])?`<div class="g3"><span class="gtitulo">${ic('alert')}Parado em reclamação</span><b>${brl(somaPro(todas,['contestada']))}</b><span class="muted small">A COE decide em até ${CONFIG.PRAZO_DISPUTA_HORAS} horas</span></div>`:''}
    </div>
    <div class="note note-azul">${ic('wallet')}<span>Seu Pix: <b>celular (47) 9••••-4471</b>. ${CONFIG.TAXA_PAGA_POR==='cliente'?'Você recebe o valor inteiro da diária. A taxa da COE é paga pelo cliente.':`A taxa da COE (${Math.round(CONFIG.COMISSAO*100)}%) é descontada de cada diária.`} <a href="#pro-perfil">Mudar</a></span></div>
    <section class="card"><h2 style="font-size:1.8rem;margin-bottom:6px">Dia a dia</h2><ul class="lista">${mov.map(({c,d}) => `<li><div><b>${fmtDia(d.data)}</b> · ${esc(c.cliente)}<p class="small muted">${esc(c.servico)}</p></div><div class="row"><b class="num">${brl(calc(c.valor).recebe)}</b>${pill(d.status,'pro')}</div></li>`).join('')}</ul></section>
  </div></div>`;
}

function proConversas(p){
  const conv = S.conversas.filter(m => m.proId===p.id), ct = S.contratos.filter(c => c.proId===p.id && c.chat.length);
  const ult = arr => { const m = [...arr].reverse().find(x => x.de!=='sis'); return m ? m.txt : 'Sem mensagens'; };
  const av = n => `<span class="av av-sm" style="--c:#51627A" aria-hidden="true">${iniciais(n)}</span>`;
  return `<div class="wrap"><div class="stack" style="--g:22px;max-width:760px">
    <header class="pagehead" style="margin-bottom:0"><h1>Conversas</h1></header>
    <section class="card"><h2 style="font-size:1.8rem">Querem contratar você</h2><p class="small muted" style="margin:4px 0 6px">Antes de contratar, não dá para passar telefone ou rede social. O número aparece sozinho depois que o cliente paga.</p><ul class="lista">${conv.map(m => `<li><a class="row" href="#pro-conversa-${m.id}" style="flex:1">${av(m.cliente)}<div><b>${esc(m.cliente)}</b><p class="small muted">${esc(ult(m.msgs))}</p></div></a>${ic('next',22)}</li>`).join('') || '<li class="muted">Nenhuma conversa.</li>'}</ul></section>
    <section class="card"><h2 style="font-size:1.8rem;margin-bottom:6px">Clientes que já contrataram</h2><ul class="lista">${ct.map(c => `<li><a class="row" href="#pro-contrato-${c.id}" style="flex:1">${av(c.cliente)}<div><b>${esc(c.cliente)}</b><p class="small muted">${esc(ult(c.chat))}</p></div></a>${ic('next',22)}</li>`).join('')}</ul></section>
  </div></div>`;
}
function proConversa(p, t, id){
  const m = S.conversas.find(x => x.id===id && x.proId===p.id); if (!m) return V.naoEncontrado();
  return `<div class="wrap"><a class="back" href="#pro-conversas">${ic('back',18)}Conversas</a><section class="card" style="max-width:760px" aria-labelledby="h-pc"><div class="row between wrapx" style="margin-bottom:12px"><h1 id="h-pc" style="font-size:2.2rem">${esc(m.cliente)}</h1><span class="pill p-mute">Contato oculto</span></div>
    ${chatHTML({id:m.id, msgs:m.msgs, lado:'pro', protegido:true, destino:'Chat com '+m.cliente+' (antes da contratação)'})}</section></div>`;
}
function proContrato(p, t, id){
  const c = S.contratos.find(x => x.id===id && x.proId===p.id); if (!c) return V.naoEncontrado();
  return `<div class="wrap"><a class="back" href="#pro-agenda">${ic('back',18)}Agenda</a>
    <header class="stack" style="--g:8px;margin-bottom:20px"><h1>${esc(c.cliente)}</h1><p class="muted" style="font-size:1.08rem">${esc(c.servico)}</p><p class="row wrapx" style="gap:8px 20px"><span class="row" style="gap:6px">${ic('pin',18)}${esc(c.endereco)}</span><span class="row" style="gap:6px">${ic('phone',18)}<b class="num">${TEL_CLIENTE_DE(c.cliente)}</b></span></p></header>
    ${pedidoHTML(c, p, 'pro')}<div style="height:20px"></div>
    <div class="split"><section class="card"><h2 style="font-size:1.8rem;margin-bottom:16px">Dias</h2><ol class="tl">${c.dias.map((d,i) => `<li><span class="dot ${ST[d.status].dot}">${ic(ST[d.status].i,16)}</span><div class="tl-body"><div class="tl-head"><b>Dia ${i+1} · ${fmtDia(d.data)}</b>${pill(d.status,'pro')}</div><p class="small muted">${textoStatus(c,d,p,'pro')}</p>${d.status==='andamento'?`<a class="btn btn-telha" href="#pro-hoje" style="justify-self:start">Ir para Hoje</a>`:''}</div></li>`).join('')}</ol></section>
    <section class="card"><h2 style="font-size:1.8rem;margin-bottom:12px">Conversa</h2>${chatHTML({id:c.id, msgs:c.chat, lado:'pro', protegido:false})}</section></div></div>`;
}
function proAvaliacoes(p){
  return `<div class="wrap"><header class="pagehead"><h1>Minhas avaliações</h1></header>${temHist() && p.hist.nAval>0 ? `<div class="split"><section class="card stack" style="--g:4px">${REV[areaDe(p.prof)].map(r => `<article class="review"><div class="row between wrapx"><b>${r[0]} <span class="muted small">· ${r[1]}</span></b>${estrelas(r[2])}</div><p>${r[3]}</p><p class="small muted">${r[4]}</p></article>`).join('')}</section>
    <aside class="card stack" style="--g:14px"><div><p class="label-sec">Sua nota</p><p class="disp" style="font-size:3rem;line-height:1">${nota(p.hist.nota)}</p><p class="muted small">${p.hist.nAval} avaliações · ${p.hist.diarias} diárias</p></div>
      <div class="dist" aria-label="Distribuição das notas">${[[5,30],[4,6],[3,2],[2,0],[1,0]].map(([n,q]) => `<div><span>${n}★</span><i style="--w:${q/38*100}%"></i><span>${q}</span></div>`).join('')}</div></aside></div>`
    : `<div class="card empty" style="max-width:640px">${ic('star',40)}<h2>Você ainda não tem avaliações</h2><p class="muted">A primeira aparece quando um cliente aprovar um dia seu. Para começar bem: fotos boas no perfil, responder rápido no chat e mandar foto no fim de cada dia.</p><a class="btn btn-telha" href="#pro-perfil">Melhorar meu perfil</a></div>`}</div>`;
}

/* ---------- Meu perfil (edição em blocos simples) ---------- */
function proPerfil(p){
  if (!S.perfilEdit) S.perfilEdit = {valor:p.valor, meia:p.meia||'', meiaOn:!!p.meia, servicos:[...p.servicos], cidades:[...p.cidades], raio:p.raio, disp:[...p.disp], bio:p.bio, horario:p.horario, pausado:p.status==='pausado', galeria:p.galeria.map(g => [...g])};
  const e = S.perfilEdit, cc = calc(Number(e.valor)||0), profs = [p.prof, ...p.outras].map(profissao);
  return `<div class="wrap"><form class="stack" style="--g:20px;max-width:760px" data-form="perfil" novalidate>
    <header class="row between wrapx"><div class="row">${avatar(p,'md')}<div><h1 style="font-size:2.6rem">Meu perfil</h1><p class="muted">${profissao(p.prof).nome} · ${verificado()}</p></div></div><a class="btn btn-linha btn-sm" href="#perfil-${p.id}">Ver como o cliente vê</a></header>
    <section class="card"><label class="switch" for="pe-pausa"><span><b>Pausar meu perfil</b><br><span class="small muted">Para férias ou agenda cheia. Quem já contratou continua vendo você.</span></span><input type="checkbox" id="pe-pausa" data-bind="pe.pausado" ${e.pausado?'checked':''}></label></section>
    <section class="card stack"><h2 style="font-size:1.8rem">Quanto cobro por dia</h2>
      <div class="stepper-valor"><button type="button" data-act="valor" data-alvo="pe" data-d="-10" aria-label="Diminuir 10 reais">−</button><div class="money-in"><span>R$</span><input id="pe-valor" type="number" inputmode="numeric" min="80" step="5" value="${e.valor}" data-bind="pe.valor" data-inp="pe-valor" aria-label="Valor da diária" aria-describedby="pe-valor-hint"></div><button type="button" data-act="valor" data-alvo="pe" data-d="10" aria-label="Aumentar 10 reais">+</button></div>
      <p class="hint" id="pe-valor-hint">${CONFIG.TAXA_PAGA_POR==='cliente'?`Você recebe <b id="pe-rec">${brl(cc.recebe)}</b>. O cliente paga <b id="pe-cli">${brl(cc.total)}</b>.`:`Você recebe <b id="pe-rec">${brl(cc.recebe)}</b>.`}</p>
      <label class="switch" for="pe-meiaon"><span><b>Aceito meia diária</b><br><span class="small muted">Até 4 horas</span></span><input type="checkbox" id="pe-meiaon" data-bind="pe.meiaOn" data-refresh="1" ${e.meiaOn?'checked':''}></label>
      ${e.meiaOn?`<div class="field"><label for="pe-meia">Valor da meia diária</label><div class="money-in"><span>R$</span><input id="pe-meia" type="number" min="50" step="5" value="${e.meia}" data-bind="pe.meia"></div></div>`:''}</section>
    <section class="card stack"><h2 style="font-size:1.8rem">Quando trabalho</h2>
      <div class="dias7">${[1,2,3,4,5,6,0].map(n => `<label class="opt"><input type="checkbox" id="pe-dia-${n}" value="${n}" data-bind="pe.disp" ${e.disp.includes(n)?'checked':''}><span aria-hidden="true">${DOW[n]}</span><span class="sr-only">${DOWL[n]}</span></label>`).join('')}</div>
      <div class="field"><label for="pe-hor">Horário</label><select id="pe-hor" data-bind="pe.horario">${HORARIOS.map(h => `<option ${e.horario===h?'selected':''}>${h}</option>`).join('')}</select></div></section>
    <section class="card stack"><h2 style="font-size:1.8rem">O que eu faço</h2>${profs.map(pp => `<fieldset><legend class="label-sec">${pp.nome}</legend><div class="opts">${pp.servicos.map((s,i) => chk('pe-srv-'+pp.id,i,s,e.servicos.includes(s),s,'pe.servicos')).join('')}</div></fieldset>`).join('')}</section>
    <section class="card stack"><h2 style="font-size:1.8rem">Onde trabalho</h2><div class="field"><label for="pe-raio">Até onde eu vou</label><select id="pe-raio" data-bind="pe.raio">${RAIOS.map(([v,t,k]) => `<option value="${v}" ${Number(e.raio)===v?'selected':''}>${t} (${k})</option>`).join('')}</select></div><fieldset><legend>Cidades que atendo</legend><div class="opts">${CIDADES.map((b,i) => chk('pe-c',i,b,e.cidades.includes(b),b,'pe.cidades')).join('')}</div></fieldset></section>
    <section class="card stack"><h2 style="font-size:1.8rem">Sobre mim</h2><div class="field"><label for="pe-bio" class="sr-only">Sobre mim</label><textarea id="pe-bio" maxlength="400" data-bind="pe.bio" data-inp="bio" data-aviso="pe-bio-aviso" aria-describedby="pe-bio-aviso">${esc(e.bio)}</textarea><div id="pe-bio-aviso" class="stack" style="--g:8px" aria-live="polite">${bioAviso(e.bio)}</div></div></section>
    <section class="card stack"><h2 style="font-size:1.8rem">Fotos dos meus trabalhos</h2>
      <div class="portf">${e.galeria.map((g,i) => `<div class="portf-item">${foto({...p, galeria:e.galeria},i)}<button type="button" class="btn btn-perigo btn-sm" data-act="rm-galeria" data-i="${i}">${ic('trash',16)}Tirar esta foto</button></div>`).join('')}</div>
      ${uplField('pe_fotos','Adicionar fotos',{multiple:true,hint:'Fotos novas passam por uma conferência rápida antes de aparecer.'})}</section>
    <div class="actionbar"><span class="muted">As mudanças aparecem na busca na hora.</span><button class="btn btn-telha" type="submit">Salvar</button></div>
  </form></div>`;
}

/* ---------- auxiliares compartilhados com o cadastro ---------- */
const chk = (grupo, i, val, marcado, label, bind) => `<label class="opt"><input type="checkbox" id="${grupo}-${i}" value="${esc(val)}" data-bind="${bind}" ${marcado?'checked':''}><span class="ic-ck">${ic('check',16)}</span>${esc(label)}</label>`;
function bioAviso(txt, max=400){
  const r = censura(txt||'');
  return `<span class="hint num">${(txt||'').length}/${max} letras</span>${r.tem?`<div class="note note-bad" role="alert">${ic('eyeoff')}<div class="stack" style="--g:4px"><b>Tire o contato do texto</b><span class="small">Encontramos ${r.motivos.join(', ')}: “${r.marcado}”. Seu telefone aparece para o cliente sozinho depois que ele paga.</span></div></div>`:''}`;
}
