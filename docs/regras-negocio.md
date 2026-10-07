# Regras de negócio e requisitos: COE Serviços

> Fonte oficial: documento "COE Serviços — Regras de negócio e requisitos" (atualizado em 02/10/2026).
> Itens marcados **A DEFINIR** ou listados em **Pontos em aberto** ainda não foram decididos. **Pergunte antes de implementar.**

A COE é um marketplace de serviços por diária: o cliente paga antes, o dinheiro fica guardado e cada diária só é liberada ao profissional quando o cliente aprova o dia (ou sozinha após 12 h). Lançamento no Vale do Itajaí (SC), sem ficar preso a uma cidade, com 5 profissões ativas e todos os profissionais verificados.

## Decisões já tomadas (resumo)

| Tema | Decisão | Regra |
| --- | --- | --- |
| Banco | PostgreSQL 16 + Flyway | PA01 |
| Front | React + TypeScript + Vite | PA04 |
| Autenticação | JWT: acesso de 15 min na memória, refresh de 30 dias em cookie HttpOnly, uma sessão por aparelho, com **teto de 90 dias desde o login**; login com celular ou e-mail + senha; MFA obrigatório para ADMIN e opcional para os demais | PA03 |
| Confirmação do celular | Obrigatória para o profissional (sem ela não vai para análise nem aparece na busca); opcional para o cliente | RN08 |
| Contato não confirmado | Não fica reservado: quem confirmar primeiro (o dono) fica com o dado | RN61 |
| Reembolso | Devolve exatamente o que o cliente pagou pela diária (diária + comissão se a taxa é do cliente; só a diária se é do profissional) | RN34, PA06 |
| LC 150 | Máx. 2 diárias da mesma diarista para o mesmo cliente em **qualquer janela de 7 dias seguidos**; meia diária conta como dia (confirmado com o advogado) | RN51 |
| Agenda | Uma diária por profissional por dia, inteira ou meia (duas meias no mesmo dia só na fase 2) | RN26 |
| Comissão | 10%, configurável até o **teto de 30%** (CHECK no banco); **paga pelo cliente** (somada ao total); copiada para o contrato na compra | RN31, PA05 |
| Idade mínima | 18 anos, obrigatório para o profissional | RN08 |
| Liberação automática | 12 h a partir do "Terminei o dia" | RN38, PA08 |
| Falta do profissional | Reembolso integral da diária; cliente decide sobre as outras; falta registrada; inativação manual e reversível | RN44a–RN44e, PA07 |
| Retenção LGPD | CPF e Pix de quem excluiu a conta: 5 anos (confirmado com o contador), depois expurgo | RN60, RNF16 |
| Parâmetros | Todos na tabela `configuracao`, nunca fixos no código | RN31, RN38, RN44, RN49, RN52 |


## Visão geral

**Proposta de valor**

- Para o cliente: "Pague a diária pelo app e só libere quando o dia for cumprido."
- Para o profissional: "O cliente paga antes de você sair de casa."
- Para a COE: comissão sobre cada diária e controle da relação, evitando que o contato vaze antes da contratação.

**Glossário**

| Termo | Significado |
| --- | --- |
| Diária | Um dia de trabalho de um profissional para um cliente, com valor fechado |
| Meia diária | Serviço de até 4 horas, com valor próprio definido pelo profissional |
| Contrato | Conjunto de diárias contratadas de uma vez com o mesmo profissional |
| Pedido | O que o cliente descreve na contratação: serviços, texto, fotos do local, material e endereço |
| Custódia (valor guardado) | Dinheiro pago pelo cliente e retido pela plataforma até a liberação |
| Liberação | Repasse do valor de uma diária ao Pix do profissional |
| Contestação (reclamação) | Pedido do cliente para travar uma diária por problema, analisado pela COE |
| Verificação | Conferência de documento, selfie, CPF e fotos antes de o perfil ir para a busca |
| Censura de contato | Bloqueio automático de telefone, e-mail e redes sociais antes da contratação |
| Plano recorrente | Contratação semanal ou quinzenal de diarista, com substituição garantida |

**Perfis de usuário**

| Perfil | Quem é | O que faz |
| --- | --- | --- |
| Visitante | Qualquer pessoa sem login | Navega, busca, vê perfis e regras |
| Cliente | Quem contrata | Conversa, contrata, paga, aprova ou contesta diárias, avalia |
| Profissional | Pedreiro, pintor, eletricista, jardineiro ou diarista | Cadastra-se, atende, avisa chegada, conclui o dia com foto, recebe |
| Admin (equipe COE) | Dono e equipe da plataforma | Verifica cadastros, decide disputas, modera contato, gerencia categorias e regras |

Um mesmo celular pode ter os perfis de cliente e de profissional (login único, conta com dois perfis).

## Regras de negócio

São 61 regras em 11 grupos. Toda regra que mexe com dinheiro, status ou contato é aplicada no backend; o front só antecipa o aviso. Valores marcados como configuráveis ficam na tela de Configurações do admin.

### 1. Catálogo e categorias

- **RN01** O catálogo tem três níveis: área → profissão → serviços.
- **RN02** Profissões ativas no lançamento: pedreiro, pintor, eletricista (Obra e reforma), diarista e jardineiro (Casa e jardim).
- **RN03** Encanador e montador de móveis aparecem como "em breve" e não aceitam cadastro nem busca.
- **RN04** O admin cria profissões e serviços. Uma profissão só pode ser ativada se tiver pelo menos 1 serviço.
- **RN05** Cada profissional tem uma profissão principal e pode marcar outras. Ele aparece na busca de todas elas.
- **RN06** A profissão principal só muda com nova verificação, pelo suporte.

### 2. Cadastro e verificação do profissional

- **RN07** O cadastro é gratuito e sem mensalidade.
- **RN08** Dados obrigatórios: nome, celular com SMS confirmado, CPF, data de nascimento, foto do RG ou CNH, selfie segurando o documento, ao menos 1 serviço, experiência, ferramentas, valor da diária, cidade, ao menos 1 dia de trabalho, ao menos 1 foto de trabalho, texto "Sobre você" e chave Pix. **Celular confirmado por SMS é sempre obrigatório para o profissional**: sem ele o cadastro não vai para análise e o perfil não aparece na busca (o cliente pode contratar sem confirmar, RF03). **Idade mínima de 18 anos, obrigatória**: a data de nascimento é conferida no cadastro e, abaixo disso, o cadastro é recusado com a mensagem "Para trabalhar na COE é preciso ter 18 anos ou mais."
- **RN09** Valor mínimo da diária: R$ 80. Meia diária: mínimo R$ 50 e menor que a diária.
- **RN10** O "Sobre você" pode ser montado com frases prontas e precisa de pelo menos 25 caracteres.
- **RN11** O perfil só aparece na busca depois que o admin aprova a verificação. Meta: resposta em até 24 horas.
- **RN12** A verificação confere: CPF válido e no nome da pessoa, documento legível e válido, selfie igual ao documento, fotos de trabalhos reais e texto sem contato. Eletricista com certificado de NR-10 tem esse item a mais.
- **RN13** O admin só aprova com todos os itens conferidos. Pode também pedir correção ou recusar; o profissional é avisado por SMS com o motivo.
- **RN14** Eletricista com NR-10 aprovado recebe o selo "NR-10 conferido" no perfil.
- **RN15** A chave Pix precisa estar no nome e CPF do profissional e é conferida antes do primeiro repasse.
- **RN16** Documentos e selfie nunca aparecem no perfil público.

### 3. Área de atendimento

- **RN17** O profissional informa a cidade onde mora, até onde vai (5, 10, 20 ou 40 km) e outras cidades que atende.
- **RN18** A busca filtra por cidade atendida. O sistema não fica preso a uma cidade.
- **RN19** Se o endereço do serviço estiver fora das cidades do profissional, o cliente é avisado e orientado a confirmar pelo chat antes de pagar.

### 4. Busca e perfil público

- **RN20** Só aparecem profissionais com status ativo (não pausados nem suspensos).
- **RN21** A ordenação padrão é "quem está livre antes"; empate por mais experiência. "Melhor avaliação" só aparece quando houver avaliações.
- **RN22** O perfil público mostra valor, experiência, horário, ferramentas, serviços, cidades, fotos, calendário e avaliações; telefone e WhatsApp ficam mascarados.
- **RN23** Sem avaliações, o perfil mostra "Novo na COE". Nunca se exibe nota inventada.

### 5. Contratação

- **RN24** Só cliente logado contrata. Visitante que tenta contratar vai para Entrar/Criar conta e volta ao mesmo perfil depois.
- **RN25** O cliente escolhe dias livres no calendário (próximas 4 semanas). Dias passados, de folga ou já ocupados não podem ser escolhidos.
- **RN26** Com meia diária disponível, o cliente escolhe o período (inteira ou meia). O profissional tem no máximo uma diária por dia, inteira ou meia: duas meias-diárias no mesmo dia não são permitidas no MVP (período manhã/tarde fica para a fase 2).
- **RN27** O pedido exige ao menos um serviço marcado ou uma descrição com 15+ caracteres. Fotos do local são opcionais (até 8).
- **RN28** Material: "já tenho", "vou comprar" ou "combino pelo chat". O material é sempre do cliente.
- **RN29** Endereço: um salvo na conta ou outro informado por CEP + número. O profissional só vê o endereço completo depois do pagamento; antes, só a cidade.

### 6. Pagamento e comissão

- **RN30** O pagamento é antecipado, por Pix ou cartão, e fica em custódia.
- **RN31** Comissão de 10% por diária (o percentual é configurável, com **teto de 30%** garantido no banco). A comissão é **paga pelo cliente**, somada ao total (PA05).
- **RN32** O contrato nasce aguardando pagamento e só vira **pago** (diárias pagas, contato liberado) quando o gateway confirma o pagamento por webhook, nunca pelo clique do cliente. Sem confirmação no prazo, o contrato expira e as diárias são canceladas.
- **RN33** Toda movimentação vira uma linha no livro-caixa (recebido, retido, liberado, reembolsado, comissão). O saldo é calculado, nunca editado.
- **RN34** A comissão só vira receita da COE quando a diária é liberada. Em reembolso, o cliente recebe exatamente o que pagou por aquela diária: diária + comissão quando a taxa é do cliente; só a diária quando a taxa é do profissional.

### 7. Execução, aprovação e liberação

- **RN35** No dia, o profissional pode avisar "Cheguei na obra"; o horário fica registrado e o cliente é avisado.
- **RN36** Para concluir o dia, o profissional envia ao menos 1 foto do serviço; recado é opcional e passa pela censura de contato.
- **RN37** Concluído o dia, o cliente pode aprovar ou contestar. Aprovar libera o valor daquela diária e não pode ser desfeito.
- **RN38** Sem resposta do cliente em 12 horas (configurável) contadas a partir do "Terminei o dia" (`terminou_em`), a diária é liberada automaticamente (PA08).
- **RN39** Aprovação manual e liberação automática não podem gerar dois repasses (trava de concorrência + idempotência).
- **RN40** O status da diária só muda pelas transições do diagrama abaixo; qualquer outra é erro.

| De | Para | Quem / como |
| --- | --- | --- |
| `agendada` | `paga` | gateway confirma o pagamento (webhook) |
| `paga` | `andamento` | profissional marca "Cheguei" |
| `andamento` | `aguardando` | profissional marca "Terminei o dia" com foto |
| `aguardando` | `liberada` | cliente aprova, ou job após 12 h sem resposta |
| `paga` / `andamento` / `aguardando` | `contestada` | cliente reclama |
| `contestada` | `liberada` ou `reembolsada` | decisão do admin |
| `agendada` | `cancelada` | pedido não pago a tempo (expirou) ou cancelado antes do pagamento |

- `liberada`, `reembolsada` e `cancelada` são **estados finais**.
- Qualquer transição fora desta tabela é erro. Toda troca grava `historico_diaria`.
- No máximo **uma** liberação ou **um** reembolso por diária (garantido também no banco).

A reclamação pode ser aberta com a diária paga, em andamento ou aguardando aprovação; só aquele dia fica travado até a equipe decidir.

### 8. Contestação e disputa

- **RN41** O cliente pode contestar uma diária paga, em andamento ou aguardando aprovação (inclui falta do profissional).
- **RN42** A contestação exige motivo e descrição (10+ caracteres); fotos são opcionais.
- **RN43** Só a diária contestada fica travada. As outras do contrato seguem normais.
- **RN44** O profissional pode responder. O admin decide em até 48 horas (configurável): libera ao profissional ou reembolsa o cliente.
- **RN44a** Falta do profissional confirmada (disputa com motivo `nao_compareceu` decidida a favor do cliente): o cliente recebe **reembolso integral** da diária da falta (diária + comissão, tudo o que pagou por ela).
- **RN44b** As outras diárias do contrato: o cliente escolhe se mantém ou cancela. Se cancelar, as diárias futuras também são reembolsadas integralmente.
- **RN44c** Toda falta confirmada é registrada numa tabela só de inserção (`ocorrencia_profissional`), que fica retida mesmo se a conta for excluída.
- **RN44d** O painel do admin mostra as faltas de cada profissional, com histórico, e alerta a partir de 2 faltas em 90 dias (parâmetros `FALTAS_ALERTA` e `FALTAS_JANELA_DIAS`).
- **RN44e** A inativação do profissional é decisão **manual** do admin, nunca automática: status `suspenso` com motivo, auditado. O cadastro **continua inteiro** e pode ser **reativado** pelo admin, também auditado. A mesma pessoa (mesmo CPF, `cpf_hash`) não cria outro cadastro de profissional: se voltar, volta pelo mesmo.

### 9. Contato e censura

- **RN45** Antes do pagamento, não é permitido trocar telefone, e-mail, links ou redes sociais.
- **RN46** A censura vale para: chat antes da contratação, "Sobre você", legendas de fotos, descrição do pedido e recado do fim do dia.
- **RN47** Bloqueia: telefone com ou sem separadores, números por extenso, e-mail (inclusive "arroba"), links, @perfil, palavras como zap/whats/insta/telegram e pedidos como "me liga". **CEP passa** (PA13): o formato `00000-000`, ou 8 dígitos precedidos de "CEP", não é tratado como telefone; telefone continua bloqueado.
- **RN48** A mensagem bloqueada não é entregue; o autor vê o motivo e o evento vai para a moderação.
- **RN49** Depois de 3 tentativas (configurável), a conta vai para análise. O admin pode advertir, suspender ou marcar falso positivo.
- **RN50** Confirmado o pagamento, telefone e WhatsApp dos dois lados são liberados e o chat deixa de ser filtrado.

### 10. Diarista e planos recorrentes

- **RN51** Pela LC 150/2015, o mesmo cliente contrata a mesma diarista por no máximo 2 dias em qualquer janela de 7 dias seguidos (janela móvel, não semana fixa), contando os contratos que já existem. Meia diária conta como um dia. A janela móvel foi confirmada com o advogado.
- **RN52** O limite semanal não pode ser configurado acima de 2.
- **RN53** Planos semanal e quinzenal: o cliente escolhe o primeiro dia e o sistema marca os seguintes; a próxima diária é cobrada 2 dias antes.
- **RN54** Se a diarista do plano faltar, a COE envia outra verificada no mesmo dia ou devolve o valor.

### 11. Avaliações, perfil e conta

- **RN55** Só avalia quem teve diária paga e aprovada pelo app. Nota de 1 a 5 e comentário opcional.
- **RN56** O profissional pode pausar o perfil; quem já contratou continua vendo.
- **RN57** Fotos novas do portfólio passam por conferência antes de aparecer.
- **RN58** Um login serve para cliente e profissional. Entra-se com celular **ou** e-mail + senha.
- **RN59** Toda ação do admin gera registro de auditoria (quem, quando, o quê, antes e depois).
- **RN60** O cliente pode baixar seus dados e pedir exclusão da conta; contratos em andamento precisam terminar antes. A exclusão é por **anonimização** (a conta nunca é apagada): nome, celular, e-mail e senha são removidos. Do profissional, CPF, chave Pix e data de nascimento ficam retidos por **5 anos** (dados financeiros e fiscais; prazo confirmado com o contador). Depois disso, um job de expurgo apaga CPF, chave Pix e data de nascimento; o `cpf_hash` só é mantido para quem estiver inativado (`suspenso`), para que a mesma pessoa não crie outro cadastro. Voltar a trabalhar depois de excluir a conta: **A DEFINIR** (reativação); enquanto isso, o mesmo CPF não cria novo cadastro.
- **RN61** Celular ou e-mail **não confirmado não fica reservado**. Se o dono verdadeiro confirmar o dado (código por SMS ou link por e-mail) em outra conta, o dado passa para a conta dele; a conta que o tinha perde esse dado e precisa cadastrar e confirmar outro antes de voltar a usar o app. Como o login aceita celular ou e-mail (RN58), quem perdeu um deles continua entrando pelo outro. **Fluxo (CORE-04, dia 7b):** no cadastro, dado não confirmado de outra conta responde que pode ser reivindicado; quem prova a posse (código por SMS ou link por e-mail) recebe um comprovante de 30 min e cria a conta já com o dado confirmado; a conta antiga perde o dado (e o MFA, se perder o celular), tem as sessões encerradas e fica limitada até cadastrar e confirmar outro contato (só entrar, ver a conta, cadastrar e confirmar contato e, a partir do DOM-08, terminar diárias já pagas). Dado confirmado nunca é tomado. Se a conta antiga ficaria sem nenhum contato, a transferência é recusada e vai para o suporte (a decidir antes do lançamento: anonimizar automaticamente conta sem histórico financeiro).


## Requisitos funcionais

São 58 requisitos em 7 módulos, cada um ligado às regras que implementa. Prioridade: **MVP** = necessário para lançar; **F2** = logo depois do lançamento.

### Acesso e conta

| ID | Requisito | Regras | Prioridade |
| --- | --- | --- | --- |
| RF01 | Entrar com celular ou e-mail + senha, ou com código por SMS; recuperar senha; segundo passo por SMS obrigatório para ADMIN e opcional (ligar/desligar) para cliente e profissional; sair de todos os aparelhos. **Fluxo (CORE-04):** senha certa com MFA exigido → SMS + resposta "falta o código" com um desafio de uso único (5 min); o código certo libera a entrada. Entrar só com código: vale para celular confirmado, nunca para ADMIN nem para quem ligou o MFA (seria um fator só). Código: 6 dígitos, 5 min, uso único, 5 tentativas; um código novo anula o anterior; no máximo 1 SMS a cada 60 s e 5 por hora por celular. Pedir código responde sempre igual, tenha ou não conta | RN58, RN61 | MVP |
| RF02 | Criar conta perguntando primeiro "contratar" ou "trabalhar" | RN58 | MVP |
| RF03 | Criar conta de cliente com nome, celular, e-mail, CEP e senha, com aceite dos termos; confirmar o celular é opcional para o cliente (pode contratar sem confirmar) | RN45, RN61 | MVP |
| RF04 | Voltar à tela de origem depois de entrar ou criar conta | RN24 | MVP |
| RF05 | Manter endereços salvos, cartão e preferências de aviso do cliente | RN29 | MVP |
| RF06 | Baixar meus dados e pedir exclusão da conta | RN60 | MVP |

### Busca e perfil público

| ID | Requisito | Regras | Prioridade |
| --- | --- | --- | --- |
| RF07 | Tela inicial com as profissões ativas e preço inicial de cada uma | RN02 | MVP |
| RF08 | Buscar por profissão, serviço, cidade, dia livre, preço máximo, experiência, ferramentas, meia diária e texto | RN18 | MVP |
| RF09 | Ordenar por livre antes, menor preço, experiência e (com histórico) avaliação | RN21 | MVP |
| RF10 | Filtros ativos em etiquetas removíveis; no celular, filtros em folha de baixo | — | MVP |
| RF11 | Busca sem resultado com "Me avise" quando chegar alguém | — | F2 |
| RF12 | Perfil público com fotos, serviços, cidades, calendário, avaliações e contato mascarado | RN22, RN23 | MVP |
| RF13 | Páginas "Como funciona", "Categorias" e "Para profissionais" | — | MVP |

### Contratação e pagamento (cliente)

| ID | Requisito | Regras | Prioridade |
| --- | --- | --- | --- |
| RF14 | Escolher dias no calendário e período (inteira/meia) | RN25, RN26 | MVP |
| RF15 | Validar o limite da LC 150 (2 diárias em qualquer janela de 7 dias), com aviso | RN51 | MVP |
| RF16 | Contratar plano semanal ou quinzenal de diarista | RN53, RN54 | F2 |
| RF17 | Montar o pedido: serviços, descrição, fotos do local, material e endereço por CEP | RN27–RN29 | MVP |
| RF18 | Mostrar resumo com valor por diária, taxa e total | RN31 | MVP |
| RF19 | Pagar por Pix (QR e copia e cola) ou cartão | RN30 | MVP |
| RF20 | Marcar o contrato como pago e liberar o telefone só após confirmação do gateway | RN32, RN50 | MVP |

### Acompanhamento (cliente)

| ID | Requisito | Regras | Prioridade |
| --- | --- | --- | --- |
| RF21 | Listar contratos ativos e histórico, com pagamentos por diária | — | MVP |
| RF22 | Linha do tempo por diária com status, chegada, foto e recado | RN35, RN36 | MVP |
| RF23 | Aprovar o dia com confirmação e avaliar em seguida | RN37, RN55 | MVP |
| RF24 | Contestar diária com motivo, descrição e fotos | RN41–RN43 | MVP |
| RF25 | Aviso de aprovação pendente com o horário da liberação automática | RN38 | MVP |
| RF26 | Chat antes da contratação (com censura) e depois (livre) | RN45–RN50 | MVP |

### Cadastro do profissional

| ID | Requisito | Regras | Prioridade |
| --- | --- | --- | --- |
| RF27 | Página "Para profissionais" antes do cadastro | — | MVP |
| RF28 | Cadastro em 11 passos, uma pergunta por tela, com barra de progresso | RN08 | MVP |
| RF29 | Escolher profissão principal e outras por figura | RN05 | MVP |
| RF30 | Enviar foto do documento, selfie e (eletricista) certificado de NR-10 | RN12, RN14 | MVP |
| RF31 | Definir valor com botões −/+ e mostrar a faixa da região e quanto recebe | RN09, RN31 | MVP |
| RF32 | Definir cidade, raio e outras cidades | RN17 | MVP |
| RF33 | Enviar fotos de trabalhos com serviço e legenda | RN46 | MVP |
| RF34 | Montar o "Sobre você" com frases prontas e texto opcional | RN10 | MVP |
| RF35 | Revisar tudo, informar Pix e aceitar a regra de contato | RN15, RN45 | MVP |
| RF36 | Salvar rascunho e continuar depois | — | F2 |

### App do profissional

| ID | Requisito | Regras | Prioridade |
| --- | --- | --- | --- |
| RF37 | Tela "Hoje" com a obra do dia, endereço, mapa e telefone do cliente | RN50 | MVP |
| RF38 | Botão "Cheguei na obra" | RN35 | MVP |
| RF39 | "Terminei o dia": foto, recados prontos e envio | RN36 | MVP |
| RF40 | Ver o pedido do cliente com fotos e material | RN29 | MVP |
| RF41 | Agenda com calendário e diárias marcadas | — | MVP |
| RF42 | "Seu dinheiro": já caiu, vai cair e parado em reclamação | RN33 | MVP |
| RF43 | Conversas com clientes interessados e contratantes | RN45 | MVP |
| RF44 | Editar perfil: valor, meia diária, dias, horário, serviços, cidades, texto e fotos | RN57 | MVP |
| RF45 | Pausar e reativar o perfil | RN56 | MVP |
| RF46 | Ver avaliações recebidas | RN55 | F2 |
| RF47 | Status das diárias em palavras simples (Garantido, Esperando o cliente, Pago, Reclamação) | — | MVP |

### Painel administrativo

| ID | Requisito | Regras | Prioridade |
| --- | --- | --- | --- |
| RF48 | Visão geral: diárias no mês, receita, valor guardado, profissionais ativos e alertas | — | MVP |
| RF49 | Fila de verificação com checklist; aprovar, pedir correção ou recusar | RN11–RN13 | MVP |
| RF50 | Disputas com os dois lados, fotos e conversa; liberar ou reembolsar | RN44 | MVP |
| RF51 | Moderação de contato: advertir, suspender, falso positivo | RN48, RN49 | MVP |
| RF52 | Lista de profissionais com busca, status e suspensão | RN20 | MVP |
| RF53 | Lista de clientes com contratos e total pago | — | MVP |
| RF54 | Financeiro por diária: valor, taxa, status e repasse | RN33, RN34 | MVP |
| RF55 | Gerenciar áreas, profissões e serviços; ativar e desativar | RN01–RN04 | MVP |
| RF56 | Configurar comissão, 12 h, 48 h, limite LC 150, tentativas e alerta de faltas | RN31, RN38, RN44, RN44d, RN49, RN52 | MVP |
| RF57 | Buscas sem resultado por profissão e cidade, para recrutar | — | F2 |
| RF58 | Registro de auditoria das ações do admin | RN59 | MVP |

## Requisitos não funcionais

### Segurança
- **RNF01** Autenticação com Spring Security e JWT (PA03); senhas com BCrypt ou Argon2; papéis CLIENTE, PROFISSIONAL e ADMIN. **MFA**: obrigatório para o **ADMIN**; opcional para cliente e profissional (quem quiser liga o segundo passo com código por SMS). O login padrão é celular **ou** e-mail + senha. Entrar só com código por SMS continua como alternativa à senha (RF01). A confirmação do celular (RN08) é outra coisa: obrigatória para o profissional, opcional para o cliente.
- **RNF02** Autorização por objeto: toda consulta de contrato, diária, conversa ou documento confere se o usuário é dono ou parte (proteção contra IDOR).
- **RNF03** Limite de tentativas (rate limit) em login, envio de SMS, recuperação de senha e mensagens do chat.
- **RNF04** Toda validação e a censura de contato rodam no servidor; o front só antecipa o aviso.
- **RNF05** Segredos (chaves do gateway, banco, SMS) só em variáveis de ambiente ou cofre; nada no repositório.
- **RNF06** Webhooks do gateway com verificação de assinatura, corpo bruto guardado e idempotência por id de evento (só eventos com assinatura válida ocupam o id).
- **RNF07** Uploads: checagem de tipo real e tamanho, remoção de metadados EXIF (localização) e nome de arquivo gerado pelo servidor.
- **RNF08** Log de auditoria imutável para ações do admin e para toda movimentação de dinheiro.

### Dinheiro e consistência
- **RNF09** Valores em BigDecimal no Java e NUMERIC no banco; nunca float ou double.
- **RNF10** Livro-razão (ledger) de partidas dobradas: saldo é sempre soma de lançamentos, nunca campo editável.
- **RNF11** Liberação, reembolso e mudança de estado da diária em transação única com trava (lock); no máximo uma liberação ou um reembolso por diária.
- **RNF12** Job da liberação automática em 12 h idempotente e seguro para rodar em mais de uma instância.

### LGPD e privacidade
- **RNF13** CPF, chave Pix e endereço criptografados no banco; exibidos mascarados.
- **RNF14** Documentos e selfies em bucket privado, acessados só por URL assinada com validade curta.
- **RNF15** Dados pessoais mascarados nos logs (telefone, CPF, e-mail, endereço).
- **RNF16** O usuário pode baixar seus dados e pedir exclusão da conta; dados financeiros e fiscais (inclusive CPF, chave Pix e data de nascimento do profissional) ficam retidos por 5 anos e depois são expurgados (RN60).
- **RNF17** Endereço completo e telefone só aparecem para a outra parte depois do pagamento confirmado.
- **RNF18** Consentimento registrado com data e versão dos termos aceitos.

### Usabilidade e acessibilidade
- **RNF19** Mobile-first, funcionando bem a partir de 360 px de largura.
- **RNF20** WCAG 2.1 nível AA: rótulo em todo campo, navegação por teclado, foco visível, contraste mínimo.
- **RNF21** Alvos de toque com pelo menos 44 px; status nunca indicado só por cor.
- **RNF22** Linguagem simples em pt-BR, pensada para o profissional com pouca familiaridade com apps.

### Desempenho e operação
- **RNF23** Busca de profissionais respondendo em até 1 s no uso normal (meta a validar em teste de carga).
- **RNF24** Imagens redimensionadas e comprimidas no upload; miniaturas para listas.
- **RNF25** Migrações de banco versionadas com Flyway.
- **RNF26** Health check, métricas e logs estruturados (Spring Actuator) desde o MVP.
- **RNF27** Backup diário do banco com teste de restauração.

### Qualidade
- **RNF28** Fluxo TDD: plano, teste, código, revisão e verificação.
- **RNF29** Cobertura mínima de 80% no geral e 100% em dinheiro (cálculo, ledger, liberação), máquina de estados da diária, censura de contato e limite da LC 150.

## Pontos em aberto

| # | Decisão | Opções | Situação / impacto |
| --- | --- | --- | --- |
| PA01 | Banco de dados | MySQL ou PostgreSQL | **Decidido: PostgreSQL 16 + Flyway** |
| PA02 | Gateway de pagamento com custódia e split | Asaas, Pagar.me, Iugu ou Mercado Pago | **A DEFINIR** até o dia 52 do cronograma. Até lá, adaptador falso. Ação: escolher e abrir o sandbox **até o dia 40** |
| PA03 | Autenticação | JWT ou sessão com cookie | **Decidido: JWT.** token de acesso de **15 min** (HS256, chave com `kid` no Secrets Manager), guardado **só na memória** do front e enviado no header `Authorization`; dentro dele só o id do usuário, os papéis, emissão, expiração e um id único (nada de celular, CPF ou nome). Refresh token de **30 dias**, renovado a cada uso, em cookie **HttpOnly/Secure/SameSite=Strict** enviado só ao endpoint de renovação, guardado como **hash** no banco; revogado no logout, na troca de senha e quando o admin inativa a conta; refresh reutilizado (sinal de roubo) revoga todos os tokens daquele login. **Uma sessão por aparelho**, com "sair de todos os aparelhos". **MFA**: obrigatório para o **ADMIN**; opcional para cliente e profissional (quem quiser liga o segundo passo com código por SMS). O login padrão é celular **ou** e-mail + senha. Entrar só com código por SMS continua como alternativa à senha (RF01). A confirmação do celular (RN08) é outra coisa: obrigatória para o profissional, opcional para o cliente. Comissão com teto de 30% (CHECK na V12). **Teto absoluto da sessão: 90 dias desde o login** (depois disso, senha de novo), validado na renovação; o refresh renovado nunca passa desse teto. **Reuso de refresh revoga só a família daquele aparelho** (uma família = um login = um aparelho), com registro no log; o aviso por e-mail ao usuário fica para quando houver envio de e-mail (DOM-11). As tabelas `spring_session*` da V2 saem na V12 |
| PA04 | Front final | HTML puro ou React | **Decidido: React + TypeScript + Vite**, seguindo as telas do protótipo HTML aprovado |
| PA05 | Quem paga a comissão de 10% | Cliente (somada) ou profissional (descontada) | **Decidido: cliente** (somada ao total). Valor padrão da `configuracao` confirmado |
| PA06 | Reembolso de diária | Devolve só a diária ou também a comissão | **Decidido:** devolve o que o cliente pagou pela diária (RN34) |
| PA07 | Profissional falta | Cancela só o dia ou todas as diárias futuras do contrato | **Decidido** (RN44a–RN44e): reembolso integral da diária da falta; o cliente escolhe manter ou cancelar as outras; falta registrada; inativação manual e reversível |
| PA08 | Marco inicial das 12 h | Envio da foto do fim do dia ou horário fixo (ex.: 20 h) | **Decidido:** a partir do "Terminei o dia" (`terminou_em`) |
| PA09 | Política de cancelamento pelo cliente | Prazos e percentual devolvido | **A DEFINIR**. Sem regra, nenhum reembolso por cancelamento é implementado |
| PA10 | Meia diária | Até 4 h, preço livre ou metade da diária | **Decidido em parte:** uma diária por profissional por dia (RN26) e meia conta como dia na LC 150 (RN51); duração e preço em aberto |
| PA11 | Substituição de profissional | Equipe COE indica outro ou só reembolso | **A DEFINIR** (planos recorrentes, fase 2) |
| PA12 | Uma conta com dois perfis | Mesmo login ou contas separadas | Mesmo login com dois papéis (RN58) |
| PA13 | Censura: CEP marcado como telefone | Liberar o padrão 00000-000 ou pedir CEP só em campo próprio | **Decidido:** o formato de CEP passa (`00000-000` ou 8 dígitos precedidos de "CEP"); telefone continua bloqueado |
| PA14 | Leitura de texto em fotos (OCR) | MVP, fase 2 ou não fazer | **A DEFINIR** |
| PA15 | Selo NR-10 do eletricista | Conferência manual ou integração | Conferência manual no MVP (RN12, RN14) |
| PA16 | Cidades do lançamento e geolocalização | Lista inicial do Vale do Itajaí; raio por CEP ou coordenadas | Lista do Vale do Itajaí em migração versionada; raio por coordenadas da cidade (Haversine). Conferir códigos IBGE |
