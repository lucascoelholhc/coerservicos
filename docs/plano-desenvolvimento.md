# Plano de desenvolvimento: COE Serviços

> Fonte: documento "COE Serviços — Plano de desenvolvimento" (atualizado em 02/10/2026). Base: `docs/regras-negocio.md` (RN01–RN60, RF01–RF58, RNF01–RNF29).

O MVP sai em 7 etapas, de 0 a 6. As etapas 0 a 5 terminam com o sistema rodando inteiro na máquina local, e a etapa 6 leva tudo para a AWS. São **53 dias de trabalho** (um dia = uma sessão de cerca de 3 horas, não um dia do calendário): sistema completo local no dia 46, homologação na AWS no dia 53.

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
| Ambiente local | Postgres + Docker Compose (MinIO no lugar do S3, Mailpit para e-mail/SMS falso) |
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
| M2 Núcleo | Criar conta, entrar e sair pela API, com papéis funcionando |
| M3 Domínio | Fluxo completo pela API: profissional aprovado → contrato → pagamento falso → diária liberada |
| M4 Front | O mesmo fluxo clicando no React |
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
| AMB-02 | `docker-compose.yml` | `postgres:16` (volume nomeado), `minio`, `mailpit` |
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
| DB-02 | `V2__conta` | `usuario`, `usuario_papel`, `codigo_sms`, `token_senha`, `aceite_termos`, Spring Session |
| DB-03 | `V3__catalogo` | `cidade`, `area`, `profissao`, `servico` + dados de referência |
| DB-04 | `V4__profissional` | Perfil, serviços, cidades, agenda, documentos, portfólio |
| DB-05 | `V5__contratacao` | `contrato`, `foto_pedido`, `diaria`, `evidencia_diaria`, `historico_diaria` |
| DB-06 | `V6__pagamento` | `cobranca`, `evento_gateway`, ledger (`conta_razao`, `transacao_financeira`, `lancamento`), `repasse`, `reembolso` |
| DB-07 | `V7__chat` | `conversa`, `mensagem`, `tentativa_contato` |
| DB-08 | `V8__avaliacao_disputa` | `avaliacao`, `disputa`, `anexo_disputa` |
| DB-09 | `V9__admin_auditoria` | `log_auditoria`, `notificacao`, `denuncia` |
| DB-10 | `V10__indices_busca` | Índices e view de busca por cidade e raio |
| DB-11 | `R__dados_local` + seeder Java | Só no perfil local; profissionais de exemplo pelo seeder (campos cifrados) |

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
| CORE-03 | Senha e login | Argon2 ou BCrypt; login por celular + senha; sessão (PA03) | RNF01 |
| CORE-04 | Login por SMS | Código de 6 dígitos em hash, 5 min, 5 tentativas; `EnviadorSms` falso no local | RF01 |
| CORE-05 | Recuperar senha | Token de uso único com validade curta | RF01 |
| CORE-06 | Papéis e autorização | CLIENTE, PROFISSIONAL, ADMIN; checagem de dono em todo recurso | RNF02 |
| CORE-07 | Rate limit | Bucket4j em login, SMS, recuperação de senha e chat | RNF03 |
| CORE-08 | CSRF e CORS | Token CSRF para o React; CORS fechado | RNF01 |
| CORE-09 | Arquivos | `Armazenamento` (MinIO/S3): tipo real, tamanho, sem EXIF, nome gerado, URL assinada | RNF07, RNF14 |
| CORE-10 | Criptografia de campo | Conversor JPA AES-GCM para CPF, Pix e endereço | RNF13 |
| CORE-11 | Auditoria e logs | `log_auditoria`; logs JSON com máscara de dados pessoais | RNF08, RNF15 |
| CORE-12 | Configuração | Leitura da `configuracao_vigente` | RN31, RN38 |
| CORE-13 | Relógio injetável | `Clock` em todo serviço com prazo | Testabilidade |

**Pronto quando:** dá para criar cliente, entrar por senha e por SMS, sair, e um cliente recebe 403 ao ler o recurso de outro.

## 6. Etapa 3: Módulos de negócio
| ID | Módulo | O que constrói | Pronto quando |
|---|---|---|---|
| DOM-01 | Catálogo | Categorias, profissões, serviços e cidades ativas | O front monta os filtros só com dados da API |
| DOM-02 | Cadastro do profissional | 11 etapas com rascunho, bio com censura, fotos, documento + selfie, Pix | Cadastro completo fica "em análise" e fora da busca |
| DOM-03 | Verificação | Fila do admin; aprovar ou recusar com motivo; selo NR-10 | Aprovado vira ativo; tudo auditado |
| DOM-04 | Busca e perfil | Filtros (profissão, cidade no raio, dia livre, valor, experiência); perfil sem contato | Nenhum campo de contato sai antes do pagamento |
| DOM-05 | Chat e censura | Censura no servidor; conta em análise após N tentativas | Testes com padrões que bloqueiam e que passam (R$, medidas, datas, CEP) |
| DOM-06 | Contratação | Pedido, datas, agenda, LC 150 (janela de 7 dias), comissão congelada | 3ª diária na janela é recusada; dia ocupado é recusado |
| DOM-07 | Pagamento e custódia | `GatewayPagamento` + adaptador falso; webhook assinado e idempotente; ledger; custódia rastreada por contrato | Webhook repetido não duplica nada; saldos batem; custódia por contrato nunca negativa |
| DOM-08 | Execução da diária | Cheguei, terminei com foto, aprovar; job das 12 h (ShedLock); repasse | Duas instâncias não liberam a mesma diária |
| DOM-09 | Disputa e reembolso | Reclamação trava só o dia; decisão do admin; reembolso = o que o cliente pagou | Os dois caminhos testados no ledger |
| DOM-10 | Avaliações | Nota dos dois lados; média no perfil | Só avalia quem pagou pelo app |
| DOM-11 | Admin e notificações | Usuários, denúncias, financeiro, configuração; SMS e e-mail | Toda ação do admin em `log_auditoria` |

**Casos de teste obrigatórios do DOM-07** (decididos no DB-12, ficaram fora da V11):
- Custódia rastreada **por contrato**: liberar a diária de um contrato nunca usa dinheiro guardado de outro contrato.
- Saldo de custódia de um contrato **nunca fica negativo**, nem com duas liberações concorrentes (trava por contrato).
- Saldo do profissional nunca fica negativo no repasse.
- Os totais batem centavo por centavo: custódia + repassado + receita = total pago.

**Teste de ponta a ponta da etapa:** profissional se cadastra, admin aprova, cliente busca, conversa, contrata 2 diárias, paga, profissional marca cheguei e terminei, cliente aprova uma e a outra libera sozinha em 12 h (relógio avançado). Os saldos do ledger batem centavo por centavo.

## 7. Etapa 4: Front em React
O React reproduz as telas do protótipo "Dia carimbado" com dados reais. Como a API é independente, telas podem ser adiantadas logo depois do módulo delas na etapa 3.

| ID | Tarefa | Entrega |
|---|---|---|
| FE-01 | Base do React | `frontend/` com Vite + React + TS (strict), React Router, `tokens.css`, fontes, layout (Cabeçalho, MenuInferior) e componentes base (Botao, Carimbo, StatusDiaria, Nota, Chip, Avatar) |
| FE-02 | Camada `src/api/` | `fetch` com cookie e CSRF, erros Problem Details em português, 401 → entrar; proxy `/api` do Vite para 8080 |
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
| MinIO | S3 privado com URL assinada |
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
| PA03 Autenticação | Dia 5 (CORE-03) | Sessão em cookie |
| PA13 CEP na censura | Dia 18 (DOM-05) | Deixar passar o formato 00000-000 |
| PA05 Quem paga a comissão | Dia 20 (DOM-06) | Cliente (padrão atual) |
| PA08 Marco das 12 h | Dia 25 (DOM-08) | A partir do "Terminei o dia" |
| PA07 Falta do profissional | Dia 28 (DOM-09) | Decidir a política |
| PA02 Gateway | Dia 52 (AWS-09) | Abrir o sandbox com antecedência |

Já decididos: PA01 (PostgreSQL), PA04 (React), PA06 (reembolso = o que o cliente pagou), LC 150 em janela de 7 dias, uma diária por profissional por dia.

## 11. Cronograma dia a dia
Cada dia começa com `/resume-session` e termina com commit, `/save-session` e "Status atual" do CLAUDE.md atualizado. Os dias 10, 33 e 44 são de revisão e servem de folga se o cronograma atrasar. Sem uma decisão necessária, marque o dia como travado e adiante o seguinte.

### Largada: Etapas 0 e 1 (dias 1–2)
| Dia | Tarefas | O que fazer | Pronto quando |
|---|---|---|---|
| 1 | AMB-01 a AMB-04, DB-01 a DB-10 | Conferir o pom; `application.yml`, `application-local.yml`, `.env`; pacotes; migrações V1–V10 e dados locais; subir no perfil local | `flyway_schema_history` com V1 a V10 + R; health UP (**M1**) |
| 2 | AMB-05 a AMB-08, teste das migrações | Compose (MinIO, Mailpit); `IntegracaoTest`; teste das migrações e constraints; JaCoCo; Problem Details; README | `./mvnw verify` verde (**M0**) |

### Etapa 2: Núcleo e segurança (dias 3–10)
| Dia | Tarefas | O que fazer | Pronto quando |
|---|---|---|---|
| 3 | CORE-01, CORE-12, CORE-13 | `Dinheiro`, comissão, configuração, `Clock` | 100% no cálculo de comissão e repasse |
| 4 | CORE-02 | Cadastro de cliente | Celular ou e-mail repetido é recusado; e-mail obrigatório |
| 5 | CORE-03 | Login, sessão, logout | Cookie `HttpOnly` e `Secure` |
| 6 | CORE-06, CORE-08 | Papéis, checagem de dono, CSRF, CORS | 403 no recurso de outro |
| 7 | CORE-04, CORE-05 | Login por SMS, recuperar senha | Código expira e trava na 5ª tentativa |
| 8 | CORE-07, CORE-11 | Rate limit, auditoria, logs mascarados | Logs sem dado pessoal |
| 9 | CORE-09, CORE-10 | Armazenamento, criptografia de campo | Upload falso rejeitado; CPF cifrado |
| 10 | Revisão | `/security-scan`, correções, cobertura | **M2** |

### Etapa 3: Módulos de negócio (dias 11–33)
| Dia | Tarefas | O que fazer | Pronto quando |
|---|---|---|---|
| 11 | DOM-01, DB-11 | API do catálogo; seeder local | Filtros com dados da API |
| 12 | DOM-02 | Cadastro, etapas 1–4 | Rascunho salvo a cada passo |
| 13 | DOM-02 | Etapas 5–8 (cidades, valor, agenda, bio) | Bio com telefone é recusada |
| 14 | DOM-02 | Etapas 9–11 (fotos, documento, Pix), envio | Fica "em análise" e fora da busca |
| 15 | DOM-03 | Fila de verificação, NR-10 | Aprovado vira ativo; auditado |
| 16 | DOM-04 | Busca com filtros e raio | Busca usa a view do raio |
| 17 | DOM-04 | Perfil público, dias livres, portfólio | Nenhum contato na resposta |
| 18 | DOM-05 | Conversa e censura no servidor | Bloqueia e deixa passar o certo |
| 19 | DOM-05 | Tentativas e conta em análise | 4ª tentativa → análise |
| 20 | DOM-06 | Contrato, endereço cifrado, total | Total confere com o banco |
| 21 | DOM-06 | Agenda, LC 150 com trava, expiração | 3ª diária na janela recusada |
| 22 | DOM-07 | `GatewayPagamento` e adaptador falso | Cobrança criada e consultada |
| 23 | DOM-07 | Webhook assinado e idempotente; ledger | Webhook repetido não duplica |
| 24 | DOM-07 | Contrato pago, contato liberado; `/security-scan` | Telefone só após pagamento |
| 25 | DOM-08 | Cheguei, terminei com foto, histórico | Transição inválida recusada |
| 26 | DOM-08 | Aprovar e liberar pelo ledger | Saldos conferem |
| 27 | DOM-08 | Job das 12 h com ShedLock; repasse | Sem liberação dupla |
| 28 | DOM-09 | Abrir disputa, resposta, prazo | Uma disputa aberta por diária |
| 29 | DOM-09 | Decisão; liberação ou reembolso | Dois caminhos no ledger |
| 30 | DOM-10 | Avaliações e média | Só quem pagou avalia |
| 31 | DOM-11 | Admin: usuários, denúncias, configuração | Ações auditadas |
| 32 | DOM-11 | Financeiro do admin; notificações | Painel bate com `saldo_conta` |
| 33 | Revisão | Teste de ponta a ponta; folga | **M3** |

### Etapa 4: Front em React (dias 34–44)
| Dia | Tarefas | O que fazer | Pronto quando |
|---|---|---|---|
| 34 | FE-01 | Projeto Vite + React + TS, tokens, layout, componentes base | Início estático igual ao protótipo |
| 35 | FE-02, FE-03 | Camada de API; telas públicas e conta | Telas públicas com dados reais |
| 36 | FE-04 | Cadastro do profissional, etapas 1–6 | Rascunho salvo na API |
| 37 | FE-04 | Etapas 7–11 com upload | Cadastro chega à fila do admin |
| 38 | FE-05 | Contratar e pagar | Contrato pago pelo navegador |
| 39 | FE-05 | Diárias, aprovar, reclamar, avaliar, chat | Fluxo do cliente completo |
| 40 | FE-06 | Área do profissional | Fluxo do profissional completo |
| 41 | FE-07 | Painel admin | Admin opera tudo pela tela |
| 42 | FE-08 | Estados de carregando, vazio, erro, sem conexão | Nenhuma tela branca em erro |
| 43 | FE-09 | Vitest e Playwright | E2E verde |
| 44 | FE-10, revisão | Acessibilidade; folga | **M4** |

Recriar as telas em React dá mais trabalho que ligar o HTML pronto. Se a etapa 4 apertar, adiante o FE-01 e o FE-03 para logo depois do dia 10.

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
