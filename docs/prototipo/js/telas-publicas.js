/* =========================================================================
   6a. TELAS PÚBLICAS — início, categorias, como funciona, busca, perfil, login
   ========================================================================= */
const V = {};
const profsAtivas = () => todasProfs().filter(p => p.ativo);
const optsProfissoes = (sel='', todas='Qualquer serviço') => `<option value="">${todas}</option>` + profsAtivas().map(p => `<option value="${p.id}" ${sel===p.id?'selected':''}>${p.nome}</option>`).join('');
const optsCidades = (sel='', todos='Qualquer cidade') => `<option value="">${todos}</option>` + CIDADES.map(b => `<option ${sel===b?'selected':''}>${b}</option>`).join('');
const menorPreco = id => { const l = PROS.filter(p => p.status==='ativo' && (p.prof===id || p.outras.includes(id))).map(p => p.valor); return l.length ? Math.min(...l) : null; };

V.inicio = () => {
  const breve = todasProfs().filter(p => !p.ativo).map(p => p.nome.toLowerCase());
  return `
  <section class="hero"><div class="wrap hero-grid">
    <div>
      <h1>Pague por dia. Libere quando o dia estiver feito.</h1>
      <p class="lead">Pedreiro, pintor, eletricista, jardineiro e diarista com documento conferido. O dinheiro fica guardado com a COE e só vai para o profissional depois que você aprova cada dia de trabalho.</p>
      <form class="busca-hero" data-form="busca-home" role="search" aria-label="Buscar profissional">
        <div class="field"><label for="h-prof">Do que você precisa?</label><select id="h-prof">${optsProfissoes(S.filtros.prof)}</select></div>
        <div class="field"><label for="h-cidade">Em qual cidade?</label><select id="h-cidade">${optsCidades(S.filtros.cidade)}</select></div>
        <button class="btn btn-telha btn-lg" type="submit">${ic('search')}Buscar</button>
      </form>
      <p class="hero-pro">É profissional? <a href="#profissionais">Conheça a COE para quem trabalha</a></p>
    </div>
    <div class="cartela" role="img" aria-label="Exemplo: obra de 3 diárias com o Valdir. Segunda já foi liberada para ele, terça você acabou de aprovar e quarta está paga e guardada.">
      <div class="cartela-head">${avatar({nome:'Valdir Schmitt',cor:'#3B3DC4'},'sm')}<div><b>Valdir Schmitt, pedreiro</b><span class="small muted">Garagem no Garcia: 3 diárias de R$ 280</span></div></div>
      <div class="cartela-dias">
        <div class="dia dia-ok"><span class="dia-sem">seg</span><span class="dia-num">28</span><span class="carimbo ok">Liberado</span><span class="dia-txt">Já está com o Valdir</span></div>
        <div class="dia dia-aprovar"><span class="dia-sem">ter, hoje</span><span class="dia-num">29</span><span class="carimbo">Aprovado</span><span class="dia-txt">Você viu a foto e aprovou</span></div>
        <div class="dia dia-guardado"><span class="dia-sem">qua</span><span class="dia-num">30</span><span class="carimbo">Guardado</span><span class="dia-txt">Pago e guardado na COE</span></div>
      </div>
      <p class="cartela-pe small muted">Você pagou as 3 diárias antes. Cada uma só vai para o Valdir depois do seu ok.</p>
    </div>
  </div></section>

  <section class="sec sec-branca" aria-labelledby="h-cats"><div class="wrap">
    <div class="sec-title"><h2 id="h-cats">Quem você precisa?</h2><a class="linkbtn" href="#busca">Ver todos os profissionais</a></div>
    <div class="cats">${profsAtivas().map(p => { const m = menorPreco(p.id);
      return `<a class="cat-tile" href="#busca-${p.id}">${cena(p.id, CENA_CAT[p.id], 0)}<span class="ct-nome"><b>${p.nome}</b><span>${m?`a partir de ${brl(m)} por dia`:'em breve'}</span></span></a>`; }).join('')}</div>
    ${breve.length?`<p class="breve-linha">Em breve: ${breve.join(' e ')}.</p>`:''}
  </div></section>

  <section class="sec" aria-labelledby="h-como"><div class="wrap trena-wrap">
    <div class="sec-title" style="margin-bottom:0"><h2 id="h-como">Como funciona</h2><a class="linkbtn" href="#como-funciona">Regras completas</a></div>
    <div class="trena" aria-hidden="true"><span style="left:2%">1</span><span style="left:35%">2</span><span style="left:68%">3</span></div>
    <ol class="passos">
      <li><span class="pn" aria-hidden="true">1</span><h3>Escolha e converse</h3><p class="muted">Veja fotos de trabalhos, experiência, valor da diária e os dias livres. Tire dúvidas pelo chat.</p></li>
      <li><span class="pn" aria-hidden="true">2</span><h3>Pague as diárias antes</h3><p class="muted">Pix ou cartão. O dinheiro fica guardado e o profissional sabe que vai receber.</p></li>
      <li><span class="pn" aria-hidden="true">3</span><h3>Aprove cada dia</h3><p class="muted">No fim do dia ele manda a foto do serviço. Você aprova e o dinheiro daquele dia vai para ele.</p></li>
    </ol>
  </div></section>

  <section class="sec sec-branca" aria-labelledby="h-prot"><div class="wrap">
    <div class="sec-title"><h2 id="h-prot">Seu dinheiro protegido</h2></div>
    <div class="fatos">
      <div><b class="fn">${CONFIG.AUTO_LIBERA_HORAS} h</b><p>Você tem ${CONFIG.AUTO_LIBERA_HORAS} horas para aprovar cada dia. Sem resposta, o valor é liberado ao profissional.</p></div>
      <div><b class="fn">1 dia</b><p>Se algo der errado, só aquele dia fica travado até a equipe COE decidir. Os outros seguem normais.</p></div>
      <div><b class="fn">RG + selfie</b><p>Todo profissional tem documento e selfie conferidos antes de aparecer na busca.</p></div>
      <div><b class="fn">Nota real</b><p>Só avalia quem contratou e pagou pelo app. Ninguém compra nem inventa avaliação.</p></div>
    </div>
  </div></section>

  <section class="cta-pro" aria-labelledby="h-pro">
    <div class="viga-clara"></div>
    <div class="wrap cta-pro-in">
      <div class="stack" style="--g:14px">
        <p class="eyebrow">Pedreiro, pintor, eletricista, jardineiro ou diarista?</p>
        <h2 id="h-pro">Cadastre-se grátis e receba garantido</h2>
        <p style="max-width:44ch">O cliente paga antes de você sair de casa. Você define o valor da diária e até onde vai trabalhar. O cadastro é feito pelo celular, uma pergunta de cada vez.</p>
        <a class="btn btn-azul btn-lg" href="#profissionais" style="justify-self:start">Quero trabalhar com a COE</a>
      </div>
      <ul>
        <li>${ic('check')}Sem mensalidade</li>
        <li>${ic('check')}Dinheiro no Pix quando o cliente aprova o dia</li>
        <li>${ic('check')}Sem resposta do cliente em ${CONFIG.AUTO_LIBERA_HORAS} h, o dinheiro é liberado</li>
        <li>${ic('check')}Taxa da COE: ${Math.round(CONFIG.COMISSAO*100)}% ${CONFIG.TAXA_PAGA_POR==='cliente'?'paga pelo cliente':'por diária'}</li>
      </ul>
    </div>
  </section>
  <footer class="foot"><div class="wrap row between wrapx"><span class="row" style="gap:8px">${SIMBOLO(26,true)} COE Serviços, começando pelo Vale do Itajaí (SC)</span><span class="row wrapx" style="gap:18px"><a href="#como-funciona">Como funciona</a><a href="#categorias">Categorias</a><a href="#profissionais">Para profissionais</a></span></div></footer>`;
};

V.categorias = () => `<div class="wrap">
  <header class="pagehead"><h1>Categorias</h1><p class="muted">Cada profissional escolhe uma profissão principal e marca os serviços que faz.</p></header>
  <div class="stack" style="--g:32px">${AREAS.map(a => `<section aria-labelledby="ar-${a.id}"><h2 id="ar-${a.id}" style="margin-bottom:14px">${a.nome}</h2>
    <div class="grid-cards">${a.profissoes.map(p => { const n = PROS.filter(x => x.status==='ativo' && (x.prof===p.id || x.outras.includes(p.id))).length;
      return p.ativo
        ? `<a class="card row start" href="#busca-${p.id}" style="text-decoration:none"><span class="cat-ic">${ic(p.ic,26)}</span><span class="stack" style="--g:6px"><b class="disp" style="font-size:1.3rem">${p.nome}</b><span class="muted small">${n} ${n===1?'profissional':'profissionais'}</span><span class="small">${p.servicos.slice(0,4).join(' · ')}</span></span></a>`
        : `<div class="card-flat row start"><span class="cat-ic breve">${ic(p.ic,26)}</span><span class="stack" style="--g:6px"><b class="disp" style="font-size:1.3rem">${p.nome}</b><span class="muted small">Em breve</span></span></div>`; }).join('')}
  </div></section>`).join('')}</div></div>`;

V.comoFunciona = () => `<div class="wrap"><div class="stack" style="--g:30px;max-width:760px">
  <header class="pagehead"><h1>Como funciona a COE</h1><p class="muted" style="max-width:60ch">Regras simples para o cliente pagar com segurança e o profissional receber com garantia.</p></header>
  <ol class="tl">
    <li><span class="dot azul">${ic('search',18)}</span><div class="tl-body"><h3>Encontre o profissional</h3><p class="muted">Filtre por serviço, cidade, dia livre, valor e experiência. Converse pelo chat antes de contratar.</p></div></li>
    <li><span class="dot azul">${ic('lock',18)}</span><div class="tl-body"><h3>Pague as diárias antes</h3><p class="muted">Pix ou cartão. O dinheiro fica guardado com a COE. O telefone do profissional aparece assim que o pagamento é confirmado.</p></div></li>
    <li><span class="dot warn">${ic('camera',18)}</span><div class="tl-body"><h3>No dia do serviço</h3><p class="muted">O profissional avisa quando chega. No fim do dia, manda uma foto do que foi feito.</p></div></li>
    <li><span class="dot telha">${ic('hand',18)}</span><div class="tl-body"><h3>Você aprova ou reclama</h3><p class="muted">Aprovou: o valor vai para o profissional. Não respondeu em ${CONFIG.AUTO_LIBERA_HORAS} horas: é liberado sozinho. Reclamou: só aquele dia fica travado e a equipe decide em até ${CONFIG.PRAZO_DISPUTA_HORAS} horas.</p></div></li>
  </ol>
  <section class="stack" style="--g:12px"><h2>Regras importantes</h2>
    <div class="note note-azul">${ic('eyeoff')}<span><b>Contato só depois de contratar.</b> Antes disso, mensagens com telefone, e-mail ou redes sociais não são entregues. Depois de ${CONFIG.TENTATIVAS_ANTES_ANALISE} tentativas, a conta vai para análise.</span></div>
    <div class="note note-azul">${ic('law')}<span><b>Diaristas:</b> pela LC 150/2015, o mesmo cliente pode contratar a mesma diarista por no máximo ${CONFIG.LIMITE_DOMESTICO_SEMANA} dias por semana.</span></div>
    <div class="note note-azul">${ic('bolt')}<span><b>Eletricistas:</b> quem tem curso de NR-10 mostra o selo no perfil depois que a COE confere o certificado.</span></div>
    <div class="note note-azul">${ic('money')}<span><b>Taxa:</b> ${Math.round(CONFIG.COMISSAO*100)}% sobre cada diária, ${CONFIG.TAXA_PAGA_POR==='cliente'?'somada no pagamento do cliente':'descontada do repasse ao profissional'}.</span></div>
  </section>
  <section class="stack" style="--g:10px"><h2>Perguntas frequentes</h2>
    ${[['E se o profissional não aparecer?','Reclame da diária no app. O valor daquele dia fica travado e é devolvido se a falta for confirmada.'],
       ['Posso pagar direto ao profissional?','Pode, mas perde a proteção: sem pagamento pelo app não há reclamação, avaliação nem garantia.'],
       ['Quem compra o material?','O cliente. O perfil mostra se o profissional leva as próprias ferramentas.'],
       ['Dá para contratar meia diária?','Sim, com quem marcar essa opção no perfil. Meia diária vale até 4 horas.']]
      .map(([q,a]) => `<details class="card"><summary class="b" style="cursor:pointer">${q}</summary><p class="muted" style="margin-top:8px">${a}</p></details>`).join('')}
  </section>
</div></div>`;

/* ---------- Busca ---------- */
function filtrar(){
  const f = S.filtros, t = S.busca.texto.trim().toLowerCase();
  const l = PROS.filter(p => p.status==='ativo'
    && (!f.prof || p.prof===f.prof || p.outras.includes(f.prof))
    && f.servicos.every(s => p.servicos.includes(s))
    && (!f.cidade || p.cidades.includes(f.cidade))
    && (!f.dia || disponib(p,f.dia)==='livre')
    && p.valor<=f.precoMax && p.anos>=f.expMin
    && (!f.ferramentas || p.ferramentas==='sim')
    && (!f.meia || p.meia)
    && (!t || [p.nome, profissao(p.prof).nome, ...p.servicos].join(' ').toLowerCase().includes(t)));
  const livreEm = p => { for (let i=1;i<=30;i++) if (disponib(p,addDays(CONFIG.HOJE,i))==='livre') return i; return 99; };
  const ord = {recomendados:(a,b) => livreEm(a)-livreEm(b) || b.anos-a.anos, preco:(a,b) => a.valor-b.valor, experiencia:(a,b) => b.anos-a.anos, nota:(a,b) => b.hist.nota-a.hist.nota}[f.ordem] || (() => 0);
  return l.sort(ord);
}
function filtrosHTML(px){
  const f = S.filtros, pf = f.prof ? profissao(f.prof) : null;
  const radio = (name,k,val,label) => `<label class="opt"><input type="radio" name="${px}-${name}" id="${px}-${name}-${String(val).replace(/[^\w]/g,'')||'x'}" value="${val}" data-chg="filtro" data-k="${k}" ${String(f[k])===String(val)?'checked':''}>${label}</label>`;
  const dias = Array.from({length:7},(_,i) => addDays(CONFIG.HOJE,i+1));
  return `
    ${px==='fm' ? `<fieldset><legend>Serviço</legend><div class="opts">${radio('prof','prof','','Todos')}${profsAtivas().map(p => radio('prof','prof',p.id,p.nome)).join('')}</div></fieldset>` : ''}
    ${pf ? `<fieldset><legend>O que precisa ser feito</legend><div class="opts">${pf.servicos.map((s,i) => `<label class="opt"><input type="checkbox" id="${px}-srv-${i}" value="${esc(s)}" data-chg="filtro-srv" ${f.servicos.includes(s)?'checked':''}><span class="ic-ck">${ic('check',16)}</span>${esc(s)}</label>`).join('')}</div></fieldset>` : ''}
    <div class="field"><label for="${px}-cidade">Cidade do serviço</label><select id="${px}-cidade" data-chg="filtro" data-k="cidade">${optsCidades(f.cidade)}</select></div>
    <fieldset><legend>Livre no dia</legend><div class="opts">${radio('dia','dia','','Qualquer dia')}${dias.map(d => radio('dia','dia',d,fmtDia(d))).join('')}</div></fieldset>
    <div class="field"><label for="${px}-preco">Diária até <output id="${px}-preco-out" class="num">${brl(f.precoMax)}</output></label><input type="range" id="${px}-preco" min="150" max="400" step="10" value="${f.precoMax}" data-chg="filtro" data-k="precoMax" data-inp="preco" data-out="${px}-preco-out"></div>
    <fieldset><legend>Experiência</legend><div class="opts">${radio('exp','expMin',0,'Qualquer')}${radio('exp','expMin',5,'5 anos ou mais')}${radio('exp','expMin',10,'10 anos ou mais')}</div></fieldset>
    <fieldset><legend>Condições</legend>
      <label class="switch" for="${px}-ferr"><span>Leva as próprias ferramentas</span><input type="checkbox" id="${px}-ferr" data-chg="filtro-bool" data-k="ferramentas" ${f.ferramentas?'checked':''}></label>
      <label class="switch" for="${px}-meia"><span>Aceita meia diária</span><input type="checkbox" id="${px}-meia" data-chg="filtro-bool" data-k="meia" ${f.meia?'checked':''}></label>
    </fieldset>
    <button type="button" class="linkbtn" data-act="limpar-filtros" style="justify-self:start">Limpar filtros</button>`;
}
function chipsAtivos(){
  const f = S.filtros, c = [];
  f.servicos.forEach(s => c.push(['servico',s,s]));
  if (f.cidade) c.push(['cidade','',f.cidade]);
  if (f.dia) c.push(['dia','','Livre ' + fmtDia(f.dia)]);
  if (f.precoMax<400) c.push(['precoMax','','Até ' + brl(f.precoMax)]);
  if (f.expMin) c.push(['expMin','',`${f.expMin}+ anos`]);
  if (f.ferramentas) c.push(['ferramentas','','Leva ferramentas']);
  if (f.meia) c.push(['meia','','Meia diária']);
  return c;
}
function dlgFiltros(){
  const n = filtrar().length;
  return `<div class="dlg" style="padding-bottom:0">${dlgHead('Filtros')}<div class="stack" style="--g:22px">${filtrosHTML('fm')}</div></div>
    <div class="folha-foot"><button type="button" class="btn btn-linha" data-act="limpar-filtros">Limpar</button><button type="button" class="btn btn-telha" data-act="fechar">Ver ${n} ${n===1?'profissional':'profissionais'}</button></div>`;
}
V.busca = () => {
  const f = S.filtros, lista = filtrar(), chips = chipsAtivos();
  const titulo = f.prof ? profissao(f.prof).plural : 'Profissionais';
  const vazio = `<div class="card empty">${ic('search',40)}<h2>${temHist()?'Ninguém com esses filtros':'Ainda não temos ninguém com esses filtros'}</h2>
    <p class="muted" style="max-width:46ch">${temHist()?'Tente outra cidade, outro dia ou aumente o valor máximo.':'Novos profissionais entram toda semana. Tire alguns filtros ou peça para ser avisado.'}</p>
    <div class="row wrapx" style="justify-content:center"><button type="button" class="btn btn-azul" data-act="limpar-filtros">Limpar filtros</button>${temHist()?'':`<button type="button" class="btn btn-linha" data-act="avisar">${ic('bell',18)}Me avise</button>`}</div></div>`;
  return `<div class="wrap">
    <header class="pagehead"><h1>${titulo}${f.cidade?' em '+esc(f.cidade):''}</h1></header>
    <div class="catbar" role="group" aria-label="Serviço">
      <button type="button" data-act="cat-filtro" data-p="" aria-pressed="${!f.prof}">Todos</button>
      ${profsAtivas().map(p => `<button type="button" data-act="cat-filtro" data-p="${p.id}" aria-pressed="${f.prof===p.id}">${ic(p.ic,20)}${p.nome}</button>`).join('')}
    </div>
    <div class="busca-top">
      <div class="field"><label for="b-texto">Buscar por nome ou serviço</label><input id="b-texto" type="search" value="${esc(S.busca.texto)}" placeholder="Ex.: contrapiso, chuveiro, poda" data-inp="busca-texto"></div>
      <div class="field"><label for="b-ordem">Mostrar primeiro</label><select id="b-ordem" data-chg="filtro" data-k="ordem">
        <option value="recomendados" ${f.ordem==='recomendados'?'selected':''}>Quem está livre antes</option><option value="preco" ${f.ordem==='preco'?'selected':''}>Menor preço</option><option value="experiencia" ${f.ordem==='experiencia'?'selected':''}>Mais experiência</option>${temHist()?`<option value="nota" ${f.ordem==='nota'?'selected':''}>Melhor avaliação</option>`:''}</select></div>
      <button type="button" class="btn btn-linha so-mobile" data-act="abrir-filtros" style="align-self:end">${ic('filter')}Filtros${chips.length?` (${chips.length})`:''}</button>
    </div>
    <div class="busca-layout">
      <aside class="filtros so-desk" aria-label="Filtros">${filtrosHTML('fd')}</aside>
      <div>
        <div class="ativos"><p class="muted" aria-live="polite" style="margin-right:6px"><b style="color:var(--ink)">${lista.length}</b> ${lista.length===1?'profissional':'profissionais'}</p>
          ${chips.map(([k,v,t]) => `<button type="button" class="chip-x" data-act="rm-filtro" data-k="${k}" data-v="${esc(v)}" aria-label="Remover filtro ${esc(t)}">${esc(t)}${ic('x',16)}</button>`).join('')}</div>
        ${!temHist() && lista.length ? `<div class="note note-azul" style="margin-bottom:14px">${ic('info')}<span class="small">A COE está começando, então ninguém tem avaliação ainda. Todos passaram pela conferência de documento e selfie.</span></div>` : ''}
        ${lista.length ? `<div class="res">${lista.map(proCard).join('')}</div>` : vazio}
      </div>
    </div></div>`;
};

/* ---------- Perfil do profissional ---------- */
V.perfil = id => {
  const p = pro(id); if (!p) return V.naoEncontrado();
  const pf = profissao(p.prof), dom = ehDomestico(p.prof), rev = REV[areaDe(p.prof)];
  return `<section class="perfil-top"><div class="wrap">
    <a class="back" href="#busca">${ic('back',18)}Voltar para a busca</a>
    <div class="perfil-head">${avatar(p,'lg')}<div class="stack" style="--g:8px"><h1>${esc(p.nome)}</h1>
      <p class="muted" style="font-size:1.1rem;margin-top:4px">${pf.nome}${p.outras.length?` e ${p.outras.map(o => profissao(o).nome.toLowerCase()).join(', ')}`:''}</p>
      <div class="meta">${verificado()}${p.nr10?`<span class="selo">${ic('bolt',17)}NR-10 conferido</span>`:''}${reputacao(p)}</div></div></div>
  </div></section>
  <div class="wrap"><div class="perfil-layout">
    <div class="stack" style="--g:34px">
      <div class="fatos-perfil">
        <div><span>Experiência</span><b>${p.anos>=1?p.anos+' anos':'Menos de 1 ano'}</b></div>
        <div><span>Horário</span><b>${p.horario}</b></div>
        <div><span>Ferramentas</span><b>${p.ferramentas==='sim'?'Leva as suas':p.ferramentas==='parte'?'Leva parte':'Cliente fornece'}</b></div>
        <div><span>Na COE desde</span><b>${p.desde}</b></div>
      </div>
      <section class="stack" style="--g:12px" aria-labelledby="h-gal"><h2 id="h-gal">Trabalhos feitos</h2><div class="galeria">${p.galeria.map((g,i) => foto(p,i)).join('')}</div></section>
      <section class="stack" style="--g:10px" aria-labelledby="h-sobre"><h2 id="h-sobre">Sobre</h2><p style="max-width:62ch;font-size:1.08rem">${esc(p.bio)}</p>${p.mei?`<p class="small muted row" style="gap:6px">${ic('idcard',16)}Trabalha como MEI e pode emitir nota fiscal</p>`:''}</section>
      <section class="stack" style="--g:10px" aria-labelledby="h-srv"><h2 id="h-srv">O que faz</h2><ul class="servicos-lista">${p.servicos.map(s => `<li>${ic('check',20)}${esc(s)}</li>`).join('')}</ul></section>
      <section class="stack" style="--g:10px" aria-labelledby="h-bai"><h2 id="h-bai">Onde atende</h2><p>Mora em <b>${p.cidade}</b> e vai até <b>${p.raio} km</b>.</p><div class="chips">${p.cidades.map(b => `<span class="chip">${ic('pin',14)}${b}</span>`).join('')}</div></section>
      <section class="stack" style="--g:12px" aria-labelledby="h-disp"><h2 id="h-disp">Dias livres</h2>${calendario(p)}</section>
      <section class="stack" style="--g:6px" aria-labelledby="h-aval"><h2 id="h-aval" style="margin-bottom:8px">Avaliações</h2>
        ${temHist() && p.hist.nAval>0 ? rev.map(r => `<article class="review"><div class="row between wrapx"><b>${r[0]} <span class="muted small">· ${r[1]}</span></b>${estrelas(r[2])}</div><p>${r[3]}</p><p class="small muted">${r[4]}</p></article>`).join('')
          : `<div class="card-flat stack" style="--g:6px"><b>${primeiro(p.nome)} ainda não tem avaliações</b><p class="muted small">Toda avaliação na COE vem de uma diária paga pelo app. Contrate e seja o primeiro a avaliar.</p></div>`}</section>
    </div>
    <aside class="card caixa-preco" aria-label="Contratar">
      <div><p class="label-sec">Valor da diária</p><p class="valor">${brl(p.valor)}</p>
        ${p.meia?`<p>Meia diária (até 4 h): <b>${brl(p.meia)}</b></p>`:''}
        <p class="small muted">${CONFIG.TAXA_PAGA_POR==='cliente'?`+ taxa de ${Math.round(CONFIG.COMISSAO*100)}% no pagamento`:'Taxa já incluída'}</p></div>
      <p class="livre">${ic('cal',18)}${proxLivre(p)}</p>
      <a class="btn btn-telha btn-lg btn-block" href="#contratar-${p.id}">Contratar diárias</a>
      <button type="button" class="btn btn-linha btn-block" data-act="tirar-duvida" data-p="${p.id}">${ic('chat',18)}Tirar dúvida pelo chat</button>
      <div class="oculto">${ic('eyeoff')}<div><span class="mask">(47) 9••••-••••</span><br><span class="muted small">Telefone e WhatsApp aparecem depois de contratar</span></div></div>
      <div class="note note-ok">${ic('lock')}<span class="small">Você paga antes, mas o dinheiro só vai para ${primeiro(p.nome)} depois que você aprova cada dia.</span></div>
      ${dom?`<div class="note note-azul">${ic('law')}<span class="small">No máximo ${CONFIG.LIMITE_DOMESTICO_SEMANA} dias por semana com a mesma diarista (LC 150/2015). Plano semanal ou quinzenal com substituição garantida.</span></div>`:''}
    </aside>
  </div></div>`;
};

/* ---------- Entrar (um login só para quem contrata e para quem trabalha) ---------- */
V.entrar = (gate=false) => `<div class="wrap"><div class="wizard stack" style="--g:18px;max-width:480px">
    ${gate?`<div class="note note-azul">${ic('lock')}<span>Para contratar, entre na sua conta ou crie uma. É grátis e leva 1 minuto.</span></div>`:''}
    <header class="stack" style="--g:6px"><h1>Entrar</h1><p class="muted">O mesmo login serve para quem contrata e para quem trabalha.</p></header>
    <form class="card stack" style="--g:16px" data-form="auth" novalidate>
      <div class="field"><label for="au-login">Celular</label><input id="au-login" type="tel" inputmode="tel" autocomplete="username" placeholder="(47) 9 0000-0000" value="(47) 99655-3021"></div>
      <div class="field"><label for="au-senha">Senha</label><input id="au-senha" type="password" autocomplete="current-password" value="prototipo"></div>
      <button class="btn btn-telha btn-lg btn-block" type="submit">Entrar</button>
      <button type="button" class="btn btn-linha btn-block" data-act="toast" data-msg="Código enviado por SMS (simulado).">${ic('phone',18)}Entrar com código por SMS</button>
      <button type="button" class="linkbtn" data-act="toast" data-msg="Enviamos um link para criar uma nova senha (simulado)." style="justify-self:center">Esqueci minha senha</button>
    </form>
    <div class="card row between wrapx"><span><b>Ainda não tem conta?</b><br><span class="muted small">Para contratar ou para trabalhar.</span></span><a class="btn btn-azul" href="#criar-conta${gate?'-cliente':''}">Criar conta</a></div>
    <div class="card-flat stack" style="--g:10px"><span class="label-sec">Atalho do protótipo</span><div class="row wrapx"><button type="button" class="btn btn-linha btn-sm" data-act="demo-login" data-r="cliente">Entrar como Juliana (cliente)</button><button type="button" class="btn btn-linha btn-sm" data-act="demo-login" data-r="profissional">Entrar como Valdir (pedreiro)</button></div></div>
  </div></div>`;

/* ---------- Criar conta: primeiro a pessoa diz se quer contratar ou trabalhar ---------- */
V.criarConta = arg => {
  if (arg==='cliente') return `<div class="wrap"><div class="wizard stack" style="--g:18px;max-width:480px">
    <a class="back" href="#criar-conta">${ic('back',18)}Voltar</a>
    <header class="stack" style="--g:6px"><p class="eyebrow">Conta de cliente</p><h1>Criar conta para contratar</h1><p class="muted">Só o básico. O endereço completo você informa na hora de contratar.</p></header>
    <form class="card stack" style="--g:16px" data-form="criar-cliente" novalidate>
      <div class="field"><label for="au-nome">Seu nome</label><input id="au-nome" autocomplete="name"></div>
      <div class="field"><label for="au-cel">Celular</label><input id="au-cel" type="tel" inputmode="tel" autocomplete="tel" placeholder="(47) 9 0000-0000"><p class="hint">Enviamos um código por SMS para confirmar.</p></div>
      <div class="field"><label for="au-cep">Seu CEP</label><input id="au-cep" inputmode="numeric" autocomplete="postal-code" placeholder="00000-000"><p class="hint">Usamos para mostrar quem atende perto de você.</p></div>
      <div class="field"><label for="au-senha2">Crie uma senha</label><input id="au-senha2" type="password" autocomplete="new-password" aria-describedby="au-senha2-hint"><p class="hint" id="au-senha2-hint">Pelo menos 6 letras ou números.</p></div>
      <label class="row start small" for="au-termos"><input type="checkbox" class="cb" id="au-termos"><span>Li e aceito os termos de uso e a política de privacidade, inclusive a regra de só trocar contato depois de contratar.</span></label>
      <p class="err" id="au-err" hidden></p>
      <button class="btn btn-telha btn-lg btn-block" type="submit">Criar conta</button>
    </form>
    <p class="small muted" style="text-align:center">Quer oferecer seus serviços? <a href="#profissionais">Cadastro de profissional</a></p>
  </div></div>`;
  return `<div class="wrap"><div class="stack" style="--g:20px;max-width:760px;margin-inline:auto">
    <header class="stack" style="--g:6px"><h1>Criar conta</h1><p class="muted" style="font-size:1.1rem">Como você vai usar a COE?</p></header>
    <div class="escolha">
      <a class="escolha-op" href="#criar-conta-cliente">
        <span class="escolha-img svgfit">${cena('pedreiro','Muro e calçada',1)}</span>
        <span class="escolha-txt"><b>Quero contratar</b><span>Encontrar e pagar pedreiro, pintor, eletricista, jardineiro ou diarista.</span><span class="escolha-cta">Criar conta de cliente ${ic('next',18)}</span></span>
      </a>
      <a class="escolha-op escolha-pro" href="#profissionais">
        <span class="escolha-img svgfit">${cena('pintor','Pintura interna',2)}</span>
        <span class="escolha-txt"><b>Quero trabalhar</b><span>Oferecer meus serviços e receber pelo app. Cadastro grátis.</span><span class="escolha-cta">Cadastro de profissional ${ic('next',18)}</span></span>
      </a>
    </div>
    <p class="muted" style="text-align:center">Já tem conta? <a href="#entrar">Entrar</a></p>
  </div></div>`;
};

/* ---------- Página para profissionais (antes do cadastro) ---------- */
V.profissionais = () => {
  const ex = 280, cc = calc(ex);
  return `<section class="cta-pro"><div class="viga-clara"></div><div class="wrap cta-pro-in">
      <div class="stack" style="--g:14px">
                <h1 style="font-size:clamp(2.6rem,8vw,4.2rem)">Trabalhe com a COE e receba garantido</h1>
        <p style="max-width:46ch;font-size:1.1rem">O cliente paga a diária antes de você sair de casa. Você termina o dia, manda uma foto e o dinheiro cai no seu Pix.</p>
        <div class="row wrapx"><a class="btn btn-azul btn-lg" href="#cadastro">Começar meu cadastro</a><a class="btn btn-linha btn-lg" href="#entrar">Já tenho cadastro</a></div>
      </div>
      <ul>
        ${profsAtivas().map(p => `<li>${ic(p.ic)}${p.nome}</li>`).join('')}
      </ul>
    </div></section>
  <section class="sec"><div class="wrap trena-wrap">
    <h2>Como funciona para você</h2>
    <div class="trena" aria-hidden="true"><span style="left:2%">1</span><span style="left:26%">2</span><span style="left:51%">3</span><span style="left:76%">4</span></div>
    <ol class="passos passos-4">
      <li><span class="pn" aria-hidden="true">1</span><h3>Cadastre-se grátis</h3><p class="muted">Pelo celular, uma pergunta de cada vez. Leva uns 10 minutos.</p></li>
      <li><span class="pn" aria-hidden="true">2</span><h3>A COE confere seus dados</h3><p class="muted">Documento e selfie em até 24 horas. Depois seu perfil aparece na busca.</p></li>
      <li><span class="pn" aria-hidden="true">3</span><h3>O cliente paga antes</h3><p class="muted">Você só vai para a obra com a diária já paga e guardada.</p></li>
      <li><span class="pn" aria-hidden="true">4</span><h3>Termine o dia e receba</h3><p class="muted">Mande a foto do serviço. O cliente aprova e o dinheiro cai no seu Pix.</p></li>
    </ol>
  </div></section>
  <section class="sec sec-branca"><div class="wrap split">
    <div class="stack" style="--g:14px"><h2>Tenha em mãos</h2>
      <ul class="servicos-lista" style="grid-template-columns:1fr">
        <li>${ic('phone',20)}Celular com câmera</li><li>${ic('idcard',20)}RG ou CNH</li><li>${ic('image',20)}Fotos de trabalhos que você já fez</li><li>${ic('wallet',20)}Uma chave Pix no seu nome</li>
      </ul></div>
    <div class="stack" style="--g:14px"><h2>Quanto você recebe</h2>
      <div class="fatos" style="grid-template-columns:1fr">
        <div><b class="fn">${brl(cc.recebe)}</b><p>É o que cai no seu Pix numa diária de ${brl(ex)}. ${CONFIG.TAXA_PAGA_POR==='cliente'?`A taxa da COE (${Math.round(CONFIG.COMISSAO*100)}%) é paga pelo cliente.`:''}</p></div>
        <div><b class="fn">${CONFIG.AUTO_LIBERA_HORAS} h</b><p>Se o cliente não responder, o dinheiro é liberado para você mesmo assim.</p></div>
      </div></div>
  </div></section>
  <section class="sec"><div class="wrap stack" style="--g:10px;max-width:820px">
    <h2 style="margin-bottom:6px">Dúvidas de quem está começando</h2>
    ${[['Preciso pagar alguma coisa?','Não. O cadastro é grátis e não tem mensalidade.'],
       ['Quando o dinheiro cai?','Quando o cliente aprova o dia. Se ele não responder em '+CONFIG.AUTO_LIBERA_HORAS+' horas, cai do mesmo jeito.'],
       ['E se o cliente reclamar?','Só aquele dia fica parado. A equipe COE olha suas fotos e a conversa e decide em até '+CONFIG.PRAZO_DISPUTA_HORAS+' horas.'],
       ['Posso passar meu telefone para o cliente?','O telefone aparece sozinho depois que o cliente paga. Antes disso, a conversa é pelo chat do app.'],
       ['Preciso ser MEI?','Não é obrigatório. Se for, o cliente vê no seu perfil que você emite nota.']]
      .map(([q,a]) => `<details class="card"><summary class="b" style="cursor:pointer">${q}</summary><p class="muted" style="margin-top:8px">${a}</p></details>`).join('')}
    <a class="btn btn-telha btn-xl" href="#cadastro" style="margin-top:14px">Começar meu cadastro</a>
  </div></section>`;
};
V.naoEncontrado = () => `<div class="wrap"><div class="card empty">${ic('search',40)}<h1>Página não encontrada</h1><a class="btn btn-telha" href="#inicio">Ir para o início</a></div></div>`;
