# COE Serviços

Marketplace de serviços pagos por diária: **pedreiro, pintor, eletricista, jardineiro e diarista**.
Lançamento no Vale do Itajaí/SC, mas o sistema **não é preso a uma cidade** (cidades + raio de atendimento).
Perfis: **CLIENTE**, **PROFISSIONAL** e **ADMIN**. Um mesmo login pode ter mais de um papel.
O cliente paga as diárias antes, o valor fica em custódia e é liberado ao profissional dia a dia: na aprovação do cliente ou sozinho após 12 h sem resposta.
Público com pouca familiaridade com tecnologia: telas simples, poucos campos, botões grandes, linguagem simples.

## Documentação de apoio (leia antes de mexer no domínio)
- `docs/regras-negocio.md`: regras RN01–RN61, requisitos RF/RNF e pontos em aberto (PA01–PA16)
- `docs/plano-desenvolvimento.md`: etapas, tarefas (AMB-, DB-, CORE-, DOM-, FE-, LOC-, AWS-) e cronograma dia a dia
- `docs/identidade-visual.md`: identidade "Dia carimbado", componentes, acessibilidade, tom dos textos (ler antes de qualquer tarefa de front)
- `docs/mapa-banco.md`: domínios, tabelas,
- estados da diária e caminho do dinheiro
- Itens marcados **A DEFINIR** não estão decididos. **Pergunte antes de implementar**, nunca suponha.

## Stack
| Camada | Escolha |
|---|---|
| Backend | Java 21, Spring Boot 4, Maven (versões no `pom.xml`) |
| Banco | **PostgreSQL 16**, banco `coeservicos`, migrations **Flyway** |
| Front | **React + TypeScript + Vite**, na pasta `frontend/` |
| Testes back | JUnit 5, AssertJ, Mockito, MockMvc, Testcontainers (Postgres real) |
| Testes front | Vitest + Testing Library; Playwright para E2E |
| Local | Postgres local ou Docker (`docker compose`, porta 5433); Mailpit (e-mail/SMS falso); **SeaweedFS** como S3 local (porta 8333, versão fixa; a imagem pública do MinIO deixou de existir; plano B: S3Proxy em memória) |
| Pagamento | Gateway **A DEFINIR** (PA02). Até lá, adaptador falso atrás da interface `GatewayPagamento` |
| Deploy | AWS depois do aceite local (ECS Fargate, RDS, S3, Secrets Manager) |

## Comandos
```powershell
# serviços locais (lê o .env): Postgres 16 na porta 5433 e Mailpit
docker compose up -d
docker compose down -v                                        # recria o banco do Docker do zero

# backend (raiz)
./mvnw clean verify                                           # build + testes + Spotless + cobertura
./mvnw spotless:apply                                         # corrige a formatação (o verify reprova sem isso)
./mvnw test                                                   # só testes
./mvnw spring-boot:run "-Dspring-boot.run.profiles=local"     # rodar local na porta 8081 (aplica Flyway + dados locais)

# frontend (Node 24, ver frontend/.nvmrc)
cd frontend
npm install
npm run dev              # http://localhost:5173, proxy /api -> http://localhost:8081; SEM CSP (o HMR precisa de script inline)
npm run lint             # eslint (typescript-eslint, react-hooks, jsx-a11y) + stylelint (hex e nome de cor proibidos fora do tokens.css)
npm test                 # Vitest + Testing Library + axe
npm run test:cobertura   # cobertura v8: 80% geral, 100% no StatusDiaria
npm run build            # tsc --noEmit + vite build -> frontend/dist
npm run preview          # http://localhost:4173, build com a CSP e a Permissions-Policy de cabecalhos-seguranca.json
npm run test:e2e         # Playwright contra o preview, 360x740 e 1280x800 (1ª vez: npx playwright install chromium)
npm run prototipo        # protótipo docs/prototipo em http://localhost:4174
npm run comparar         # protótipo x React lado a lado em frontend/test-results/comparacao/
```

## Status atual
<!-- Atualizar a cada dia concluído do cronograma -->
- Feito: AMB-01 (pom), AMB-03 parcial (perfil `local`, `.env`/`.env.example`), DB-01 a DB-10 (V1–V10 com as correções CRITICAL: partidas dobradas por transação, sem TRUNCATE, papel `coe_app`), teste das migrações com Testcontainers Postgres 16 e JaCoCo com regra de 80%.
- Feito (DB-12): **V11** com as correções HIGH (webhook só ocupa o id com assinatura válida + corpo bruto; uma liberação ou um reembolso por diária; cidades versionadas), as lacunas frente às RN (RN27, RN20/RN56, RN13, RN08, RN17) e os demais apontamentos do database-reviewer (exclusão de conta por anonimização, provas sem cascata, índices em todas as FKs, configuração validada, versão no gatilho). Custódia por contrato e saldo nunca negativo ficaram para o DOM-07.
- Feito (etapa 0): AMB-02 (`docker-compose` com Postgres 16 e Mailpit; S3 local fica para o CORE-09), AMB-03 (perfis `local`, `test` e `prod`), AMB-04 (pacotes por domínio com `package-info`), AMB-05 (base `IntegracaoTest` + fumaça do health), AMB-06 (Spotless com palantir-java-format no `verify`), AMB-07 (README), AMB-08 (`TratadorDeErros`: Problem Details em pt-BR, `type` = `urn:coe:erro:<codigo>`; 401/403 ficam para o CORE-03/06).
- Cronograma em **fatias verticais** (`docs/plano-desenvolvimento.md`, seção 11): primeiro clique no dia 10, versão local completa no dia 46.
- Feito (dia 3): CORE-13 (`Clock` UTC + fuso de negócio, ArchUnit proibindo `now()` sem `Clock`), CORE-01 (`Dinheiro`, `Percentual`, `CalculadoraDiaria`, conversores JPA e JSON; 100% de cobertura no pacote), CORE-12 (`ConfiguracaoNegocio` com fail fast e cache de 60 s). Pendência para o DOM-09/DOM-11: chaves `FALTAS_ALERTA` e `FALTAS_JANELA_DIAS` (migração do DB-14, faltas do profissional).
- Feito (dia 4): CORE-02 (cadastro de cliente em `POST /api/contas/cliente`; `usuario` + papel `CLIENTE` + `aceite_termos` numa transação; 409 com código e campo, inclusive na corrida; primeira `SecurityFilterChain` stateless com 401/403 em Problem Details; senha `{bcrypt}` custo 12; campo desconhecido no JSON = 400). Pendências registradas no plano (seção do DOM-07/CORE-03): cidade pelo CEP, celular não confirmado até o CORE-04, enumeração e custo do BCrypt cobertos pelo rate limit do CORE-07.
- Feito (dia 5): DB-13 (V12: `refresh_token` só com hash e conteúdo imutável, `mfa_sms_ativo`, finalidade `mfa`, comissão até 30%) e CORE-03 (login com celular ou e-mail, JWT HS256 com kid de 15 min, refresh rotativo de 30 dias em cookie, reuso revoga a família, sair e sair de todos, MFA obrigatório para ADMIN com dispensa só no perfil local). Riscos aceitos no plano: access token vale até 15 min após o logout; força bruta até o CORE-07; Origin na renovação no CORE-08.
- Feito (dia 6): DB-15 (V13: `sessao_iniciada_em`, teto absoluto de 90 dias da sessão, motivo `teto`), CORE-06 (negar por padrão com `@PreAuthorize` ou `@Publico` em todo endpoint, ArchUnit e teste cruzando com `ROTAS_PUBLICAS`; `UsuarioAutenticado`; recurso de outro usuário = 404; `GET /api/contas/eu`) e CORE-08 (origens por perfil com fail fast, CORS com credenciais só em `/api/auth/**`, Origin obrigatório em todo POST de `/api/auth/` menos o entrar, Referrer-Policy/X-Frame-Options/nosniff/no-store, HSTS só no prod). Pendências no plano (seção do CORE-03/06/08).
- Feito (dia 7a): DB-16 (V14: `codigo_sms` com HMAC, dono, desafio, invalidação e um ativo por celular e finalidade; `token_verificacao` no lugar da `token_senha`; `email_verificado_em`; `ck_usuario_credenciais` = senha + celular ou e-mail; MFA exige celular confirmado) e CORE-04 (código SMS com HMAC e limites no banco, MFA com desafio, login só com código, confirmar celular, ligar/desligar MFA; sai a dispensa do MFA no local). Pendências no plano (seção do CORE-04).
- Feito (dia 7b): CORE-04 (envio assíncrono de SMS e e-mail depois do commit; contador de falhas; confirmar e-mail por link com Mailpit no local; RN61: prova de posse, transferência auditada, marca `contato_pendente` com lista de liberados por anotação, recolocar o contato perdido) e CORE-05 (esqueci, redefinir e trocar senha; link só para e-mail confirmado). Sem migração nova. Pendências e decisões no plano (seção do CORE-04).
- Feito (fix do dia 7, PR #13): **V15** (só o índice `ix_codigo_sms_usuario_criado (usuario_id, criado_em DESC)`; os de envio por celular e por endereço já existiam, conferidos com EXPLAIN), espera de 60 s por destino e finalidade (teto de 5/h somando), 409 neutro `contato-em-uso`. **CORE-04 e CORE-05 concluídas.** Fila de envio com prioridade e rate limit por IP ficam na CORE-07 (dia 11).
- Feito (dia 8): **FE-01** (React 19 + TS strict + Vite 8 + React Router 8 em `frontend/`; tokens e Archivo com eixo de largura hospedada no front; moldura com cabeçalho, menu de baixo e rodapé; componentes base; Início estático igual ao protótipo, sem números de regra; CSP e Permissions-Policy no Spring e no `vite preview`; E2E em 360 e 1280 px). Protótipo copiado em `docs/prototipo/` (ef7d397, só leitura). Decisões e pendências no plano (seção 7, FE-01).
- Feito (dia 9): **DOM-01** (`GET /api/publico/catalogo` e `GET /api/publico/regras`, públicos, cache de 60 s) e **FE-02** (`frontend/src/api/`: cliente único, token só na memória, uma renovação para várias 401, `voltar` seguro, erros em pt-BR, `useRecurso`). O Início lê profissões, cidades, prazo de liberação e comissão da API. Decisões e pendências no plano (seção 7, DOM-01 + FE-02).
- Feito (dia 10): **FE-03 (parte), marco M2**: sessão ao abrir o app, entrar (senha, segundo passo do MFA, só com código por SMS), criar conta de cliente com prova de posse (RN61, inclusive pela outra aba), Minha conta (sair, confirmar e-mail e celular, código no login, trocar senha, recolocar contato), confirmar e-mail, esqueci e redefinir senha; termos e privacidade em **rascunho**. Backend: `GET /api/contas/eu` com `emailConfirmado`, `contatoPendente`, `mfaAtivo`; `/api/publico/regras` com `versaoTermos`; a confirmação da posse do e-mail devolve `{comprovante, email}`. Decisões e pendências no plano (seção 7, FE-03).
- Feito (fechamento do FE-03, 14/10): teste manual dos 7 passos no navegador, todos aprovados (código do SMS no log, links no Mailpit). Correção achada nele: sem o aviso "Enviamos um link" quando o e-mail foi provado no cadastro (RN61) e Minha conta sem e-mail não pede para confirmar o e-mail. FE-03 mesclado na main (PR #18).
- Feito (dia 11, PR #19): **CORE-07** (V16: `limite_balde` UNLOGGED e `descartado_em`; rate limit com Bucket4j: por IP e globais no Postgres, teto geral e GETs públicos em memória, por login/celular/usuário; fail-closed 429/503; `Retry-After` em todo 429; corpo acima de 64 KB = 413; fila de envio prioritária e normal com cota devolvida no descarte; 409 `renovacao-em-andamento` com uma nova tentativa no front; prod não sobe com `DB_USER` acima do `coe_app`).
- Feito (dia 11): **CORE-11** (auditoria dos eventos sensíveis da conta na `log_auditoria`; logs sem dado pessoal: `Mascara` + `MascaraDeLog` no `%m` e nas mensagens das exceções, JSON ECS só no prod com o mesmo filtro, SMS falso em claro só no perfil local; id de correlação `requisicao` no MDC, no `X-Request-Id` e no Problem Details; `VarreduraDeLogsTest`). **CORE-07 e CORE-11 concluídas.**
- Feito (dia 12): **CORE-09** (SeaweedFS 4.48 como S3 local no compose e nos testes; `Armazenamento` com S3, URL assinada curta e chave `<categoria>/<uuid>`; `ArmazenamentoTransacional` apaga o arquivo no rollback; `ValidadorDeImagem` com tipo real, limites 413/415/422, metadados recusados antes de abrir, EXIF/sRGB/2560 px e regravação sem metadados; KMS obrigatória no prod). Revisões java e segurança sem CRITICAL, 3 HIGH corrigidos; pendências do DOM-02 e da AWS-04 no plano (seção 6, CORE-09). **Upload falso rejeitado.**
- Feito (dia 13, branch `feat/CORE-10-criptografia`): **CORE-10** (AES-256-GCM por campo com kid e rotação, AAD por campo, HMAC do CPF com chave própria, `Cpf` com dígitos verificadores, conversores JPA de CPF, chave Pix e endereço; colunas já eram `bytea`, sem migração). Ajuste do CORE-09 em `fix/CORE-09-qualidade-documento` (JPEG 0,92 para documento/selfie).
- Feito (dia 14, branch `feat/DOM-02-cadastro-profissional`, PR depois do da CORE-10): **DOM-02** backend (V17: `DIARIA_MINIMA`, `MEIA_DIARIA_MINIMA`, `SOBRE_MINIMO_CARACTERES`; V18: um documento por tipo). Rascunho só do dono, PUT por etapa, envio que confere tudo de novo e vai para `em_analise`; idade de 18 anos com `Clock`; CPF cifrado com HMAC único (409 neutro auditado, limite próprio contra enumeração); censura de contato em `compartilhado.censura` (100% de cobertura); documentos e fotos por multipart com URL de 2 min só do dono e auditoria de enviado/visto/removido. Revisões java, segurança e banco sem CRITICAL; HIGHs corrigidos. Decisões e pendências no plano (seção 6, DOM-02).
- Feito (dia 17, branch `feat/DOM-03-verificacao`, PR depois do do DOM-02): **DOM-03** backend (sem migração): fila do admin, detalhe com CPF completo auditado, URL de documento auditada, aprovar com checklist inteiro (RN13) e decisão do NR-10 (selo, RN14), pedir correção e recusar com motivo por SMS; papel PROFISSIONAL na aprovação. Revisões java e segurança sem CRITICAL/HIGH; MEDIUMs corrigidos. Decisões e pendências no plano (seção 6, DOM-03).
- Próximo: FE-04 (telas do cadastro), FE-07 (tela da verificação) e DB-11 (seeder local de profissionais).

## Estrutura do backend (por domínio, não por camada)
`usuario` (conta, login, SMS) · `catalogo` (cidades, profissões, serviços) · `profissional` (cadastro, verificação, portfólio, agenda) · `contrato` (contratos e diárias) · `pagamento` (gateway, webhooks, ledger, repasse, reembolso) · `mensagem` (chat + censura) · `avaliacao` · `disputa` · `moderacao` (denúncias) · `admin` · `config` · `compartilhado` (Dinheiro, erros, auditoria, armazenamento)

- Cada domínio tem controller, service, repository, entidades e DTOs.
- Erros: lance `RegraDeNegocioException` (422, com código da regra), `RecursoNaoEncontradoException` (404, também para recurso de outro usuário) ou `ConflitoException` (409), de `compartilhado.erro`. O `TratadorDeErros` converte em Problem Details; nunca monte resposta de erro no controller.
- Testes de integração estendem `IntegracaoTest` (contexto, Testcontainers, MockMvc e o relógio `RelogioAjustavel` compartilhados).
- **Varredura dos logs (CORE-11)**: o `VarreduraDeLogsTest` roda os fluxos de conta (cadastro, entrar, código por SMS, segundo passo, posse, esqueci, redefinir, trocar senha, recolocar contato) e falha se o log tiver celular, e-mail, senha, código, link, comprovante ou token usados. **Fluxo novo que mexe com dado pessoal ou segredo entra nessa varredura** (guarde cada segredo com `guardar(...)` e cada código com `codigo(...)`).
- **O banco não é limpo entre os testes** (só os baldes do rate limit e as filas falsas de SMS/e-mail zeram antes de cada um): cada teste cria os próprios dados, com celulares fictícios (`479000000NN`) e e-mails **únicos dele** (contador ou `ContasDeTeste`), e **nunca** depende de tabela vazia, de "a primeira linha" sem filtro ou de contagem global. Consulte sempre filtrando pelos dados do próprio teste. (Causa das falhas da CORE-07: um teste reaproveitava o celular de outro e herdava a espera de 60 s e linhas já invalidadas.)

### Como usar Dinheiro, ConfiguracaoNegocio e Clock
- **Dinheiro** (`compartilhado.dinheiro`): todo valor em reais é `Dinheiro`, nunca `BigDecimal` solto nem `double`. `Dinheiro.de("280.00")`; `somar`, `subtrair` (negativo lança `RegraDeNegocioException`), `multiplicar(Percentual)` (HALF_EVEN), `formatar()` → `R$ 1.234,56`. Entrada com mais de 2 casas é recusada. Percentuais são `Percentual.de("0.10")`. No JSON os dois são texto (`"280.00"`); nas entidades, os conversores JPA já se aplicam sozinhos.
- **Valores de uma diária**: sempre `CalculadoraDiaria.calcular(valor, comissao, taxaPagaPor)`, que devolve comissão, total do cliente, repasse e reembolso (= o que o cliente pagou). Totais do contrato = `TotaisContrato.somar(diarias)`; nunca calcule a comissão sobre o total.
- **ConfiguracaoNegocio** (`config`): injete e leia os getters tipados (`comissao()`, `autoLiberacao()`, `limiteDiaristaJanela7Dias()`...). Num cálculo que usa mais de um parâmetro (ex.: comissão e quem paga), pegue `parametros()` uma vez e use o mesmo snapshot. Se a releitura falhar depois da subida, ela mantém os últimos valores válidos. Nunca fixe esses valores no código. Chave nova = enum `ChaveConfiguracao` + getter + migração com o valor. Depois de o admin mudar um parâmetro, chame `invalidarCache()`.
- **Clock**: injete `java.time.Clock` em todo serviço com data ou prazo; `Instant.now()`, `LocalDate.now()` e afins sem `Clock` quebram o build (ArchUnit). Datas de negócio (diária, LC 150, idade, "hoje") usam `FusoDeNegocio.hoje(clock)` / `FusoDeNegocio.ZONA` (São Paulo). Nos testes de integração, use `relogio.avancar(...)` ou `relogio.fixarEm(...)`.

### Como usar o cadastro e a segurança
- **Cadastro de cliente**: `POST /api/contas/cliente` (público) com `{"nome","celular","email","cep","senha","versaoTermosAceita"}`. Responde 201 com `{id, nome}` e **não faz login**. Erros: 400 com `campos` (validação ou campo desconhecido), 409 neutro `urn:coe:erro:contato-em-uso` com `campo` (o mesmo corpo para contato confirmado ou não; o front sempre oferece provar a posse), 422 `termos-desatualizados` quando a versão difere de `ConfiguracaoNegocio.versaoTermos()`.
- **Normalização** (`usuario.Contato`): celular só com dígitos (o `55` do país só sai com 13 dígitos; 55 também é DDD), 11 dígitos com o 3º = 9; e-mail com trim e minúsculas; CEP só dígitos; nome com trim. Use os mesmos métodos em qualquer outro cadastro (profissional no DOM-02).
- **Senha**: injete `PasswordEncoder` (bean `codificadorDeSenha` em `SegurancaConfig`), nunca crie um `BCryptPasswordEncoder` solto. Gera `{bcrypt}` custo 12 e confere também hash sem prefixo (seed local). Regras: 8 a 72 bytes, diferente do celular e do e-mail, fora da lista de óbvias (`@SenhaPermitida`).
- **Segurança**: tudo em `/api/**` exige login, exceto o que estiver liberado em `SegurancaConfig` (hoje só o cadastro e o health). Endpoint público novo = liberar ali **e** testar o 401 do resto. Sem sessão (stateless) e CSRF desligado até o CORE-08.
- **Conflito**: `new ConflitoException(codigo, mensagem, campo)` vira 409 com `type` `urn:coe:erro:<codigo>` e `campo`.
- **JSON**: campo desconhecido = 400 em toda a API. Exceção futura: o webhook do gateway (DOM-07) lê o corpo bruto de forma tolerante.
- **Testes**: celulares só fictícios (`479000000NN`), nunca número real.

### Como autenticar
- **Front**: `POST /api/auth/entrar` com `{"login","senha"}` (login = celular com ou sem máscara/+55, ou e-mail). Resposta 200: `{accessToken, expiraEm, usuario:{id,nome,papeis}}` + cookie `coe_refresh` (HttpOnly, só `/api/auth`). Guarde o `accessToken` **só na memória** e mande `Authorization: Bearer <token>`. Ao receber 401, chame `POST /api/auth/renovar` (o navegador manda o cookie); se a renovação der 401, vá para a tela de entrar. Se a renovação der **409 `renovacao-em-andamento`**, outra aba acabou de renovar com o mesmo cookie (até 30 s antes) e o cookie novo já está no navegador: espere ~300 ms e tente **uma vez** (o `cliente.ts` já faz isso); 409 de novo = sem sessão naquela aba, sem mensagem de erro. O 409 não apaga o cookie nem revoga a sessão. Sair: `POST /api/auth/sair` (este aparelho) ou `/api/auth/sair-de-todos`.
- **Erros do login**: 401 `login-invalido` (sempre a mesma mensagem), 403 `conta-suspensa`, 403 `celular-nao-confirmado` (MFA exigido sem celular confirmado), 429 `muitas-tentativas`, 403 `segundo-passo-necessario` (MFA ligado ou ADMIN) com `desafioId` no corpo.
- **Segundo passo (MFA)**: `POST /api/auth/segundo-passo` com `{"desafioId","codigo"}` (o código chega por SMS); responde como o `entrar`. Desafio vale 5 min, uma vez, 5 tentativas; qualquer erro = 401 `login-invalido`.
- **Login só com código**: `POST /api/auth/codigo` com `{"celular"}` (sempre 202, mesma mensagem) e `POST /api/auth/entrar-com-codigo` com `{"celular","codigo"}`. Só celular confirmado; nunca ADMIN nem quem ligou o MFA.
- **Minha conta**: `POST /api/contas/eu/celular/codigo` (202; 409 `celular-ja-confirmado`; 429) e `/eu/celular/confirmar` `{"codigo"}` (204; 422 `codigo-invalido`); `POST /api/contas/eu/mfa/codigo` e `/eu/mfa` `{"ativo","codigo"}` (204; 403 `celular-nao-confirmado`; ADMIN não desliga: 403 `mfa-obrigatorio`). Conta suspensa com token ainda válido: 403 `conta-suspensa`.
- **E-mail**: `POST /api/contas/eu/email/confirmacao` (202; 409 `email-ja-confirmado`; 429) manda o link `coe.front.url-base` + `/confirmar-email#token=...` (token no **fragmento**; o front lê, limpa com `history.replaceState` e manda no corpo); `POST /api/contas/email/confirmar` `{"token"}` (público, sem Origin: não usa cookie) = 204 ou 422 `link-invalido`. O cadastro já manda esse link sozinho. Limites por endereço: espera de 60 s **por finalidade** (confirmar, posse e senha não se bloqueiam entre si) e teto de 5 por hora **somando as finalidades**; quem passa do limite recebe a mesma resposta, sem envio.
- **RN61 (contato não confirmado não fica reservado)**: cadastro com contato de outra conta = 409 neutro `contato-em-uso` (igual para confirmado e não confirmado, sem campo que diferencie; mensagem "Esse contato já está em uso. Se ele for seu, confirme que é seu para usar nesta conta."); o PUT de contato e as confirmações usam o mesmo 409; prova de posse pública em `/api/contas/posse/{celular,email}` e `.../confirmar` devolve `{"comprovante"}` (30 min); o cadastro aceita `comprovanteCelular` / `comprovanteEmail`. A conta antiga fica com `contatoPendente: true` (no corpo do login e no claim `contato_pendente`, recalculado do banco) e só usa endpoints com `@LiberadoComContatoPendente` (o interceptor também consulta o banco: token antigo sem a marca não escapa); recoloca o dado com `PUT /api/contas/eu/{celular,email}` (só com o campo vazio; manda código/link, a confirmação grava). Conta ADMIN, ou que ficaria sem contato, nunca perde dado (409 `transferencia-indisponivel`). Endpoint novo liberado com contato pendente = anotação **e** a lista do `LiberadosComContatoPendenteTest`.
- **Senha**: `POST /api/auth/senha/esqueci` `{"login"}` (202 sempre igual; link `/redefinir-senha#token=` só para e-mail **confirmado**, senão SMS no celular confirmado, senão nada); `POST /api/auth/senha/redefinir` `{"token","novaSenha"}` ou `{"celular","codigo","novaSenha"}` (204, revoga todas as sessões, não entra; 422 `link-invalido` / `senha-nao-permitida`); `POST /api/auth/senha/trocar` `{"senhaAtual","novaSenha"}` (logado; 422 `senha-atual-incorreta`; mantém a sessão do cookie e revoga as outras). Regra da senha reaproveitável: `SenhaPermitidaValidador.motivoDeRecusa(senha, celular, email)`.
- **Envio de SMS e e-mail**: nunca chame o provedor direto; publique `MensagemPronta.sms(...)` / `MensagemPronta.email(...)` dentro da transação (o `DespachoDeMensagens` envia depois do commit, numa fila limitada). Nos testes, `sms.ultimoCodigo(celular)` e `email.ultimoToken(endereco)` esperam a fila.
- **Código SMS no código**: `ServicoDeCodigoSms` (`enviar`, `emitirDesafio`, `conferir`, `conferirDesafio`; devolve resultado, não lança, para gravar a tentativa errada). Limites por celular: espera de 60 s **por finalidade** e teto de 5 por hora **somando as finalidades** (decisão de 07/10; índices conferidos com EXPLAIN no `MigracaoV15Test`). Nos testes, leia o código com `sms.ultimoCodigo(celular)` (`IntegracaoTest`).
- **Testes de integração**: use `bearer(usuarioId, Papel.X)` da `IntegracaoTest` no header `Authorization`, sem passar pelo login. Contas de teste com senha: `ContasDeTeste` (pacote `usuario`).
- **Local**: o `.env` precisa de `COE_JWT_KID_ATUAL`, `COE_JWT_CHAVES` e `COE_CHAVE_CODIGOS` (ver `.env.example`); sem eles a aplicação não sobe. No perfil local o SMS é falso: o texto com o código sai no log (`SMS FALSO (perfil local) para 47*****0001: ...`); o ADMIN do seed entra com senha + esse código. Em prod, sem provedor de SMS (A DEFINIR) a aplicação não sobe, e o SMS falso não sobe com o perfil prod. E-mail no local: `docker compose up -d mailpit` (SMTP 1025, painel http://localhost:8025); em prod, `SPRING_MAIL_HOST` (+ porta, usuário e senha), `COE_EMAIL_REMETENTE` e `COE_FRONT_URL_BASE` (https) são obrigatórios.
- **Origin**: todo POST em `/api/auth/` (menos o `entrar`) exige o header `Origin` de uma origem permitida (`coe.seguranca.origens-permitidas`; local e test: `http://localhost:5173`). O navegador manda sozinho; no curl, mande `-H "Origin: http://localhost:5173"`.

### Catálogo e regras públicas (sem login)
- `GET /api/publico/catalogo`: `{areas:[{codigo, nome, profissoes:[{codigo, nome, nomePlural, icone, servicos:[{nome}]}]}], cidades:[{codigoIbge, nome, uf}]}`, só o que está ativo, na ordem do catálogo; cidades por UF e nome sem acento. O identificador público é o `codigo` da profissão e o `codigoIbge` da cidade (nunca o id interno). Faixa de diária, NR-10 e LC 150 não saem.
- `GET /api/publico/regras`: `{comissao: "0.1000", prazoLiberacao: "PT12H", taxaPagaPor: "CLIENTE" | "PROFISSIONAL"}`, lidos de um snapshot do `ConfiguracaoNegocio`. Nenhum outro parâmetro sai.
- Os dois guardam 60 s em memória (`CatalogoPublico.invalidarCache()` / `ConfiguracaoNegocio.invalidarCache()`) e respondem com `no-store`. Mudar a configuração = INSERT numa nova vigência (a tabela `configuracao` é só de inserção).

### Como proteger um endpoint (negar por padrão e anti-IDOR)
- **Todo método de `@RestController` tem `@PreAuthorize(...)` ou `@Publico`** (o build quebra sem um dos dois, ArchUnit). Endpoint público novo = `@Publico` **e** a rota em `SegurancaConfig.ROTAS_PUBLICAS` (o `EndpointsPublicosTest` confere que as duas listas são iguais). Papéis: `hasRole('CLIENTE')`, `hasRole('PROFISSIONAL')`, `hasRole('ADMIN')`; qualquer logado: `isAuthenticated()`. `/api/admin/**` também é ADMIN na URL.
- **O dono vem só do token**: injete `UsuarioAutenticado` e use `usuario.id()`. Nunca use id do corpo ou da URL para decidir de quem é o recurso.
- **A consulta já filtra pelo dono** e recurso de outro usuário é **404** (`RecursoNaoEncontradoException`), igual a inexistente, para não revelar que existe:
  ```java
  // repositório
  Optional<Contrato> findByIdAndClienteId(UUID id, UUID clienteId);
  // serviço
  Contrato contrato = contratos.findByIdAndClienteId(id, usuarioAutenticado.id())
          .orElseThrow(() -> new RecursoNaoEncontradoException("Contrato não encontrado."));
  // controller
  @GetMapping("/{id}") @PreAuthorize("hasRole('CLIENTE')")
  ContratoResponse buscar(@PathVariable UUID id) { ... }
  ```
- **Teste obrigatório** de todo recurso com dono: o dono recebe 200; outro usuário recebe 404 com o mesmo corpo de um id inexistente; papel errado recebe 403; sem token, 401 (modelo: `AntiIdorTest`).
- **Nunca expor entidade JPA na API.**
- API sob `/api/**`; admin sob `/api/admin/**`.

### Como limitar um endpoint (CORE-07)
- **Negar por padrão**: toda rota que muda algo em `/api` (POST, PUT, PATCH, DELETE) tem limite, senão o `CoberturaDosLimitesTest` quebra o build. Três jeitos:
  - **por IP ou global** (antes do Security, sem ler o corpo): uma linha em `config/RotasLimitadas` (`Cobranca.porIp("regra")`, `Cobranca.global("regra")`, IP antes do global) e a regra em `coe.limites.regras` (`capacidade`, `janela`, `fatias` só nos globais);
  - **por usuário logado**: `@LimitadoPorUsuario("regra")` no método do controller (a chave é o id do token);
  - **por login, celular ou outro dado do corpo**: no serviço, `limitador.consumir(Balde.porChave("regra", chaveDeLimite.doLogin(...)))`; dado pessoal entra **sempre** como HMAC (`ChaveDeLimite`), nunca em claro na `limite_balde`.
- Exceção sem limite próprio só com motivo no `CoberturaDosLimitesTest.EXCECOES` (o teto geral de `/api/**` por IP vale para todas).
- Números no `application.yml` (`coe.limites.*`), técnicos, por perfil; nunca na tabela `configuracao`. Serão revistos com dados reais depois do lançamento.
- Respostas: 429 `muitas-tentativas` sempre com `Retry-After` (lance `MuitasTentativasException(mensagem, espera)`); banco do limitador fora = 503 `servico-instavel` (fail-closed: nunca deixa passar sem contar).
- Testes: o banco não é limpo entre testes, mas a `IntegracaoTest` zera os baldes antes de cada um; use IPs de teste diferentes (`requisicao.setRemoteAddr(...)`) para isolar.

### Como auditar (CORE-11)
- Injete `RegistroDeAuditoria` e chame `registrar(ator, papel, "dominio.acao", entidade, id, antes, depois, ip)` **dentro da transação da ação** (desfez a ação, some a linha). Ação da aplicação sozinha: `registrarDoSistema(...)` (papel `SISTEMA`). `ip` nulo = o da requisição atual, gravado agrupado (IPv6 por /64). Papel do usuário: `usuario.papelPrincipal()`.
- Em `antes`/`depois` **só dado não pessoal** (ex.: `{"canal":"email"}`, `{"ativo":true}`): nunca celular, e-mail, CPF, Pix, endereço, senha, código ou token, nem mascarados. O IP é dado pessoal com prazo de guarda a definir (plano).
- Já auditados: `senha.trocar`, `senha.redefinir`, `mfa.ligar`, `mfa.desligar`, `sessao.sair-de-todos`, `sessao.reuso-refresh`, `contato.recolocar`, `contato.transferir`, `conta.bloqueio-codigos`, `limite.teto-login`. Toda ação de admin e todo movimento de dinheiro também serão (DOM-07, FE-07).
- Teste: a linha existe, com ator, papel e IP, e não tem o dado pessoal usado (modelo: `AuditoriaDeEventosTest`).

### Como logar sem dado pessoal (CORE-11)
- Monte a mensagem com ids e com o dado já mascarado: `Mascara.celular`, `email`, `cpf`, `cep`, `token`, `codigo` (`compartilhado.log`). **Controller não loga** (ArchUnit): o corpo da requisição nunca vai para o log.
- O `logback-spring.xml` aplica a `MascaraDeLog` no `%m` e nas mensagens das exceções (as linhas "at" ficam intactas). É rede de segurança, não licença para logar dado pessoal. No prod o log é JSON (ECS) com o mesmo filtro (`MascaraNoJsonDoLog`); local e test em texto.
- SMS falso: o código sai em claro **só no perfil local** (logger `coe.sms-falso`, appender próprio).
- **Id de correlação**: toda requisição ganha um id novo (`FiltroDeCorrelacao`), no MDC (`requisicao`, aparece como `[id]` no log em texto), no header `X-Request-Id` e no Problem Details (campo `requisicao`). O `X-Request-Id` de fora é ignorado. A fila de envio leva o id para a thread que envia. Em teste que compara corpos de erro que devem ser iguais, use `semRequisicao(corpo)` (IntegracaoTest).
- Fluxo novo que mexe com dado pessoal entra no `VarreduraDeLogsTest`.

### Como guardar dado sensível (CORE-10)
- **CPF**: sempre `Cpf.de(texto)` (`compartilhado.pessoal`; dígitos verificadores, 422 `cpf-invalido`; `toString` mascarado). Na entidade: `@Convert(converter = CpfCifradoConverter.class)` na coluna `cpf_cifrado` **e** `hmacDeCpf.de(cpf)` na `cpf_hash` (única). Unicidade e busca só pelo HMAC (`WHERE cpf_hash = ?`); nunca decifrar para comparar.
- **Chave Pix e endereço**: `@Convert(converter = ChavePixCifradaConverter.class)` / `EnderecoCifradoConverter` nas colunas `bytea` (`chave_pix_cifrada`, `endereco_cifrado`). Fora da entidade: `cifraDeCampo.cifrar(texto, Campo.X)` / `decifrar(bytes, Campo.X)`; para amarrar o dado à linha (ex.: chave Pix do repasse), a versão com contexto (`cifrar(texto, Campo.X, "repasse:" + id)`), que não decifra em outra linha.
- **Formato**: AES-256-GCM, `[versão][tamanho do kid][kid][nonce 12][cifra + tag]`, nonce aleatório a cada cifra, AAD = cabeçalho + campo (o CPF cifrado não decifra como endereço). Dado adulterado, de outro campo ou com chave errada = `DadoCifradoInvalidoException` (500, mensagem sem o valor).
- **Chaves**: `COE_CIFRA_KID_ATUAL` + `COE_CIFRA_CHAVES` (`kid:base64` de 32 bytes, separadas por vírgula) e `COE_CHAVE_HMAC_CPF` (outra chave; igual a uma de cifra não sobe). Obrigatórias em todo perfil. **Rotação**: chave nova com kid novo vira a atual, a anterior fica na lista (decifra com qualquer kid conhecido, cifra sempre com o atual) até tudo ser cifrado de novo. Trocar a chave do HMAC exige recalcular os hashes.
- **Nunca** em log, auditoria, Problem Details ou `toString`: CPF, chave Pix, endereço, data de nascimento. Campo novo cifrado = valor novo em `Campo` (o nome nunca muda: o dado gravado depende dele) + conversor.
- **Testes**: CPFs válidos e únicos com `CpfsDeTeste.unico()` (o HMAC é único e o banco não é limpo entre testes); a coluna se confere por JDBC (`CifraNoBancoTest`).
- **Data de nascimento** fica em `date` (o banco confere a faixa; a idade de 18 anos é validada com `Clock`); retida 5 anos depois da exclusão como o CPF.

### Como guardar um arquivo (CORE-09)
- **Fluxo no serviço do domínio**, dentro de `@Transactional`: `ArquivoValidado arquivo = validador.validar(bytes, Categoria.X)` (`ValidadorDeImagem`) e depois `String chave = armazenamento.guardar(arquivo, Categoria.X)` (`Armazenamento`); grave a `chave` na coluna `chave_arquivo` na mesma transação. Para mostrar, `armazenamento.urlAssinada(chave, validade)` (até `coe.armazenamento.validade-url`, 5 min; teto 15 min). Nunca guarde bytes crus nem o nome original.
- **Categorias** (prefixo da chave e limite): `DOCUMENTO`, `SELFIE`, `PORTFOLIO`, `PEDIDO`, `EVIDENCIA`, `DISPUTA`. Chave = `<categoria>/<uuid>.<jpg|png>`; chave fora desse padrão é recusada em `urlAssinada` e `apagar`.
- **Validação** (RNF07): JPEG, PNG e WebP pelo conteúdo (WebP vira JPEG, fundo branco); HEIC e PDF recusados. Erros: 415 `arquivo-tipo-invalido` (não é imagem, ou não abre: corrompida, cortada, metadado grande demais), 413 `arquivo-grande-demais` (8 MB por categoria), 422 `imagem-grande-demais` (40 MP pelo cabeçalho), 503 sem vaga de processamento; nas rotas de upload, multipart sem `Content-Length` = 411 e malformado = 400. A imagem é sempre gravada de novo: orientação do EXIF aplicada, sRGB, lado até 2560 px, sem metadados. Miniaturas (RNF24) ficam no domínio (DOM-04).
- **Transação**: `guardar` fora de transação lança `IllegalStateException`; transação desfeita apaga o arquivo numa thread própria (`apagar-orfao`). `apagar` dentro de transação só apaga depois do commit (fora dela, na hora). O PUT segura a conexão do banco: guarde o arquivo como último passo antes do commit. Só o `ArmazenamentoS3` usa o SDK da AWS (ArchUnit).
- **Endpoint de upload** (DOM-02/04/08): multipart; a rota entra em `config/RotasDeUpload` (só nelas o corpo passa dos 64 KB, até `spring.servlet.multipart.max-request-size`), com `@LimitadoPorUsuario`, `@PreAuthorize` e teste anti-IDOR. A primeira tela que mostrar foto acrescenta o host do bucket ao `img-src` da CSP (`cabecalhos-seguranca.json` e `PoliticasDoNavegadorTest` juntos).
- **Local e testes**: SeaweedFS (`docker compose up -d seaweedfs`, porta 8333, chaves `S3_ACCESS_KEY`/`S3_SECRET_KEY` do `.env`); bucket privado criado na subida. Nos testes de integração o SeaweedFS do Testcontainers é a mesma imagem do compose, e o bucket não é limpo: cada teste grava as próprias chaves. Em prod (perfil `prod` ou origens só https): `COE_S3_BUCKET`, `COE_S3_REGIAO`, `COE_S3_KMS_KEY_ID` (obrigatória) e o papel IAM; endpoint ou chaves estáticas impedem a subida.
- **Log**: a chave sai como "categoria X, arquivo uuid.ext" (`ArmazenamentoS3.paraLog`), nunca o conteúdo nem o nome original. A auditoria fica com a ação do domínio ("documento enviado").

### Cadastro do profissional (DOM-02)
- **API** em `/api/profissional/cadastro` (qualquer logado; o papel PROFISSIONAL só vem na aprovação do DOM-03): `GET` (404 `nao-encontrado` antes da primeira etapa) devolve o rascunho com `etapaAtual`, `etapas` (etapa → completa), CPF e Pix **mascarados**, documentos sem chave de arquivo; `PUT /trabalho`, `/dados`, `/servicos`, `/experiencia`, `/valor`, `/onde`, `/dias`, `/sobre`, `/pix` (cada um valida só o seu pedaço e devolve o rascunho); `POST /envio` `{"versaoTermosAceita","aceitoRegraDeContato"}` confere tudo de novo (etapas, celular confirmado, idade, valores mínimos de hoje, censura do "Sobre" juntado) e muda para `em_analise`; `POST /documentos` (multipart `tipo` + `arquivo`; o mesmo tipo substitui) e `POST /fotos` (`arquivo`, `servico`, `legenda`; até 12); `GET .../{id}/url` (URL assinada: documento 2 min, foto a padrão), `DELETE .../{id}`, `PUT /fotos/{id}`.
- **Dono e trava**: `rascunhoEditavel()` cria o rascunho com `INSERT ... ON CONFLICT DO NOTHING` e o lê com `SELECT ... FOR UPDATE` (espera até 5 s; passou = 409 `edicao-concorrente`): toda escrita do mesmo cadastro passa uma de cada vez. Em análise (ou aprovado) nada muda: 422 `cadastro-em-analise`. Precisa de transação aberta.
- **Erros**: 422 `idade-minima` ("Para trabalhar na COE é preciso ter 18 anos ou mais."), `data-nascimento-invalida`, `cpf-invalido`, `pix-invalido`, `valor-abaixo-do-minimo`, `meia-diaria-invalida`, `sobre-curto`, `texto-com-contato`, `profissao-invalida`, `servico-invalido`, `cidade-invalida`, `experiencia-invalida`, `documento-tipo-invalido` (NR-10 sem ser eletricista), `limite-de-fotos`, `cadastro-incompleto`, `celular-nao-confirmado`, `termos-desatualizados`; 409 `cpf-em-uso` neutro (auditado como `profissional.cpf-repetido`, sem o CPF).
- **Limites**: `cadastro-usuario` nas etapas; `dados-usuario` (10/24 h) + `dados-ip` no `PUT /dados` (enumeração de CPF pelo 409); `upload-usuario` + `upload-ip` nos uploads.
- **Auditoria** (só dado não pessoal): `profissional.dados-alterados`, `pix-alterado` (`{"tipo"}`), `documento-enviado`/`documento-visto`/`documento-removido` (`{"tipo"}`), `cpf-repetido`, `enviado-analise`.
- **Censura de contato** (`compartilhado.censura.CensuraDeContato`): `exigirSemContato(texto, campo)` em todo texto livre antes do pagamento (bio, frases, horário, legenda; o chat do DOM-05 reaproveita). Normaliza (NFKC, dígitos Unicode, invisíveis, acentos); bloqueia telefone com qualquer separador, números ditados (por extenso, também misturados com dígitos), e-mail, links, @perfil, redes e pedidos ("me liga"); deixa passar CEP, R$, medidas, horas e datas curtas. Caso novo = teste no `CensuraDeContatoTest` (o pacote exige 100%).

### Verificação do cadastro (DOM-03)
- **API** em `/api/admin/verificacao` (só ADMIN): `GET ?pagina=` fila em análise, mais antigo primeiro (`{itens:[{id,nome,profissao,enviadoEm,nr10}], temMais}`, 50 por página); `GET /{id}` detalhe (`cpf` completo para conferir com o documento, `celularConfirmado`, `nr10`, `enviadoEm`, `cadastro` igual ao do profissional; auditado `profissional.verificacao-vista`); `GET /{id}/documentos/{doc}/url` (2 min, auditado `profissional.documento-visto-admin`) e `/{id}/fotos/{foto}/url`; `POST /{id}/aprovar` `{itens:{cpf,documento,selfie,fotos,texto}, nr10?: "verificado"|"recusado", motivoNr10?}`; `POST /{id}/correcao` e `/{id}/recusar` `{motivo}` (até 160, uma linha, sem contato: vai por SMS).
- **Regras**: decide só cadastro `em_analise` (senão 409 `verificacao-indisponivel`), nunca o próprio (403 `verificacao-propria`); trava a linha do profissional antes de tudo (5 s). Aprovar: 422 `checklist-incompleto`, `nr10-sem-decisao`, `nr10-sem-certificado`, `nr10-sem-motivo`; vira `ativo`, documentos pendentes `aprovado` (certificado `recusado` se o NR-10 foi recusado), papel PROFISSIONAL (vale no próximo token). SMS só para celular confirmado. Auditoria `profissional.aprovar`, `pedir-correcao`, `recusar` (sem o motivo). Limites `admin-usuario` e `admin-detalhe-usuario`.

## Regras de negócio já decididas (resumo; a fonte é `docs/regras-negocio.md`)
- Parâmetros vêm da tabela `configuracao` (view `configuracao_vigente`). **Nunca fixar no código**: comissão 10% (teto de 30% no banco), liberação em 12 h, disputa decidida em 48 h, 3 tentativas de contato antes de análise, limite LC 150 = 2.
- Comissão e "quem paga a taxa" são **copiados para o contrato** na compra. Mudar a configuração não altera contrato fechado. A comissão é **paga pelo cliente**, somada ao total (PA05).
- **Liberação automática**: 12 h contadas a partir do "Terminei o dia" (`terminou_em`) (PA08).
- **Idade mínima** do profissional: 18 anos, obrigatório, validado com `Clock` injetado. Mensagem: "Para trabalhar na COE é preciso ter 18 anos ou mais."
- **Falta do profissional** (PA07): reembolso integral da diária da falta; o cliente escolhe manter ou cancelar as outras (canceladas futuras = reembolso integral); falta registrada em `ocorrencia_profissional` (só inserção); alerta com 2 faltas em 90 dias; inativação **manual e reversível** (status `suspenso` com motivo, auditado); o mesmo CPF não cria outro cadastro.
- **Retenção LGPD**: CPF, Pix e data de nascimento de quem excluiu a conta ficam 5 anos; depois, expurgo (tarefa futura).
- **Reembolso** devolve exatamente o que o cliente pagou por aquela diária: diária + comissão se a taxa é do cliente; só a diária se a taxa é do profissional. Comissão só vira receita quando a diária é liberada.
- **LC 150**: mesma diarista + mesmo cliente = no máximo 2 diárias em **qualquer janela de 7 dias seguidos** (não semana fixa; confirmado com o advogado). Meia diária conta como um dia.
- **Uma diária por profissional por dia**, inteira ou meia. Duas meias no mesmo dia só na fase 2.
- Contato (telefone, endereço completo) só aparece **depois do pagamento confirmado**. Antes disso, censura no servidor em chat, bio, pedido e legendas. CEP (`00000-000` ou "CEP" + 8 dígitos) passa; telefone não (PA13).
- Reclamação trava **só aquela diária**; a equipe decide liberar ou reembolsar.

## Regras técnicas inegociáveis

### Dinheiro
- **BigDecimal** no Java (escala 2, HALF_EVEN) e **NUMERIC(12,2)** no banco; percentuais em NUMERIC(5,4). Nunca double ou float.
- **Ledger de partidas dobradas** (`transacao_financeira` + `lancamento`): débitos = créditos em cada transação (checado no COMMIT). Lançamento nunca é alterado nem apagado; correção = nova transação. Saldo = view `saldo_conta`.
- Liberar, reembolsar e mudar status acontecem **em uma transação**, com trava de concorrência. No máximo uma liberação **ou** um reembolso por diária, garantido também no banco.
- Webhooks: assinatura validada, **idempotentes** (só eventos com assinatura válida ocupam o id) e com o **corpo bruto** guardado.
- Liberação automática por job agendado, idempotente, com trava para várias instâncias (ShedLock) e log.
- O dinheiro em custódia fica **no gateway** (subconta/split), nunca em conta própria da COE. O ledger espelha esse dinheiro.

### Máquina de estados da diária
- Estados: `agendada → paga → andamento → aguardando → liberada`; `paga | andamento | aguardando → contestada → liberada | reembolsada`; `agendada → cancelada` (pedido não pago).
- Transições validadas **só no backend**. Transição fora dessa lista = erro. Toda troca grava `historico_diaria`.

### Banco e Flyway
- Todo schema por migration Flyway em `src/main/resources/db/migration`. `ddl-auto=validate`; `update`/`create` proibidos.
- **Nunca editar uma migration que já foi commitada ou aplicada fora da sua máquina.** Mudança = nova `V<n>__descricao.sql`.
- **Numeração das migrations na ordem em que são criadas; nunca reservar número.** Migração futura é citada só pela tarefa (ex.: "migração do DB-14, faltas do profissional"), em docs, plano e CLAUDE.md; o número `V<n>` só aparece quando o arquivo existir. A próxima é sempre a maior versão existente + 1.
- **Dois usuários no banco:** o Flyway roda com o usuário **dono** do schema; a aplicação conecta com um login membro do papel **`coe_app`** (criado na V1), que não é dono: não altera tabelas, não desliga triggers e não faz UPDATE/DELETE/TRUNCATE em `configuracao`, `historico_diaria`, `transacao_financeira`, `lancamento` e `log_auditoria`. Em produção: `SPRING_FLYWAY_USER`/`SPRING_FLYWAY_PASSWORD` para o dono e `DB_USER`/`DB_PASSWORD` para o login da aplicação. Toda tabela nova recebe os privilégios de `coe_app` pelos default privileges; tabela só de inserção precisa de `REVOKE UPDATE, DELETE, TRUNCATE` e dos gatilhos `fn_somente_insercao` (linha e TRUNCATE).
- `db/local/R__dados_local.sql` roda **só no perfil local** (`spring.flyway.locations` do `application-local.yml`). Nada de dado de teste nas migrations versionadas.
- Constraints do banco são a última barreira, não a única: a regra também é validada no serviço, com teste.
- Nunca rodar migration em produção sem pedir.

### Segurança (prioridade máxima)
- Spring Security com papéis CLIENTE, PROFISSIONAL e ADMIN.
- **Autorização por objeto em toda consulta** (anti-IDOR): cada usuário só acessa os próprios contratos, diárias, conversas e documentos. Recurso de outro usuário retorna 403 ou 404.
- Senhas com BCrypt ou Argon2. Autenticação: **JWT** (PA03 decidido): token de acesso de **15 min** (HS256, chave com `kid` no Secrets Manager), guardado **só na memória** do front e enviado no header `Authorization`; dentro dele só o id do usuário, os papéis, emissão, expiração e um id único (nada de celular, CPF ou nome). Refresh token de **30 dias**, renovado a cada uso, em cookie **HttpOnly/Secure/SameSite=Strict** enviado só ao endpoint de renovação, guardado como **hash** no banco; revogado no logout, na troca de senha e quando o admin inativa a conta; refresh reutilizado (sinal de roubo) revoga todos os tokens daquele login. **Uma sessão por aparelho**, com "sair de todos os aparelhos". As tabelas `spring_session*` saem na V12 (DB-13).
- **Login** com celular **ou** e-mail + senha (RN58). **MFA obrigatório para ADMIN**, opcional para cliente e profissional; entrar só com código por SMS continua como alternativa à senha (RF01). **Confirmação do celular**: obrigatória para o profissional (sem ela não vai para análise nem aparece na busca), opcional para o cliente (RN08). Contato não confirmado não fica reservado (RN61).
- CORS restrito ao domínio da COE. CSRF só no endpoint de renovação (único que usa cookie; o SameSite=Strict já cobre quase tudo).
- Rate limit em login, SMS, recuperação de senha, chat e criação de conta.
- Bean Validation em toda entrada. **Nunca confiar no front.**
- **LGPD:**
  - CPF, chave Pix e endereço criptografados em repouso (AES-GCM, chave em variável de ambiente / KMS).
  - CPF também guardado como hash (HMAC com chave secreta) para unicidade.
  - Documentos e selfies em bucket **privado**, acessados só por URL assinada e temporária.
  - CPF, telefone, e-mail e tokens mascarados em logs.
- **Uploads:** validar tipo real (magic bytes) e tamanho, remover EXIF, gerar nome aleatório.
- Segredos só em variáveis de ambiente. Nunca commitar `.env`, chaves ou credenciais (`.env.example` sim).
- Erros sem stack trace, no formato Problem Details (RFC 9457), mensagens em pt-BR.
- Headers de segurança e HTTPS em produção.
- Toda ação do admin e todo movimento de dinheiro gera **auditoria** (`log_auditoria`, só inserção).
- Dependências verificadas com OWASP Dependency-Check (back) e `npm audit` (front): `npm audit --omit=dev` sem nenhum alto ou crítico; aviso só de dependência de desenvolvimento pode virar risco aceito, registrado no plano.

## Frontend (React)
- Pasta `frontend/`: React + TypeScript (strict) + Vite + React Router. Estado global mínimo; dados do servidor via camada `src/api/`.
- **Referência de telas e fluxos: o protótipo HTML "Dia carimbado"** (identidade em `docs/identidade-visual.md`). O React reproduz essas telas; não redesenhar sem pedir.
- Identidade "Dia carimbado": azul-violeta de carimbo `--carimbo #3B3DC4` (marca e ação principal), amarelo `--sinal #FFCF33` **só no que pede atenção** (texto sempre `--tinta` sobre ele), fundo `--papel #EEF0F4`; uma família só, **Archivo** (títulos 800 com largura 125%). Cores sempre por variável CSS de `tokens.css`, nunca hex solto.
- **Mobile-first a partir de 360 px**; alvos de toque com pelo menos 44 px (botões 52 px); WCAG 2.1 AA (rótulo em todo campo, foco visível, teclado); status nunca só por cor.
- Textos em pt-BR simples, pensados para o profissional ("Cheguei", "Terminei o dia", "Dinheiro").
- Chamadas à API: `fetch` com o JWT no header `Authorization`; ao receber 401, renova o token e, se falhar, vai para a tela de entrar; erros Problem Details mostrados em português.
- **Nenhuma regra de negócio só no front**: valores, comissão, limite LC 150, censura e estados vêm da API. O front pode avisar antes, o backend decide.
- Toda tela tem estados de carregando, vazio, erro e sem conexão.
- Local: Vite com proxy `/api` → `http://localhost:8081` (mesma origem; a 8080 da máquina é do Apache). Produção: build servido no mesmo domínio da API.
- **CSP**: produção e `vite preview` com a política de `frontend/cabecalhos-seguranca.json` (o Spring manda o mesmo texto; `PoliticasDoNavegadorTest` compara). Nada de `<script>` ou `style=` inline, nem recurso de outro domínio (fonte, imagem, script). O `npm run dev` roda sem CSP: confira no `npm run test:e2e`, que falha com qualquer violação.

### Como chamar a API no front
- **Cliente:** `chamar<T>(caminho, { metodo, corpo, sinal })` de `src/api/cliente.ts` (prefixo `/api`, JSON, limite de 15 s). Nunca `fetch` solto nas telas. Funções por recurso ao lado (`buscarCatalogo`, `buscarRegras`), com validação do que pode vir errado (ex.: `taxaPagaPor` desconhecido vira erro).
- **Sessão:** token de acesso só na memória (`src/api/sessao.ts`; `useSessao()` para o usuário); nunca em localStorage, sessionStorage ou log. 401 numa chamada com token: uma única `POST /api/auth/renovar` para todas as 401 juntas e a chamada é repetida uma vez; renovação recusada limpa a sessão e o `SessaoProvider` leva a `/entrar?voltar=` (só caminho interno, `caminhoInternoSeguro`).
- **Erros:** sempre `ErroDaApi` com `tipo` (`problema`, `sem-conexao`, `tempo`, `limite`, `servidor`, `sessao`, `resposta`, `cancelado`), `codigo` (do `urn:coe:erro:<codigo>`), `campos` (erro por campo) e `message` em pt-BR. Mostre `erro.message`; nunca o corpo cru.
- **Na tela:** `useRecurso(buscar, estaVazio?)` dá `situacao` (`carregando`, `pronto`, `vazio`, `erro`), `dados`, `erro` e `tentarDeNovo`, e cancela ao desmontar. Passe funções estáveis (de módulo). Erro com `FalhaAoCarregar`; carregando sem pular layout.
- **Tipos:** à mão em `src/api/tipos.ts`, iguais aos DTOs do backend.
- **Testes:** `simularFetch` / `simularApiPublica` (`src/teste/`) no Vitest; `simularApi(page)` (`e2e/apiSimulada.ts`) no Playwright, com os mesmos dados de `src/teste/dadosDaApi.ts`. Números de regra só podem aparecer vindos da resposta simulada.

### Como fazer uma tela
- **Onde:** página em `src/paginas/<nome>/` (`Nome.tsx`, `Nome.module.css`, `Nome.test.tsx`; textos provisórios num `conteudo.ts`); componente reaproveitável em `src/componentes/<Nome>/`; rota em `src/rotas.tsx`, dentro da `Moldura` (pular para o conteúdo, cabeçalho, menu de baixo, rodapé e foco no `<main>` na troca de página).
- **Estilo:** só CSS Modules e variáveis do `src/styles/tokens.css` (o stylelint barra hex e nome de cor). Faixa de largura com a classe global `largura`. Mobile-first a partir de 360 px, media queries `(width >= 720px)`.
- **Peças prontas:** `Botao` (principal/escuro/contorno/suave/perigo/sucesso; `para` vira link; **um principal por tela**; sobre fundo `--carimbo` use `sobreFundoEscuro`), `CampoSelecao` (rótulo sempre visível, id gerado), `Nota` (`anunciar` só quando aparece depois de uma ação), `StatusDiaria` (estado da API + lado), `Carimbo`, `Chip`, `Avatar`, `Icone` (decorativo, sempre com texto ao lado), `Ilustracao` (com a etiqueta "Ilustração").
- **Título da aba:** `useTitulo('Nome da página')`. Caminhos sempre de `src/caminhos.ts` (`CAMINHOS`, `SECOES_DO_INICIO`, `caminhoDaBusca`), nunca texto solto. Link para seção do Início: `<Link to={SECOES_DO_INICIO.x}>` (nunca `<a href>`, que recarrega a página e perde a sessão).
- **Honestidade:** exemplo é marcado como exemplo; nenhum número de regra (comissão, prazo, limite) fixo no front.
- **Testes:** TDD com Testing Library; `semViolacoes(container)` (axe) em todo componente e página; fluxo crítico no `e2e/` (360 e 1280 px, sem violação de CSP, sem rolagem lateral).

### Contas e sessão no front (FE-03)
- **Rotas de conta** (`CAMINHOS`): públicas `/entrar`, `/criar-conta`, `/criar-conta/cliente`, `/esqueci-senha`, `/redefinir-senha`, `/confirmar-email`, `/provar-email`, `/termos`, `/privacidade`; privadas (dentro de `RotaPrivada`) `/conta`, `/conta/confirmar-celular`, `/conta/codigo-no-login`, `/conta/trocar-senha`, `/conta/recolocar-contato`.
- **Sessão:** `recuperarSessaoAoAbrir()` (no `main.tsx`) faz uma renovação silenciosa; enquanto `situacao === 'verificando'`, página privada mostra "Carregando…". Sem sessão, `/entrar?voltar=`; "Sair" leva ao Início (`encerrarSessao({ voluntaria: true })`) e, se a API falhar, **não** finge que saiu (o cookie continuaria valendo). `iniciarSessao` abre nova geração (renovação atrasada não troca um login novo). Depois de mudar algo que vai no token (ex.: contato pendente), `atualizarSessao()`.
- **Links do e-mail** (`#token=`): leia com `lerTokenDoFragmento` no inicializador do `useState` e chame `limparFragmento()` no efeito **antes** de qualquer chamada; o token vai só no corpo do POST.
- **Código por SMS:** sempre `CampoCodigo` (6 números, `one-time-code`); com `aoReenviar`, mostra `MandarDeNovo` (60 s de espera visível; falha vira aviso sem apagar o que foi digitado). Para link, `MandarDeNovo item="link"`. Envio de formulário com `useEnvioUnico` (trava de duplo clique) e erros com `comoErroDaApi`.
- **Prova de posse (RN61):** no 409 `contato-em-uso` do cadastro, "Esse celular/e-mail é meu" (pelo campo do 409). Comprovante só em memória; mudou o contato, descarta; 422 `comprovante-invalido` recomeça. E-mail: o link abre `/provar-email`, que manda `{tipo, email, comprovante}` por `BroadcastChannel('coe-posse')` (`api/canalDaPosse.ts`) e espera 1 s a resposta da aba do cadastro do mesmo e-mail normalizado; sem resposta, continua o cadastro ali com o e-mail fixo. A senha nunca passa entre abas.
- **Contato pendente:** com `usuario.contatoPendente`, a `RotaPrivada` só abre as rotas de `ROTAS_LIBERADAS_COM_CONTATO_PENDENTE` (`layout/liberadasComContatoPendente.ts`, igual ao `@LiberadoComContatoPendente` do backend); o resto mostra a nota e "Recolocar contato" (negar por padrão). Tela privada nova que precise funcionar com contato pendente = anotação no backend **e** a rota nessa lista. Recolocar: `PUT /eu/{email,celular}` (reenviar = repetir o PUT com o mesmo valor), confirmação, `atualizarSessao()`.

## Testes
- **TDD obrigatório**: teste falhando primeiro, depois a implementação.
- Cobertura mínima de **80%**. **100%** em dinheiro, máquina de estados, censura de contato e limite da LC 150.
- Use `Clock` injetado para testar prazos (12 h, 48 h, janela de 7 dias) sem esperar.
- Casos obrigatórios:
  - transições inválidas de status
  - webhook duplicado e webhook com assinatura inválida
  - liberação automática concorrendo com aprovação manual
  - liberar e reembolsar a mesma diária
  - dois clientes reservando o mesmo dia do mesmo profissional
  - LC 150: 3ª diária na janela de 7 dias recusada, inclusive virando a semana
  - acesso a recurso de outro usuário
  - censura: frases que devem bloquear e frases que devem passar (valores em R$, medidas, datas, `CEP 89010-000`, `CEP 89010000`)
  - idade mínima: 17 anos e 364 dias recusado; exatos 18 anos aceito
  - falta do profissional: reembolso integral, registro em `ocorrencia_profissional` e inativação só manual
  - transação financeira desbalanceada recusada

## Fluxo de trabalho (ECC)
Toda mudança segue estas etapas, sem pular nenhuma:

1. `/ecc:plan "<ID da tarefa>: <funcionalidade>"` com as RN/RF que ela cobre: plano revisado e aprovado por mim antes de codar
2. skill `tdd-workflow` (ou `springboot-tdd`): RED → GREEN → REFACTOR, com evidência do teste falhando
3. `/code-review` e agente `java-reviewer`: revisão com contexto limpo
4. skill `verification-loop` (ou `springboot-verification`) + `/test-coverage`: build, testes e cobertura passando

- Pagamento, segurança, autenticação, uploads ou dados pessoais passam também por `/security-scan` (agente `security-reviewer`).
- Queries e migrations novas: agente `database-reviewer`.
- Build quebrado: `/build-fix` ou agente `java-build-resolver`.
- Commits pequenos em Conventional Commits (`feat:`, `fix:`, `test:`, `refactor:`, `chore:`). Uma branch por tarefa: `feat/<ID>-<nome-curto>`.
- **Nunca** fazer push na `main`.
- Ao encerrar o dia: atualizar "Status atual", `/save-session`. Ao retomar: `/resume-session`.
- Na dúvida sobre regra de negócio: **perguntar**, não supor.
