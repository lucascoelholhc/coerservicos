/* =========================================================================
   7. AÇÕES — cliques, envios e mudanças. No front real, cada ação que
   altera dados vira uma chamada de API (indicada nos comentários).
   ========================================================================= */
function toast(msg){ const t = $('#toast'); t.innerHTML = ic('check',18)+esc(msg); t.classList.add('show'); clearTimeout(toast._t); toast._t = setTimeout(() => t.classList.remove('show'), 3400); }
function dlg(html, cls='', kind=''){ const d = $('#dlg'); d.className = cls; d.dataset.kind = kind; d.innerHTML = html; if (!d.open) d.showModal(); const f = d.querySelector('[autofocus]') || d.querySelector('input,textarea,select,button:not(.iconbtn)'); f && f.focus(); }
function fechar(){ const d = $('#dlg'); if (d.open) d.close(); d.dataset.kind = ''; }
const achar = (cid, data) => { const c = S.contratos.find(x => x.id===cid); return {c, d: c && c.dias.find(x => x.data===data)}; };
const profRef = id => { for (const a of AREAS) { const p = a.profissoes.find(x => x.id===id); if (p) return p; } return null; };
const rolarChat = id => { const ch = document.getElementById('chat-'+id); if (ch) ch.scrollTop = ch.scrollHeight; };

function copiarTexto(txt, el){
  try { navigator.clipboard.writeText(txt).then(() => toast('Copiado'), () => { if (el){ el.select(); toast('Selecionado. Use copiar do teclado.'); } }); }
  catch(e){ if (el) el.select(); }
}

function confirmarPagamento(){ // API: POST /contratos (após webhook do Pix/cartão)
  const c = S.ct, p = pro(c.proId), id = 'c'+(S.contratos.length+1);
  const valor = c.periodo==='meia' && p.meia ? p.meia : p.valor;
  const casa = S.cliente.enderecos[0];
  const end = c.endSel==='novo' && c.cepInfo ? {linha:`${c.cepInfo.rua}, ${c.numero}${c.compl?' '+c.compl:''} · ${c.cepInfo.bairro}`, bairro:c.cepInfo.bairro, cidade:c.cepInfo.cidade} : {linha:casa.linha, bairro:casa.linha.split('· ')[1]||'', cidade:casa.cidade};
  const titulo = c.servicos.length ? c.servicos.join(', ') : (c.desc.trim().split(/[.\n]/)[0].slice(0,60) || 'Serviço combinado pelo chat');
  const fotos = S.upl.ct_fotos || [];
  S.contratos.unshift({id, cliente:S.cliente.nome, local:`${end.bairro}, ${end.cidade}`, endereco:`${end.linha} · ${end.cidade}`, proId:p.id, servico:titulo, modo:c.modo, valor, criado:stamp(),
    pedido:{servicos:[...c.servicos], desc:c.desc.trim(), material:c.material, fotos:[...fotos]},
    dias:c.dias.map((d,i) => ({data:d, status:(c.modo==='avulsa'||i===0)?'paga':'agendada'})),
    chat:[{de:'sis', txt:`Contratação confirmada. Telefone liberado: ${p.fone}.`}].concat(fotos.length?[{de:'sis', txt:`${primeiro(S.cliente.nome)} enviou ${fotos.length} ${fotos.length===1?'foto':'fotos'} do local.`}]:[])});
  S.upl.ct_fotos = [];
  c.novoId = id; c.etapa = 4; S.paneContrato = 'diarias'; draw(true);
}

const ACT = {
  skip(){ $('#app').focus(); },
  fechar,
  toast(ds){ toast(ds.msg); },
  avisar(){ toast('Pronto! Avisamos por SMS quando chegar um profissional com esse perfil.'); },
  'abrir-filtros'(){ dlg(dlgFiltros(), 'folha', 'filtros'); },
  'limpar-filtros'(){ S.filtros = FILTROS_PADRAO(); S.busca.texto = ''; refresh(); },
  'rm-filtro'(ds){
    const f = S.filtros, pd = FILTROS_PADRAO();
    if (ds.k==='servico') f.servicos = f.servicos.filter(s => s!==ds.v);
    else { f[ds.k] = pd[ds.k]; if (ds.k==='prof') f.servicos = []; }
    refresh();
  },
  'tirar-duvida'(ds){
    if (S.role!=='cliente'){ S.redirect = 'perfil-'+ds.p; go('entrar'); return; }
    let m = S.conversas.find(x => x.proId===ds.p && x.cliente===S.cliente.nome);
    if (!m){ m = {id:'m'+(S.conversas.length+1), cliente:S.cliente.nome, proId:ds.p, msgs:[]}; S.conversas.push(m); }
    go('conversa-'+m.id);
  },
  dia(ds){
    const c = S.ct, p = pro(c.proId), d = ds.d, dom = ehDomestico(p.prof), lim = CONFIG.LIMITE_DOMESTICO_SEMANA;
    c.aviso = '';
    if (c.modo!=='avulsa'){
      const step = c.modo==='semanal'?7:14, fim = addDays(weekKey(CONFIG.HOJE), CONFIG.SEMANAS_CAL*7-1);
      if (c.dias[0]===d){ c.dias = []; refresh(); return; }
      const lista = [], bloq = [];
      for (let x=d; x<=fim; x=addDays(x,step)){
        if (disponib(p,x)!=='livre') continue;
        if (dom && contarSemanaDomestico(p.id,x,lista) >= lim){ bloq.push(x); continue; }
        lista.push(x);
      }
      c.dias = lista;
      if (bloq.length) c.aviso = `${bloq.map(fmtDia).join(', ')} ficou de fora: você já tem ${lim} dias com ${primeiro(p.nome)} nessa semana.`;
      refresh(); return;
    }
    const i = c.dias.indexOf(d);
    if (i>=0){ c.dias.splice(i,1); refresh(); return; }
    if (dom && contarSemanaDomestico(p.id,d,c.dias) >= lim){
      c.aviso = `Não foi possível marcar ${fmtDia(d)}. Na semana de ${dm(weekKey(d))} você já tem ${lim} dias com ${primeiro(p.nome)}, que é o limite da lei para trabalho doméstico. Escolha um dia em outra semana.`;
      refresh(); return;
    }
    c.dias.push(d); c.dias.sort(); refresh();
  },
  'ct-etapa'(ds, el){
    if (el.getAttribute('aria-disabled')==='true'){ S.ct.aviso = 'Escolha pelo menos um dia para continuar.'; refresh(); return; }
    const c = S.ct, alvo = Number(ds.e);
    if (c.etapa===2 && alvo===3){ // valida o pedido antes do pagamento
      const err = {};
      if (censura(c.desc).tem) err.desc = 'Tire telefone, e-mail ou rede social do texto. O contato aparece depois do pagamento.';
      else if (!c.servicos.length && c.desc.trim().length<15) err.desc = 'Marque o que você precisa ou explique um pouco o serviço.';
      if (c.endSel==='novo' && (!c.cepInfo || !String(c.numero).trim())) err.end = !c.cepInfo ? 'Digite um CEP válido.' : 'Falta o número.';
      c.err = err;
      if (Object.keys(err).length){ refresh(); const f = document.querySelector(err.desc ? '#ct-desc' : '#ct-cep'); if (f) f.focus(); return; }
    }
    c.etapa = alvo; c.aviso = ''; c.erro = ''; draw(true);
  },
  'ct-fotos-exemplo'(){ S.upl.ct_fotos = [sampleImg('Parede da lavanderia','#8E8F8A'), sampleImg('Chão da garagem','#9A5A34')]; refresh(); },
  pag(ds){ S.ct.pag = ds.v; refresh(); },
  pagar(){ confirmarPagamento(); },
  copiar(ds){ const el = document.getElementById(ds.alvo); copiarTexto(el.value, el); },
  'copiar-txt'(ds){ copiarTexto(ds.txt); },
  'abrir-chat'(ds){ S.paneContrato = 'chat'; go('contrato-'+ds.c); },
  pane(ds){ S.paneContrato = ds.v; refresh(); },
  aprovar(ds){
    const {c,d} = achar(ds.c, ds.d), p = pro(c.proId);
    dlg(`<div class="dlg">${dlgHead('Aprovar o dia?')}<p>Ao aprovar ${fmtDia(d.data)}, <b>${brl(calc(c.valor).recebe)}</b> é liberado para ${esc(p.nome)}. Isso não pode ser desfeito.</p>
      <div class="dlg-foot"><button type="button" class="btn btn-linha" data-act="fechar">Cancelar</button><button type="button" class="btn btn-telha" data-act="aprovar-ok" data-c="${c.id}" data-d="${d.data}" autofocus>Aprovar e liberar</button></div></div>`);
  },
  'aprovar-ok'(ds){ // API: POST /diarias/:id/aprovar
    const {c,d} = achar(ds.c, ds.d), p = pro(c.proId);
    d.status = 'liberada'; d.em = stamp(); refresh();
    dlg(`<form class="dlg" data-form="avaliar" data-p="${p.id}">${dlgHead('Dia aprovado')}<p class="muted">${brl(calc(c.valor).recebe)} liberado para ${primeiro(p.nome)}. Como foi o trabalho?</p>
      <fieldset><legend class="sr-only">Nota de 1 a 5</legend><div class="rate">${[5,4,3,2,1].map(n => `<input type="radio" name="nota" id="nota-${n}" value="${n}"><label for="nota-${n}" aria-label="${n} ${n===1?'estrela':'estrelas'}">${ic('star',40)}</label>`).join('')}</div></fieldset>
      <div class="field"><label for="av-txt">Comentário (opcional)</label><textarea id="av-txt" style="min-height:80px"></textarea></div>
      <div class="dlg-foot"><button type="button" class="btn btn-linha" data-act="fechar">Agora não</button><button class="btn btn-telha" type="submit">Enviar avaliação</button></div></form>`);
  },
  contestar(ds){
    const {c,d} = achar(ds.c, ds.d);
    S.upl.contest = [];
    dlg(`<form class="dlg" data-form="contestar" data-c="${c.id}" data-d="${d.data}" novalidate>${dlgHead('Contestar diária de '+fmtDia(d.data))}
      <div class="note note-warn">${ic('lock')}<span class="small">Só esta diária (${brl(c.valor)}) fica travada até a equipe COE analisar. As outras seguem normalmente.</span></div>
      <div class="field"><label for="ct-mot">O que aconteceu?</label><select id="ct-mot">${['Profissional não compareceu','Serviço incompleto','Qualidade do serviço','Dano no imóvel','Outro motivo'].map(o => `<option ${d.status==='andamento'&&o.startsWith('Prof')?'selected':''}>${o}</option>`).join('')}</select></div>
      <div class="field"><label for="ct-txt">Descreva o problema</label><textarea id="ct-txt" aria-describedby="ct-txt-err"></textarea><p class="err" id="ct-txt-err" hidden></p></div>
      ${uplField('contest','Fotos (opcional, ajuda muito)',{multiple:true,capture:'environment'})}
      <div class="dlg-foot"><button type="button" class="btn btn-linha" data-act="fechar">Cancelar</button><button class="btn btn-telha" type="submit">Enviar contestação</button></div></form>`);
  },
  'rm-upl'(ds){
    S.upl[ds.k].splice(Number(ds.i),1);
    if (ds.k==='cad_fotos'){ (S.cad.dados.fotosInfo||[]).splice(Number(ds.i),1); refresh(); return; }
    const th = document.getElementById('th-'+skey(ds.k)); if (th) th.innerHTML = thumbs(ds.k);
  },
  'foto-exemplo'(ds){ S.upl[ds.k] = [sampleImg('Serviço concluído','#8A5A3C')]; delete S.err[ds.k]; refresh(); },
  'cat-filtro'(ds){ S.filtros.prof = ds.p; S.filtros.servicos = []; refresh(); },
  cheguei(ds){ // API: POST /diarias/:id/chegada (hora + localização)
    const {c,d} = achar(ds.c, ds.d); d.chegada = horaAgora();
    toast(`Pronto! Avisamos ${primeiro(c.cliente)} que você chegou.`); refresh();
  },
  'fim-frase'(ds){ const st = S.fim[ds.k] || (S.fim[ds.k] = {frases:[], texto:''}); const i = st.frases.indexOf(ds.v); if (i>=0) st.frases.splice(i,1); else st.frases.push(ds.v); refresh(); },
  terminar(ds){ // API: POST /diarias/:id/concluir (com fotos)
    const k = `fim_${ds.c}_${ds.d}`, st = S.fim[k] || (S.fim[k] = {frases:[], texto:''});
    if (!(S.upl[k]||[]).length){ S.err[k] = 'Tire pelo menos uma foto do serviço.'; refresh(); const inp = document.getElementById('in-'+skey(k)); if (inp) inp.focus(); return; }
    if (censura(st.texto).tem){ toast('Tire o telefone do recado. O cliente já tem seu número.'); return; }
    const {d} = achar(ds.c, ds.d);
    Object.assign(d, {status:'aguardando', concluidoEm:stamp(), liberaEm:stamp(CONFIG.AUTO_LIBERA_HORAS), foto:true, obs:[...st.frases, st.texto.trim()].filter(Boolean).join('. ')});
    st.ok = true; delete S.upl[k]; delete S.err[k]; draw(true);
  },
  valor(ds){
    const alvo = ds.alvo==='cad' ? S.cad.dados : S.perfilEdit;
    alvo.valor = Math.max(80, (Number(alvo.valor)||0) + Number(ds.d));
    const el = document.getElementById(ds.alvo==='cad'?'cad-valor':'pe-valor'); if (el){ el.value = alvo.valor; INP[ds.alvo==='cad'?'cad-valor':'pe-valor'](el); }
    delete S.cad.err.valor;
  },

  resolver(ds){ // API: POST /disputas/:id/decisao
    const {c,d} = achar(ds.c, ds.d);
    d.status = ds.dec; d.em = stamp();
    toast(ds.dec==='liberada' ? `${brl(c.valor)} liberado ao profissional.` : `${brl(calc(c.valor).total)} devolvido ao cliente.`); refresh();
  },
  verif(ds, el){ // API: POST /verificacoes/:id/decisao
    const v = S.verificacoes.find(x => x.id===ds.id); if (!v) return;
    if (ds.dec==='ok' && el.getAttribute('aria-disabled')==='true'){ toast('Confira todos os itens antes de aprovar.'); return; }
    S.verificacoes = S.verificacoes.filter(x => x.id!==ds.id);
    if (ds.dec==='ok'){
      const srv = v.servicos.length ? v.servicos : profissao(v.prof).servicos.slice(0,2);
      PROS.push({id:'p'+(PROS.length+20), nome:v.nome, prof:v.prof, outras:v.outras||[], servicos:srv, anos:Number(v.anos)||0, ferramentas:v.ferramentas||'nao', valor:v.valor, meia:v.meia||null,
        cidade:v.cidade||v.cidades[0], raio:v.raio||20, cidades:v.cidades, disp:v.dias||[1,2,3,4,5], horario:v.horario||'8h às 17h', mei:v.mei==='sim', fone:'(47) 99000-0000', cor:'#51627A', ocupados:[], desde:'set/2026', status:'ativo', bio:v.bio,
        galeria:srv.concat(srv,srv).slice(0,6).map((s,i) => [`Trabalho ${i+1}`, s]), hist:{nota:0,nAval:0,diarias:0}, nr10:v.checks.nr10===true});
    }
    toast({ok:`${primeiro(v.nome)} aprovado. O perfil já aparece na busca.`, corrigir:`Pedido de correção enviado por SMS para ${primeiro(v.nome)}.`, recusar:`Cadastro de ${primeiro(v.nome)} recusado. Enviamos o motivo por SMS.`}[ds.dec]);
    go('admin-verificacoes');
  },
  moderar(ds){ // API: POST /moderacao/:id
    const m = S.moderacao.find(x => x.id===ds.id); m.status = ds.dec;
    if (ds.dec==='suspenso'){ const p = PROS.find(x => x.nome===m.autor); if (p) p.status = 'suspenso'; const k = S.clientes.find(x => x.nome===m.autor); if (k) k.status = 'suspenso'; }
    toast({advertido:`${primeiro(m.autor)} recebeu uma advertência.`, suspenso:`Conta de ${primeiro(m.autor)} suspensa.`, ignorado:'Marcado como falso positivo. Isso ajuda a ajustar o filtro.'}[ds.dec]); refresh();
  },
  'pro-status'(ds){ const p = pro(ds.id); p.status = ds.st; toast(ds.st==='suspenso'?`${primeiro(p.nome)} suspenso. O perfil saiu da busca.`:`${primeiro(p.nome)} reativado.`); refresh(); },
  'cat-rm'(ds){ const p = profRef(ds.p); const s = p.servicos.splice(Number(ds.i),1); toast(`"${s}" removido de ${p.nome}.`); refresh(); },
  'demo-login'(ds){
    S.role = ds.r; toast(ds.r==='cliente' ? 'Bem-vinda, Juliana!' : 'Bom trabalho, Valdir!');
    const r = S.redirect; S.redirect = null; go(ds.r==='cliente' ? (r || 'cliente') : 'pro-hoje');
  },
  'cad-voltar'(){ S.cad.etapa--; S.cad.err = {}; draw(true); },
  'cad-ir'(ds){ S.cad.etapa = Number(ds.e); S.cad.err = {}; draw(true); },
  'exemplo-cad'(){
    const d = S.cad.dados, e = S.cad.etapa;
    if (e===1) Object.assign(d,{prof:'pedreiro',outras:['pintor']});
    if (e===2) Object.assign(d,{nome:'Cleiton Rodrigues',cel:'(47) 99123-4567',cpf:'123.456.789-09',nasc:'1984-05-12'});
    if (e===3){ S.upl.cad_doc = [sampleImg('Documento','#5E6B7A')]; S.upl.cad_selfie = [sampleImg('Selfie','#7A6655')]; }
    if (e===4) d.servicos = ['Alvenaria','Reboco','Contrapiso','Pintura de muro'];
    if (e===5) Object.assign(d,{anos:'10',ferramentas:'sim',mei:'nao'});
    if (e===6) Object.assign(d,{valor:270,meiaOn:true,meia:150,horario:'7h às 17h'});
    if (e===7) Object.assign(d,{cidade:'Blumenau', raio:20, cidades:['Blumenau','Gaspar','Pomerode']});
    if (e===8) d.dias = [1,2,3,4,5,6];
    if (e===9){ S.upl.cad_fotos = [sampleImg('Muro','#9A5A34'),sampleImg('Reboco','#7E858E'),sampleImg('Contrapiso','#B0714A')]; d.fotosInfo = [{txt:'Muro de 12 m',srv:'Alvenaria'},{txt:'Reboco da fachada',srv:'Reboco'},{txt:'Contrapiso da sala',srv:'Contrapiso'}]; }
    if (e===10){ d.frases = ['Chego no horário combinado','Deixo o local limpo no fim do dia','Faço obra do começo ao fim']; d.bioExtra = 'Trabalho com obra há 10 anos, principalmente sobrados no Garcia.'; }
    if (e===11) Object.assign(d,{pixTipo:'Celular',pixChave:'(47) 99123-4567',termos:true,regra:true});
    S.cad.err = {}; refresh();
  },
  'rm-galeria'(ds){ S.perfilEdit.galeria.splice(Number(ds.i),1); refresh(); },
  sair(){ S.role = 'visitante'; go('inicio'); toast('Você saiu da conta.'); },
};

const FORM = {
  'busca-home'(f){ S.filtros = FILTROS_PADRAO(); S.filtros.prof = f.querySelector('#h-prof').value; S.filtros.cidade = f.querySelector('#h-cidade').value; S.busca.texto = ''; go('busca'); },
  cartao(f){
    const vazio = ['cc-num','cc-nome','cc-val','cc-cvv'].find(id => !f.querySelector('#'+id).value.trim());
    if (vazio){ S.ct.erro = 'Preencha todos os campos do cartão.'; refresh(); document.getElementById(vazio).focus(); return; }
    confirmarPagamento();
  },
  chat(f){ // API: POST /conversas/:id/mensagens (o servidor repete a checagem)
    const id = f.dataset.id, lado = f.dataset.lado, protegido = !!f.dataset.protegido;
    const inp = f.querySelector('input'), txt = inp.value.trim(); if (!txt) return;
    const conv = S.conversas.find(x => x.id===id), ct = S.contratos.find(x => x.id===id);
    const msgs = conv ? conv.msgs : ct.chat;
    const autor = lado==='cli' ? S.cliente.nome : pro(S.proLogadoId).nome;
    if (protegido){
      const r = censura(txt);
      if (r.tem){
        registraBloqueio(autor, lado==='cli'?'Cliente':'Profissional', f.dataset.destino, txt, r.motivos);
        msgs.push({de:'sis', txt:`Uma mensagem de ${primeiro(autor)} não foi entregue porque tinha ${r.motivos[0]}.`, h:horaCurta()});
        S.avisoChat[id] = {motivos:r.motivos, marcado:r.marcado, n:tentativas(autor), txt};
        refresh(); const i2 = document.getElementById('chat-in-'+id); if (i2) i2.focus(); rolarChat(id); return;
      }
    }
    delete S.avisoChat[id];
    msgs.push({de:lado, txt, h:horaCurta()}); refresh();
    const i2 = document.getElementById('chat-in-'+id); if (i2){ i2.value=''; i2.focus(); } rolarChat(id);
    const resposta = lado==='cli' ? (conv ? 'Certo! Se quiser, contrata pelo app que eu já reservo o dia.' : 'Combinado!') : 'Obrigada, até lá!';
    const hashAntes = location.hash;
    setTimeout(() => { msgs.push({de:lado==='cli'?'pro':'cli', txt:resposta, h:horaCurta()}); if (location.hash===hashAntes){ refresh(); rolarChat(id); } }, 1400);
  },
  avaliar(f){ const n = f.querySelector('input[name="nota"]:checked'); fechar(); toast(n ? `Obrigado! Você deu ${n.value} ${n.value==='1'?'estrela':'estrelas'}.` : 'Obrigado!'); },
  contestar(f){ // API: POST /diarias/:id/contestacao
    const txt = f.querySelector('#ct-txt'), er = f.querySelector('#ct-txt-err');
    if (txt.value.trim().length < 10){ er.hidden = false; er.innerHTML = ic('alert',16)+'Conte com um pouco mais de detalhe o que aconteceu.'; txt.setAttribute('aria-invalid','true'); txt.focus(); return; }
    const {d} = achar(f.dataset.c, f.dataset.d);
    d.status = 'contestada'; d.contest = {motivo:f.querySelector('#ct-mot').value, desc:txt.value.trim(), aberta:stamp(), fotos:(S.upl.contest||[]).length, resposta:''};
    fechar(); toast(`Contestação enviada. Respondemos em até ${CONFIG.PRAZO_DISPUTA_HORAS} horas.`); refresh();
  },
  auth(){ // API: POST /auth/login — o backend devolve o perfil (cliente ou profissional)
    S.role = 'cliente'; toast(`Bem-vinda, ${S.cliente.exibir}!`);
    const r = S.redirect; S.redirect = null; go(r || 'cliente');
  },
  'criar-cliente'(f){ // API: POST /clientes
    const nome = f.querySelector('#au-nome').value.trim(), cel = f.querySelector('#au-cel').value.trim(), senha = f.querySelector('#au-senha2').value, termos = f.querySelector('#au-termos').checked, er = f.querySelector('#au-err');
    const msg = !nome ? 'Escreva seu nome.' : !cel ? 'Escreva seu celular.' : senha.length<6 ? 'A senha precisa ter pelo menos 6 letras ou números.' : !termos ? 'Marque que aceita os termos para continuar.' : '';
    if (msg){ er.hidden = false; er.innerHTML = ic('alert',16)+msg; er.setAttribute('role','alert'); return; }
    S.cliente.exibir = primeiro(nome); S.role = 'cliente'; toast(`Conta criada. Bem-vindo(a), ${S.cliente.exibir}!`);
    const r = S.redirect; S.redirect = null; go(r || 'busca');
  },
  cadastro(){ // API: POST /profissionais (etapa final) — etapas salvas como rascunho
    const d = S.cad.dados, e = S.cad.etapa, err = {};
    const req = (k,m) => { if (!String(d[k]??'').trim()) err[k] = m; };
    if (e===1){ req('prof','Escolha o seu trabalho.'); d.outras = d.outras.filter(o => o!==d.prof); d.servicos = d.servicos.filter(x => [d.prof,...d.outras].some(pp => profissao(pp).servicos.includes(x))); }
    if (e===2){ req('nome','Escreva seu nome completo.'); req('cel','Escreva seu celular.'); req('cpf','Escreva seu CPF.'); req('nasc','Escolha sua data de nascimento.'); }
    if (e===3){ if (!(S.upl.cad_doc||[]).length) err.cad_doc = 'Falta a foto do documento.'; if (!(S.upl.cad_selfie||[]).length) err.cad_selfie = 'Falta a selfie com o documento.'; }
    if (e===4){ if (!d.servicos.length) err.servicos = 'Marque pelo menos um serviço.'; }
    if (e===5){ req('anos','Escolha há quanto tempo trabalha.'); req('ferramentas','Escolha uma opção.'); }
    if (e===6){ if (!(Number(d.valor)>=80)) err.valor = 'A diária precisa ser de pelo menos R$ 80.'; if (d.meiaOn && !(Number(d.meia)>=50 && Number(d.meia)<Number(d.valor))) err.meia = 'A meia diária precisa ser de pelo menos R$ 50 e menor que a diária.'; }
    if (e===7){ if (!d.cidade) err.cidade = 'Escolha a cidade onde você mora.'; else if (!d.cidades.includes(d.cidade)) d.cidades.unshift(d.cidade); }
    if (e===8){ if (!d.dias.length) err.dias = 'Marque pelo menos um dia.'; }
    if (e===9){ if (!(S.upl.cad_fotos||[]).length) err.cad_fotos = 'Mande pelo menos uma foto de trabalho.';
      (d.fotosInfo||[]).forEach((fi,i) => { if (censura(fi.txt||'').tem) err['leg'+i] = 'Tire o contato da legenda.'; }); }
    if (e===10){ const bio = bioDeFrases(d); if (censura(d.bioExtra||'').tem) err.bio = 'Tire telefone, e-mail ou rede social do texto para continuar.'; else if (bio.length<25) err.bio = 'Toque em pelo menos duas frases ou escreva um pouco sobre você.'; }
    if (e===11){ req('pixTipo','Escolha o tipo de chave.'); req('pixChave','Escreva sua chave Pix.'); if (!d.termos || !d.regra) err.termos = 'Marque as duas confirmações para enviar.'; }
    S.cad.err = err;
    if (Object.keys(err).length){
      refresh();
      const k = Object.keys(err)[0];
      const alvo = document.getElementById('cad-'+k) || document.getElementById('in-'+skey(k)) || document.getElementById('cad-leg-'+k.replace('leg','')) || document.getElementById('cad-bioExtra') || document.querySelector(`[data-bind="cad.${k}"]`);
      alvo && alvo.focus(); return;
    }
    if (e===11){
      d.bio = bioDeFrases(d);
      S.verificacoes.push({id:'v'+Date.now(), nome:d.nome, prof:d.prof, outras:d.outras, anos:Number(d.anos), enviado:stamp(), cel:'(47) 9••••-'+String(d.cel).replace(/\D/g,'').slice(-4), cpf:'•••.'+String(d.cpf).replace(/\D/g,'').slice(3,6)+'.•••-••',
        cidade:d.cidade, raio:Number(d.raio), cidades:d.cidades, dias:d.dias, horario:d.horario, valor:Number(d.valor), meia:d.meiaOn?Number(d.meia):null, servicos:d.servicos, ferramentas:d.ferramentas, mei:d.mei, fotos:(S.upl.cad_fotos||[]).length, bio:d.bio,
        docs:{doc:(S.upl.cad_doc||[])[0], selfie:(S.upl.cad_selfie||[])[0]}, checks:Object.assign({cpf:null,doc:null,selfie:null,fotos:null,bio:!censura(d.bio).tem}, d.prof==='eletricista'&&(S.upl.cad_nr10||[]).length?{nr10:null}:{}), nr10:(S.upl.cad_nr10||[]).length?'enviado':undefined});
    }
    S.cad.etapa++; draw(true);
  },
  perfil(){ // API: PATCH /profissionais/me
    const p = pro(S.proLogadoId), e = S.perfilEdit;
    if (censura(e.bio).tem){ toast('Tire o contato do texto "Sobre mim" para salvar.'); document.getElementById('pe-bio').focus(); return; }
    const novas = (S.upl.pe_fotos||[]).map((_,i) => [`Foto nova ${i+1}`, e.servicos[0]||'']);
    e.galeria.push(...novas); S.upl.pe_fotos = [];
    Object.assign(p, {valor:Number(e.valor)||p.valor, meia:e.meiaOn?(Number(e.meia)||null):null, servicos:[...e.servicos], disp:e.disp.map(Number), cidades:[...e.cidades], raio:Number(e.raio), bio:e.bio, horario:e.horario, status:e.pausado?'pausado':'ativo', galeria:e.galeria.length?e.galeria.map(g => [...g]):p.galeria});
    toast(e.pausado ? 'Perfil salvo e pausado. Ele não aparece na busca.' : 'Perfil salvo.'); refresh();
  },
  'salvar-conta'(){ toast('Dados salvos.'); },
  'cat-add'(f){ const inp = f.querySelector('input'), v = inp.value.trim(); if (!v) return; profRef(f.dataset.p).servicos.push(v); toast(`"${v}" adicionado.`); refresh(); },
  'prof-add'(f){
    const inp = f.querySelector('input'), nome = inp.value.trim(); if (!nome) return;
    const id = nome.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g,'').replace(/[^a-z0-9]+/g,'-').replace(/^-|-$/g,'');
    if (profRef(id)){ toast('Já existe uma profissão com esse nome.'); return; }
    AREAS.find(a => a.id===f.dataset.a).profissoes.push({id, nome, ic:'tools', ativo:false, desc:'Nova profissão', servicos:[]});
    toast(`${nome} criada como "Em breve". Adicione os serviços e ative.`); refresh();
  },
  config(f){ // API: PUT /config
    const n = id => Number(f.querySelector('#'+id).value);
    Object.assign(CONFIG, {COMISSAO:n('cf-com')/100, TAXA_PAGA_POR:f.querySelector('input[name="cf-quem"]:checked').value, AUTO_LIBERA_HORAS:n('cf-lib'), PRAZO_DISPUTA_HORAS:n('cf-disp'), LIMITE_DOMESTICO_SEMANA:Math.min(2,n('cf-lc')), TENTATIVAS_ANTES_ANALISE:n('cf-tent')});
    toast('Configurações salvas. Valem para novas contratações.'); refresh();
  },
};

const CHG = {
  filtro(el){ const k = el.dataset.k; S.filtros[k] = ['precoMax','expMin'].includes(k) ? Number(el.value) : el.value; if (k==='prof') S.filtros.servicos = []; refresh(); },
  'filtro-srv'(el){ const a = S.filtros.servicos, v = el.value; if (el.checked && !a.includes(v)) a.push(v); if (!el.checked) S.filtros.servicos = a.filter(x => x!==v); refresh(); },
  'filtro-bool'(el){ S.filtros[el.dataset.k] = el.checked; refresh(); },
  'ct-modo'(el){ S.ct.modo = el.value; S.ct.dias = []; S.ct.aviso = ''; refresh(); },
  'ct-periodo'(el){ S.ct.periodo = el.value; refresh(); },
  'adm-status'(el){ S.admStatus = el.value; refresh(); },
  'cat-ativo'(el){ const p = profRef(el.dataset.p); if (el.checked && !p.servicos.length){ el.checked = false; toast('Adicione pelo menos um serviço antes de ativar.'); return; } p.ativo = el.checked; toast(`${p.nome} ${p.ativo?'ativada':'marcada como "Em breve"'}.`); refresh(); },
  'verif-check'(el){ const v = S.verificacoes.find(x => x.id===el.dataset.id); v.checks[el.dataset.k] = el.checked ? true : null; refresh(); },
};
const INP = {
  preco(el){ const o = document.getElementById(el.dataset.out); if (o) o.textContent = brl(Number(el.value)); },
  'busca-texto'(el){ S.busca.texto = el.value; refresh(); },
  'adm-busca'(el){ S.admBusca = el.value; refresh(); },
  'cad-valor'(el){ const cc = calc(Number(el.value)||0); const a = $('#cad-rec'), b = $('#cad-cli'); if (a) a.textContent = brl(cc.recebe); if (b) b.textContent = brl(cc.total); },
  'pe-valor'(el){ const cc = calc(Number(el.value)||0); const a = $('#pe-rec'), b = $('#pe-cli'); if (a) a.textContent = brl(cc.recebe); if (b) b.textContent = brl(cc.total); },
  ccnum(el){ el.value = el.value.replace(/\D/g,'').slice(0,16).replace(/(\d{4})(?=\d)/g,'$1 '); },
  'ct-cep'(el){ const c = S.ct; let v = el.value.replace(/\D/g,'').slice(0,8); el.value = v.length>5 ? v.slice(0,5)+'-'+v.slice(5) : v; c.cep = el.value; const info = buscaCep(v); if (!!info !== !!c.cepInfo){ c.cepInfo = info; refresh(); } else c.cepInfo = info; },
  'fim-txt'(el){ const st = S.fim[el.dataset.k] || (S.fim[el.dataset.k] = {frases:[], texto:''}); st.texto = el.value; },
  bio(el){ const a = document.getElementById(el.dataset.aviso); if (a) a.innerHTML = bioAviso(el.value, el.maxLength>0?el.maxLength:400); },
};
function bindSet(el){
  const [obj, key] = el.dataset.bind.split('.');
  const alvo = obj==='cad' ? S.cad.dados : obj==='ct' ? S.ct : obj==='pe' ? S.perfilEdit : null;
  if (!alvo) return;
  if (el.type==='checkbox' && el.hasAttribute('value')){
    const v = /^\d$/.test(el.value) ? Number(el.value) : el.value;
    const arr = alvo[key] || (alvo[key] = []);
    const i = arr.indexOf(v); if (el.checked && i<0) arr.push(v); if (!el.checked && i>=0) arr.splice(i,1);
  } else if (el.type==='checkbox') alvo[key] = el.checked;
  else alvo[key] = el.value;
  if (obj==='cad' && S.cad.err[key]) delete S.cad.err[key];
  if (el.dataset.refresh || (obj==='ct' && key==='end')) refresh();
}
function handleUpload(el){
  const key = el.dataset.upl, files = [...el.files];
  Promise.all(files.map(f => new Promise(res => { const r = new FileReader(); r.onload = () => res(r.result); r.readAsDataURL(f); }))).then(urls => {
    S.upl[key] = el.multiple ? [...(S.upl[key]||[]), ...urls] : urls.slice(0,1);
    delete S.cad.err[key]; delete S.err[key];
    if (key==='cad_fotos'){ const fi = S.cad.dados.fotosInfo || (S.cad.dados.fotosInfo = []); urls.forEach(() => fi.push({txt:'',srv:''})); refresh(); return; }
    const er = document.getElementById('er-'+skey(key)); if (er) er.remove();
    const th = document.getElementById('th-'+skey(key)); if (th) th.innerHTML = thumbs(key);
    el.value = '';
  });
}

document.addEventListener('click', e => {
  const el = e.target.closest('[data-act]'); if (!el) return;
  const f = ACT[el.dataset.act]; if (!f) return;
  e.preventDefault(); f(el.dataset, el, e);
});
document.addEventListener('submit', e => { const f = FORM[e.target.dataset.form]; if (f){ e.preventDefault(); f(e.target); } });
document.addEventListener('change', e => {
  const el = e.target;
  if (el.id==='proto-role'){ trocarPapel(el.value); return; }
  if (el.id==='proto-cenario'){ S.cenario = el.value; if (S.filtros.ordem==='nota' && !temHist()) S.filtros.ordem = 'recomendados'; refresh(); toast(temHist()?'Cenário: plataforma com histórico de avaliações.':'Cenário: lançamento, sem avaliações.'); return; }
  if (el.type==='file' && el.dataset.upl){ handleUpload(el); return; }
  if (el.dataset.bindFoto!==undefined){ const fi = S.cad.dados.fotosInfo || (S.cad.dados.fotosInfo = []); (fi[el.dataset.bindFoto] || (fi[el.dataset.bindFoto] = {}))[el.dataset.campo] = el.value; return; }
  if (el.dataset.actChg && CHG[el.dataset.actChg]){ CHG[el.dataset.actChg](el); return; }
  if (el.dataset.chg && CHG[el.dataset.chg]) CHG[el.dataset.chg](el);
});
document.addEventListener('input', e => {
  const el = e.target;
  if (el.dataset.bind) bindSet(el);
  if (el.dataset.bindFoto!==undefined){ const fi = S.cad.dados.fotosInfo || (S.cad.dados.fotosInfo = []); (fi[el.dataset.bindFoto] || (fi[el.dataset.bindFoto] = {}))[el.dataset.campo] = el.value; }
  if (el.dataset.inp && INP[el.dataset.inp]) INP[el.dataset.inp](el);
});
