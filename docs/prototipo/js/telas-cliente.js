/* =========================================================================
   6b. TELAS DO CLIENTE — contratação, diárias, contrato, mensagens, conta
   ========================================================================= */
function exigeCliente(destino){
  if (S.role==='cliente') return null;
  S.redirect = destino; return V.entrar(true);
}

V.contratar = id => {
  const p = pro(id); if (!p) return V.naoEncontrado();
  const g = exigeCliente('contratar-'+id); if (g) return g;
  if (!S.ct || S.ct.proId!==id) S.ct = {proId:id, etapa:1, modo:'avulsa', periodo:'inteira', dias:[], servicos:[], desc:'', material:'', endSel:'casa', cep:'', cepInfo:null, numero:'', compl:'', pag:'pix', aviso:'', erro:'', err:{}};
  const c = S.ct, dom = ehDomestico(p.prof), plano = c.modo!=='avulsa';
  const valorDia = c.periodo==='meia' && p.meia ? p.meia : p.valor;
  const n = c.dias.length, agora = plano ? calc(valorDia, Math.min(n,1)) : calc(valorDia, n);
  const stepper = c.etapa<4 ? `<ol class="stepper" aria-label="Etapas">${['Dias','Resumo','Pagamento'].map((t,i) => `<li class="${i+1<c.etapa?'done':i+1===c.etapa?'cur':''}" ${i+1===c.etapa?'aria-current="step"':''}>${i+1}. ${t}</li>`).join('')}</ol>` : '';
  const head = `<a class="back" href="#perfil-${p.id}">${ic('back',18)}Perfil de ${primeiro(p.nome)}</a>${stepper}`;
  const mini = `<div class="row">${avatar(p,'sm')}<div><b>${esc(p.nome)}</b><p class="small muted">${profissao(p.prof).nome} · ${brl(valorDia)}/${c.periodo==='meia'?'meia diária':'diária'}</p></div></div>`;
  const chipsDias = `<div class="chips">${c.dias.map(d => `<span class="chip">${fmtDia(d)}</span>`).join('')}</div>`;

  if (c.etapa===1) return `<div class="wrap">${head}<div class="duas"><div class="stack" style="--g:20px">
    <h1>Escolha os dias</h1>
    ${p.meia ? `<fieldset><legend>Período</legend><div class="bigopts">
      <label class="bigopt"><input type="radio" name="ct-per" id="ct-per-inteira" value="inteira" data-chg="ct-periodo" ${c.periodo==='inteira'?'checked':''}><span class="bi">${ic('clock',24)}</span><span><b>Diária inteira</b><span class="muted">${p.horario} · ${brl(p.valor)}</span></span></label>
      <label class="bigopt"><input type="radio" name="ct-per" id="ct-per-meia" value="meia" data-chg="ct-periodo" ${c.periodo==='meia'?'checked':''}><span class="bi">${ic('clock',24)}</span><span><b>Meia diária</b><span class="muted">Até 4 horas · ${brl(p.meia)}</span></span></label>
    </div></fieldset>` : ''}
    ${dom ? `<fieldset><legend>Tipo de contratação</legend><div class="bigopts">
      ${[['avulsa','Diária avulsa','Dias soltos, quando precisar.','cal'],['semanal','Plano semanal','Mesmo dia toda semana, com substituição garantida.','repeat'],['quinzenal','Plano quinzenal','A cada 15 dias, com substituição garantida.','repeat']]
        .map(([v,t,d,i]) => `<label class="bigopt"><input type="radio" name="ct-modo" id="ct-modo-${v}" value="${v}" data-chg="ct-modo" ${c.modo===v?'checked':''}><span class="bi">${ic(i,24)}</span><span><b>${t}</b><span class="muted">${d}</span></span></label>`).join('')}
    </div></fieldset>
    <div class="note note-azul">${ic('law')}<span>Limite de <b>${CONFIG.LIMITE_DOMESTICO_SEMANA} dias por semana</b> com a mesma profissional (LC 150/2015). Os dias que você já tem com ${primeiro(p.nome)} contam no limite.</span></div>` : ''}
    <p class="muted">${plano ? `Toque no primeiro dia. Os próximos são marcados ${c.modo==='semanal'?'toda semana':'a cada 15 dias'}.` : 'Toque nos dias em verde para escolher. Toque de novo para desmarcar.'}</p>
    <div class="card">${calendario(p,{mode:'select',sel:c.dias})}</div>
    <div aria-live="assertive">${c.aviso ? `<div class="note note-warn" role="alert">${ic('alert')}<span>${c.aviso}</span></div>` : ''}</div>
    ${n ? `<div class="stack" style="--g:8px"><h2 style="font-size:1.15rem">Dias escolhidos</h2>${chipsDias}</div>` : ''}
    <div class="actionbar"><div class="tot"><b>${n} ${n===1?'diária':'diárias'}</b><span>${n?(plano?`Hoje: ${brl(agora.total)}`:`${brl(agora.total)} com taxa`):'Nenhum dia escolhido'}</span></div>
      <button type="button" class="btn btn-telha" data-act="ct-etapa" data-e="2" ${n?'':'aria-disabled="true"'}>Continuar ${ic('next',18)}</button></div>
  </div><aside class="card stack" style="--g:12px">${mini}<p class="small muted">Verde: livre. Riscado: já tem outra contratação. Tracejado: dia em que ${primeiro(p.nome)} não trabalha.</p></aside></div></div>`;

  if (c.etapa===2) {
    const nome = primeiro(p.nome), e = c.err || {}, area = areaDe(p.prof);
    const profs = [p.prof, ...p.outras].map(profissao);
    const srvs = [...new Set(profs.flatMap(pp => pp.servicos))].filter(x => p.servicos.includes(x));
    const casa = S.cliente.enderecos[0];
    const cidadeServ = c.endSel==='casa' ? casa.cidade : (c.cepInfo ? c.cepInfo.cidade : '');
    const foraArea = cidadeServ && !p.cidades.includes(cidadeServ);
    const matLabel = p.prof==='diarista' ? 'Produtos de limpeza' : 'Material';
    const linhas = plano
      ? `<li><span>1ª diária · ${fmtDia(c.dias[0])}</span><span>${brl(valorDia)}</span></li><li><span>Taxa de serviço (${Math.round(CONFIG.COMISSAO*100)}%)</span><span>${brl(agora.taxa)}</span></li><li class="total"><span>Hoje</span><b>${brl(agora.total)}</b></li>`
      : `<li><span>${n} ${n===1?'diária':'diárias'} × ${brl(valorDia)}</span><span>${brl(agora.base)}</span></li><li><span>Taxa de serviço (${Math.round(CONFIG.COMISSAO*100)}%)</span><span>${brl(agora.taxa)}</span></li><li class="total"><span>Total</span><b>${brl(agora.total)}</b></li>`;
    const secao = (num, titulo, extra='') => `<div class="row" style="margin-bottom:4px"><span class="passo-num">${num}</span><h2 style="font-size:1.8rem">${titulo}${extra?` <span class="muted" style="font-size:1.1rem">${extra}</span>`:''}</h2></div>`;
    return `<div class="wrap">${head}<div class="duas"><div class="stack" style="--g:22px">
      <header class="stack" style="--g:6px"><h1>Conte o que precisa</h1><p class="muted" style="font-size:1.05rem">Quanto mais ${nome} souber antes, mais preparado chega no dia.</p></header>

      <section class="card stack" style="--g:14px" aria-labelledby="r-quando">${secao(1,'Quando')}
        <ul class="lista">${c.dias.map(d => `<li><span><b>${DOWL[parseD(d).getDay()]}, ${parseD(d).getDate()} de ${MES[parseD(d).getMonth()]}</b><br><span class="muted small">${c.periodo==='meia'?'Meia diária (até 4 h)':p.horario}</span></span><span class="num">${brl(valorDia)}</span></li>`).join('')}</ul>
        ${plano?`<p class="small muted">Plano ${c.modo}: cada próxima diária é cobrada 2 dias antes. Cancele quando quiser.</p>`:''}
        <button type="button" class="linkbtn" data-act="ct-etapa" data-e="1" style="justify-self:start">Mudar os dias</button>
        <h2 id="r-quando" class="sr-only">Quando</h2></section>

      <section class="card stack" style="--g:14px">${secao(2,'O que precisa ser feito')}
        <fieldset><legend>Toque no que você precisa</legend><div class="opts">${srvs.map((x,i) => chk('ct-srv',i,x,c.servicos.includes(x),x,'ct.servicos')).join('')}</div></fieldset>
        <div class="field"><label for="ct-desc">Explique com suas palavras</label>
          <textarea id="ct-desc" maxlength="600" data-bind="ct.desc" data-inp="bio" data-aviso="ct-desc-aviso" aria-describedby="ct-desc-aviso"${e.desc?' aria-invalid="true"':''} placeholder="${esc(PEDIDO_EX[p.prof]||'Descreva o serviço')}">${esc(c.desc)}</textarea>
          <div id="ct-desc-aviso" class="stack" style="--g:8px" aria-live="polite">${bioAviso(c.desc,600)}</div>
          ${e.desc?`<p class="err" role="alert">${ic('alert',16)}${e.desc}</p>`:''}</div></section>

      <section class="card stack" style="--g:14px">${secao(3,'Fotos do local','(se puder)')}
        ${uplField('ct_fotos','Fotos de onde vai ser o serviço',{multiple:true,capture:'environment',hint:`Ajuda ${nome} a entender o tamanho do trabalho e a avisar o ${matLabel.toLowerCase()} certo. Até 8 fotos.`,botao:'Adicionar fotos'})}
        <button type="button" class="linkbtn" data-act="ct-fotos-exemplo" style="justify-self:start">Usar fotos de exemplo (protótipo)</button>
        <p class="small muted row start" style="gap:6px">${ic('lock',16)}As fotos só ficam visíveis para ${nome} e para a equipe COE. Não mostre documentos nem números de telefone nelas.</p></section>

      <section class="card stack" style="--g:12px">${secao(4, matLabel)}
        <div class="listopts" role="radiogroup" aria-label="${matLabel}">${[['tenho',`Já tenho ${p.prof==='diarista'?'os produtos':'o material'}`],['compro',`Vou comprar: ${nome} me diz o que falta`],['combinar','Ainda não sei, combino pelo chat']].map(([v,t]) => `<label class="bigopt"><input type="radio" name="ct-mat" id="ct-mat-${v}" value="${v}" data-bind="ct.material" ${c.material===v?'checked':''}><b>${t}</b></label>`).join('')}</div></section>

      <section class="card stack" style="--g:12px">${secao(5,'Onde vai ser')}
        <div class="listopts" role="radiogroup" aria-label="Endereço do serviço">
          <label class="bigopt"><input type="radio" name="ct-end" id="ct-end-casa" value="casa" data-bind="ct.endSel" data-refresh="1" ${c.endSel==='casa'?'checked':''}><span class="bi">${ic('home',24)}</span><span class="stack" style="--g:2px"><b>${casa.nome}</b><span class="muted">${esc(casa.linha)} · ${casa.cidade}</span></span></label>
          <label class="bigopt"><input type="radio" name="ct-end" id="ct-end-novo" value="novo" data-bind="ct.endSel" data-refresh="1" ${c.endSel==='novo'?'checked':''}><span class="bi">${ic('pin',24)}</span><span class="stack" style="--g:2px"><b>Outro endereço</b><span class="muted">Informar pelo CEP</span></span></label>
        </div>
        ${c.endSel==='novo' ? `<div class="stack" style="--g:12px">
          <div class="field"><label for="ct-cep">CEP</label><input id="ct-cep" inputmode="numeric" autocomplete="postal-code" placeholder="00000-000" value="${esc(c.cep)}" data-inp="ct-cep"${e.end&&!c.cepInfo?' aria-invalid="true"':''}>
            ${c.cepInfo?`<p class="note note-ok">${ic('pin')}<span><b>${esc(c.cepInfo.rua)}</b><br>${esc(c.cepInfo.bairro)} · ${esc(c.cepInfo.cidade)}/SC <span class="muted small">(endereço simulado)</span></span></p>`:'<p class="hint">Digite o CEP e o endereço aparece sozinho.</p>'}</div>
          <div class="row start"><div class="field" style="flex:0 0 120px"><label for="ct-num">Número</label><input id="ct-num" inputmode="numeric" value="${esc(c.numero)}" data-bind="ct.numero"${e.end&&c.cepInfo&&!c.numero?' aria-invalid="true"':''}></div><div class="field" style="flex:1"><label for="ct-compl">Complemento</label><input id="ct-compl" value="${esc(c.compl)}" data-bind="ct.compl" placeholder="Apto, bloco, fundos…"></div></div>
          ${e.end?`<p class="err" role="alert">${ic('alert',16)}${e.end}</p>`:''}</div>` : ''}
        ${foraArea?`<div class="note note-warn">${ic('alert')}<span>${nome} normalmente não atende ${esc(cidadeServ)}. Pergunte pelo chat antes de pagar.</span></div>`:''}
        <p class="small muted row start" style="gap:6px">${ic('eyeoff',16)}${nome} só vê o endereço completo depois do pagamento. Antes disso, só a cidade.</p></section>

      <div class="actionbar so-mobile"><div class="tot"><b>${brl(agora.total)}</b><span>${n} ${n===1?'diária':'diárias'} com taxa</span></div><button type="button" class="btn btn-telha" data-act="ct-etapa" data-e="3">Pagamento ${ic('next',18)}</button></div>
    </div>
    <aside class="card stack caixa-preco" style="--g:16px"><h2 style="font-size:1.8rem">Resumo</h2>${mini}
      <ul class="soma">${linhas}</ul>
      <ol class="ticket-passos">
        <li class="agora"><b>1</b>Você paga agora. O dinheiro fica guardado.</li>
        <li><b>2</b>${nome} trabalha e manda foto no fim do dia.</li>
        <li><b>3</b>Você aprova e o valor daquele dia é liberado.</li>
      </ol>
      <button type="button" class="btn btn-telha btn-lg btn-block" data-act="ct-etapa" data-e="3">Ir para pagamento</button>
      <button type="button" class="btn btn-linha btn-block" data-act="ct-etapa" data-e="1">${ic('back',18)}Voltar aos dias</button>
    </aside></div></div>`;
  }

  if (c.etapa===3) {
    const total = agora.total;
    const pixCode = `00020126580014BR.GOV.BCB.PIX0136coe-prototipo-sem-valor-real520400005303986540${total.toFixed(2)}5802BR5912COE SERVICOS6008BLUMENAU62070503***6304A1B2`;
    return `<div class="wrap">${head}<div class="duas"><div class="stack" style="--g:20px">
      <h1>Pagamento</h1>
      <div class="seg" role="tablist" aria-label="Forma de pagamento">
        <button type="button" role="tab" id="tab-pix" aria-selected="${c.pag==='pix'}" aria-controls="pane-pag" data-act="pag" data-v="pix">${ic('qr')}Pix</button>
        <button type="button" role="tab" id="tab-cartao" aria-selected="${c.pag==='cartao'}" aria-controls="pane-pag" data-act="pag" data-v="cartao">${ic('card')}Cartão</button>
      </div>
      <div id="pane-pag" role="tabpanel" aria-labelledby="tab-${c.pag}" class="card">
      ${c.pag==='pix' ? `<div class="pix"><p>Abra o app do seu banco, escolha <b>Pix › Ler QR Code</b> e aponte para o código.</p>${qrSvg(Math.round(total*100))}
          <p class="small muted">O código expira em <b class="num" id="pix-timer">30:00</b></p>
          <div class="field" style="width:100%;text-align:left"><label for="pix-code">Ou copie o código Pix</label><div class="copyrow"><input id="pix-code" readonly value="${pixCode}"><button type="button" class="btn btn-linha" data-act="copiar" data-alvo="pix-code">${ic('copy',18)}Copiar</button></div></div>
          <button type="button" class="btn btn-telha btn-lg btn-block" data-act="pagar">Já paguei (simular confirmação)</button>
          <p class="small muted">No app real, esta tela avança sozinha quando o banco confirma o Pix.</p></div>`
      : `<form class="stack" data-form="cartao" novalidate>
          <div class="field"><label for="cc-num">Número do cartão</label><input id="cc-num" inputmode="numeric" autocomplete="cc-number" placeholder="0000 0000 0000 0000" data-inp="ccnum"></div>
          <div class="field"><label for="cc-nome">Nome impresso no cartão</label><input id="cc-nome" autocomplete="cc-name"></div>
          <div class="row start"><div class="field" style="flex:1"><label for="cc-val">Validade</label><input id="cc-val" inputmode="numeric" autocomplete="cc-exp" placeholder="MM/AA"></div><div class="field" style="flex:1"><label for="cc-cvv">Código (CVV)</label><input id="cc-cvv" inputmode="numeric" autocomplete="cc-csc" placeholder="3 dígitos"></div></div>
          ${c.erro?`<p class="err" role="alert">${ic('alert',16)}${c.erro}</p>`:''}
          <button class="btn btn-telha btn-lg btn-block" type="submit">${ic('lock',18)}Pagar ${brl(total)}</button>
          <p class="small muted">Protótipo: nenhuma cobrança é feita. Qualquer número serve.</p></form>`}
      </div></div>
    <aside class="card stack" style="--g:14px">${mini}<ul class="soma"><li class="total"><span>${plano?'Hoje':'Total'}</span><b>${brl(total)}</b></li></ul><button type="button" class="btn btn-linha" data-act="ct-etapa" data-e="2">${ic('back',18)}Voltar ao resumo</button></aside></div></div>`;
  }

  return `<div class="wrap"><div class="card sucesso" style="max-width:640px;margin-inline:auto">
    <span class="grande">${ic('check',42)}</span><h1>Contratação confirmada</h1>
    <p class="muted" style="max-width:48ch">Pagamento recebido. ${brl(agora.total)} está guardado com a COE e será liberado dia a dia, conforme você aprovar.</p>
    <div class="liberado"><b class="row" style="gap:6px">${ic('phone',18)}Contato de ${esc(p.nome)} liberado</b><span class="row wrapx"><span class="num" style="font-size:1.25rem;font-weight:700">${p.fone}</span><button type="button" class="btn btn-linha btn-sm" data-act="copiar-txt" data-txt="${p.fone}">${ic('copy',16)}Copiar</button></span><span class="small muted">Mesmo número no WhatsApp. Mantenha os combinados no chat do app: é ele que vale numa contestação.</span></div>
    <div class="row wrapx" style="justify-content:center"><a class="btn btn-telha btn-lg" href="#contrato-${c.novoId}">Ver minhas diárias</a><button type="button" class="btn btn-linha btn-lg" data-act="abrir-chat" data-c="${c.novoId}">${ic('chat')}Abrir chat</button></div>
  </div></div>`;
};

V.cliente = tab => {
  const g = exigeCliente('cliente'); if (g) return g;
  tab = tab || 'ativos';
  const meus = meusContratos();
  const pend = meus.flatMap(c => c.dias.filter(d => d.status==='aguardando').map(d => ({c,d})));
  const lista = tab==='historico' ? meus.filter(c => !contratoAtivo(c)) : meus.filter(contratoAtivo);
  const card = c => {
    const p = pro(c.proId), lib = c.dias.filter(d => d.status==='liberada').length, tot = c.dias.length;
    const prox = c.dias.find(d => ['paga','agendada','andamento'].includes(d.status));
    const [st,cls] = contratoStatus(c);
    const cor = {liberada:'var(--ok)',aguardando:'var(--laranja)',contestada:'var(--bad)',andamento:'#8E90E0',paga:'var(--navy)',agendada:'var(--line-2)',reembolsada:'var(--line-2)'};
    return `<article class="card ccard"><div class="row between wrapx start"><div class="row">${avatar(p,'sm')}<div><h3><a href="#contrato-${c.id}">${esc(p.nome)}</a></h3><p class="small muted">${esc(c.servico)}</p></div></div><span class="pill ${cls}"><i></i>${st}</span></div>
      <div class="barra" aria-hidden="true">${c.dias.map(d => `<i style="flex:1;background:${cor[d.status]};margin-right:2px"></i>`).join('')}</div>
      <div class="row between wrapx small"><span class="muted">${lib} de ${tot} diárias liberadas${c.modo!=='avulsa'?` · plano ${c.modo}`:''}</span><span>${prox?`Próxima: <b>${fmtDia(prox.data)}</b>`:''}</span></div>
      <a class="btn btn-linha btn-block" href="#contrato-${c.id}">Abrir contrato</a></article>`;
  };
  const pagamentos = meus.flatMap(c => c.dias.filter(d => d.status!=='agendada').map(d => ({c,d}))).sort((a,b) => b.d.data.localeCompare(a.d.data));
  return `<div class="wrap">
    <header class="pagehead"><h1>Olá, ${esc(S.cliente.exibir)}</h1><p class="muted">Acompanhe suas contratações e aprove os dias de trabalho.</p></header>
    ${pend.map(({c,d}) => `<div class="alerta" role="region" aria-label="Aprovação pendente"><div class="row start"><span class="ai">${ic('hand')}</span><div><b>${primeiro(pro(c.proId).nome)} concluiu o dia ${fmtDia(d.data)}</b><p class="small muted">Aprove ou conteste até ${d.liberaEm}. Depois disso o valor é liberado.</p></div></div><a class="btn btn-telha" href="#contrato-${c.id}">Ver e aprovar</a></div>`).join('')}
    <nav class="tabs" aria-label="Contratos"><a href="#cliente" ${tab==='ativos'?'aria-current="page"':''}>Ativos (${meus.filter(contratoAtivo).length})</a><a href="#cliente-historico" ${tab==='historico'?'aria-current="page"':''}>Histórico e pagamentos</a></nav>
    ${tab==='historico' ? `<div class="stack" style="--g:24px">${lista.length?`<div class="grid-cards">${lista.map(card).join('')}</div>`:''}
      <section class="card"><h2 style="font-size:1.2rem;margin-bottom:8px">Pagamentos por diária</h2><ul class="lista">${pagamentos.map(({c,d}) => `<li><div><b>${fmtDia(d.data)}</b> · ${esc(pro(c.proId).nome)}<p class="small muted">${esc(c.servico)}</p></div><div class="row"><span class="num b">${brl(c.valor)}</span>${pill(d.status)}</div></li>`).join('')}</ul></section></div>`
    : (lista.length ? `<div class="grid-cards">${lista.map(card).join('')}</div>` : `<div class="card empty">${ic('cal',40)}<h2>Nenhuma contratação ativa</h2><a class="btn btn-telha" href="#busca">Buscar profissional</a></div>`)}
  </div>`;
};

V.contrato = id => {
  const g = exigeCliente('contrato-'+id); if (g) return g;
  const c = S.contratos.find(x => x.id===id); if (!c) return V.naoEncontrado();
  const p = pro(c.proId), r = contratoResumo(c), [st,cls] = contratoStatus(c), pane = S.paneContrato;
  const tl = c.dias.map((d,i) => {
    const s = ST[d.status]; let extra = '';
    if (d.status==='aguardando') extra = `<div class="tl-box">${d.foto?`<figure class="cena">${cena(p.prof, p.servicos[2]||p.servicos[0], 2)}<span class="tag-ilus">Ilustração</span><figcaption>Foto enviada por ${primeiro(p.nome)}</figcaption></figure>`:''}${d.obs?`<p>“${esc(d.obs)}”</p>`:''}
      <div class="tl-acoes"><button type="button" class="btn btn-telha" data-act="aprovar" data-c="${c.id}" data-d="${d.data}">${ic('check',18)}Aprovar dia</button><button type="button" class="btn btn-perigo" data-act="contestar" data-c="${c.id}" data-d="${d.data}">Contestar</button></div></div>`;
    if (d.status==='andamento') extra = `<div><button type="button" class="linkbtn" data-act="contestar" data-c="${c.id}" data-d="${d.data}">${primeiro(p.nome)} não apareceu? Contestar</button></div>`;
    if (d.status==='contestada' && d.contest) extra = `<div class="tl-box"><p><b>${esc(d.contest.motivo)}</b> · aberta em ${d.contest.aberta}</p><p>“${esc(d.contest.desc)}”</p>${d.contest.resposta?`<p class="small muted"><b>Resposta de ${primeiro(p.nome)}:</b> ${esc(d.contest.resposta)}</p>`:''}</div>`;
    return `<li><span class="dot ${s.dot}">${ic(s.i,16)}</span><div class="tl-body"><div class="tl-head"><b>Dia ${i+1} · ${fmtDia(d.data)}</b>${pill(d.status)}</div><p class="small muted">${textoStatus(c,d,p,'cli')}</p>${extra}</div></li>`;
  }).join('');
  return `<div class="wrap">
    <a class="back" href="#cliente">${ic('back',18)}Minhas diárias</a>
    <header class="stack" style="--g:14px;margin-bottom:20px">
      <div class="row between wrapx"><div class="row">${avatar(p,'md')}<div><h1 style="font-size:clamp(1.35rem,3.5vw,1.9rem)">${esc(p.nome)}</h1><p class="muted">${esc(c.servico)}</p></div></div><span class="pill ${cls}"><i></i>${st}</span></div>
      <div class="row wrapx small muted" style="gap:8px 18px"><span class="row" style="gap:6px">${ic('pin',16)}${esc(c.endereco)}</span><span class="row" style="gap:6px">${ic('phone',16)}<b style="color:var(--ink)" class="num">${p.fone}</b> liberado</span>${c.modo!=='avulsa'?`<span class="row" style="gap:6px">${ic('repeat',16)}Plano ${c.modo} · substituição garantida</span>`:''}</div>
      <div class="dinheiro"><div class="din"><span>Pago</span><b>${brl(r.pago)}</b></div><div class="din"><span>Guardado</span><b>${brl(r.retido)}</b></div><div class="din"><span>Liberado</span><b>${brl(r.liberado)}</b></div><div class="din"><span>Em análise</span><b>${brl(r.disputa)}</b></div></div>
    </header>
    ${c.pedido?`<details class="card" style="margin-bottom:16px"><summary class="b" style="cursor:pointer">Ver o que você pediu</summary><div style="margin-top:12px">${pedidoHTML(c,p,'cli').replace('class="card stack"','class="stack"')}</div></details>`:''}
    <div class="seg ctabs" role="tablist" aria-label="Seções do contrato" style="margin-bottom:16px">
      <button type="button" role="tab" aria-selected="${pane==='diarias'}" aria-controls="p-diarias" data-act="pane" data-v="diarias">${ic('cal',18)}Diárias</button>
      <button type="button" role="tab" aria-selected="${pane==='chat'}" aria-controls="p-chat" data-act="pane" data-v="chat">${ic('chat',18)}Chat</button>
    </div>
    <div class="split">
      <section class="pane card ${pane==='diarias'?'':'is-off'}" id="p-diarias" aria-labelledby="h-dia"><h2 id="h-dia" style="font-size:1.2rem;margin-bottom:16px">Linha do tempo das diárias</h2><ol class="tl">${tl}</ol></section>
      <section class="pane card ${pane==='chat'?'':'is-off'}" id="p-chat" aria-labelledby="h-chat"><h2 id="h-chat" style="font-size:1.2rem;margin-bottom:12px">Chat com ${primeiro(p.nome)}</h2>${chatHTML({id:c.id, msgs:c.chat, lado:'cli', protegido:false})}</section>
    </div></div>`;
};

V.mensagens = () => {
  const g = exigeCliente('mensagens'); if (g) return g;
  const conv = S.conversas.filter(m => m.cliente===S.cliente.nome);
  const ct = meusContratos().filter(c => c.chat.length);
  const ult = arr => { const m = [...arr].reverse().find(x => x.de!=='sis'); return m ? m.txt : 'Sem mensagens'; };
  return `<div class="wrap"><header class="pagehead"><h1>Mensagens</h1><p class="muted">Antes de contratar, a conversa fica só no app. Depois, o telefone é liberado.</p></header>
    <div class="stack" style="--g:22px">
    <section class="card"><h2 style="font-size:1.15rem;margin-bottom:6px">Antes de contratar</h2><ul class="lista">${conv.length ? conv.map(m => { const p = pro(m.proId); return `<li><a class="row" href="#conversa-${m.id}" style="flex:1">${avatar(p,'sm')}<div><b>${esc(p.nome)}</b><p class="small muted">${esc(ult(m.msgs))}</p></div></a><span class="pill p-mute"><i></i>Contato oculto</span></li>`; }).join('') : '<li class="muted">Nenhuma conversa ainda.</li>'}</ul></section>
    <section class="card"><h2 style="font-size:1.15rem;margin-bottom:6px">Contratos</h2><ul class="lista">${ct.map(c => { const p = pro(c.proId); return `<li><button type="button" class="row linkbtn" style="text-decoration:none;flex:1;text-align:left" data-act="abrir-chat" data-c="${c.id}">${avatar(p,'sm')}<div><b>${esc(p.nome)}</b><p class="small muted" style="font-weight:400">${esc(ult(c.chat))}</p></div></button><span class="pill p-ok"><i></i>Contato liberado</span></li>`; }).join('')}</ul></section>
    </div></div>`;
};

V.conversa = id => {
  const g = exigeCliente('conversa-'+id); if (g) return g;
  const m = S.conversas.find(x => x.id===id); if (!m) return V.naoEncontrado();
  const p = pro(m.proId);
  return `<div class="wrap"><a class="back" href="#mensagens">${ic('back',18)}Mensagens</a>
    <div class="duas"><section class="card" aria-labelledby="h-conv"><div class="row between wrapx" style="margin-bottom:12px"><div class="row">${avatar(p,'sm')}<div><h1 id="h-conv" style="font-size:1.3rem">${esc(p.nome)}</h1><p class="small muted">${profissao(p.prof).nome}</p></div></div><span class="pill p-mute"><i></i>Contato oculto</span></div>
      ${chatHTML({id:m.id, msgs:m.msgs, lado:'cli', protegido:true, destino:'Chat com '+p.nome+' (antes da contratação)'})}</section>
    <aside class="card stack" style="--g:12px"><p class="muted small">Gostou da conversa?</p><p class="valor-grande">${brl(p.valor)}<span class="small muted" style="font-family:var(--f-body)"> /diária</span></p><a class="btn btn-telha btn-block" href="#contratar-${p.id}">Contratar ${primeiro(p.nome)}</a><a class="btn btn-linha btn-block" href="#perfil-${p.id}">Ver perfil</a></aside></div></div>`;
};

V.conta = () => {
  const g = exigeCliente('conta'); if (g) return g;
  return `<div class="wrap"><div class="wizard stack" style="--g:20px;margin-inline:0">
    <header class="pagehead"><h1>Minha conta</h1></header>
    <form class="card stack" data-form="salvar-conta" novalidate><h2 style="font-size:1.15rem">Dados</h2>
      <div class="field"><label for="ca-nome">Nome</label><input id="ca-nome" value="${esc(S.cliente.nome)}" autocomplete="name"></div>
      <div class="field"><label for="ca-cel">Celular</label><input id="ca-cel" value="${TEL_CLIENTE}" type="tel"></div>
      <div class="field"><label for="ca-end">Endereço principal</label><input id="ca-end" value="Rua Amazonas, 1200 · Garcia"><p class="hint">Só aparece para o profissional depois do pagamento.</p></div>
      <button class="btn btn-azul" type="submit">Salvar</button></form>
    <section class="card stack" style="--g:10px"><h2 style="font-size:1.15rem">Pagamento</h2><div class="row between"><span class="row">${ic('card')}Cartão final 4417</span><button type="button" class="linkbtn" data-act="toast" data-msg="Cartão removido (simulado).">Remover</button></div><button type="button" class="btn btn-linha" data-act="toast" data-msg="Tela de novo cartão (não incluída no protótipo).">${ic('plus',18)}Adicionar cartão</button></section>
    <section class="card"><h2 style="font-size:1.15rem">Avisos</h2>
      <label class="switch" for="nt-1"><span>Quando o profissional concluir o dia</span><input type="checkbox" id="nt-1" checked></label>
      <label class="switch" for="nt-2"><span>1 hora antes da liberação automática</span><input type="checkbox" id="nt-2" checked></label>
      <label class="switch" for="nt-3"><span>Novos profissionais na minha região</span><input type="checkbox" id="nt-3"></label></section>
    <section class="card stack" style="--g:10px"><h2 style="font-size:1.15rem">Privacidade</h2><p class="small muted">Você pode baixar seus dados ou excluir a conta a qualquer momento (LGPD).</p><div class="row wrapx"><button type="button" class="btn btn-linha" data-act="toast" data-msg="Arquivo com seus dados será enviado por e-mail (simulado).">Baixar meus dados</button><button type="button" class="btn btn-perigo" data-act="toast" data-msg="Exclusão pedida. Contratos em andamento precisam terminar antes (simulado).">Excluir conta</button></div></section>
  </div></div>`;
};
const TEL_CLIENTE = '(47) 99655-3021';
const PEDIDO_EX = {
  pedreiro:'Ex.: rebocar a parede da lavanderia, uns 3 × 2,5 m. Já tenho cimento e areia.',
  pintor:'Ex.: pintar sala e corredor, uns 40 m² de parede. A tinta já está comprada.',
  eletricista:'Ex.: o disjuntor do chuveiro desarma toda hora. Quero trocar também 4 tomadas da cozinha.',
  jardineiro:'Ex.: cortar a grama do quintal (uns 200 m²) e podar a cerca viva da frente.',
  diarista:'Ex.: casa de 3 quartos e 2 banheiros. Tem um cachorro. Os produtos ficam na lavanderia.',
};
/* CEP simulado. No sistema real: consulta ao ViaCEP (ou similar) pelo backend. */
function buscaCep(cep){
  const d = cep.replace(/\D/g,''); if (d.length!==8) return null;
  const pre = Number(d.slice(0,4));
  const cidade = pre>=8901&&pre<=8907?'Blumenau': pre===8911?'Gaspar': pre===8910?'Pomerode': pre===8908?'Indaial': pre===8912?'Timbó': pre===8835?'Brusque': pre===8830?'Itajaí': pre===8933?'Balneário Camboriú': pre===8925?'Jaraguá do Sul':'Blumenau';
  return {rua:'Rua Sete de Setembro', bairro:'Centro', cidade};
}
