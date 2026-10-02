# Regras de negócio: COE Serviços

> Itens marcados **A DEFINIR** ainda não foram decididos. Pergunte antes de implementar.

## 1. Visão geral
- Marketplace de serviços por diária, começando em **Blumenau/SC**.
- Categorias ativas no lançamento:
  - **Construção:** pedreiro, azulejista, servente
  - **Casa:** diarista, passadeira
- Categorias "em breve": pintor, eletricista, encanador, jardineiro, montador de móveis.
- Perfis: **CLIENTE**, **PROFISSIONAL** e **ADMIN** (dono da plataforma).

## 2. Catálogo
- Três níveis: **área → profissão → serviços**.
- O profissional tem uma profissão principal, pode marcar outras e escolhe os serviços que faz.
- O admin cria, ativa e desativa profissões e serviços.

## 3. Cadastro do profissional (grátis, em etapas)
1. Dados pessoais: nome, celular, CPF, nascimento
2. Verificação: foto do documento + selfie com o documento
3. Profissão principal e outras
4. Serviços, tempo de atuação, se leva ferramentas, se é MEI
5. Valor da diária, meia diária opcional, horário
6. Bairros atendidos e dias de trabalho
7. Fotos de trabalhos, com legenda e serviço
8. Texto "Sobre você"
9. Chave Pix, revisão e aceite dos termos e da regra de contato

- O perfil só aparece na busca **depois que o admin aprova a verificação**.
- Se a verificação for recusada, o profissional vê o motivo e pode reenviar. Limite de reenvios: **A DEFINIR**.

## 4. Contato censurado (proteção contra vazamento de leads)
- Telefone, WhatsApp, e-mail e endereço completo ficam ocultos até o **pagamento ser confirmado**. Depois, são liberados automaticamente.
- Antes da contratação, o chat, o "Sobre você" e as legendas de fotos bloqueiam:
  - telefone com ou sem separadores, e escrito por extenso
  - e-mail, inclusive escrito com "arroba"
  - links
  - @perfil
  - palavras como zap, whats, wpp, insta, telegram
  - pedidos como "me liga", "meu número"
- Mensagem bloqueada **não é entregue**. O usuário vê o motivo e o evento vai para a fila de moderação.
- Após **3 tentativas** (configurável), a conta vai para análise. O admin pode advertir, suspender ou marcar falso positivo.
- **A regra roda no backend.** O front só antecipa o aviso.
- Deve **passar** (não é contato): valores em R$, medidas (m², cm), datas e horários.
- Falso positivo conhecido: CEP (8 dígitos). Liberar ou bloquear: **A DEFINIR**.

## 5. Pagamento por diária
- O cliente paga as diárias **antecipadamente** (Pix ou cartão). O valor fica retido em custódia **no gateway**.
- No fim de cada dia, o profissional marca como concluído com **foto obrigatória**.
- O cliente **aprova**, e aquela diária é liberada ao profissional via Pix.
- Sem resposta em **12 h** (configurável), a liberação é automática.

### Comissão
- **10%** por diária (configurável).
- Quem paga é configurável. O padrão é o **cliente**, com a comissão somada ao total.
- Se a diária for reembolsada, a comissão volta ou não: **A DEFINIR**.

### Meia diária
- Valor próprio do profissional, até 4 h. Regra final: **A DEFINIR**.

### Gateway
- Precisa ter Pix, cartão, custódia/subconta e split.
- Opções: Asaas, Pagar.me, Iugu, Mercado Pago. Escolha: **A DEFINIR**.

## 6. Contestação (disputa)
- Trava **só aquela diária**. As outras do contrato seguem normalmente.
- O cliente informa motivo, descrição e fotos opcionais.
- Dá para contestar dia **em andamento** (ex.: o profissional faltou).
- O admin decide em até **48 h**: libera ou reembolsa.
- Se o admin não decidir em 48 h: **A DEFINIR** (sugestão: alerta crítico no painel, sem decisão automática).
- Depois de LIBERADA (inclusive por liberação automática), a diária **não pode ser contestada** pelo app. Canal de reclamação posterior: **A DEFINIR**.

## 7. Cancelamento
- Cancelamento pelo cliente antes do dia (prazo e reembolso): **A DEFINIR**.
- Desistência do profissional antes do dia (reembolso integral? penalidade?): **A DEFINIR**.
- Enquanto não estiver definido, o estado CANCELADA fica previsto na máquina de estados, mas nenhuma regra de reembolso é implementada sem confirmação.

## 8. Diarista e passadeira
- **LC 150/2015:** o mesmo cliente contrata a mesma profissional no máximo **2 dias por semana**.
  - Validar no backend, considerando contratos já existentes.
  - O limite é configurável pelo admin.
- Planos semanal e quinzenal:
  - Se a profissional faltar, há **substituição garantida** ou devolução do valor.
  - A substituta também respeita o limite de 2 dias/semana com aquele cliente.
  - A próxima diária é cobrada 2 dias antes.

## 9. Avaliações
- Só avalia quem teve diária paga e aprovada pelo app.
- No lançamento ninguém tem nota: mostrar **"Novo na COE"**. **Nunca** exibir nota inventada.

## 10. Máquina de estados da diária

| De | Para | Quem/como |
|---|---|---|
| AGENDADA (plano) | PAGA | cobrança confirmada via webhook |
| PAGA | EM_ANDAMENTO | início do dia |
| EM_ANDAMENTO | AGUARDANDO_APROVACAO | profissional conclui com foto |
| AGUARDANDO_APROVACAO | LIBERADA | cliente aprova ou job após 12 h |
| PAGA / EM_ANDAMENTO / AGUARDANDO_APROVACAO | CONTESTADA | cliente contesta |
| CONTESTADA | LIBERADA ou REEMBOLSADA | decisão do admin |
| AGENDADA / PAGA | CANCELADA | regra **A DEFINIR** (seção 7) |

- LIBERADA, REEMBOLSADA e CANCELADA são **estados finais**.
- Qualquer transição fora desta tabela é erro.

## 11. Painel admin
- **Visão geral:** alertas e buscas sem resultado (mostram onde recrutar)
- **Verificação:** checklist de CPF, documento, selfie, fotos reais e texto sem contato
- **Disputas:** os dois lados e a conversa
- **Moderação de contato**
- **Profissionais e clientes:** suspender e reativar
- **Financeiro:** guardado, repassado, receita, em disputa, reembolsado (tudo calculado a partir do ledger)
- **Categorias**
- **Configurações:** comissão, quem paga, horas de liberação, prazo de disputa, limite LC 150, tentativas de contato

## 12. LGPD: retenção de dados
- Prazo de guarda de documentos e selfies após aprovação ou recusa: **A DEFINIR**.
- Fluxo de exclusão de conta, mantendo registros financeiros exigidos por lei: **A DEFINIR**.

## 13. Tabelas previstas
usuarios, profissionais, areas, profissoes, servicos, profissional_servicos, profissional_bairros, disponibilidade, portfolio_fotos, verificacoes, contratos, diarias, pagamentos, ledger, contestacoes, avaliacoes, conversas, mensagens, moderacao_eventos, configuracoes, auditoria_admin
