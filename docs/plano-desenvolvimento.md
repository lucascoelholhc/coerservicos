# Plano de desenvolvimento: COE Serviços

> Fonte: documento "COE Serviços — Plano de desenvolvimento" (atualizado em 02/10/2026). Base: `docs/regras-negocio.md` (RN01–RN60, RF01–RF58, RNF01–RNF29).

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
| Ambiente local | Postgres + Docker Compose (Mailpit para e-mail/SMS falso). S3 local escolhido no CORE-09: a imagem pública do MinIO deixou de existir |
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
| AMB-02 | `docker-compose.yml` | `postgres:16` (volume nomeado, porta 5433, cria o papel `coe_app`), `mailpit`. Sem S3 por ora (entra no CORE-09) |
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
| DB-13 | `V12__autenticacao_jwt` | Remove `spring_session*` (PA03 = JWT); cria `refresh_token` (hash único, usuário, aparelho, validade, revogado em, substituído por, família para revogar tudo em caso de reuso); `usuario.mfa_sms_ativo` (padrão falso); finalidade `mfa` no `codigo_sms` |
| DB-14 | `V13__ocorrencia_profissional` | Junto do DOM-09. `ocorrencia_profissional` só de inserção (gatilhos `fn_somente_insercao` de linha e TRUNCATE, sem UPDATE/DELETE/TRUNCATE para o `coe_app`); `profissional.motivo_suspensao` obrigatório com status `suspenso`; parâmetros `FALTAS_ALERTA` = 2 e `FALTAS_JANELA_DIAS` = 90 com faixa validada (PA07) |

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
| CORE-03 | Senha e login | Argon2 ou BCrypt; login por celular + senha; JWT de 15 min (HS256, `kid`) + refresh de 30 dias rotativo em cookie HttpOnly; revogação no logout, na troca de senha e na inativação; reuso de refresh revoga a família; uma sessão por aparelho e "sair de todos" (PA03) | RNF01 |
| CORE-04 | Login por SMS e MFA opcional | Código de 6 dígitos em hash, 5 min, 5 tentativas; `EnviadorSms` falso no local; entrar só com código (alternativa à senha); segundo passo por SMS **só para quem ligar** (MFA opcional) | RF01 |
| CORE-05 | Recuperar senha | Token de uso único com validade curta | RF01 |
| CORE-06 | Papéis e autorização | CLIENTE, PROFISSIONAL, ADMIN; checagem de dono em todo recurso | RNF02 |
| CORE-07 | Rate limit | Bucket4j em login, SMS, recuperação de senha e chat | RNF03 |
| CORE-08 | CSRF e CORS | CORS fechado ao domínio da COE; CSRF só no endpoint de renovação do token (único que usa cookie) | RNF01 |
| CORE-09 | Arquivos | `Armazenamento` (S3): tipo real, tamanho, sem EXIF, nome gerado, URL assinada. Escolher o S3 local do compose (SeaweedFS, LocalStack ou RustFS) | RNF07, RNF14 |
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
| DOM-05 | Chat e censura | Censura no servidor; conta em análise após N tentativas; CEP passa (PA13) | Testes com padrões que bloqueiam e que passam (R$, medidas, datas, `CEP 89010-000`, `CEP 89010000`) |
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
| FE-02 | Camada `src/api/` | `fetch` com o JWT no header `Authorization`, renovação do token ao receber 401 e, se falhar, tela de entrar; erros Problem Details em português; proxy `/api` do Vite para 8080 |
| FE-03 | Telas públicas e conta | Início, categorias, busca, perfil, entrar, criar conta (nome, celular, e-mail, CEP e senha; RF03), recuperar senha |
| FE-04 | Cadastro do profissional | 11 etapas com upload de fotos e documentos |
| FE-05 | Área do cliente | Contratar, pagar (adaptador falso), acompanhar diárias, aprovar, reclamar, avaliar, chat |
| FE-06 | Área do profissional | Hoje, cheguei, terminei com foto, agenda, dinheiro, conversas, perfil |
| FE-07 | Painel admin | Verificações, disputas, usuários, financeiro, configuração |
| FE-08 | Estados de tela | Carregando, vazio, erro e sem conexão |
| FE-09 | Testes | Vitest nos componentes; Playwright no caminho feliz em 360 px e desktop |
| FE-10 | Acessibilidade | axe nas telas principais + teste só com teclado |

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
- [ ] LOC-15 `/security-scan` sem achado crítico ou alto; OWASP Dependency-Check e `npm audit` sem CVE crítica.
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
| 3 | CORE-01, CORE-12, CORE-13 | `Dinheiro`, comissão, configuração, `Clock` | 100% no cálculo de comissão e repasse |
| 4 | CORE-02 | Cadastro de cliente (com e-mail) | Celular ou e-mail repetido é recusado; e-mail obrigatório |
| 5 | CORE-03, DB-13 | Login com JWT, renovação, logout, sair de todos; V12 (`refresh_token`, `mfa_sms_ativo`, sem `spring_session*`) | Token expirado ou revogado é recusado; refresh reutilizado revoga a família |
| 6 | CORE-06, CORE-08 | Papéis, checagem de dono, CORS, CSRF na renovação; 401/403 em Problem Details | 403 no recurso de outro |
| 7 | CORE-04, CORE-05 | Login por SMS, MFA opcional, recuperar senha | Código expira e trava na 5ª tentativa |

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
| 12 | CORE-09 | Armazenamento (escolher o S3 local do compose): tipo real, tamanho, sem EXIF, URL assinada | Upload falso rejeitado |
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
| 36 | DOM-09, DB-14 | Abrir disputa, resposta, prazo; V13 (`ocorrencia_profissional`) | Uma disputa aberta por diária |
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
