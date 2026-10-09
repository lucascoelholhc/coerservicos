/* =========================================================================
   8. ROTEADOR E MOLDURA (cabeçalho, menus)
   Rotas por hash: #busca, #busca-pedreiro, #perfil-p1, #contrato-c1,
   #pro-agenda, #admin-verif-v1 …
   ========================================================================= */
const NAV = {
  visitante:[['inicio','Início','home'],['busca','Buscar','search'],['entrar','Entrar','user']],
  cliente:[['inicio','Início','home'],['busca','Buscar','search'],['cliente','Diárias','cal'],['mensagens','Mensagens','chat']],
  profissional:[['pro-hoje','Hoje','home'],['pro-agenda','Agenda','cal'],['pro-dinheiro','Dinheiro','wallet'],['pro-conversas','Conversas','chat'],['pro-perfil','Perfil','user']],
  admin:[['admin-geral','Painel','chart'],['admin-verificacoes','Verificar','idcard'],['admin-disputas','Disputas','alert'],['admin-moderacao','Moderação','eyeoff']],
};
const HOME = {visitante:'inicio', cliente:'cliente', profissional:'pro-hoje', admin:'admin-geral'};
const ROTAS = {inicio:V.inicio, categorias:V.categorias, comofunciona:V.comoFunciona, busca:V.busca, perfil:V.perfil, contratar:V.contratar,
  entrar:() => V.entrar(!!S.redirect), criarconta:V.criarConta, profissionais:V.profissionais, cliente:V.cliente, contrato:V.contrato, mensagens:V.mensagens, conversa:V.conversa, conta:V.conta,
  cadastro:V.cadastro, pro:V.pro, admin:V.admin};

function rota(){
  const h = location.hash.slice(1) || 'inicio';
  if (h==='como-funciona') return {h, v:'comofunciona', arg:''};
  if (h.startsWith('criar-conta')) return {h, v:'criarconta', arg:h.slice(12)};
  const [v, ...r] = h.split('-'); return {h, v, arg:r.join('-')};
}
function go(h){ if (location.hash==='#'+h) draw(true); else location.hash = h; }
function trocarPapel(r){ S.role = r; S.perfilEdit = null; go(HOME[r]); }

function badge(k){
  let n = 0;
  if (k==='cliente') n = meusContratos().flatMap(c => c.dias).filter(d => d.status==='aguardando').length;
  if (k==='pro-hoje') n = diariasDoPro(S.proLogadoId).filter(x => x.d.status==='andamento').length;
  if (k==='pro-conversas') n = S.conversas.filter(m => m.proId===S.proLogadoId).length;
  if (k==='admin-verificacoes') n = S.verificacoes.length;
  if (k==='admin-disputas') n = disputasAbertas().length;
  if (k==='admin-moderacao') n = moderacaoPendente().length;
  return n ? `<span class="badge">${n}<span class="sr-only"> pendente(s)</span></span>` : '';
}
function moldura(){
  const {h, v} = rota(), itens = NAV[S.role];
  const ativo = (itens.find(([k]) => k===h) || itens.find(([k]) => k===v || (k==='cliente' && v==='contrato') || (k==='mensagens' && v==='conversa') || (k==='entrar' && v==='criarconta')
    || (k==='pro-conversas' && /^pro-conversa-/.test(h)) || (k==='pro-agenda' && /^pro-contrato/.test(h)) || (k==='pro-hoje' && /^pro-terminar|^pro$/.test(h)) || (k==='admin-verificacoes' && /^admin-verif-/.test(h))) || [])[0];
  $('#bnav').innerHTML = itens.map(([k,t,i]) => `<li><a href="#${k}" ${k===ativo?'aria-current="page"':''}>${ic(i,24)}${t}${badge(k)}</a></li>`).join('');
  $('#topnav').innerHTML = itens.filter(([k]) => k!=='entrar').map(([k,t,i]) => `<a href="#${k}" ${k===ativo?'aria-current="page"':''}>${ic(i,18)}${t}${badge(k)}</a>`).join('')
    + (['visitante','cliente'].includes(S.role) ? `<a href="#como-funciona" ${h==='como-funciona'?'aria-current="page"':''}>${ic('help',18)}Como funciona</a>` : '')
    + (S.role==='visitante' ? `<a href="#profissionais" ${['profissionais','cadastro'].includes(v)?'aria-current="page"':''}>${ic('hand',18)}Para profissionais</a>` : '');
  const nomes = {cliente:S.cliente.exibir, profissional:primeiro(pro(S.proLogadoId).nome), admin:'Equipe COE'};
  $('#topright').innerHTML = S.role==='visitante'
    ? `<a class="btn btn-linha btn-sm" href="#entrar">Entrar</a><a class="btn btn-telha btn-sm" href="#criar-conta">Criar conta</a>`
    : `<a class="who" href="#${S.role==='cliente'?'conta':S.role==='profissional'?'pro-perfil':'admin-config'}">${ic('user',18)}<span>${esc(nomes[S.role])}</span></a><button type="button" class="iconbtn" data-act="sair" aria-label="Sair da conta">${ic('logout',18)}</button>`;
  $('#logo-link').innerHTML = logoHTML();
  $('#proto-role').value = S.role;
  $('#proto-cenario').value = S.cenario;
}

function draw(nav=false){
  const {v, arg} = rota();
  const fn = ROTAS[v] || V.naoEncontrado;
  $('#app').innerHTML = fn(arg);
  moldura();
  document.body.classList.toggle('modo-pro', S.role==='profissional');
  const {h} = rota();
  if (v==='contrato' || v==='conversa') rolarChat(arg);
  if (v==='pro' && /^(conversa|contrato)-/.test(arg)) rolarChat(arg.split('-').slice(1).join('-'));
  if (nav){ window.scrollTo(0,0); const h1 = $('#app h1'); if (h1){ h1.tabIndex = -1; h1.focus({preventScroll:true}); } }
}
function refresh(){
  const a = document.activeElement, id = a && a.id, y = window.scrollY;
  const sel = a && typeof a.selectionStart==='number' ? [a.selectionStart, a.selectionEnd] : null;
  const dlgEl = $('#dlg');
  draw(false);
  if (dlgEl.open && dlgEl.dataset.kind==='filtros') dlgEl.innerHTML = dlgFiltros();
  window.scrollTo(0,y);
  if (id){ const el = document.getElementById(id); if (el){ el.focus({preventScroll:true}); if (sel && el.setSelectionRange) try { el.setSelectionRange(sel[0], sel[1]); } catch(e){} } }
}
window.addEventListener('hashchange', () => {
  const {v, arg} = rota();
  if (v==='busca' && arg){ S.filtros = FILTROS_PADRAO(); S.filtros.prof = arg; S.busca.texto = ''; }
  if (v!=='contrato') S.paneContrato = 'diarias';
  if (v!=='conversa' && v!=='pro' && v!=='contrato') S.avisoChat = {};
  if (v==='pro' && !arg.startsWith('perfil')) S.perfilEdit = null;
  fechar();
  draw(true);
});

let pixSeg = 30*60;
setInterval(() => { const t = document.getElementById('pix-timer'); if (!t){ pixSeg = 30*60; return; } pixSeg = Math.max(0,pixSeg-1); t.textContent = `${pad(Math.floor(pixSeg/60))}:${pad(pixSeg%60)}`; }, 1000);

draw(false);
