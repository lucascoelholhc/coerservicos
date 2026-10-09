/* =========================================================================
   6d. CADASTRO DO PROFISSIONAL — uma pergunta por tela, respostas por toque.
   Pode parar e continuar depois (no sistema real, cada etapa salva rascunho).
   ========================================================================= */
const CAD = [
  {t:'Qual é o seu trabalho?', g:'Seu trabalho'},
  {t:'Seus dados', g:'Você'},
  {t:'Documento e selfie', g:'Você'},
  {t:'O que você faz?', g:'Seu trabalho'},
  {t:'Sua experiência', g:'Seu trabalho'},
  {t:'Quanto você cobra por dia?', g:'Seu trabalho'},
  {t:'Onde você trabalha?', g:'Onde e quando'},
  {t:'Em quais dias você trabalha?', g:'Onde e quando'},
  {t:'Fotos dos seus trabalhos', g:'Fotos'},
  {t:'Conte sobre você', g:'Seu perfil'},
  {t:'Onde receber o dinheiro', g:'Pagamento'},
];

function cadField(id, label, {type='text', hint='', ph='', auto='', mode=''}={}){
  const d = S.cad.dados, e = S.cad.err[id];
  const desc = [hint?`cad-${id}-hint`:'', e?`cad-${id}-err`:''].filter(Boolean).join(' ');
  return `<div class="field"><label for="cad-${id}">${label}</label>${hint?`<p class="hint" id="cad-${id}-hint">${hint}</p>`:''}
    <input id="cad-${id}" type="${type}" data-bind="cad.${id}" value="${esc(d[id]||'')}"${ph?` placeholder="${ph}"`:''}${auto?` autocomplete="${auto}"`:''}${mode?` inputmode="${mode}"`:''}${e?' aria-invalid="true"':''}${desc?` aria-describedby="${desc}"`:''}>
    ${e?`<p class="err" id="cad-${id}-err">${ic('alert',16)}${e}</p>`:''}</div>`;
}
const cadErr = k => S.cad.err[k] ? `<p class="err" id="cad-${k}-err" role="alert">${ic('alert',16)}${S.cad.err[k]}</p>` : '';
function bioDeFrases(d){ return [...(d.frases||[]).map(f => f+'.'), (d.bioExtra||'').trim()].filter(Boolean).join(' '); }

V.cadastro = () => {
  const {etapa, dados:d} = S.cad, tot = CAD.length;
  if (etapa>tot) return `<div class="wrap"><div class="card sucesso wizard">
    <span class="grande">${ic('shield',44)}</span><h1>Cadastro enviado</h1>
    <p style="max-width:44ch;font-size:1.1rem">Agora a COE confere seus documentos. Em até 24 horas você recebe um SMS avisando que seu perfil está no ar.</p>
    <ol class="tl" style="text-align:left;width:100%;max-width:420px">
      <li><span class="dot ok">${ic('check',16)}</span><div class="tl-body"><b>Cadastro recebido</b><span class="small muted">agora</span></div></li>
      <li><span class="dot warn">${ic('clock',16)}</span><div class="tl-body"><b>Conferência do documento e da selfie</b><span class="small muted">até 24 horas</span></div></li>
      <li><span class="dot mute">${ic('user',16)}</span><div class="tl-body"><b>Seu perfil aparece na busca</b><span class="small muted">você recebe um SMS</span></div></li>
    </ol>
    <div class="row wrapx" style="justify-content:center"><a class="btn btn-telha btn-lg" href="#inicio">Voltar ao início</a><a class="btn btn-linha btn-lg" href="#admin-verificacoes">Ver fila de verificação (admin)</a></div></div></div>`;

  const pf = d.prof ? profissao(d.prof) : null;
  let body = '';
  if (etapa===1) body = `<p class="note note-azul">${ic('info')}<span>Este cadastro é para quem quer <b>oferecer serviços</b>. Quer contratar alguém? <a href="#criar-conta-cliente">Crie uma conta de cliente</a>.</span></p><div class="bigopts" role="radiogroup" aria-label="Profissão">${profsAtivas().map(p => `<label class="bigopt" style="padding:10px"><input type="radio" name="cad-prof" id="cad-prof-${p.id}" value="${p.id}" data-bind="cad.prof" data-refresh="1" ${d.prof===p.id?'checked':''}><span class="svgfit" style="width:78px;height:62px;border-radius:4px;overflow:hidden;flex:none">${cena(p.id, CENA_CAT[p.id], 0)}</span><b style="font:800 1.7rem/1 var(--f-display)">${p.nome}</b></label>`).join('')}</div>${cadErr('prof')}
    ${d.prof ? `<fieldset style="margin-top:8px"><legend>Faz outro trabalho também? <span class="muted">(se quiser)</span></legend><div class="opts">${profsAtivas().filter(p => p.id!==d.prof).map((p,i) => chk('cad-outra',i,p.id,d.outras.includes(p.id),p.nome,'cad.outras')).join('')}</div></fieldset>` : ''}`;
  if (etapa===2) body = cadField('nome','Nome completo',{auto:'name'}) + cadField('cel','Celular com WhatsApp',{type:'tel',mode:'tel',ph:'(47) 9 0000-0000',auto:'tel',hint:'Enviamos um código por SMS. Seu número fica escondido até alguém contratar você.'}) + cadField('cpf','CPF',{mode:'numeric',ph:'000.000.000-00'}) + cadField('nasc','Data de nascimento',{type:'date'});
  if (etapa===3) body = `<div class="note note-azul">${ic('shield')}<span>Isso passa segurança para o cliente. Seus documentos <b>nunca</b> aparecem no perfil.</span></div>`
    + uplField('cad_doc','1. Foto do RG ou da CNH',{hint:'A frente do documento, sem reflexo, dando para ler tudo.',capture:'environment',grande:true,botao:'Fotografar documento',err:S.cad.err.cad_doc})
    + uplField('cad_selfie','2. Selfie segurando o documento',{hint:'Seu rosto e o documento aparecendo juntos, num lugar claro.',capture:'user',grande:true,botao:'Tirar selfie',err:S.cad.err.cad_selfie})
    + (d.prof==='eletricista' ? uplField('cad_nr10','3. Certificado de NR-10 (se tiver)',{hint:'Se mandar, seu perfil ganha o selo "NR-10 conferido".',botao:'Fotografar certificado'}) : '');
  if (etapa===4) {
    const profs = [d.prof, ...d.outras].map(profissao);
    body = `<p class="muted">Toque em tudo o que você faz bem. O cliente procura por esses serviços.</p>${profs.map(pp => `<fieldset><legend class="label-sec">${pp.nome}</legend><div class="opts">${pp.servicos.map((s,i) => chk('cad-srv-'+pp.id,i,s,d.servicos.includes(s),s,'cad.servicos')).join('')}</div></fieldset>`).join('')}${cadErr('servicos')}`;
  }
  if (etapa===5) body = `<fieldset><legend>Há quanto tempo trabalha nisso?</legend><div class="listopts">${EXPERIENCIA.map(([v,t]) => `<label class="bigopt"><input type="radio" name="cad-anos" id="cad-anos-${v}" value="${v}" data-bind="cad.anos" ${String(d.anos)===String(v)?'checked':''}><b>${t}</b></label>`).join('')}</div>${cadErr('anos')}</fieldset>
    <fieldset><legend>E as ferramentas?</legend><div class="listopts">${Object.entries(FERRAMENTAS).map(([v,t]) => `<label class="bigopt"><input type="radio" name="cad-ferr" id="cad-ferr-${v}" value="${v}" data-bind="cad.ferramentas" ${d.ferramentas===v?'checked':''}><b>${t}</b></label>`).join('')}</div>${cadErr('ferramentas')}</fieldset>
    <fieldset><legend>Você é MEI? <span class="muted">(se não souber, pode pular)</span></legend><div class="opts">${[['sim','Sim, emito nota'],['nao','Não']].map(([v,t]) => `<label class="opt"><input type="radio" name="cad-mei" id="cad-mei-${v}" value="${v}" data-bind="cad.mei" ${d.mei===v?'checked':''}>${t}</label>`).join('')}</div></fieldset>`;
  if (etapa===6) {
    const cc = calc(Number(d.valor)||0), fx = pf ? pf.faixa : [150,350];
    body = `<p class="muted">Na região, ${pf?pf.plural.toLowerCase():'profissionais'} costumam cobrar entre <b>${brl(fx[0])}</b> e <b>${brl(fx[1])}</b> por dia (exemplo). Use − e + para ajustar.</p>
      <div class="stepper-valor"><button type="button" data-act="valor" data-alvo="cad" data-d="-10" aria-label="Diminuir 10 reais">−</button><div class="money-in"><span>R$</span><input id="cad-valor" type="number" inputmode="numeric" min="80" step="5" value="${esc(d.valor||'')}" data-bind="cad.valor" data-inp="cad-valor" aria-label="Valor da diária" aria-describedby="cad-valor-calc"${S.cad.err.valor?' aria-invalid="true"':''}></div><button type="button" data-act="valor" data-alvo="cad" data-d="10" aria-label="Aumentar 10 reais">+</button></div>
      <p class="note note-ok" id="cad-valor-calc">${ic('wallet')}<span>${CONFIG.TAXA_PAGA_POR==='cliente'?`Você recebe <b id="cad-rec">${brl(cc.recebe)}</b> por dia. O cliente paga <b id="cad-cli">${brl(cc.total)}</b> com a taxa da COE.`:`Você recebe <b id="cad-rec">${brl(cc.recebe)}</b> por dia.`}</span></p>${cadErr('valor')}
      <label class="switch" for="cad-meiaon"><span><b>Aceito meia diária</b><br><span class="small muted">Serviço de até 4 horas</span></span><input type="checkbox" id="cad-meiaon" data-bind="cad.meiaOn" data-refresh="1" ${d.meiaOn?'checked':''}></label>
      ${d.meiaOn ? `<div class="field"><label for="cad-meia">Valor da meia diária</label><div class="money-in"><span>R$</span><input id="cad-meia" type="number" inputmode="numeric" min="50" step="5" value="${esc(d.meia||'')}" data-bind="cad.meia"${S.cad.err.meia?' aria-invalid="true"':''}></div>${cadErr('meia')}</div>` : ''}
      <div class="field"><label for="cad-horario">Seu horário</label><select id="cad-horario" data-bind="cad.horario">${HORARIOS.map(h => `<option ${(d.horario||'7h às 17h')===h?'selected':''}>${h}</option>`).join('')}</select></div>`;
  }
  if (etapa===7) body = `<div class="field"><label for="cad-cidade">Em que cidade você mora?</label><select id="cad-cidade" data-bind="cad.cidade" data-refresh="1"${S.cad.err.cidade?' aria-invalid="true"':''}><option value="">Escolha…</option>${CIDADES.map(c => `<option ${d.cidade===c?'selected':''}>${c}</option>`).join('')}</select>${cadErr('cidade')}<p class="hint">Não achou sua cidade? No sistema real a cidade vem do seu CEP.</p></div>
    <fieldset><legend>Até onde você vai para trabalhar?</legend><div class="listopts">${RAIOS.map(([v,t,k]) => `<label class="bigopt"><input type="radio" name="cad-raio" id="cad-raio-${v}" value="${v}" data-bind="cad.raio" ${Number(d.raio)===v?'checked':''}><span class="stack" style="--g:2px"><b>${t}</b><span class="muted">${k} de onde você mora</span></span></label>`).join('')}</div></fieldset>
    ${d.cidade ? `<fieldset><legend>Outras cidades que você atende <span class="muted">(se quiser)</span></legend><div class="opts">${CIDADES.filter(c => c!==d.cidade).map((c,i) => chk('cad-c',i,c,d.cidades.includes(c),c,'cad.cidades')).join('')}</div></fieldset>` : ''}`;
  if (etapa===8) body = `<div class="dias7">${[1,2,3,4,5,6,0].map(n => `<label class="opt"><input type="checkbox" id="cad-dia-${n}" value="${n}" data-bind="cad.dias" ${d.dias.includes(n)?'checked':''}><span aria-hidden="true">${DOW[n]}</span><span class="sr-only">${DOWL[n]}</span></label>`).join('')}</div>${cadErr('dias')}
    <p class="muted">Você pode bloquear dias específicos depois, na sua agenda.</p>`;
  if (etapa===9) {
    const fotos = S.upl.cad_fotos || [], info = d.fotosInfo || [], srvs = d.servicos.length ? d.servicos : (pf ? pf.servicos : []);
    body = `<p class="muted">Quem mostra trabalhos é mais contratado. Mande pelo menos 1 foto. O ideal são 4 ou mais.</p>
      ${uplField('cad_fotos','Fotos de trabalhos que você já fez',{multiple:true,grande:true,botao:'Adicionar fotos',err:S.cad.err.cad_fotos})}
      <button type="button" class="linkbtn" data-act="exemplo-cad" style="justify-self:start">Usar fotos de exemplo (protótipo)</button>
      ${fotos.length ? `<div class="portf">${fotos.map((src,i) => `<div class="portf-item"><div class="cena"><img src="${src}" alt="Foto ${i+1}"></div>
        <div class="field"><label for="cad-lsrv-${i}" class="small">Que serviço é esse?</label><select id="cad-lsrv-${i}" data-bind-foto="${i}" data-campo="srv"><option value="">Escolha…</option>${srvs.map(s => `<option ${((info[i]||{}).srv)===s?'selected':''}>${esc(s)}</option>`).join('')}</select></div>
        <div class="field"><label for="cad-leg-${i}" class="small">Legenda (se quiser)</label><input id="cad-leg-${i}" value="${esc((info[i]||{}).txt||'')}" data-bind-foto="${i}" data-campo="txt" placeholder="Ex.: muro de 12 m"${S.cad.err['leg'+i]?' aria-invalid="true"':''}>${S.cad.err['leg'+i]?`<p class="err">${ic('alert',14)}${S.cad.err['leg'+i]}</p>`:''}</div></div>`).join('')}</div>` : ''}`;
  }
  if (etapa===10) {
    const fr = [...FRASES.comum, ...(FRASES[d.prof]||[])], bio = bioDeFrases(d);
    body = `<p class="muted">Toque nas frases que combinam com você. Não precisa escrever nada se não quiser.</p>
      <div class="frases" role="group" aria-label="Frases prontas">${fr.map((f,i) => `<label class="opt"><input type="checkbox" id="cad-fr-${i}" value="${esc(f)}" data-bind="cad.frases" data-refresh="1" ${(d.frases||[]).includes(f)?'checked':''}><span class="ic-ck">${ic('check',18)}</span>${esc(f)}</label>`).join('')}</div>
      <div class="field"><label for="cad-bioExtra">Quer contar mais alguma coisa? <span class="muted">(se quiser)</span></label><textarea id="cad-bioExtra" maxlength="300" data-bind="cad.bioExtra" data-inp="bio" data-aviso="cad-bio-aviso" aria-describedby="cad-bio-aviso" placeholder="Ex.: Trabalho com obra há 12 anos. Já fiz muito sobrado aqui no Garcia.">${esc(d.bioExtra||'')}</textarea><div id="cad-bio-aviso" class="stack" style="--g:8px" aria-live="polite">${bioAviso(d.bioExtra,300)}</div>${cadErr('bio')}</div>
      <div class="stack" style="--g:6px"><span class="label-sec">Assim vai aparecer no seu perfil</span><p class="previa">${bio?esc(bio):'<span class="muted">Toque em algumas frases acima.</span>'}</p></div>`;
  }
  if (etapa===11) {
    const linha = (rot, val, e) => `<div><span class="muted small b">${rot}</span><span>${val}</span><button type="button" class="linkbtn small" data-act="cad-ir" data-e="${e}">Mudar</button></div>`;
    body = `<fieldset><legend>Tipo de chave Pix</legend><div class="opts">${['CPF','Celular','E-mail','Chave aleatória'].map(t => `<label class="opt"><input type="radio" name="cad-pixTipo" id="cad-pix-${t.replace(/\W/g,'')}" value="${t}" data-bind="cad.pixTipo" ${d.pixTipo===t?'checked':''}>${t}</label>`).join('')}</div>${cadErr('pixTipo')}</fieldset>
      ${cadField('pixChave','Sua chave Pix',{hint:'A chave precisa estar no seu nome. A COE confere antes do primeiro pagamento.'})}
      <h2 style="font-size:1.8rem;margin-top:10px">Confira antes de enviar</h2>
      <div class="revisao">
        ${linha('Nome', esc(d.nome||'—'), 2)}
        ${linha('Trabalho', pf ? pf.nome + (d.outras.length?` e ${d.outras.map(o => profissao(o).nome.toLowerCase()).join(', ')}`:'') : '—', 1)}
        ${linha('Serviços', d.servicos.join(', ')||'—', 4)}
        ${linha('Experiência', (EXPERIENCIA.find(([v]) => String(v)===String(d.anos))||[,'—'])[1], 5)}
        ${linha('Diária', d.valor?brl(Number(d.valor))+(d.meiaOn&&d.meia?` · meia ${brl(Number(d.meia))}`:''):'—', 6)}
        ${linha('Onde', d.cidade?`${d.cidade} · até ${d.raio} km${d.cidades.filter(c => c!==d.cidade).length?` · + ${d.cidades.filter(c => c!==d.cidade).join(', ')}`:''}`:'—', 7)}
        ${linha('Dias', d.dias.length?[1,2,3,4,5,6,0].filter(n => d.dias.includes(n)).map(n => DOW[n]).join(', '):'—', 8)}
        ${linha('Fotos', `${(S.upl.cad_fotos||[]).length} fotos`, 9)}
      </div>
      <label class="row start" for="cad-termos"><input type="checkbox" class="cb" id="cad-termos" data-bind="cad.termos" ${d.termos?'checked':''}><span>Li e aceito os termos de uso e a política de privacidade.</span></label>
      <label class="row start" for="cad-regra"><input type="checkbox" class="cb" id="cad-regra" data-bind="cad.regra" ${d.regra?'checked':''}><span>Entendi que não posso passar meu telefone antes de o cliente pagar, e que receber por fora tira a garantia do pagamento.</span></label>
      ${cadErr('termos')}`;
  }
  const g = CAD[etapa-1];
  return `<div class="wrap"><div class="wizard stack" style="--g:18px">
    <div class="stack" style="--g:10px">
      <div class="row between"><p class="eyebrow">Passo ${etapa} de ${tot} · ${g.g}</p><button type="button" class="linkbtn small" data-act="exemplo-cad">Preencher exemplo</button></div>
      <div class="etapas-mini" role="progressbar" aria-label="Progresso do cadastro" aria-valuemin="1" aria-valuemax="${tot}" aria-valuenow="${etapa}">${CAD.map((_,i) => `<i class="${i+1<etapa?'feito':i+1===etapa?'atual':''}"></i>`).join('')}</div>
      <h1>${g.t}</h1></div>
    <form class="stack" style="--g:18px" data-form="cadastro" novalidate>${body}
      <div class="wizard-foot">${etapa>1?`<button type="button" class="btn btn-linha btn-lg" data-act="cad-voltar" aria-label="Voltar">${ic('back',20)}</button>`:''}<button class="btn btn-telha btn-lg" type="submit">${etapa===tot?'Enviar cadastro':'Continuar'}${etapa<tot?ic('next',20):''}</button></div>
      ${etapa>1&&etapa<tot?`<button type="button" class="linkbtn" data-act="toast" data-msg="Guardamos o que você preencheu. É só voltar pelo mesmo celular." style="justify-self:center">Continuar depois</button>`:''}
    </form></div></div>`;
};
