/* =========================================================================
   6d. PAINEL ADMINISTRATIVO (equipe COE)
   ========================================================================= */
const ADM_NAV = [
  ['geral','Visão geral','chart'],['verificacoes','Verificações','idcard'],['disputas','Disputas','alert'],['moderacao','Moderação','eyeoff'],
  ['profissionais','Profissionais','users'],['clientes','Clientes','user'],['financeiro','Financeiro','money'],['categorias','Categorias','layers'],['config','Configurações','gear'],
];
const todasDiarias = () => S.contratos.flatMap(c => c.dias.map(d => ({c,d})));
const disputasAbertas = () => todasDiarias().filter(x => x.d.status==='contestada');
const moderacaoPendente = () => S.moderacao.filter(m => m.status==='bloqueada');

function admCounts(){ return {verificacoes:S.verificacoes.length, disputas:disputasAbertas().length, moderacao:moderacaoPendente().length}; }

V.admin = arg => {
  S.role = 'admin';
  const [tab, sub] = (arg||'geral').split(/-(.+)/);
  const cnt = admCounts();
  const nav = `<nav class="adm-nav" aria-label="Seções do painel">${ADM_NAV.map(([k,t,i]) => `<a href="#admin-${k}" ${tab===k||(tab==='verif'&&k==='verificacoes')?'aria-current="page"':''}>${ic(i,19)}<span>${t}</span>${cnt[k]?`<span class="cnt">${cnt[k]}</span>`:''}</a>`).join('')}</nav>`;
  const corpo = (ADM[tab] || ADM.geral)(sub);
  return `<div class="wrap"><header class="pagehead"><span class="eyebrow">${ic('shield',17)}Equipe COE</span><h1>Painel administrativo</h1></header><div class="adm">${nav}<div class="adm-main">${corpo}</div></div></div>`;
};

const ADM = {};

ADM.geral = () => {
  const dias = todasDiarias().filter(x => x.d.status!=='agendada');
  const setembro = dias.filter(x => x.d.data.startsWith('2026-09'));
  const mov = setembro.reduce((a,x) => a+x.c.valor, 0);
  const guardado = dias.filter(x => ['paga','andamento','aguardando'].includes(x.d.status)).reduce((a,x) => a+x.c.valor, 0);
  const ativos = PROS.filter(p => p.status==='ativo').length;
  const semanas = ['2026-09-01','2026-09-08','2026-09-15','2026-09-22'].map(ini => [ini, setembro.filter(x => x.d.data>=ini && x.d.data<=addDays(ini,6)).length]);
  const max = Math.max(4, ...semanas.map(s => s[1]));
  const aut = todasDiarias().filter(x => x.d.status==='aguardando');
  const reincid = [...new Set(S.moderacao.map(m => m.autor))].filter(a => tentativas(a)>=2);
  const alertas = [
    S.verificacoes.length && ['idcard', `${S.verificacoes.length} cadastros esperando verificação`, 'O mais antigo foi enviado em ' + S.verificacoes[0].enviado, 'admin-verificacoes'],
    disputasAbertas().length && ['alert', `${disputasAbertas().length} disputas abertas`, `Prazo de resposta: ${CONFIG.PRAZO_DISPUTA_HORAS} horas`, 'admin-disputas'],
    aut.length && ['clock', `${aut.length} diária(s) liberam automaticamente nas próximas horas`, aut.map(x => `${x.c.cliente} → ${pro(x.c.proId).nome}`).join('; '), 'admin-financeiro'],
    reincid.length && ['eyeoff', `${reincid.length} pessoas tentaram passar contato mais de uma vez`, reincid.join(', '), 'admin-moderacao'],
  ].filter(Boolean);
  return `
  <div class="tiles">
    <div class="tile t-azul"><span>${ic('cal',16)}Diárias em setembro</span><b>${setembro.length}</b><small>${brl(mov)} movimentados</small></div>
    <div class="tile t-ok"><span>${ic('money',16)}Receita da COE</span><b>${brl(mov*CONFIG.COMISSAO)}</b><small>${Math.round(CONFIG.COMISSAO*100)}% das diárias do mês</small></div>
    <div class="tile t-telha"><span>${ic('lock',16)}Guardado agora</span><b>${brl(guardado)}</b><small>Pago e ainda não liberado</small></div>
    <div class="tile"><span>${ic('users',16)}Profissionais ativos</span><b>${ativos}</b><small>${S.verificacoes.length} em verificação · ${S.clientes.length} clientes</small></div>
  </div>
  <section class="card" aria-labelledby="h-at"><h2 id="h-at" style="font-size:1.2rem;margin-bottom:8px">Precisa da sua atenção</h2>
    <ul class="feed">${alertas.length ? alertas.map(([i,t,s,h]) => `<li><span class="fi">${ic(i,18)}</span><div><b>${t}</b><p class="small muted">${esc(s)}</p></div><a class="btn btn-linha btn-sm" href="#${h}">Abrir</a></li>`).join('') : '<li><span class="fi">'+ic('check',18)+'</span><div><b>Tudo em dia</b></div><span></span></li>'}</ul></section>
  <div class="split">
    <section class="card" aria-labelledby="h-sem"><h2 id="h-sem" style="font-size:1.2rem">Diárias por semana</h2><p class="small muted" style="margin-bottom:8px">Setembro de 2026. A semana atual aparece em azul.</p>
      <div class="bars" role="img" aria-label="${semanas.map(s => `semana de ${dm(s[0])}: ${s[1]} diárias`).join('; ')}">${semanas.map((s,i) => `<div class="${i===3?'atual':''}" style="--h:${s[1]/max*100}%"><b>${s[1]}</b></div>`).join('')}</div>
      <div class="bars-x" aria-hidden="true">${semanas.map(s => `<span>${dm(s[0])}</span>`).join('')}</div></section>
    <section class="card stack" style="--g:6px" aria-labelledby="h-dem"><h2 id="h-dem" style="font-size:1.2rem">Buscas sem resultado</h2><p class="small muted">Mostra onde recrutar profissionais. Números de exemplo.</p>
      <ul class="lista">${[['Encanador','Toda a cidade',17],['Montador de móveis','Toda a cidade',9],['Eletricista','Badenfurt',6],['Diarista','Itoupava Seca',4]].map(([p,b,n]) => `<li><div><b>${p}</b><p class="small muted">${b}</p></div><span class="num b">${n} buscas</span></li>`).join('')}</ul></section>
  </div>`;
};

ADM.verificacoes = () => `<section class="stack" style="--g:14px"><div class="sec-title"><h2>Cadastros para verificar</h2><span class="muted small">Meta: responder em até 24 horas</span></div>
  ${S.verificacoes.length ? `<div class="grid-cards">${S.verificacoes.map(v => { const ok = Object.values(v.checks).filter(x => x===true).length, tot = Object.keys(v.checks).length, bioRuim = censura(v.bio).tem;
    return `<article class="card stack" style="--g:12px"><div class="row"><span class="av av-md" style="--c:#51627A" aria-hidden="true">${iniciais(v.nome)}</span><div><h3>${esc(v.nome)}</h3><p class="small muted">${profissao(v.prof).nome} · ${v.anos} anos · enviado ${v.enviado}</p></div></div>
      <div class="chips"><span class="chip">${ok} de ${tot} itens conferidos</span><span class="chip">${v.fotos} fotos</span>${bioRuim?`<span class="pill p-bad"><i></i>Contato no texto</span>`:''}</div>
      <a class="btn btn-azul btn-block" href="#admin-verif-${v.id}">Analisar cadastro</a></article>`; }).join('')}</div>`
  : `<div class="card empty">${ic('check',40)}<h2>Fila vazia</h2><p class="muted">Nenhum cadastro esperando verificação.</p></div>`}</section>`;

ADM.verif = id => {
  const v = S.verificacoes.find(x => x.id===id); if (!v) return ADM.verificacoes();
  const bio = censura(v.bio);
  const itens = [['cpf','CPF válido e no nome da pessoa'],['doc','Documento legível e dentro da validade'],['selfie','Selfie confere com o documento'],['fotos','Fotos parecem trabalhos reais (não tiradas da internet)'],['bio','Texto do perfil sem contato']].concat('nr10' in v.checks ? [['nr10','Certificado de NR-10 válido (eletricista)']] : []);
  const docImg = (k, rot, cor) => v.docs && v.docs[k] ? `<figure class="cena"><img src="${v.docs[k]}" alt="${rot} enviado"><figcaption>${rot}</figcaption></figure>` : `<figure class="cena" style="background:${cor}"><span class="tag-ilus">Exemplo</span><figcaption>${rot}</figcaption></figure>`;
  return `<a class="back" href="#admin-verificacoes">${ic('back',18)}Fila de verificação</a>
  <div class="split">
    <div class="stack" style="--g:18px">
      <section class="card stack" style="--g:12px"><div class="row">${`<span class="av av-lg" style="--c:#51627A" aria-hidden="true">${iniciais(v.nome)}</span>`}<div><h2>${esc(v.nome)}</h2><p class="muted">${profissao(v.prof).nome}${v.outras.length?' + '+v.outras.map(o => profissao(o).nome).join(', '):''} · enviado ${v.enviado}</p></div></div>
        <div class="evid">${docImg('doc','Documento','#6F7A86')}${docImg('selfie','Selfie com documento','#8A7766')}</div></section>
      <section class="card"><h3 style="margin-bottom:8px">Dados informados</h3><ul class="lista">
        <li><span class="muted">Celular</span><b class="num">${v.cel}</b></li><li><span class="muted">CPF</span><b class="num">${v.cpf}</b></li>
        <li><span class="muted">Experiência</span><b>${v.anos} anos</b></li><li><span class="muted">Diária</span><b>${brl(v.valor)}</b></li>
        <li><span class="muted">Ferramentas</span><b>${FERRAMENTAS[v.ferramentas]||'—'}</b></li><li><span class="muted">Onde atende</span><b>${v.cidades.join(', ')}</b></li>
        <li style="display:grid;gap:6px"><span class="muted">Serviços</span><div class="chips">${v.servicos.map(s => `<span class="chip">${esc(s)}</span>`).join('')}</div></li></ul></section>
      <section class="card stack" style="--g:8px"><h3>Sobre (texto do perfil)</h3><p>${bio.marcado}</p>${bio.tem?`<div class="note note-bad">${ic('eyeoff')}<span class="small">Contato encontrado: ${bio.motivos.join(', ')}. Peça correção antes de publicar.</span></div>`:''}</section>
      <section class="card stack" style="--g:8px"><h3>Fotos de trabalhos (${v.fotos})</h3><div class="galeria">${Array.from({length:Math.min(v.fotos,6)},(_,i) => `<figure class="cena">${cena(v.prof, (v.servicos[i%v.servicos.length]||''), i)}<span class="tag-ilus">Ilustração</span></figure>`).join('')}</div></section>
    </div>
    <aside class="card stack" style="--g:14px;position:sticky;top:110px">
      <h3>Conferência</h3>
      <ul class="check-list">${itens.map(([k,t]) => `<li><label for="vk-${k}"><input type="checkbox" class="cb" id="vk-${k}" data-act-chg="verif-check" data-id="${v.id}" data-k="${k}" ${v.checks[k]===true?'checked':''}><span>${t}${v.checks[k]===false?' <span class="pill p-bad"><i></i>Problema</span>':''}</span></label></li>`).join('')}</ul>
      <div class="field"><label for="vr-motivo">Mensagem para o profissional (se pedir correção ou recusar)</label><textarea id="vr-motivo" style="min-height:90px">${bio.tem?'Olá! Tire o telefone/rede social do texto "Sobre você". O contato aparece para o cliente depois da contratação.':''}</textarea></div>
      <button type="button" class="btn btn-ok btn-block" data-act="verif" data-id="${v.id}" data-dec="ok" ${Object.values(v.checks).every(x => x===true)?'':'aria-disabled="true"'}>${ic('check',18)}Aprovar e publicar</button>
      <button type="button" class="btn btn-linha btn-block" data-act="verif" data-id="${v.id}" data-dec="corrigir">Pedir correção</button>
      <button type="button" class="btn btn-perigo btn-block" data-act="verif" data-id="${v.id}" data-dec="recusar">Recusar cadastro</button>
      <p class="small muted">Aprovar só é liberado com todos os itens conferidos.</p>
    </aside>
  </div>`;
};

ADM.disputas = () => {
  const lista = disputasAbertas();
  return `<section class="stack" style="--g:14px"><div class="sec-title"><h2>Disputas abertas</h2><span class="muted small">Prazo de resposta: ${CONFIG.PRAZO_DISPUTA_HORAS} horas</span></div>
  ${lista.length ? lista.map(({c,d}) => { const p = pro(c.proId); return `<article class="card stack" style="--g:14px">
    <div class="row between wrapx start"><div><h3>${esc(d.contest.motivo)}</h3><p class="small muted">Diária de ${fmtDia(d.data)} · ${brl(c.valor)} travado · aberta em ${d.contest.aberta}</p></div>${pill('contestada')}</div>
    <div class="split" style="gap:12px">
      <div class="lado"><b>Cliente: ${esc(c.cliente)}</b><p>“${esc(d.contest.desc)}”</p>${d.contest.fotos?`<div class="evid">${Array.from({length:d.contest.fotos},(_,i) => `<figure class="cena">${cena(p.prof, p.servicos[i%p.servicos.length], i+3)}<span class="tag-ilus">Ilustração</span><figcaption>Evidência ${i+1}</figcaption></figure>`).join('')}</div>`:'<p class="small muted">Sem fotos anexadas.</p>'}</div>
      <div class="lado"><b>Profissional: ${esc(p.nome)}</b><p>“${esc(d.contest.resposta||'Ainda não respondeu.')}”</p><p class="small muted">${anosTxt(p.anos)} · ${S.contratos.filter(x => x.proId===p.id).flatMap(x => x.dias).filter(x => x.status==='contestada').length} disputa(s) aberta(s)</p></div>
    </div>
    ${c.chat.length?`<details><summary class="b" style="cursor:pointer">Ver conversa do contrato (${c.chat.length} mensagens)</summary><div class="stack" style="--g:6px;margin-top:8px">${c.chat.map(m => `<p class="small"><b>${m.de==='cli'?esc(c.cliente):m.de==='pro'?esc(p.nome):'Sistema'}:</b> ${esc(m.txt)} <span class="muted">${m.h||''}</span></p>`).join('')}</div></details>`:''}
    <div class="row wrapx"><button type="button" class="btn btn-ok" data-act="resolver" data-c="${c.id}" data-d="${d.data}" data-dec="liberada">Liberar ao profissional</button><button type="button" class="btn btn-perigo" data-act="resolver" data-c="${c.id}" data-d="${d.data}" data-dec="reembolsada">Reembolsar cliente</button><button type="button" class="btn btn-linha" data-act="toast" data-msg="Pedido de informações enviado às duas partes pelo chat.">Pedir mais informações</button></div>
  </article>`; }).join('') : `<div class="card empty">${ic('check',40)}<h2>Nenhuma disputa aberta</h2></div>`}</section>`;
};

ADM.moderacao = () => {
  const pessoas = [...new Set(S.moderacao.map(m => m.autor))];
  const stPill = s => ({bloqueada:'<span class="pill p-bad"><i></i>Bloqueada</span>', advertido:'<span class="pill p-warn"><i></i>Advertido</span>', ignorado:'<span class="pill p-mute"><i></i>Falso positivo</span>', suspenso:'<span class="pill p-bad"><i></i>Conta suspensa</span>'}[s]);
  return `<section class="stack" style="--g:14px"><div class="sec-title"><h2>Moderação de contato</h2><span class="muted small">Tentativas de passar telefone, e-mail ou redes antes da contratação</span></div>
  <div class="tiles"><div class="tile t-bad"><span>Bloqueios pendentes</span><b>${moderacaoPendente().length}</b></div><div class="tile"><span>Pessoas envolvidas</span><b>${pessoas.length}</b></div><div class="tile t-telha"><span>Com ${CONFIG.TENTATIVAS_ANTES_ANALISE}+ tentativas</span><b>${pessoas.filter(a => tentativas(a)>=CONFIG.TENTATIVAS_ANTES_ANALISE).length}</b></div></div>
  <div class="stack" style="--g:12px">${S.moderacao.map(m => `<article class="card stack" style="--g:10px">
    <div class="row between wrapx start"><div><b>${esc(m.autor)}</b> <span class="muted small">· ${m.tipo} · ${tentativas(m.autor)} tentativa(s)</span><p class="small muted">${esc(m.onde)} · ${m.quando}</p></div>${stPill(m.status)}</div>
    <p class="card-flat" style="padding:10px 12px">${censura(m.trecho).marcado}</p>
    <div class="row wrapx between"><span class="chips">${m.motivos.map(x => `<span class="chip">${esc(x)}</span>`).join('')}</span>
      ${m.status==='bloqueada'?`<span class="row wrapx"><button type="button" class="btn btn-linha btn-sm" data-act="moderar" data-id="${m.id}" data-dec="advertido">Advertir</button><button type="button" class="btn btn-perigo btn-sm" data-act="moderar" data-id="${m.id}" data-dec="suspenso">Suspender conta</button><button type="button" class="btn btn-suave btn-sm" data-act="moderar" data-id="${m.id}" data-dec="ignorado">Falso positivo</button></span>`:''}</div>
  </article>`).join('')}</div>
  <section class="card stack" style="--g:8px"><h3>O que o sistema bloqueia</h3><p class="small muted">Vale para chat antes da contratação, texto "Sobre você" e legendas de fotos. Depois da contratação o telefone é liberado e o chat não é mais filtrado.</p>
    <div class="chips">${['Telefone com ou sem traços','Número escrito por extenso','E-mail (também "arroba")','Links e sites','@perfil de rede social','Palavras: zap, whats, insta, telegram','Pedidos: "me liga", "meu número"'].map(t => `<span class="chip">${t}</span>`).join('')}</div>
    <p class="small muted">Fotos com número de telefone escrito precisam de leitura de texto na imagem (OCR) no backend; por enquanto entram na conferência manual.</p></section></section>`;
};

ADM.profissionais = () => {
  const t = S.admBusca.toLowerCase(), st = S.admStatus;
  const l = PROS.filter(p => (st==='todos'||p.status===st) && (!t || (p.nome+' '+profissao(p.prof).nome).toLowerCase().includes(t)));
  const stp = s => ({ativo:'<span class="pill p-ok"><i></i>Ativo</span>', pausado:'<span class="pill p-warn"><i></i>Pausado</span>', suspenso:'<span class="pill p-bad"><i></i>Suspenso</span>'}[s]);
  return `<section class="stack" style="--g:14px"><div class="sec-title"><h2>Profissionais</h2><span class="muted small">${PROS.length} cadastrados</span></div>
    <div class="filtro-bar"><div class="field"><label for="adm-busca">Buscar</label><input id="adm-busca" type="search" value="${esc(S.admBusca)}" placeholder="Nome ou profissão" data-inp="adm-busca"></div>
      <fieldset><legend class="sr-only">Status</legend><div class="opts">${['todos','ativo','pausado','suspenso'].map(s => `<label class="opt"><input type="radio" name="adm-st" id="adm-st-${s}" value="${s}" data-chg="adm-status" ${st===s?'checked':''}>${s[0].toUpperCase()+s.slice(1)}</label>`).join('')}</div></fieldset></div>
    <div class="tabela-wrap"><table class="tabela"><thead><tr><th>Nome</th><th>Profissão</th><th>Experiência</th><th class="num">Diária</th><th>Cidades</th><th>Status</th><th>Contato</th><th>Ações</th></tr></thead><tbody>
      ${l.map(p => `<tr><td><a href="#perfil-${p.id}" class="b">${esc(p.nome)}</a></td><td>${profissao(p.prof).nome}</td><td>${p.anos} anos</td><td class="num">${brl(p.valor)}</td><td>${p.cidades.join(', ')}</td><td>${stp(p.status)}</td><td>${tentativas(p.nome)?`<span class="pill p-bad"><i></i>${tentativas(p.nome)} tentativa(s)</span>`:'<span class="muted small">—</span>'}</td>
        <td>${p.status==='suspenso'?`<button type="button" class="btn btn-linha btn-sm" data-act="pro-status" data-id="${p.id}" data-st="ativo">Reativar</button>`:`<button type="button" class="btn btn-perigo btn-sm" data-act="pro-status" data-id="${p.id}" data-st="suspenso">Suspender</button>`}</td></tr>`).join('') || '<tr><td colspan="8" class="muted">Nenhum profissional encontrado.</td></tr>'}
    </tbody></table></div></section>`;
};

ADM.clientes = () => `<section class="stack" style="--g:14px"><div class="sec-title"><h2>Clientes</h2><span class="muted small">${S.clientes.length} cadastrados</span></div>
  <div class="tabela-wrap"><table class="tabela"><thead><tr><th>Nome</th><th>Cidade</th><th>Desde</th><th class="num">Contratos</th><th class="num">Total pago</th><th>Contato</th><th>Status</th></tr></thead><tbody>
  ${S.clientes.map(k => { const cs = S.contratos.filter(c => c.cliente===k.nome); const tot = cs.flatMap(c => c.dias.filter(d => !['agendada','reembolsada'].includes(d.status)).map(() => c.valor)).reduce((a,b) => a+b, 0);
    return `<tr><td class="b">${esc(k.nome)}</td><td>${k.cidade}</td><td>${k.desde}</td><td class="num">${cs.length}</td><td class="num">${brl(calc(tot).total)}</td><td>${tentativas(k.nome)?`<span class="pill p-bad"><i></i>${tentativas(k.nome)} tentativa(s)</span>`:'<span class="muted small">—</span>'}</td><td>${k.status==='suspenso'?'<span class="pill p-bad"><i></i>Suspenso</span>':'<span class="pill p-ok"><i></i>Ativo</span>'}</td></tr>`; }).join('')}
  </tbody></table></div></section>`;

ADM.financeiro = () => {
  const l = todasDiarias().filter(x => x.d.status!=='agendada').sort((a,b) => b.d.data.localeCompare(a.d.data));
  const soma = sts => l.filter(x => sts.includes(x.d.status)).reduce((a,x) => a+x.c.valor, 0);
  return `<section class="stack" style="--g:14px"><div class="sec-title"><h2>Financeiro</h2><span class="muted small">Valores das diárias; taxa de ${Math.round(CONFIG.COMISSAO*100)}% ${CONFIG.TAXA_PAGA_POR==='cliente'?'somada ao cliente':'descontada do profissional'}</span></div>
    <div class="tiles">
      <div class="tile t-telha"><span>Guardado (a liberar)</span><b>${brl(soma(['paga','andamento','aguardando']))}</b></div>
      <div class="tile t-ok"><span>Repassado aos profissionais</span><b>${brl(soma(['liberada']))}</b></div>
      <div class="tile t-azul"><span>Receita da COE</span><b>${brl(soma(['liberada'])*CONFIG.COMISSAO)}</b><small>Só de diárias liberadas</small></div>
      <div class="tile t-bad"><span>Em disputa</span><b>${brl(soma(['contestada']))}</b></div>
      <div class="tile"><span>Reembolsado</span><b>${brl(soma(['reembolsada']))}</b></div>
    </div>
    <div class="tabela-wrap"><table class="tabela"><thead><tr><th>Dia</th><th>Cliente</th><th>Profissional</th><th class="num">Diária</th><th class="num">Taxa COE</th><th>Status</th><th>Repasse Pix</th></tr></thead><tbody>
      ${l.map(({c,d}) => `<tr><td>${fmtDia(d.data)}</td><td>${esc(c.cliente)}</td><td>${esc(pro(c.proId).nome)}</td><td class="num">${brl(c.valor)}</td><td class="num">${brl(calc(c.valor).taxa)}</td><td>${pill(d.status)}</td><td class="small">${d.status==='liberada'?`Enviado ${d.em}`:d.status==='reembolsada'?'Devolvido ao cliente':'—'}</td></tr>`).join('')}
    </tbody></table></div>
    <p class="small muted">No sistema real, os valores ficam numa conta de pagamento com split (ex.: subconta por profissional no gateway) e o repasse sai automático ao liberar.</p></section>`;
};

ADM.categorias = () => `<section class="stack" style="--g:14px"><div class="sec-title"><h2>Categorias e serviços</h2><span class="muted small">Área → profissão → serviços. Mudanças valem na hora para busca e cadastro.</span></div>
  <div class="arvore">${AREAS.map(a => `<div class="area"><div class="area-head"><h3>${a.nome}</h3><span class="small muted">${a.profissoes.filter(p => p.ativo).length} de ${a.profissoes.length} ativas</span></div>
    ${a.profissoes.map(p => `<div class="prof"><div class="row between wrapx"><div class="row"><span class="bi" style="width:40px;height:40px;border-radius:10px;background:var(--surface-2);display:grid;place-items:center">${ic(p.ic||'tools',20)}</span><div><b>${esc(p.nome)}</b>${p.lc150?' <span class="pill p-azul"><i></i>LC 150</span>':''}<p class="small muted">${PROS.filter(x => x.prof===p.id||x.outras.includes(p.id)).length} profissionais</p></div></div>
        <label class="switch" for="cat-on-${p.id}" style="padding:0"><span class="small b">${p.ativo?'Ativa':'Em breve'}</span><input type="checkbox" id="cat-on-${p.id}" data-chg="cat-ativo" data-p="${p.id}" ${p.ativo?'checked':''}></label></div>
      <div class="chips">${p.servicos.map((s,i) => `<span class="srv-x">${esc(s)}<button type="button" data-act="cat-rm" data-p="${p.id}" data-i="${i}" aria-label="Remover ${esc(s)}">${ic('x',14)}</button></span>`).join('')}</div>
      <form class="row" data-form="cat-add" data-p="${p.id}"><label class="sr-only" for="cat-new-${p.id}">Novo serviço de ${esc(p.nome)}</label><input id="cat-new-${p.id}" placeholder="Novo serviço" style="min-height:44px;max-width:280px"><button class="btn btn-linha btn-sm" type="submit">${ic('plus',16)}Adicionar</button></form>
    </div>`).join('')}
    <form class="prof row wrapx" data-form="prof-add" data-a="${a.id}"><label class="sr-only" for="prof-new-${a.id}">Nova profissão em ${a.nome}</label><input id="prof-new-${a.id}" placeholder="Nova profissão em ${a.nome}" style="min-height:44px;max-width:320px"><button class="btn btn-azul btn-sm" type="submit">${ic('plus',16)}Criar profissão</button></form>
  </div>`).join('')}</div></section>`;

ADM.config = () => `<section class="stack" style="--g:14px;max-width:680px"><div class="sec-title"><h2>Configurações</h2></div>
  <form class="card stack" style="--g:18px" data-form="config" novalidate>
    <div class="field"><label for="cf-com">Comissão por diária (%)</label><input id="cf-com" type="number" min="0" max="30" step="0.5" value="${CONFIG.COMISSAO*100}"></div>
    <fieldset><legend>Quem paga a comissão</legend><div class="opts"><label class="opt"><input type="radio" name="cf-quem" id="cf-quem-c" value="cliente" ${CONFIG.TAXA_PAGA_POR==='cliente'?'checked':''}>Cliente (soma no total)</label><label class="opt"><input type="radio" name="cf-quem" id="cf-quem-p" value="profissional" ${CONFIG.TAXA_PAGA_POR==='profissional'?'checked':''}>Profissional (desconta do repasse)</label></div></fieldset>
    <div class="field"><label for="cf-lib">Liberação automática (horas sem resposta do cliente)</label><input id="cf-lib" type="number" min="1" max="72" value="${CONFIG.AUTO_LIBERA_HORAS}"></div>
    <div class="field"><label for="cf-disp">Prazo da equipe para decidir disputa (horas)</label><input id="cf-disp" type="number" min="1" max="168" value="${CONFIG.PRAZO_DISPUTA_HORAS}"></div>
    <div class="field"><label for="cf-lc">Limite semanal de diárias com a mesma diarista</label><input id="cf-lc" type="number" min="1" max="2" value="${CONFIG.LIMITE_DOMESTICO_SEMANA}"><p class="hint">A LC 150/2015 considera vínculo de emprego a partir de 3 dias por semana. Não aumente sem orientação jurídica.</p></div>
    <div class="field"><label for="cf-tent">Tentativas de passar contato antes de ir para análise</label><input id="cf-tent" type="number" min="1" max="10" value="${CONFIG.TENTATIVAS_ANTES_ANALISE}"></div>
    <button class="btn btn-telha" type="submit">Salvar configurações</button>
  </form></section>`;
