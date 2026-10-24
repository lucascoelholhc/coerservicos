# Plano de desenvolvimento: COE Serviços

> Fonte: documento "COE Serviços — Plano de desenvolvimento" (atualizado em 02/10/2026). Base: `docs/regras-negocio.md` (RN01–RN61, RF01–RF58, RNF01–RNF29).

As tarefas estão organizadas por tipo nas seções 3 a 9 (ambiente, banco, núcleo, domínio, front, aceite local e AWS), mas são **executadas em fatias verticais** (seção 11): cada fatia entrega backend e telas juntos e termina com algo que dá para testar no navegador. A versão local completa sai no fim da fatia 4 e do aceite local; depois vem a AWS. São **53 dias de trabalho** (um dia = uma sessão de cerca de 3 horas, não um dia do calendário): sistema completo local no dia 46, homologação na AWS no dia 53.

## 1. Visão geral

### Stack
| Camada | Escolha |
|---|---|
| Linguagem e framework | Java 21 + Spring Boot 4 (WebMVC, Security, Data JPA, Validation, Actuator) |
| Build | Maven |
| Banco | PostgreSQL 16 |
| Migrações | Flyway (SQL versionado) |
| Testes back | JUnit 5, AssertJ, Mockito, Spring Boot Test, Testcontainers (Postgres real), JaCoCo |
| Front | React + TypeScript + Vite em `frontend/`, seguindo o protótipo "Dia carimbado" (`docs/identidade-visual.md`) |
| Testes front | Vitest + Testing Library; Playwright para E2E |
| Ambiente local | Postgres + Docker Compose (Mailpit para e-mail/SMS falso; **SeaweedFS** como S3 local, versão fixa, escolhido no CORE-09 porque a imagem pública do MinIO deixou de existir; plano B: S3Proxy em memória) |
| Pagamento no local | Adaptador falso do gateway, com a mesma interface do gateway real |

### Arquitetura
Monólito modular: um único projeto Spring, com um pacote por domínio (`usuario`, `catalogo`, `profissional`, `contrato`, `pagamento`, `mensagem`, `avaliacao`, `disputa`, `moderacao`, `admin`, `config`, `compartilhado`). Assim cada módulo pode virar serviço separado no futuro sem reescrever tudo.

### Fora do MVP
- Itens marcados F2 nos requisitos.
- App nativo. O React é mobile-first e basta no começo.
- Integração real com o gateway. Entra quando o PA02 for decidido; até lá roda o adaptador falso.

### Marcos
| Marco | Entrega que prova que acabou |
|---|---|
| M0 Ambiente | `./mvnw verify` verde, com Testcontainers e cobertura |
| M1 Banco | Todas as migrações aplicadas num banco limpo, com dados locais |
| M2 Fatia 1 (dia 10) | Criar conta, entrar e sair **pelo navegador**, com papéis funcionando |
| Fatia 2 (dia 21) | Cadastro do profissional, aprovação pelo admin e busca com perfil público, pelo navegador |
| M3 Fatia 3 (dia 35) | Contratar, pagar no adaptador falso, "Cheguei", "Terminei o dia" e aprovar, pelo navegador |
| M4 Fatia 4 (dia 43) | Disputa, faltas, avaliações e painel do admin; tudo clicável, com E2E verde |
| M5 Local pronto | Checklist de aceite da etapa 5 todo marcado |
| M6 AWS | Ambiente de homologação no ar com HTTPS |

## 2. Como cada tarefa roda no ECC
Toda tarefa passa pelo mesmo ciclo. Uma tarefa corresponde a uma branch e a um PR pequeno, de meio dia a dois dias de trabalho.

1. **Planejar**: `/ecc:plan "<ID>: <tarefa>"`, citando as RN/RF que ela cobre. Revisar o plano antes de seguir.
2. **Teste primeiro**: skill `tdd-workflow` (ou `springboot-tdd`). Escrever os testes que falham a partir dos critérios de aceite.
3. **Implementar** até os testes passarem (`springboot-patterns`, `jpa-patterns`, `database-migrations` conforme a tarefa).
4. **Revisar**: `/code-review` em contexto limpo e agente `java-reviewer`. Dinheiro, login, upload e dados pessoais: também `/security-scan`.
5. **Verificar**: skill `verification-loop` (ou `springboot-verification`) + `/test-coverage`. Build quebrado: `/build-fix` ou agente `java-build-resolver`.
6. **Fechar**: `/update-docs`, commit, PR e `/save-session`.

### Definição de pronto (toda tarefa)
- [ ] Testes escritos antes do código e passando.
- [ ] Cobertura ≥ 80% no módulo e 100% em dinheiro, máquina de estados, censura e LC 150.
- [ ] `/code-review` sem pendência crítica ou alta.
- [ ] `./mvnw verify` verde localmente (e `npm test` no front, quando houver).
- [ ] Migração nova, se houver, roda num banco limpo e sobre o banco existente. **Migração já commitada nunca é editada.**
- [ ] Nenhum segredo, CPF ou telefone no código nem nos logs.
- [ ] RN/RF cobertos anotados no PR.

### Convenções
- Branch `feat/<ID>-<nome-curto>`; Conventional Commits.
- IDs de tarefa: `AMB-` (ambiente), `DB-` (banco), `CORE-` (núcleo), `DOM-` (domínio), `FE-` (front), `LOC-` (aceite local), `AWS-`.

## 3. Etapa 0: Ambiente local e esqueleto
| ID | Tarefa | Entrega |
|---|---|---|
| AMB-01 | Revisar o projeto | Java 21, Spring Boot 4; dependências webmvc, security, data-jpa, validation, actuator, flyway (starter), flyway-database-postgresql, postgresql, testcontainers |
| AMB-02 | `docker-compose.yml` | `postgres:16` (volume nomeado, porta 5433, cria o papel `coe_app`), `mailpit`; `seaweedfs` (S3 em `127.0.0.1:8333`, entrou no CORE-09) |
| AMB-03 | Perfis Spring | `local`, `test`, `prod`; segredos por variável de ambiente; `.env.example` no repositório e `.env` no `.gitignore` |
| AMB-04 | Pacotes | Um pacote por domínio + `compartilhado` (erros, dinheiro, auditoria, armazenamento) |
| AMB-05 | Teste base | Classe `IntegracaoTest` com Testcontainers Postgres reutilizável + teste de fumaça |
| AMB-06 | Qualidade no build | JaCoCo com regra de 80%; formatação (Spotless ou Checkstyle) |
| AMB-07 | CLAUDE.md e README | Comandos para subir, testar e recriar o banco local |
| AMB-08 | Erro padrão | `@RestControllerAdvice` com Problem Details (RFC 9457), mensagens em pt-BR |

**Pronto quando:** `./mvnw verify` passa e `GET /actuator/health` responde `UP`.

## 4. Etapa 1: Banco de dados (PostgreSQL + Flyway)
O schema está em 10 migrações versionadas (V1–V10) + dados locais. Detalhe de cada tabela em `docs/mapa-banco.md`.

### Padrões
- Chave primária `uuid` (`gen_random_uuid()`), para que nenhum ID seja sequencial e fácil de adivinhar.
- Dinheiro `numeric(12,2)`; percentuais `numeric(5,4)`.
- Instantes `timestamptz` (UTC); dia de serviço `date`.
- Status em `text` + `CHECK`.
- `criado_em`, `atualizado_em` e `versao` (trava otimista) em tabela que muda.
- CPF, chave Pix e endereço cifrados pela aplicação (`bytea`); hash separado para unicidade.

### Migrações
Numeradas na ordem em que são criadas, sem reservar número: tarefa futura aparece só pelo ID (ex.: "migração do DB-14"); o `V<n>` entra quando o arquivo existir.

| ID | Migração | Conteúdo |
|---|---|---|
| DB-01 | `V1__base` | Extensões (`pgcrypto`, `citext`, `unaccent`, `pg_trgm`), funções utilitárias, `configuracao` |
| DB-02 | `V2__conta` | `usuario`, `usuario_papel`, `codigo_sms`, `token_senha`, `aceite_termos`, Spring Session (sai na V12: PA03 = JWT) |
| DB-03 | `V3__catalogo` | `cidade`, `area`, `profissao`, `servico` + dados de referência |
| DB-04 | `V4__profissional` | Perfil, serviços, cidades, agenda, documentos, portfólio |
| DB-05 | `V5__contratacao` | `contrato`, `foto_pedido`, `diaria`, `evidencia_diaria`, `historico_diaria` |
| DB-06 | `V6__pagamento` | `cobranca`, `evento_gateway`, ledger (`conta_razao`, `transacao_financeira`, `lancamento`), `repasse`, `reembolso` |
| DB-07 | `V7__chat` | `conversa`, `mensagem`, `tentativa_contato` |
| DB-08 | `V8__avaliacao_disputa` | `avaliacao`, `disputa`, `anexo_disputa` |
| DB-09 | `V9__admin_auditoria` | `log_auditoria`, `notificacao`, `denuncia` |
| DB-10 | `V10__indices_busca` | Índices e view de busca por cidade e raio |
| DB-11 | `R__dados_local` + seeder Java | Só no perfil local; profissionais de exemplo pelo seeder (campos cifrados) |
| DB-13 | `V12__autenticacao_jwt` | Remove `spring_session*` (PA03 = JWT); cria `refresh_token` (hash único, usuário, aparelho, validade, revogado em, substituído por, família para revogar tudo em caso de reuso); `usuario.mfa_sms_ativo` (padrão falso); finalidade `mfa` no `codigo_sms`; comissão com teto de 30% (CHECK) |
| DB-16 | `V14__codigos_contato_e_senha` | Confere que `token_senha` e `codigo_sms` estão vazias (senão falha); `usuario.email_verificado_em`; `ck_usuario_credenciais` = senha + celular **ou** e-mail (RN61); verificado só com o dado presente; MFA exige celular confirmado; `codigo_sms` com `codigo_hmac` (32 bytes), `usuario_id` (FK RESTRICT), `desafio_hash` (só no `mfa`), `invalidado_em`, `criado_em` vindo do Clock, finalidades `configurar_mfa`, `comprovar_posse`, `recuperar_senha`, um ativo por celular e finalidade, `coe_app` sem DELETE e com UPDATE só nas colunas de uso; `token_verificacao` (links por e-mail e comprovantes de posse, só o hash) no lugar da `token_senha`; motivo de revogação `contato_transferido` |
| DB-15 | `V13__teto_da_sessao` | `refresh_token.sessao_iniciada_em` (gravado no login, herdado pelo sucessor, imutável; FK do sucessor inclui a coluna, então o banco recusa início diferente); linhas existentes preenchidas com o `min(criado_em)` da família; motivo de revogação `teto` |
| DB-14 | Migração das faltas do profissional (`ocorrencia_profissional`; número quando o arquivo for criado) | Junto do DOM-09. `ocorrencia_profissional` só de inserção (gatilhos `fn_somente_insercao` de linha e TRUNCATE, sem UPDATE/DELETE/TRUNCATE para o `coe_app`); `profissional.motivo_suspensao` obrigatório com status `suspenso`; parâmetros `FALTAS_ALERTA` = 2 e `FALTAS_JANELA_DIAS` = 90 com faixa validada (PA07) |

Correções CRITICAL já aplicadas em V1–V10 (antes do primeiro commit): partidas dobradas conferidas por transação no COMMIT (sem transação vazia e sem lançamento em transação fechada); tabelas só de inserção também barram TRUNCATE; papel `coe_app` sem posse do schema.

Correções HIGH revisadas em 02/10, **aplicadas na V11 (DB-12)** junto com os demais apontamentos do database-reviewer (exclusão de conta por anonimização, provas sem cascata, índices em todas as FKs, configuração validada, versão no gatilho, autocontratação barrada): webhook só ocupa o id com assinatura válida e guarda o corpo bruto; uma liberação ou um reembolso por diária garantido no banco; cidades do lançamento em migração versionada. Na mesma V11, as lacunas frente às RN: serviços marcados no pedido e descrição opcional (RN27), status `pausado` (RN20, RN56), pedir correção na verificação (RN13), data de nascimento (RN08) e raio 5/10/20/40 km (RN17).

### Fica na aplicação, não no banco
- Transições da máquina de estados da diária (RN40).
- Limite da LC 150: janela móvel de 7 dias (RN51), com trava na contratação.
- Distância para o raio: Haversine com lat/long da cidade (PostGIS só se a busca pedir).

## 5. Etapa 2: Núcleo do backend
| ID | Tarefa | O que constrói | Cobre |
|---|---|---|---|
| CORE-01 | Tipo `Dinheiro` | Value object sobre BigDecimal (escala 2, HALF_EVEN), comissão e repasse | RNF09, RN31 |
| CORE-02 | Cadastro de cliente | `POST /api/contas/cliente` com nome, celular, e-mail, CEP e senha; aceite dos termos com versão | RF03, RNF18 |
| CORE-03 | Senha e login | Argon2 ou BCrypt; login por celular **ou** e-mail + senha; JWT de 15 min (HS256, `kid`) + refresh de 30 dias rotativo em cookie HttpOnly; revogação no logout, na troca de senha e na inativação; reuso de refresh revoga a família; uma sessão por aparelho e "sair de todos" (PA03) | RNF01 |
| CORE-04 | Login por SMS, MFA e confirmação de contato | Código de 6 dígitos em hash, 5 min, 5 tentativas; `EnviadorSms` falso no local; entrar só com código (alternativa à senha); segundo passo por SMS **obrigatório para ADMIN** e opcional para os demais; o login responde 403 `segundo-passo-necessario` com um id de desafio **de uso único, curto (minutos), preso ao usuário e que não autentica nada sozinho**; confirmação do celular (obrigatória para profissional, opcional para cliente, RN08) e do e-mail por link; **RN61**: contato confirmado pelo dono passa para a conta dele e a outra conta fica bloqueada até cadastrar e confirmar outro (exige migration: hoje `ck_usuario_credenciais` obriga celular e e-mail) | RF01, RN08, RN61 |
| CORE-05 | Recuperar senha | Token de uso único com validade curta | RF01 |
| CORE-06 | Papéis e autorização | CLIENTE, PROFISSIONAL, ADMIN; checagem de dono em todo recurso | RNF02 |
| CORE-07 | Rate limit | Bucket4j em login, SMS, recuperação de senha e chat; rate limit por IP e global em `/api/auth/*`, `/api/contas/posse/*`, `/api/contas/cliente`, `GET /api/publico/*`, `PUT /api/contas/eu/{celular,email}` e `trocar` (o "existe ou não existe" que sobra no cadastro fica com ele); **fila de envio com prioridade** (login, MFA e recuperação de senha separados de prova de posse e confirmação de contato), com a cota devolvida quando a mensagem é descartada, e teste de inundação de prova de posse sem perder código de login | RNF03 |
| CORE-08 | CSRF e CORS | CORS fechado ao domínio da COE; CSRF só no endpoint de renovação do token (único que usa cookie) | RNF01 |
| CORE-09 | Arquivos | `Armazenamento` (S3): tipo real, tamanho, sem EXIF, nome gerado, URL assinada. S3 local do compose: **SeaweedFS** (plano B: S3Proxy) | RNF07, RNF14 |
| CORE-10 | Criptografia de campo | Conversor JPA AES-GCM para CPF, Pix e endereço | RNF13 |
| CORE-11 | Auditoria e logs | `log_auditoria`; logs JSON com máscara de dados pessoais | RNF08, RNF15 |
| CORE-12 | Configuração | Leitura da `configuracao_vigente` | RN31, RN38 |
| CORE-13 | Relógio injetável | `Clock` em todo serviço com prazo | Testabilidade |

**Pronto quando:** dá para criar cliente, entrar por senha e por SMS, sair, e um cliente recebe 403 ao ler o recurso de outro.

## 6. Etapa 3: Módulos de negócio
| ID | Módulo | O que constrói | Pronto quando |
|---|---|---|---|
| DOM-01 | Catálogo | Categorias, profissões, serviços e cidades ativas | O front monta os filtros só com dados da API |
| DOM-02 | Cadastro do profissional | 11 etapas com rascunho, bio com censura, fotos, documento + selfie, Pix; idade mínima de 18 anos com `Clock` injetado (RN08) | Cadastro completo fica "em análise" e fora da busca; menor de 18 é recusado com "Para trabalhar na COE é preciso ter 18 anos ou mais." |
| DOM-03 | Verificação | Fila do admin; aprovar ou recusar com motivo; selo NR-10 | Aprovado vira ativo; tudo auditado |
| DOM-04 | Busca e perfil | Filtros (profissão, cidade no raio, dia livre, valor, experiência); perfil sem contato | Nenhum campo de contato sai antes do pagamento |
| DOM-05 | Chat e censura | Censura no servidor; conta em análise após N tentativas; CEP passa (PA13); conta com `usuario.status = em_analise` entra normalmente (precisa cumprir diárias já pagas), mas fica **sem chat e sem contrato novo** até o admin decidir | Testes com padrões que bloqueiam e que passam (R$, medidas, datas, `CEP 89010-000`, `CEP 89010000`) |
| DOM-06 | Contratação | Pedido, datas, agenda, LC 150 (janela de 7 dias), comissão congelada | 3ª diária na janela é recusada; dia ocupado é recusado |
| DOM-07 | Pagamento e custódia | `GatewayPagamento` + adaptador falso; webhook assinado e idempotente; ledger; custódia rastreada por contrato | Webhook repetido não duplica nada; saldos batem; custódia por contrato nunca negativa |
| DOM-08 | Execução da diária | Cheguei, terminei com foto, aprovar; job das 12 h a partir do `terminou_em` (PA08, ShedLock); repasse | Duas instâncias não liberam a mesma diária |
| DOM-09 | Disputa e reembolso | Reclamação trava só o dia; decisão do admin; reembolso = o que o cliente pagou; falta do profissional (PA07) com DB-14 | Os dois caminhos testados no ledger; casos obrigatórios da falta abaixo |
| DOM-10 | Avaliações | Nota dos dois lados; média no perfil | Só avalia quem pagou pelo app |
| DOM-11 | Admin e notificações | Usuários, denúncias, financeiro, configuração; faltas por profissional com histórico e alerta (RN44d); inativar e reativar profissional (RN44e); SMS e e-mail | Toda ação do admin em `log_auditoria`; alerta com 2 faltas em 90 dias |

**Casos de teste obrigatórios do DOM-07** (decididos no DB-12, ficaram fora da V11):
- Custódia rastreada **por contrato**: liberar a diária de um contrato nunca usa dinheiro guardado de outro contrato.
- Saldo de custódia de um contrato **nunca fica negativo**, nem com duas liberações concorrentes (trava por contrato).
- Saldo do profissional nunca fica negativo no repasse.
- Os totais batem centavo por centavo: custódia + repassado + receita = total pago.

**Pendências deixadas pelo CORE-02** (dia 4):
- `usuario.cidade_id` fica nulo no cadastro: preencher a cidade a partir do CEP (DOM-01 ou depois; a regra de busca do CEP ainda não está decidida).
- O celular nasce **não confirmado** (`celular_verificado_em` nulo); a confirmação por SMS entra no CORE-04 (dia 7), e o login deve tratar a conta ainda não confirmada conforme a RN08.
- O 409 de "celular/e-mail já cadastrado" permite descobrir se alguém tem conta: aceito, coberto pelo rate limit do CORE-07 (dia 11) no `POST /api/contas/cliente`.
- O BCrypt de custo 12 num endpoint público custa CPU: o mesmo rate limit do CORE-07 protege contra abuso.
- Campo desconhecido no JSON é recusado (400) em toda a API (`fail-on-unknown-properties`). **Exceção do DOM-07**: o webhook do gateway guarda o corpo bruto e lê de forma tolerante (ignora campo novo); nunca usar DTO rígido ali.
- O IP do aceite vem de `getRemoteAddr()`. Em produção, `forward-headers-strategy: native` (RemoteIpValve) faz dele o IP real, confiando no `X-Forwarded-For` só de IP interno; na etapa AWS, conferir que o ALB está em sub-rede privada e testar o IP gravado em homologação.
- **CORE-03:** o BCrypt recusa senha acima de 72 bytes no `matches`; o login e a troca de senha validam o tamanho antes e respondem 401 genérico (com teste). Hash sem prefixo (`$2b$` do seed) é regravado como `{bcrypt}` após o login (`upgradeEncoding`). Papéis com `JOIN FETCH` no login se o `EAGER` virar N+1 nas listagens do admin.
- **CORE-04 (decidido, RN61):** contato não confirmado não fica reservado; quando o dono confirma, o dado passa para a conta dele. O login por celular ou e-mail (CORE-03) garante que quem perdeu um dos dois continua entrando.
- **CORE-07:** limite de tamanho do corpo da requisição (filtro ou ALB/WAF), além do rate limit.

**Pendências do dia 6** (CORE-06/08, revisões sem HIGH):
- **CSP e Permissions-Policy**: entram no dia 8, junto do front (o build é servido no mesmo domínio; `default-src 'self'; frame-ancestors 'none'`). Decidir se quem emite é o Spring ou o ALB/CDN na etapa AWS.
- `POST /api/auth/entrar` fica sem checagem de Origin (login CSRF): o `SameSite=Strict` e o corpo JSON obrigatório mitigam; decisão registrada.
- Origem fora da lista numa requisição CORS recebe o 403 em texto do Spring (não Problem Details); o front legítimo nunca recebe isso.
- A regra ArchUnit aceita `@PreAuthorize("isAuthenticated()")` e não olha anotação de classe; quando surgirem endpoints de PROFISSIONAL e ADMIN, acrescentar teste cruzando papel x rota (403 do papel errado em cada endpoint).
- O `AntiIdorTest` prova o padrão com um mapa em memória; refazer com repositório JPA (`findByIdAndClienteId`) no primeiro domínio real com dono (contrato).
- HSTS com `includeSubDomains`: confirmar na etapa AWS que nenhum subdomínio é só HTTP antes de ligar o prod.
- Preenchimento da V13 conferido no banco local (5 tokens, 2 famílias com cadeia, 0 divergências, FK validada); sem teste automatizado (não há produção).

**Riscos aceitos e pendências do CORE-03** (dia 5):
- Um access token continua válido por até **15 min** depois do logout, da troca de senha ou da suspensão (PA03); a renovação já é recusada na hora.
- Força bruta no login até o rate limit do **CORE-07** (dia 11); hoje o BCrypt custo 12 é a única barreira.
- Checagem de `Origin` (CSRF) na renovação por cookie: **CORE-08** (dia 6).
- Expurgo dos `refresh_token` vencidos ou revogados: job futuro (o `coe_app` não tem DELETE na tabela).
- Reuso de refresh revoga **só a família** daquele aparelho, com registro no log (decidido em 05/10); quem quiser derrubar tudo usa "sair de todos". **Aviso por e-mail** ao usuário no reuso: entra no DOM-11, quando houver envio de e-mail.
- `POST /api/auth/sair` exige access token válido (decisão do dia 5): o front renova antes de sair se o token tiver vencido.
- "Sair de todos" concorrendo com uma renovação já travada pode deixar o sucessor dela válido (janela de milissegundos, aceita).
- **Teto absoluto da sessão: 90 dias desde o login** (decidido em 05/10): implementado no dia 6 (V13, `sessao_iniciada_em`); o sucessor vale `min(agora + 30 dias, início + 90 dias)` e o cookie usa o mesmo prazo.
- Opcionais apontados pela revisão: recusar chave JWT de baixa qualidade (bytes iguais, chaves repetidas) e falhar a subida com os perfis `local` e `prod` juntos.
- ~~Dispensa do MFA do ADMIN no perfil `local`~~: removida no CORE-04 (dia 7a); no local o ADMIN entra com o código que o SMS falso escreve no log.

**Pendências do dia 7a** (CORE-04 parte SMS, revisões sem HIGH):
- **Provedor de SMS: A DEFINIR** (sugestão: Amazon SNS). Sem ele a aplicação não sobe em prod (`ConfiguracaoDeSms`).
- **CORE-07 (rate limit por IP)** precisa cobrir `/api/auth/codigo`, `/api/auth/entrar-com-codigo`, `/api/auth/segundo-passo` e `/api/contas/eu/*/codigo`. Hoje o limite é só por celular (1 a cada 60 s e 5 por hora, somando as finalidades), o que permite a um anônimo gastar a cota de um celular e invalidar o código ativo do dono (negação do login por código e do 2º fator; sem invasão) e "bombardear" um número com até 5 SMS por hora.
- **Decidido em 07/10 (entra no 7b): contador de falhas.** 10 códigos errados em 24 h na mesma conta bloqueiam **toda** entrada por código (login por código, segundo passo do MFA, prova de posse, recuperação de senha por SMS) por 1 h, com log (id da conta, sem dados). Contado na `codigo_sms`, sem tabela nem migração nova: soma de `tentativas` (só erros; o código certo não soma) desde agora − 24 h; bloqueado enquanto houver 10+ erros na janela e o código com erro mais recente tiver menos de 1 h (o `criado_em` do código erra o instante do erro em até 5 min, a validade do código; aceito). Prova de posse por celular conta pelo número (o código não tem dono); link de posse por e-mail não tem contador (token de 32 bytes). Valores em `coe.seguranca.codigos.*`, não fixos. Durante o bloqueio: login por código e recuperação por SMS respondem igual a sempre (401 genérico / 202), o segundo passo do MFA responde 429 `muitas-tentativas`; login com senha sem MFA continua.
- **Decidido em 07/10 (entra no 7b): envio assíncrono de SMS e e-mail.** O serviço publica um evento só em memória (destino e texto) e o envio acontece em `@TransactionalEventListener(AFTER_COMMIT)` num executor próprio com fila limitada (2 a 4 threads, fila 100). A submissão ao executor é explícita, sem o `@Async` literal (aceito em 07/10): o descarte com fila cheia loga o destino mascarado e a contagem de pendentes fica exata. Fila cheia ou erro do provedor: log mascarado, a resposta não muda. Rollback não envia. **Nunca gravar código ou link em texto no banco**: a tabela `notificacao` fica só para avisos sem segredo (DOM-11).
- Código SMS gravado e cota consumida antes do envio: se o provedor falhar, o código fica ativo sem ter chegado (o próximo pedido o invalida).
- **DOM-02**: profissional sem celular confirmado não vai para análise nem aparece na busca (trava no cadastro do profissional, RN08).
- **DOM-08**: quando existir o filtro de `contato-pendente` (RN61, dia 7b), "Cheguei", "Terminei o dia" e "aprovar diária" entram na **lista de liberados**: a marca bloqueia começar coisas novas (contratar, chat, editar perfil, cadastro de profissional), nunca a execução do que já foi pago (um profissional que perdeu um e-mail não confirmado não pode ficar travado numa diária paga).
- **Expurgo LGPD** de `codigo_sms` e `token_verificacao` (guardam celular e e-mail): job com o dono do schema (o `coe_app` não tem DELETE), apagando linhas com mais de 30 a 90 dias (prazo a definir com o expurgo geral).
- **IP na `log_auditoria` é dado pessoal (CORE-11, 14/10)**: precisa de prazo de guarda. Sugestão: **6 meses para o IP** (depois, o job do dono do schema zera a coluna `ip`; a `log_auditoria` é só de inserção para o `coe_app`); o resto da linha segue a regra geral de guarda da auditoria. **A DEFINIR** com quem cuida do jurídico. O IP já é gravado agrupado (IPv6 por /64, como no rate limit).
- **Anonimização (RN60)** deve zerar também `celular_verificado_em`, `email_verificado_em` e `mfa_sms_ativo`.
- Envios de e-mail (7b) contados por endereço em `token_verificacao`; os de SMS em `codigo_sms`.
- **Decisões do plano do 7b (07/10):**
  - Trocar a senha logado é `POST /api/auth/senha/trocar` com `@PreAuthorize("isAuthenticated()")` (estar sob `/api/auth` não o torna público): o cookie do refresh (`Path=/api/auth`) chega, então a família atual é mantida e as outras são revogadas.
  - `PUT /api/contas/eu/celular` e `/eu/email` só com o campo vazio (conta com contato pendente) e dado livre, sem comprovante por enquanto. Trocar um contato existente fica para tarefa futura (`trocar_celular`).
  - Transferência (RN61): a conta antiga perde só o dado reivindicado; **`mfa_sms_ativo` é zerado só quando ela perde o celular** (perder só o e-mail não mexe no MFA).
  - Se a conta antiga ficaria sem celular e sem e-mail, a transferência é recusada com 409 `transferencia-indisponivel` (caso para o suporte).
  - `POST /api/contas/email/confirmar` é público e dispensa o Origin: não usa cookie nem credencial do ambiente; o segredo é o token no corpo, então um POST de outro site não ganha nada (CSRF não se aplica).
- **A decidir antes do lançamento (RN61/RN60):** se a conta antiga perder celular e e-mail e não tiver contrato nem pagamento, anonimizar automaticamente (fluxo da RN60, motivo `contato_reivindicado`, auditado); com histórico financeiro, segue para o suporte. Até lá, 409 `transferencia-indisponivel`.
- **Decidido em 07/10: recuperar a senha** só manda link para e-mail confirmado (senão SMS no celular confirmado, senão suporte); o cadastro manda o link de confirmação do e-mail sozinho.
- **FE-03 (dia 10):** aviso na conta enquanto o e-mail não estiver confirmado: "Confirme seu e-mail para conseguir recuperar a senha". As páginas `/confirmar-email`, `/provar-email` e `/redefinir-senha` leem o token do fragmento, limpam o fragmento com `history.replaceState` logo ao carregar e não carregam recurso de terceiros.

**Pendências do dia 7b** (revisões java, segurança e banco sem HIGH depois das correções):
- **V15 (feita, 07/10):** só `ix_codigo_sms_usuario_criado (usuario_id, criado_em DESC)`, para o contador por conta. Os índices de envio por celular (`ix_codigo_sms_celular`, V2) e por endereço (`ix_token_verificacao_destino`, V14) já existiam e servem (EXPLAIN no `MigracaoV15Test`). O `ix_codigo_sms_usuario` (V14) ficou redundante como prefixo: remover numa migração futura, com aval.
- **CORE-07 (rate limit por IP e global)** precisa cobrir também `/api/contas/posse/*`, `/api/contas/cliente`, `/api/auth/senha/*`, `PUT /api/contas/eu/{celular,email}` e o `trocar` (sem limite de tentativas da senha atual: cada erro custa um BCrypt). Sem ele: SMS/e-mail "bomba" (até 5 por hora por destino), negação do código do dono (pedir de novo invalida o anterior) e bloqueio por 10 erros usado contra a vítima. Configurar o IP real atrás do ALB antes de ligar (`forward-headers-strategy`).
- **Decidido em 07/10 (feito):** 409 neutro `contato-em-uso` no cadastro, no PUT de contato e nas confirmações, igual para contato confirmado e não confirmado; a prova de posse de contato confirmado segue silenciosa. O "existe ou não existe" que sobra (inerente ao cadastro) e a diferença de tempo de `esqueci` e `posse` ficam com o rate limit da CORE-07.
- **Decidido em 07/10:** fila de envio com prioridade entra na CORE-07 (dia 11), com cota devolvida no descarte e teste de inundação de posse; hoje não muda nada.
- **Para decidir:** posseiro com celular **e** e-mail da vítima impede o cadastro dela com os dois (409 `transferencia-indisponivel`); ligado à decisão de anonimizar a conta antiga sem histórico.
- **Decidido em 07/10 (feito):** a espera de 60 s passou a ser por (destino, finalidade), para SMS e e-mail; o teto de 5 por hora continua somando as finalidades. "Esqueci a senha" logo depois do e-mail de confirmação do cadastro agora envia.
- O interceptor de contato pendente faz uma consulta por chave em toda requisição autenticada não liberada; cache curto se virar gargalo.
- `/api/auth/entrar` continua sem exigir Origin (decisão do dia 6); com o MFA, um POST de outro site com a senha certa dispara um SMS. Risco baixo (exige a senha), registrado.

**CORE-07 + CORE-11 (dia 11): decisões de 14/10**
- **Contadores:** Postgres (Bucket4j 8.14, `SELECT … FOR UPDATE`, tabela `limite_balde` UNLOGGED da V16) nos endpoints sensíveis e nos limites globais; memória da instância (Bucket4j + Caffeine) nos GETs públicos e no teto geral por IP em `/api/**`. Chave sem dado pessoal em claro (login, celular e e-mail por HMAC; IPv6 por /64). Valores no `application.yml` por perfil (`coe.limites.*`), não na tabela `configuracao` (são técnicos).
- **Números aprovados** (por IP salvo indicação): `/api/**` 300/min; `GET /api/publico/*` 60/min; `entrar` 30/10 min, login + IP **8/15 min**, login (qualquer IP) 20/h (atingir é auditado); `segundo-passo` 20/10 min; `codigo` e `senha/esqueci` 10/h; `entrar-com-codigo` 20/10 min e 10/10 min por celular; `renovar` 60/min; `senha/redefinir` 20/h; `senha/trocar` 5/15 min por usuário; `contas/cliente` **30/h** e global 500/h; posse (enviar) 10/h e global 300/h somado com as confirmações; posse (confirmar) 20/10 min; `PUT /eu/{celular,email}` 5/h por usuário; `/eu/*/codigo` e `/eu/email/confirmacao` 10/h por usuário; `/eu/*/confirmar` e `/eu/mfa` 20/10 min por usuário; `/contas/email/confirmar` 20/10 min. Os limites por destino (60 s, 5/h, bloqueio por 10 erros) continuam.
- **Os números serão revistos com dados reais depois do lançamento** (NAT de operadora e cadastro presencial no mesmo Wi-Fi motivaram o 30/h do cadastro e o 8/15 min do login + IP).
- **429** com `Retry-After` em todos os limites, inclusive os por destino que já existiam (60 s e 5/h). Corpo acima de 64 KB em `/api/**` = 413 `corpo-grande-demais` (uploads da CORE-09 terão o deles).
- **Abas abrindo juntas:** a corrida benigna do refresh (reuso em até 30 s) passa de 401 a 409 `renovacao-em-andamento`, sem apagar o cookie; o front tenta de novo uma vez depois de 300 ms, com o cookie já rotacionado.
- **Fila de envio:** prioritária (login por código, MFA, recuperação de senha) e normal (prova de posse, confirmação de contato). Mensagem descartada por fila cheia marca `descartado_em` (V16) na linha que a gerou e sai das contagens: a cota volta.
- **SMS falso do local** continua escrevendo o código, num logger próprio fora da máscara; só no perfil local (o teste de que ele não sobe no prod continua).
- **Revisão do banco da V16 (sem CRITICAL), entra nas etapas seguintes:** (H1) o `FOR UPDATE` de uma chave global serializa as requisições e, numa inundação, pode esgotar o pool de conexões: baldes por IP antes do global, chave global dividida em N linhas sorteadas (limite/N cada), `SET LOCAL lock_timeout` curto na transação do limitador e, se estourar, 429 (fail-closed) nas regras sensíveis, com teste de concorrência; (M3) o descarte usa `invalidado_em = coalesce(invalidado_em, agora)` e `AND usado_em IS NULL`, tratando zero linhas afetadas; (L5) só descarta a linha que acabou de gerar a mensagem. Efeito aceito: se o código novo é descartado, o anterior já foi invalidado e a pessoa fica sem código válido até pedir outro (a cota devolvida permite pedir na hora).

**CORE-11 e revisões do dia 11 (14/10): o que fechou e o que ficou**
- **Fechou**: força bruta no login, SMS/e-mail "bomba", negação do código do dono e o "existe ou não existe" do cadastro (rate limit da CORE-07); limite de corpo (413); reuso do refresh com várias abas (409 + uma nova tentativa); auditoria dos eventos sensíveis da conta; logs sem dado pessoal (máscara central + rede de segurança no Logback e no JSON do prod; Hibernate sem logar a mensagem do Postgres); id de correlação; prod não sobe com `DB_USER` acima do `coe_app`.
- **Revisões (java-reviewer, security-reviewer, /security-scan)**: 3 HIGH corrigidos — devolução da cota segurava 2 conexões dentro do AFTER_COMMIT (agora numa thread própria); caminho com percent-encoding (`%63odigo`, `%61pi`) pulava os filtros de entrada (agora comparam o caminho decodificado, `CaminhoDaRequisicao`); o global de envio normal era gasto antes do limite do usuário nas rotas `/eu/*` (agora depois, `@LimitadoPorUsuario(global = ...)`). MEDIUM/LOW aplicados: furos da máscara (celular/CPF com espaço ou ponto, CPF em lista, e-mail com acento, token base64 padrão, "code"/"otp"), CR/LF neutralizado no log, `nosniff`/`no-store` nas respostas dos filtros, `getReader` no corpo já lido, limpeza dos baldes a cada 5 min. `/security-scan` (AgentShield): B 80/100, segredos 100/100; os 11 achados são só "prompt defense" no CLAUDE.md, não aplicados (o CLAUDE.md é instrução do Claude Code no repositório, não prompt de produto; a aplicação não usa LLM).
- **Ficou (pendências de segurança)**:
  - Teto por login (20/h, qualquer IP) trava o login por senha da vítima por até 1 h, inclusive do ADMIN (que não entra por código). Avaliar contar só falhas, ou isentar o ADMIN do teto e manter login + IP. Alerta quando um global esvaziar (hoje só log).
  - Fail-closed do limitador: o pool próprio (5 conexões) pode dar 503 numa inundação distribuída; primeira camada de rate limit no ALB/WAF na etapa AWS e alarme de 503.
  - Perfil errado em produção (sem `prod`): o IP vira o do ALB e todos dividem um balde; checar o perfil/IP na subida ou na etapa AWS; RemoteIpValve confia em 10/8, 172.16/12, 192.168/16 (restringir `internalProxies` se houver CloudFront ou outros serviços na VPC).
  - `conta.bloqueio-codigos` pode ser gravado duas vezes em erros concorrentes (`erros == limite` sob READ COMMITTED); gravar dentro da trava ou com chave única.
  - `ConferenciaDoLoginDoBanco`: checar também `rds_superuser`, `rolcreaterole`, `rolbypassrls` (no RDS o mestre não é `rolsuper`).
  - `ChaveDeIp` aceita "beef"/"dead" (nome de host): hoje o valor vem sempre de `getRemoteAddr()` numérico; validar o literal sem `InetAddress` quando mexer.
  - Máscara é rede de segurança: nome e endereço não são mascarados; nunca logar esses valores. Log do próprio Postgres/RDS (`DETAIL: Key (...)`) vai ao CloudWatch sem máscara: retenção e acesso na etapa AWS.
  - IP na `log_auditoria`: prazo de guarda a definir com o jurídico (registrado acima); DELETE com corpo não é limitado pelo filtro de tamanho (não há rota assim hoje).
  - OWASP Dependency-Check (back) antes do release (Bucket4j 8.14.0, Caffeine 3.2.4, logback 1.5.38, Tomcat 11.0.24, Spring Security 7.1.1, pgjdbc 42.7.13).

**CORE-09 (dia 12): decisões de 14/10**
- **S3 local = SeaweedFS** (`chrislusf/seaweedfs`, tag fixa, nunca `latest`; a mesma nos testes com Testcontainers). As identidades do S3 são geradas na subida a partir de `S3_ACCESS_KEY`/`S3_SECRET_KEY` do `.env` (`docker/seaweedfs/iniciar.sh`), sem chave no git. **Plano B:** S3Proxy em memória (Java, `andrewgaul/s3proxy`), se o SeaweedFS deixar de servir (URL assinada, path-style ou imagem).
- **Upload passa pela API** (multipart), nunca PUT assinado direto ao bucket: a validação acontece antes de gravar.
- **Tudo privado + URL assinada** (5 min), inclusive o portfólio público (RN22); CDN ou leitura pública ficam para depois, se o custo pedir. O GET assinado leva `response-content-type` do tipo detectado.
- **Formatos:** JPEG, PNG e WebP (leitura pela TwelveMonkeys `imageio-webp`); WebP é gravado como JPEG (fundo branco se tiver alfa). HEIC recusado (sem leitor Java puro; o Safari do iPhone converte para JPEG quando o campo pede `image/jpeg,image/png`). PDF fora por ora (decidir no DOM-02 se precisar).
- **Limites** (`coe.armazenamento.*`): 8 MB por arquivo (por categoria), multipart com limite próprio (o de 64 KB de `/api/**` não vale para `multipart/*`), lado maior reduzido a 2560 px. Pixels: até 40 MP **só** com leitura por subamostragem (`ImageReadParam`) e semáforo de processamento simultâneo configurável; dimensões conferidas no cabeçalho, antes de decodificar.
- **Sem metadados:** a imagem é decodificada e gravada de novo; a orientação do EXIF é aplicada nos pixels **antes**, e as cores vão para sRGB ao descartar o ICC.
- **Chave** `<categoria>/<UUID aleatório>.<jpg|png>` (documento, selfie, portfolio, pedido, evidencia, disputa); nome original nunca gravado nem logado. PUT com o `Content-Type` detectado. Criptografia do servidor com `coe.armazenamento.kms-key-id` (vazia no local, preenchida no prod, AWS-04).
- **Arquivo órfão:** `guardar` exige transação ativa (senão falha) e registra a remoção no rollback, numa thread própria (lição do AFTER_COMMIT). Queda entre o PUT e o commit pode deixar órfão: varredura periódica (bucket x `chave_arquivo`) fica como tarefa futura.
- **Miniaturas (RNF24)** ficam no domínio (DOM-04, portfólio). **Endpoints de upload** chegam no DOM-02/DOM-08, com limite por usuário (o `CoberturaDosLimitesTest` cobra). **CSP:** `img-src` ganha o host do bucket (`http://localhost:8333` no local) junto com a primeira tela que mostrar foto, mudando `cabecalhos-seguranca.json` e `PoliticasDoNavegadorTest` juntos. **Auditoria** fica com a ação de domínio ("documento enviado"); o armazenamento só loga chave e categoria.

**CORE-09: revisões (java-reviewer, security-reviewer, /security-scan) — o que fechou e o que ficou**
- **Fechou (3 HIGH e os MEDIUM baratos)**: erro de cor/ICC depois da leitura virava 500 (agora 415); bomba de descompressão em metadado do PNG/WebP (`MetadadosDaImagem`: bloco que não é de imagem acima de 64 KB, ou mais de 256 blocos, é recusado antes de abrir); upload sem `Content-Length` nas `RotasDeUpload` = 411 (nada de ler até 11 MB antes do login); JPEG cortado recusado (aviso do leitor); uma cópia da imagem a menos na memória; cor escondida sob alfa 0 apagada; KMS obrigatória no prod; "produção" = perfil `prod` OU origens só https; `apagar` dentro de transação só depois do commit; multipart malformado = 400; chaves do SeaweedFS com 16+ caracteres; regex da chave montada das categorias; leitores de imagem conferidos na subida.
- **Ficou para o DOM-02/04/08 (endpoints de upload)**: `@LimitadoPorUsuario` baixo (ex.: 10/h) + limite por IP em cada rota de upload (um usuário com fotos de 40 MP ocupa as 2 vagas e os outros recebem 503); anti-IDOR no documento/selfie/portfólio/evidência/anexo e na rota que devolve a URL assinada (a chave nunca vem do cliente, só o id do registro); URL de documento e selfie com validade menor (1–2 min) e só para o dono e o ADMIN que verifica; teste de integração com multipart real (`MaxUploadSizeExceeded`, campo com `Content-Type` e extensão mentirosos); upload na `VarreduraDeLogsTest`; auditoria "documento enviado/visto pelo admin"; `spring.servlet.multipart.location` fora de diretório compartilhado.
- **Ficou para a AWS-04**: bucket com Block Public Access, política que nega `PutObject` sem SSE-KMS e sem TLS; memória do contêiner (`-Xmx`, `MaxDirectMemorySize`) com folga para 2 vagas: o pico por vaga passa de 100 MB (imagem até ~5100 px de lado decodificada inteira, JPEG progressivo guarda coeficientes fora do heap); medir com JPEG progressivo, PNG e WebP de 40 MP; reduzir `pixels-maximo` se a medição pedir. Tag do SeaweedFS fixada por versão, não por digest (só desenvolvimento).
- **Ficou (outros)**: varredura de órfãos (bucket x `chave_arquivo`) com prazo, porque são dados pessoais; apagar os arquivos na exclusão de conta (retenção de 5 anos, depois expurgo); orientação do EXIF só no JPEG (WebP/PNG com EXIF raros: vêm como estão); qualidade do JPEG por categoria (16/10: documento (inclui NR-10), selfie, evidência e disputa 0,92; portfólio e pedido 0,85; 4:4:4 não foi preciso); PNG regravado pode passar de 8 MB (o limite vale na entrada); `guardar` segura a conexão do banco durante o PUT (chamada limitada a 30 s): o domínio deve guardar como último passo antes do commit; `http://` para endpoint remoto fora do prod (só local/test usam endpoint).

**CORE-10 (dia 13): decisões de 16/10**
- AES-256-GCM em `compartilhado.cripto`: formato `[versão 1][tamanho do kid][kid][nonce 12][cifra + tag 16]`, nonce aleatório (SecureRandom) a cada cifra, AAD = cabeçalho + nome do campo (`cpf`, `chave-pix`, `endereco`). Rotação por kid: cifra com `COE_CIFRA_KID_ATUAL`, decifra com qualquer kid de `COE_CIFRA_CHAVES`.
- CPF também como HMAC-SHA256 (`COE_CHAVE_HMAC_CPF`, diferente das chaves de cifra; a subida confere) na `cpf_hash` única: unicidade e busca exata sem decifrar. `Cpf` com dígitos verificadores e `toString` mascarado.
- Colunas já eram `bytea` (V4 `cpf_cifrado`, `cpf_hash` de 32 bytes, `chave_pix_cifrada`; V5 `endereco_cifrado`; V6 `repasse_pix.chave_pix_cifrada`): sem migração.
- **Data de nascimento continua `date`** (decisão sozinho, 16/10): o banco confere a faixa (`ck_profissional_nascimento`) e a idade de 18 anos é calculada no serviço; o CLAUDE.md lista só CPF, Pix e endereço como cifrados. Reversível: coluna nova `bytea` + conversor `Campo.NASCIMENTO` + migração de dados.
- **Revisões (java-reviewer, security-reviewer): sem CRITICAL nem HIGH.** Corrigido: contexto opcional no AAD (`cifrar(texto, campo, contexto)`) para amarrar o dado à linha (o repasse do DOM-07 usa `"repasse:" + id` na chave Pix; os conversores JPA ficam sem contexto); CPF que decifra mas não é válido vira erro de dado (500), não 422; teste pelo Hibernate com `@Convert` (prova a injeção dos conversores); javadoc corrigido. **Ficou para o DOM-02**: gravar `cpf_cifrado` e `cpf_hash` num método só da entidade (`definirCpf`) e conferir hash = HMAC(CPF decifrado) na leitura (detecta troca de linha); mensagem neutra e rate limit no 409 do CPF repetido (enumeração). **Ficou para o DOM-07**: chave Pix do repasse com contexto. **Rotação**: o kid fica em claro no byte 2 da coluna (dá para contar por SQL quantas linhas usam cada kid); nunca tirar um kid da lista com linhas usando (dariam 500); rotina de recifra própria (salvar a entidade não recifra o que não mudou). **HMAC sem kid**: trocar a chave exige janela dupla e recálculo em lote; o hash do CPF retido 5 anos é re-identificável com a chave, então o expurgo zera também `cpf_hash`. Alerta no log para `DadoCifradoInvalidoException`.
- **Para a AWS-04**: segredos separados (cifra num, HMAC do CPF noutro), IAM por segredo só para a tarefa; preferir KMS GenerateMac para o HMAC do CPF (a chave não sai da KMS); envelope com chave de dados (formato versão 2 com o id da chave de dados); chaves por ambiente; backup das chaves separado (perdê-las = perder os dados). As três variáveis no Secrets Manager (cifradas pela KMS); avaliar cifra de envelope com chave de dados da KMS (a interface já separa chave atual e anteriores); rotina de recifra quando trocar o kid (ler, decifrar com o antigo, cifrar com o atual) e de recálculo do HMAC se a chave dele mudar; acesso às chaves só para a tarefa da aplicação.

**DOM-02 (dia 14): decisões de 16/10 (noite autônoma; conferir)**
- **Rascunho só do dono** (`usuario_id` do token; de outro = 404 igual a inexistente). O papel PROFISSIONAL **só** na aprovação (DOM-03); nome e celular vêm da conta. Em análise, **nada muda** (422 `cadastro-em-analise`); `correcao_pedida` volta a editar.
- **Decisões tomadas sozinho** (como desfazer entre parênteses): limite de **12 fotos** (constante `FOTOS_MAXIMAS`); URL do documento **2 min** (`coe.cadastro.validade-url-documento`), foto com a validade padrão; endpoints do cadastro **não** liberados com contato pendente (anotação + lista do `LiberadosComContatoPendenteTest`); comprovante de MEI fora do escopo; chave Pix do tipo `cpf` não precisa ser o próprio CPF (o admin confere, RN15; validar no serviço se o produto quiser); o envio confere a versão dos termos mas **não** grava novo `aceite_termos` (pendência jurídica: aceite próprio do profissional); **CPF único também em rascunho** (um rascunho "reserva" o CPF e quem tiver o CPF usado por outro fala com o suporte; alternativa: unicidade só a partir de `em_analise`, com índice parcial); um documento por tipo (V18; em banco local com repetidos, fica o mais novo); faixas de experiência `0, 1, 3, 5, 10, 20` (`FAIXAS_DE_EXPERIENCIA`; conferir com o protótipo no FE-04).
- **Revisões** (java-reviewer, security-reviewer, database-reviewer): sem CRITICAL. Corrigido: escapes da censura (número colado a unidade ou R$, dígitos Unicode e invisíveis, separadores extras, números por extenso com conectores e misturados com dígitos, variações de e-mail e de redes, mais domínios), "Sobre" conferido junto no envio; corrida no primeiro rascunho (INSERT ON CONFLICT + trava da linha com espera de 5 s → 409); ordem das fotos = maior + 1; V18; em análise antes do 409 do CPF; auditorias de dados, Pix e documento visto/removido; limite próprio do `/dados`; envio confere os valores mínimos de hoje; coleções mudadas no lugar (o Hibernate grava só a diferença).
- **Ficou para o DOM-03**: URL do documento para o ADMIN que verifica (auditada); travar `profissional` antes de `usuario` em todo caminho do admin (ordem única das travas, sem deadlock); histórico dos documentos recusados (hoje o mesmo tipo substitui; o histórico fica na `log_auditoria`).
- **Ficou para o FE-04**: telas das 11 etapas pelo protótipo; aviso da censura antes de enviar (o servidor decide); CSP `img-src` com o host do bucket na primeira tela com foto.
- **Ficou (outros)**: N+1 leve na montagem da resposta (nomes do catálogo por id; até ~40 serviços); varredura de arquivos órfãos (substituição e V18) junto com a do CORE-09; `UNIQUE (profissional_id, ordem) DEFERRABLE` quando houver reordenar fotos.

**DOM-03 (dia 17, adiantado na noite de 16/10): decisões (conferir)**
- Sem migração (V4/V11 já tinham `correcao_pedida`, motivos, `nr10_status` e a análise do documento). Fila paginada (50, teto da página 200) pelo índice parcial `ix_profissional_fila_analise`.
- **Decisões tomadas sozinho** (como desfazer): o detalhe mostra o **CPF completo** ao admin (necessário para conferir com o documento; auditado; limite próprio de 120/h); Pix continua mascarado (conferido no repasse, RN15); URL da foto do portfólio **sem** auditoria (vai ser pública no perfil); NR-10 recusado **não** impede a aprovação do cadastro (só tira o selo); recusa é definitiva no MVP (o cadastro não muda mais; reabrir fica com o admin numa tarefa futura); o motivo passa pela censura de contato e não entra na auditoria; SMS só para celular confirmado; o papel PROFISSIONAL vale no próximo token (até 15 min; o front chama `atualizarSessao()` quando souber da aprovação).
- **Revisões** (java-reviewer, security-reviewer): sem CRITICAL nem HIGH. Corrigido: motivo com censura e sem quebra de linha; SMS só para celular confirmado; limites na fila e no detalhe; `nr10_status` volta a `nao_tem` se o certificado saiu na correção; teto da página; varredura de logs do fluxo do admin.
- **Ficou**: exigir que o admin tenha aberto documento e selfie antes de aprovar (hoje o checklist é declarado; a auditoria registra o que foi aberto) — decidir no FE-07; recusar aprovação de conta suspensa ou excluída (409) junto com a suspensão do admin (FE-07/DOM-09); teste de corrida com dois admins em threads.

**DB-11 (dia 17, noite de 19/10): decisões (conferir)**
- `SeederLocal` (pacote `profissional`) no lugar de SQL: CPF e Pix só cifram em Java. Roda como `ApplicationRunner` com `@Profile("local")` e confere o placeholder `ambiente = local`; o ArchUnit exige `@Profile("local")` em todo `ApplicationRunner`/`CommandLineRunner`.
- **Decisões tomadas sozinho** (como desfazer):
  - **Mesmo caminho do cadastro**: as etapas vão pelo `CadastroProfissionalService` como o dono (token em memória) e com a Bean Validation dos pedidos; exemplo que fere regra derruba a subida (desfazer: logar e pular).
  - **Ativos aprovados direto**: entidade, sem admin (`aprovado_por` nulo), sem SMS nem auditoria de admin; o NR-10 dos eletricistas ativos fica `verificado` sem certificado guardado (desfazer: aprovar pelo `VerificacaoService` como o admin do seed, que exige documentos para todos).
  - **Celulares `479000501NN`** em vez de `4790000010N`: essa faixa colide com os celulares dos testes de integração (`CadastroClienteTest` começa em 47900000101) e o seeder roda também no banco do Testcontainers.
  - **CPFs fictícios** de bases `999000101…` com DV válido (acima da faixa do `CpfsDeTeste`); **Pix** aleatória fixa `00000000-0000-4000-8000-…`.
  - **Contas** com celular e e-mail (`profissionalNN@coe.local`) confirmados e papel CLIENTE (como quem se cadastra pelo app); idade e valores do protótipo; experiência na faixa do cadastro (22 anos → 20).
  - **Nelson Kraus**: no protótipo o "Sobre" tinha telefone (para o admin recusar); aqui passa pela censura, então o texto é limpo.
  - **Arquivos**: 2 fotos de portfólio por exemplo e, nos em análise, RG frente, selfie e certificado (Nelson); imagens geradas 640×480 com "EXEMPLO". Sem SeaweedFS: sem arquivo, aviso no log, em análise entra direto no status (desfazer: exigir o armazenamento).
  - **Retomada**: só o rascunho é retomado numa nova subida; correção pedida ou recusa feita por quem testa nunca é desfeita; sem arquivos (SeaweedFS fora) = aviso no log para recriar o banco local.
  - **ArchUnit** só nos runners (`ApplicationRunner`/`CommandLineRunner`): `@EventListener`, `@PostConstruct` e `SmartInitializingSingleton` têm usos legítimos de checagem na subida; a convenção (dado de exemplo = runner com `@Profile("local")`) está no CLAUDE.md.
- **Revisões** (java-reviewer, security-reviewer, database-reviewer): sem CRITICAL nem HIGH. Corrigido: e-mail confirmado (sem ele a conta ficava com contato pendente, achado no teste local), retomar só rascunho, celular/e-mail de outra conta = erro claro, cidade fora do catálogo = erro claro, imagens geradas fora do `try` do armazenamento e exceção no log, recusa com o perfil prod junto, contexto de segurança limpo depois, precedência da regra do ArchUnit.
  - **Sem histórico** (avaliações, contratos) até existirem as tabelas e os serviços (DOM-05 em diante).

**DOM-04 (dia 18, noite de 19/10): decisões (conferir)**
- Sem migração: os índices da V10 e as PKs/índices de agenda bastam (EXPLAIN local: 3,4 ms; database-reviewer sem índice faltando). A view `profissional_atende_cidade` da V10 não é usada (produto cartesiano caro); fica para remover numa migração futura.
- **Decisões tomadas sozinho** (como desfazer):
  - **Nome "Primeiro S."** na busca e no perfil (menos dado pessoal num endpoint aberto; o protótipo mostrava o nome completo) — trocar `nomeDeExibicao`.
  - **Dias livres a partir de amanhã**, janela de 4 semanas no perfil e no "livre antes"; filtro de dia até 90 dias — constantes em `BuscaPublicaService`.
  - **Diária `agendada` (pedido sem pagamento) ocupa o dia** na busca, igual ao índice único da agenda — tirar `agendada` do `livre(...)` se o negócio preferir.
  - **Profissão principal inativa ("em breve") tira o profissional da busca e do perfil**.
  - **Ferramentas**: `sim` = traz todas; `parte` = traz ao menos parte. **Filtro por texto (RF08) e ordem por avaliação (RF09) ficaram de fora**: texto livre pede índice e regra de relevância; avaliação só existe com o DOM de avaliações (RN21) — 422 `ordem-invalida` até lá.
  - **Sem cache** (o plano não pede); teto de 2 s por consulta + `publico-ip` 60/min. Limite global da busca e um balde mais apertado só para ela ficam para a revisão de números com dados reais.
  - **Código de erro**: 422 para valor/ordem/filtro inválidos (texto validado no serviço); 400 só para tipo errado no binding.
- **Revisões** (java-reviewer, security-reviewer, database-reviewer): sem CRITICAL. HIGH corrigido: foto ocultada pelo admin (`visivel = false`) saía no perfil. Corrigido também: série de dias por inteiro (sem depender do fuso da sessão), profissão principal ativa, teto de tempo, filtro de ferramentas, limites de tamanho e sinal nos filtros, `expiraEm` tomado antes de assinar, nota nula = "Novo na COE", rota sem Bearer casando também o método, teste com IP próprio por chamada.
- **Ficou**: calcular o `proximo_livre` só da página quando a ordem não for "livre" e medir com ~5 mil profissionais antes do lançamento; censura de contato no nome da conta (hoje só o "Primeiro S." sai); moderação de foto por denúncia (filtro extra quando o domínio `moderacao` existir); CSP `img-src` com o host do bucket na primeira tela com foto (FE).

**Casos de teste obrigatórios do CORE-03 e CORE-04** (PA03):
- Token de acesso expirado (Clock adiantado 15 min) é recusado com 401; renovação devolve um novo par
- Refresh usado duas vezes: a segunda é recusada e **todos** os tokens daquele login são revogados
- Logout revoga o refresh daquele aparelho; "sair de todos" revoga todos; troca de senha e inativação pelo admin também
- O token não contém celular, CPF nem nome
- O cookie do refresh sai com HttpOnly, Secure, SameSite=Strict e caminho só do endpoint de renovação
- Sem MFA ligado, login com celular + senha não pede SMS; com MFA ligado, exige o código antes de emitir o token

**Casos de teste obrigatórios do DOM-02** (RN08, idade mínima, com `Clock` injetado):
- 17 anos e 364 dias: cadastro recusado com "Para trabalhar na COE é preciso ter 18 anos ou mais."
- Exatos 18 anos (no dia do aniversário): cadastro aceito

**Casos de teste obrigatórios do DOM-05** (PA13):
- `CEP 89010-000` e `CEP 89010000` passam pela censura
- Telefone (com e sem separadores) continua bloqueado

**Casos de teste obrigatórios do DOM-09** (PA07, falta do profissional):
- Falta confirmada: reembolso **integral** da diária da falta (diária + comissão), conferido no ledger
- Cliente mantém as outras diárias: elas seguem normais
- Cliente cancela: as diárias futuras do contrato são reembolsadas integralmente; as já liberadas não mudam
- Falta confirmada grava uma linha em `ocorrencia_profissional`
- Disputa `nao_compareceu` decidida a favor do profissional **não** grava falta
- `ocorrencia_profissional` recusa UPDATE, DELETE e TRUNCATE
- Alerta no painel com `FALTAS_ALERTA` faltas em `FALTAS_JANELA_DIAS` dias, e sem alerta com uma falta a menos ou fora da janela
- Inativação é manual: nenhuma quantidade de faltas muda o status sozinha; inativar exige motivo e grava `log_auditoria`
- Reativação pelo admin volta o profissional a `ativo`, auditada
- O mesmo CPF não cria outro cadastro de profissional

**Teste de ponta a ponta da etapa:** profissional se cadastra, admin aprova, cliente busca, conversa, contrata 2 diárias, paga, profissional marca cheguei e terminei, cliente aprova uma e a outra libera sozinha em 12 h (relógio avançado). Os saldos do ledger batem centavo por centavo.

## 7. Etapa 4: Front em React
O React reproduz as telas do protótipo "Dia carimbado" com dados reais. Como a API é independente, telas podem ser adiantadas logo depois do módulo delas na etapa 3.

| ID | Tarefa | Entrega |
|---|---|---|
| FE-01 | Base do React | `frontend/` com Vite + React + TS (strict), React Router, `tokens.css`, fontes, layout (Cabeçalho, MenuInferior) e componentes base (Botao, Carimbo, StatusDiaria, Nota, Chip, Avatar) |
| FE-02 | Camada `src/api/` | `fetch` com o JWT no header `Authorization`, renovação do token ao receber 401 e, se falhar, tela de entrar; erros Problem Details em português; proxy `/api` do Vite para 8081 (porta do perfil local; a 8080 da máquina é do Apache) |
| FE-03 | Telas públicas e conta | Início, categorias, busca, perfil, entrar, criar conta (nome, celular, e-mail, CEP e senha; RF03), recuperar senha; aviso "Confirme seu e-mail para conseguir recuperar a senha" enquanto não confirmado; páginas de link (`/confirmar-email`, `/provar-email`, `/redefinir-senha`) limpam o `#token=` |
| FE-04 | Cadastro do profissional | 11 etapas com upload de fotos e documentos |
| FE-05 | Área do cliente | Contratar, pagar (adaptador falso), acompanhar diárias, aprovar, reclamar, avaliar, chat |
| FE-06 | Área do profissional | Hoje, cheguei, terminei com foto, agenda, dinheiro, conversas, perfil |
| FE-07 | Painel admin | Verificações, disputas, usuários, financeiro, configuração |
| FE-08 | Estados de tela | Carregando, vazio, erro e sem conexão |
| FE-09 | Testes | Vitest nos componentes; Playwright no caminho feliz em 360 px e desktop |
| FE-10 | Acessibilidade | axe nas telas principais + teste só com teclado |

**FE-04 (dias 19–20, feito em 22/10): decisões (conferir)**
- **Onde fica:** `/cadastro` (privada, qualquer logado), assistente das 11 etapas do protótipo com a etapa no endereço (`?etapa=N`; sem ela, a `etapaAtual` da API). Cada "Continuar" salva a etapa no servidor (PUT do DOM-02); dá para ir e voltar entre as etapas e o envio confere tudo de novo. Depois do envio o assistente some e fica a tela da situação (em análise, aprovado, recusado, suspenso); correção pedida volta a editar com o motivo no topo.
- **Decisões tomadas sozinho** (como desfazer):
  - **Conta antes do cadastro:** quem vai trabalhar cria a mesma conta do cliente (`POST /api/contas/cliente`) em `/criar-conta/profissional` e segue para `/cadastro`; "Quero trabalhar" (escolha de conta) e o botão do Início apontam para lá (antes, `/para-profissionais`, que não existe). Desfazer: página própria para profissionais (FE de marketing) e voltar os links.
  - **CPF e Pix nunca voltam em claro:** o campo começa vazio e a dica mostra a máscara ("Já guardamos o CPF ***.***.***-35"); sem digitar, fica o salvo. Mudar só a data de nascimento pede o CPF de novo (o PUT `/dados` exige os dois).
  - **Upload na hora:** documento, selfie e cada foto vão ao escolher o arquivo (tempo de 90 s por envio); `capture` no input (câmera de trás no documento, da frente na selfie), sem mudar a Permissions-Policy (`camera=()` continua: `getUserMedia` não é usado).
  - **Prévia da foto por `blob:`** (CSP `img-src 'self' data: blob:`, no Spring e no `cabecalhos-seguranca.json`); a foto já enviada abre por um link com a URL assinada (só http/https), sem o host do bucket na CSP; documento e selfie nunca são mostrados de volta. Desfazer: tirar `blob:` e mostrar só o nome do arquivo.
  - **RG (frente) ou CNH** escolhido na tela; o verso do RG não é pedido (o backend aceita, não exige).
  - **NR-10 só para eletricista** (o código `eletricista` no front, como o protótipo; o catálogo público não diz qual profissão pede NR-10 e o servidor recusa as outras).
  - **Prévia "Você recebe"** com a comissão e quem paga de `/api/publico/regras`, em centavos com HALF_EVEN (`formatacao/dinheiro.ts`, a mesma conta da `CalculadoraDiaria`); o servidor decide. A faixa "na região, X costumam cobrar entre…" do protótipo ficou de fora (não vem da API).
  - **"Em até 24 horas"** do protótipo saiu da tela de enviado (prazo da verificação não decidido).
  - **"Sobre" com ponto em cada frase** também no perfil público (`BuscaPublicaService.textoDoSobre`), para a prévia do cadastro ser igual ao perfil.
  - **`motivoCorrecao` no rascunho** (só com a correção pedida; o mesmo texto do SMS, já censurado).
  - **Aceite dos termos** no envio, como o protótipo; o backend confere a versão mas não grava um aceite próprio do profissional (pendência jurídica de 16/10 continua).
- **Revisões** (react-reviewer, security-reviewer): sem CRITICAL. Corrigido o HIGH (chave Pix pedida de novo quando o envio falhava depois de salvá-la) e os MEDIUM: Voltar desabilitado enquanto salva e a etapa só avança se ainda for a da tela; prévias `blob:` revogadas (também ao trocar o mesmo documento) e envio em série parado ao sair; foco no primeiro campo com erro (`aria-invalid` nas opções) e no título da tela de situação; "Foto tirada." anunciado com o foco de volta em "Adicionar fotos"; link da URL assinada só https (http só para localhost); aviso de conferir a foto depois de tempo esgotado; vetores da `CalculadoraDiariaTest` no teste da prévia; teste de que o `motivoCorrecao` não sai fora da correção pedida. LOW feitos: serviço salvo na foto continua como opção, texto do botão com `capture`, aviso de máximo nas opções.
- **Teste no navegador (22/10, Chrome, backend local e Vite):** conta de teste da regressão do FE-03 passou pelas 11 etapas com documento, selfie e fotos de verdade no SeaweedFS; o servidor recusou diária abaixo do mínimo (erro no campo, foco nele), telefone no "Sobre" (censura) e o envio sem celular confirmado; depois de confirmar o celular (UPDATE só dessa conta no banco local), o reenvio mandou só o `POST /envio` (sem pedir a chave Pix de novo) e chegou em "Cadastro enviado". Achado e corrigido: os botões − e + desalinhavam com o erro do valor (agora ficam na caixa do campo, `CampoTexto.prefixo`).
- **Ficou:** aviso da censura antes de enviar (hoje só a mensagem do servidor, no campo); limites de tela (10 frases, 300 letras, 4 outras profissões, 20 cidades, faixas, raios, horários) repetidos do servidor sem vir da API (o servidor decide; ler da API se mudarem); aviso de tamanho do arquivo antes de mandar (hoje o 413 do servidor); separar o erro do envio do erro de recarregar o rascunho na etapa de documentos; mudar só a data de nascimento pede o CPF de novo (contrato do `PUT /dados`); o que foi digitado e não salvo some ao Voltar; reordenar fotos; ver o documento enviado; o papel PROFISSIONAL no token depois da aprovação (`atualizarSessao()` quando o FE-06 souber da aprovação); tela própria para profissionais (marketing) antes do cadastro.

**FE-03 (dia 10): decisões e pendências**
- **Termos de uso e política de privacidade:** hoje `/termos` e `/privacidade` são **rascunho** (etiqueta visível). O texto final é **pendência obrigatória antes do lançamento** (LGPD, RNF18); quem redige: **A DEFINIR**. Ao publicar, subir `VERSAO_TERMOS` na configuração (o cadastro já pede o aceite da versão em vigor).
- **Sessão no front:** uma renovação silenciosa ao abrir o app (cookie HttpOnly do refresh); enquanto verifica, página privada mostra "Carregando…"; "Sair" leva ao Início (saída voluntária), sessão perdida leva a `/entrar?voltar=`.
- **`mfaAtivo` em `GET /api/contas/eu`** é a escolha da pessoa; para o ADMIN o código é sempre obrigatório (o front decide pelo papel).
- **"Quero trabalhar"** usa `CAMINHOS.paraProfissionais` (`/para-profissionais`), a mesma página do Início; não encontrado até o DOM-02. (FE-04: agora vai para `/criar-conta/profissional`.)
- **Posse do e-mail (decidido em 13/10, opção 1):** `POST /api/contas/posse/email/confirmar` devolve `{comprovante, email}` (e-mail normalizado do link; nada da outra conta). A `/provar-email` entrega o comprovante à aba do cadastro pelo `BroadcastChannel('coe-posse')` (resposta em até 1 s) ou continua o cadastro com o e-mail fixo. Comprovante vencido: o 422 `comprovante-invalido` do backend decide (sem relógio no front). Duas abas com o mesmo comprovante: o backend arbitra (comprovante de uso único).
- **Contato pendente no front (decidido em 13/10):** segue o backend. Liberadas: `/conta`, `/conta/recolocar-contato`, `/conta/trocar-senha`, `/conta/confirmar-celular`; o resto (hoje `/conta/codigo-no-login`) mostra a nota. Reenviar código ou link do contato novo = repetir o `PUT /eu/{celular,email}` (o `/eu/celular/codigo` e o `/eu/email/confirmacao` só servem para o contato já gravado).
- **Sair (revisão de 13/10):** falha da API não limpa a sessão local (o cookie da renovação continuaria valendo); só a sessão já encerrada no servidor sai.
- **Pendências das revisões (MEDIUM/LOW, sem CRITICAL nem HIGH abertos):**
  - Canal da posse sem nonce: uma aba da mesma origem pode responder "recebido" sem pegar o comprovante (só atrapalha o fluxo; exige XSS, que a CSP barra). Um nonce teria de ir no link do e-mail (mudança no backend); decidir depois.
  - Várias abas abrindo juntas renovam com o mesmo cookie: conferir no backend se o reuso do refresh tem janela de tolerância antes de virar "roubo" (CORE-07 ou revisão da CORE-03).
  - Refatorar: funções longas (`FormularioDoCliente`, `Entrar`, `NovoEmail`/`NovoCelular` do recolocar) em hooks; trava de envio própria em `Entrar`, `EsqueciSenha`, `RedefinirSenha` e `FormularioDoCliente` → `useEnvioUnico`; `comoErroDaApi` em todo catch; `SENHA_MINIMA` num lugar só; `CampoCodigo` no `/redefinir-senha`; `MandarDeNovo` no "Mandar o link de novo" da posse e de Minha conta; validar a forma da `SessaoResposta` (entrar e renovar); não passar o login digitado em `history.state` no "esqueci".
  - Testes no StrictMode para `/confirmar-email`, `/provar-email` e `/redefinir-senha`.

**DOM-01 + FE-02 (dia 9): decisões e pendências**
- **Catálogo público:** `GET /api/publico/catalogo` (áreas com profissões ativas, serviços ativos e cidades ativas por UF e `f_sem_acento(nome) COLLATE "C"`); sem ids internos, faixa de diária, NR-10 ou LC 150. Área não tem coluna `ativa`: aparece se tiver ao menos uma profissão ativa. Três consultas e cache de 60 s com `invalidarCache()` (para o admin, no FE-07). O `icone` sai na resposta, mas o front escolhe a ilustração pelo `codigo` da profissão.
- **Regras públicas:** `GET /api/publico/regras` = `{comissao: "0.1000", prazoLiberacao: "PT12H", taxaPagaPor: "CLIENTE"}`, de um snapshot do `ConfiguracaoNegocio`; nada mais sai. Frases do Início (09/10): "A taxa da COE é de 10%, paga pelo cliente." ou "..., descontada do valor do profissional."; valor desconhecido não mostra a frase.
- **Cache HTTP/CDN** dos dois GETs públicos: hoje `no-store`; decidir na etapa AWS (AWS-05/AWS-07).
- **Rate limit** dos dois GETs públicos entra na **CORE-07** (dia 11).
- **Dia 10, junto com o entrar:** renovação silenciosa ao abrir o app (recuperar a sessão pelo cookie) e a conferência manual da renovação pelo navegador (precisa de um login de verdade). A tela de entrar lê o `voltar` só com `caminhoInternoSeguro` e navega pelo Router (nunca `window.location`).
- **Busca de dados no front:** `useRecurso` (sem biblioteca); React Query só quando as telas logadas pedirem. Tipos das respostas à mão em `frontend/src/api/tipos.ts`; OpenAPI fica para depois.
- **Rotas do front** num arquivo só (`frontend/src/caminhos.ts`); a busca é `/busca?profissao=<codigo>&cidade=<codigoIbge>` (página de não encontrado até o dia 21).
- **Regras sem resposta:** o Início mostra a versão sem número ("depois de um prazo") e esconde a frase da taxa, sem caixa de erro; o "Tentar de novo" fica só no catálogo.

**FE-01 (dia 8): decisões e pendências**
- **Números de regra no front:** o Início não cita prazo de liberação nem comissão ("depois de um prazo", "A taxa da COE é paga pelo cliente."). No dia 9/10 entra um endpoint público de leitura (ex.: `GET /api/publico/regras`, `@Publico`, só comissão e prazo de liberação, lidos de `ConfiguracaoNegocio`, com cache) e o texto monta a frase com o valor da API. Nenhum desses números fica fixo no front.
- **Textos provisórios:** `frontend/src/paginas/inicio/conteudo.ts` (profissões do V3, cidades do V11, textos do protótipo) vira dado da API do catálogo no DOM-01/FE-03.
- **CSP e Permissions-Policy:** o Spring manda os dois em toda resposta (`SegurancaConfig`), com o mesmo texto de `frontend/cabecalhos-seguranca.json`, que o `vite preview` usa (`PoliticasDoNavegadorTest` compara). O `npm run dev` não tem CSP (o HMR precisa de script inline).
- **Quem emite os cabeçalhos em produção** (Spring ou ALB/CloudFront servindo o build): decidir no AWS-05/AWS-07. Se o HTML sair da borda, a borda aplica o mesmo `cabecalhos-seguranca.json`.
- **Permissões do aparelho:** câmera, microfone, localização, pagamento e USB fechados. Cada uma só abre quando uma funcionalidade pedir, com a decisão registrada aqui (próxima prevista: `camera=(self)` para a selfie do cadastro do profissional, FE-04). Quando houver upload, o `img-src` troca `data:` pelo host do bucket com URL assinada. (FE-04: a selfie usa `<input capture>`, sem `camera=(self)`; o `img-src` ganhou só `blob:`; o host do bucket entra na primeira tela que mostrar foto do bucket, FE da busca.)
- **Risco aceito (`npm audit`):** 7 avisos "high", todos de desenvolvimento, na cadeia `braces` 3.0.3 → micromatch → stylelint (ReDoS ao expandir globs no lint local). Não há versão corrigida do `braces` (a 3.0.3 é a última) e o `audit fix` rebaixaria o stylelint para a 7. `npm audit --omit=dev` = 0. **Decisão (09/10):** risco aceito só por ser dependência de desenvolvimento. **Regra a partir de agora:** `npm audit --omit=dev` sem nenhum aviso alto ou crítico (dependência de produção nunca entra como risco aceito). Rever na LOC-15 ou quando sair a correção no stylelint.
- **Rotas que ainda não existem** (`/buscar`, `/entrar`, `/criar-conta`, `/como-funciona`, `/para-profissionais`) caem em "Página não encontrada" até o FE-03.
- **Componentes base ainda sem tela** (StatusDiaria, Nota, Chip, carimbo "recusado"): entram nas telas do FE-03 a FE-06.
- **Lint de acessibilidade:** `eslint-plugin-jsx-a11y-x` (fork da es-tooling) aceito em 09/10, porque o `eslint-plugin-jsx-a11y` oficial só suporta o ESLint até a 9 (fora de suporte). Voltar ao oficial quando ele suportar o ESLint 10.
- **"Pix ou cartão"** no Início (passo 2 de "Como funciona") segue a RN30 e fica. Conferir o texto quando o gateway for escolhido (PA02).
- **Protótipo:** `docs/prototipo/` é cópia só de leitura do commit ef7d397; as ilustrações saíram dele uma vez (`frontend/scripts/gerar-ilustracoes.mjs`) e os SVGs ficam no git.

**Pronto quando:** o caminho feliz roda clicando, sem nenhum dado falso no front.

## 8. Etapa 5: Aceite local
**Subir do zero**
- [ ] LOC-01 Clone limpo → `.env` → Postgres → `./mvnw spring-boot:run` (perfil local) e `npm run dev` funcionam sem passo manual a mais.
- [ ] LOC-02 O início abre com os dados de exemplo.
- [ ] LOC-03 Recriar o banco local volta ao estado inicial.

**Fluxos pelo navegador**
- [ ] LOC-04 Profissional se cadastra nas 11 etapas e fica em análise.
- [ ] LOC-05 Admin aprova, e o profissional aparece na busca da cidade e do raio certos.
- [ ] LOC-06 Chat bloqueia telefone, e-mail, link e @perfil, e deixa passar CEP e texto normal.
- [ ] LOC-07 Cliente contrata, paga no adaptador falso e só então vê o telefone.
- [ ] LOC-08 Profissional marca cheguei e terminei com foto; cliente aprova; valor aparece em "Dinheiro".
- [ ] LOC-09 Diária sem resposta libera sozinha (relógio adiantado no perfil local).
- [ ] LOC-10 Reclamação trava só aquele dia; admin decide pelos dois caminhos.
- [ ] LOC-11 Diarista: a 3ª diária na janela de 7 dias para o mesmo cliente é recusada.
- [ ] LOC-12 Painel financeiro: custódia + repassado + receita = total pago.

**Qualidade**
- [ ] LOC-13 `./mvnw verify` e `npm test` verdes, com a cobertura exigida.
- [ ] LOC-14 Playwright verde em 360 px e desktop.
- [ ] LOC-15 `/security-scan` sem achado crítico ou alto; OWASP Dependency-Check e `npm audit` sem CVE crítica; `npm audit --omit=dev` sem alto nem crítico; rever o risco aceito do `braces` (dev, FE-01).
- [ ] LOC-16 Logs sem CPF, telefone, e-mail ou senha numa execução completa.
- [ ] LOC-17 Imagem Docker da aplicação (multi-stage, com o build do React) sobe localmente. É a mesma que vai para a AWS.

## 9. Etapa 6: AWS
| No local | Na AWS |
|---|---|
| Aplicação local | ECS Fargate atrás de um Application Load Balancer |
| Postgres local | RDS for PostgreSQL 16, subnet privada, backup automático |
| S3 local (CORE-09) | S3 privado com URL assinada |
| `.env` | Secrets Manager + KMS |
| Mailpit / SMS falso | Amazon SES + provedor de SMS |
| Logs no terminal | CloudWatch Logs e alarmes |
| `localhost` | Route 53 + certificado ACM (HTTPS) |
| Build na máquina | GitHub Actions → ECR, acesso à AWS por OIDC |

| ID | Tarefa | Entrega |
|---|---|---|
| AWS-01 | Conta e acesso | MFA no root, IAM Identity Center, alerta de orçamento |
| AWS-02 | Rede | VPC com subnets públicas (ALB) e privadas (app e banco) |
| AWS-03 | Banco | RDS; Flyway na subida; teste de restaurar backup |
| AWS-04 | Arquivos e segredos | S3, Secrets Manager, KMS |
| AWS-05 | Aplicação | ECR, ECS, ALB com health check |
| AWS-06 | Pipeline | Testes → imagem → homologação; produção com aprovação manual |
| AWS-07 | Domínio e HTTPS | Domínio, ACM, redirecionamento para HTTPS |
| AWS-08 | Observabilidade | Logs, métricas, alarmes (5xx, disputas, job de liberação parado) |
| AWS-09 | Gateway real | Adaptador do gateway escolhido (PA02) no sandbox |
| AWS-10 | Produção | Mesma infra com outras variáveis; aceite repetido em homologação |

Tudo em código (Terraform ou AWS CDK), nada configurado à mão no console.

**Correlação com o ALB (CORE-11, 14/10):** o ALB manda `X-Amzn-Trace-Id`. Gravar esse valor no MDC ao lado do nosso id (`requisicao`), sem aceitar `X-Request-Id` de fora, para cruzar o log da aplicação com o log de acesso do ALB. Validar o formato antes de gravar (só `Root=1-…`, tamanho limitado).

**Conexões com o RDS (CORE-07, 14/10):** cada instância abre o pool da aplicação (Hikari, padrão 10) **mais 5 do limitador de taxa** (`coe.limites.conexoes`, pool próprio para não esgotar o da aplicação). No AWS-03/AWS-05, dimensionar o `max_connections` do RDS para (pool da aplicação + 5) × número máximo de instâncias do auto scaling, com folga para o Flyway e a administração, ou pôr o **RDS Proxy** na frente. Os dois pools usam o login da aplicação (membro de `coe_app`), nunca o dono do schema.

## 10. Decisões que travam tarefas
| Ponto | Precisa estar decidido antes de | Proposta |
|---|---|---|
| PA02 Gateway | Dia 52 (AWS-09) | Escolher o gateway e **abrir o sandbox até o dia 40**, para o AWS-09 ter conta e credenciais de teste prontas |

Já decididos: PA01 (PostgreSQL), PA03 (JWT de 15 min + refresh de 30 dias em cookie HttpOnly, uma sessão por aparelho, MFA opcional), PA04 (React), PA05 (comissão paga pelo cliente), PA06 (reembolso = o que o cliente pagou), PA07 (falta do profissional, RN44a–RN44e), PA08 (12 h a partir do "Terminei o dia"), PA13 (CEP passa na censura), LC 150 em janela de 7 dias (confirmada com o advogado), uma diária por profissional por dia, idade mínima de 18 anos, retenção LGPD de 5 anos.

### Tarefas futuras registradas
- **Expurgo LGPD** (job agendado, idempotente, com log): 5 anos após a exclusão da conta, apaga CPF, chave Pix e data de nascimento; mantém o `cpf_hash` só de quem estiver inativado (`suspenso`). Precisa de migração nova: hoje o `ck_profissional_completo` exige esses campos fora do rascunho.
- **Reativação após exclusão de conta**: **A DEFINIR**. Enquanto isso, o mesmo CPF não cria novo cadastro.

## 11. Cronograma dia a dia
Cada dia começa com `/resume-session` e termina com commit, `/save-session` e "Status atual" do CLAUDE.md atualizado. Os dias 11, 22 e 44 são de revisão e servem de folga se o cronograma atrasar. Sem uma decisão necessária, marque o dia como travado e adiante o seguinte.

### Largada: Etapas 0 e 1 (dias 1–2)
| Dia | Tarefas | O que fazer | Pronto quando |
|---|---|---|---|
| 1 | AMB-01 a AMB-04, DB-01 a DB-10 | Conferir o pom; `application.yml`, `application-local.yml`, `.env`; pacotes; migrações V1–V10 e dados locais; subir no perfil local | `flyway_schema_history` com V1 a V10 + R; health UP (**M1**) |
| 2 | AMB-05 a AMB-08, teste das migrações | Compose (Postgres, Mailpit); `IntegracaoTest`; teste das migrações e constraints; JaCoCo; Problem Details; README | `./mvnw verify` verde (**M0**) |

### Fatia 0: Núcleo (dias 3–7)
| Dia | Tarefas | O que fazer | Pronto quando |
|---|---|---|---|
| 3 ✅ | CORE-01, CORE-12, CORE-13 | `Dinheiro`, comissão, configuração, `Clock` | 100% no cálculo de comissão e repasse (**feito**) |
| 4 ✅ | CORE-02 | Cadastro de cliente (com e-mail) | Celular ou e-mail repetido é recusado; e-mail obrigatório (**feito**) |
| 5 ✅ | CORE-03, DB-13 | Login com JWT (celular ou e-mail), renovação, logout, sair de todos; V12 (`refresh_token`, `mfa_sms_ativo`, sem `spring_session*`, teto de 30% na comissão) | Token expirado ou revogado é recusado; refresh reutilizado revoga a família (**feito**) |
| 6 ✅ | CORE-06, CORE-08, DB-15 | Papéis (`@PreAuthorize` ou `@Publico` em todo endpoint), checagem de dono (404 no recurso de outro), `GET /api/contas/eu`, CORS por perfil, Origin no renovar e no sair, headers de segurança; V13 (teto de 90 dias da sessão); 401/403 em Problem Details | 404 no recurso de outro, 403 no papel errado (**feito**) |
| 7 | CORE-04, CORE-05 | Login por SMS, MFA (obrigatório para ADMIN), confirmação de contato (RN61), recuperar senha | Código expira e trava na 5ª tentativa. **Feito** (7a: PR #11; 7b: e-mail, RN61, senha) |

### Fatia 1: Entrar no app (dias 8–11) — primeiro clique no dia 10
| Dia | Tarefas | O que fazer | Pronto quando |
|---|---|---|---|
| 8 | FE-01 | Vite + React + TS, `tokens.css`, fontes, layout e componentes base | Início estático igual ao protótipo |
| 9 | FE-02, DOM-01 | Camada `src/api/` (JWT, renovação, erros em pt-BR); API do catálogo | Filtros com dados da API |
| 10 | FE-03 (parte) | Início, categorias, entrar, criar conta, recuperar senha | **Criar conta e entrar pelo navegador** (**M2**) |
| 11 | CORE-07, CORE-11, revisão | Rate limit, auditoria, logs mascarados; `/security-scan`; folga | Logs sem dado pessoal |

### Fatia 2: O profissional entra na busca (dias 12–22)
| Dia | Tarefas | O que fazer | Pronto quando |
|---|---|---|---|
| 12 | CORE-09 | Armazenamento (S3 local: SeaweedFS): tipo real, tamanho, sem EXIF, URL assinada | Upload falso rejeitado |
| 13 | CORE-10 | Criptografia de CPF, Pix e endereço | CPF cifrado no banco |
| 14 | DOM-02 | Cadastro, etapas 1–4, com idade mínima (18 anos, `Clock`) | 17 anos e 364 dias recusado; exatos 18 aceito |
| 15 | DOM-02 | Etapas 5–8 (cidades, valor, agenda, bio) | Bio com telefone é recusada |
| 16 | DOM-02 | Etapas 9–11 (fotos, documento, Pix), envio | Fica "em análise" e fora da busca |
| 17 | DOM-03, DB-11 | Fila de verificação, NR-10; seeder local de profissionais | Aprovado vira ativo; auditado |
| 18 | DOM-04 | Busca com filtros e raio; perfil público, dias livres, portfólio | Nenhum contato na resposta |
| 19 | FE-04 | Telas do cadastro do profissional, etapas 1–6 | Rascunho salvo na API |
| 20 | FE-04, FE-07 (parte) | Etapas 7–11 com upload; fila de verificação no admin | Cadastro chega ao admin, que aprova |
| 21 | FE-03 (resto) | Busca e perfil público | **Cadastrar, aprovar e achar o profissional na busca, pelo navegador** |
| 22 | Revisão | `/security-scan`, cobertura; folga | |

### Fatia 3: Contratar, pagar e trabalhar (dias 23–35)
| Dia | Tarefas | O que fazer | Pronto quando |
|---|---|---|---|
| 23 | DOM-05 | Conversa e censura no servidor (CEP passa, PA13) | Bloqueia e deixa passar o certo |
| 24 | DOM-05 | Tentativas e conta em análise | 4ª tentativa → análise |
| 25 | DOM-06 | Contrato, endereço cifrado, total | Total confere com o banco |
| 26 | DOM-06 | Agenda, LC 150 com trava, expiração | 3ª diária na janela recusada |
| 27 | DOM-07 | `GatewayPagamento` e adaptador falso | Cobrança criada e consultada |
| 28 | DOM-07 | Webhook assinado e idempotente; ledger | Webhook repetido não duplica |
| 29 | DOM-07 | Contrato pago, contato liberado; `/security-scan` | Telefone só após pagamento |
| 30 | DOM-08 | Cheguei, terminei com foto, histórico | Transição inválida recusada |
| 31 | DOM-08 | Aprovar e liberar pelo ledger | Saldos conferem |
| 32 | DOM-08 | Job das 12 h a partir do `terminou_em` (ShedLock); repasse | Sem liberação dupla |
| 33 | FE-05 | Contratar, pagar (adaptador falso) e chat | Contrato pago pelo navegador |
| 34 | FE-06 | Área do profissional: Hoje, Cheguei, Terminei, agenda, dinheiro | Fluxo do profissional completo |
| 35 | FE-05 | Acompanhar e aprovar diárias | **Contratar, pagar, trabalhar e aprovar, pelo navegador** (**M3**) |

### Fatia 4: Problemas e administração (dias 36–44)
| Dia | Tarefas | O que fazer | Pronto quando |
|---|---|---|---|
| 36 | DOM-09, DB-14 | Abrir disputa, resposta, prazo; migração do DB-14 (`ocorrencia_profissional`) | Uma disputa aberta por diária |
| 37 | DOM-09 | Decisão; falta do profissional (PA07): reembolso integral, registro da falta | Casos obrigatórios do DOM-09 passam |
| 38 | DOM-10 | Avaliações e média | Só quem pagou avalia |
| 39 | DOM-11 | Usuários, faltas com alerta, inativar e reativar, configuração | Ações auditadas; alerta com 2 faltas em 90 dias |
| 40 | DOM-11 | Financeiro do admin; notificações. **Prazo para abrir o sandbox do gateway (PA02)** | Painel bate com `saldo_conta` |
| 41 | FE-07, FE-05 | Painel admin completo; reclamar e avaliar | Admin opera tudo pela tela |
| 42 | FE-08 | Estados de carregando, vazio, erro e sem conexão | Nenhuma tela branca em erro |
| 43 | FE-09, FE-10 | Vitest, Playwright (360 px e desktop), acessibilidade | **Tudo clicável, E2E verde** (**M4**) |
| 44 | Revisão | Teste de ponta a ponta da etapa 3; folga | |

Regra de cada fatia: primeiro a API, com testes; depois a tela. Assim uma mudança de contrato de API aparece antes de virar retrabalho no front.

### Etapa 5: Aceite local (dias 45–46)
| Dia | Tarefas | O que fazer | Pronto quando |
|---|---|---|---|
| 45 | LOC-01 a LOC-12 | Clone limpo; checklist de subida e de fluxos | Todos os fluxos marcados |
| 46 | LOC-13 a LOC-17 | Cobertura, Playwright, segurança, logs, imagem Docker | **M5** |

### Etapa 6: AWS (dias 47–53)
| Dia | Tarefas | O que fazer | Pronto quando |
|---|---|---|---|
| 47 | AWS-01, AWS-02 | Conta, orçamento, VPC em Terraform ou CDK | Infra aplicada sem erro |
| 48 | AWS-03, AWS-04 | RDS, S3, Secrets Manager, KMS | Flyway roda no RDS |
| 49 | AWS-05 | ECR, ECS, ALB | App responde pelo ALB |
| 50 | AWS-06 | GitHub Actions com OIDC | Push na `main` publica sozinho |
| 51 | AWS-07, AWS-08 | Domínio, HTTPS, alarmes | Homologação em HTTPS |
| 52 | AWS-09 | Gateway real no sandbox | Pagamento de teste via webhook |
| 53 | AWS-10 | Aceite em homologação | **M6** |
