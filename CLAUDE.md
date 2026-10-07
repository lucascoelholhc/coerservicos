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
| Local | Postgres local ou Docker (`docker compose`, porta 5433); Mailpit (e-mail/SMS falso). S3 local **A DEFINIR no CORE-09** (a imagem pública do MinIO deixou de existir) |
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

# frontend
cd frontend
npm install
npm run dev        # http://localhost:5173, com proxy /api -> http://localhost:8081
npm test           # Vitest
npm run build      # gera frontend/dist
npx playwright test
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
- Próximo: dia 8 (fatia 1), conforme o cronograma (seção 11 do plano).

## Estrutura do backend (por domínio, não por camada)
`usuario` (conta, login, SMS) · `catalogo` (cidades, profissões, serviços) · `profissional` (cadastro, verificação, portfólio, agenda) · `contrato` (contratos e diárias) · `pagamento` (gateway, webhooks, ledger, repasse, reembolso) · `mensagem` (chat + censura) · `avaliacao` · `disputa` · `moderacao` (denúncias) · `admin` · `config` · `compartilhado` (Dinheiro, erros, auditoria, armazenamento)

- Cada domínio tem controller, service, repository, entidades e DTOs.
- Erros: lance `RegraDeNegocioException` (422, com código da regra), `RecursoNaoEncontradoException` (404, também para recurso de outro usuário) ou `ConflitoException` (409), de `compartilhado.erro`. O `TratadorDeErros` converte em Problem Details; nunca monte resposta de erro no controller.
- Testes de integração estendem `IntegracaoTest` (contexto, Testcontainers, MockMvc e o relógio `RelogioAjustavel` compartilhados).

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
- **Front**: `POST /api/auth/entrar` com `{"login","senha"}` (login = celular com ou sem máscara/+55, ou e-mail). Resposta 200: `{accessToken, expiraEm, usuario:{id,nome,papeis}}` + cookie `coe_refresh` (HttpOnly, só `/api/auth`). Guarde o `accessToken` **só na memória** e mande `Authorization: Bearer <token>`. Ao receber 401, chame `POST /api/auth/renovar` (o navegador manda o cookie); se a renovação der 401, vá para a tela de entrar. Sair: `POST /api/auth/sair` (este aparelho) ou `/api/auth/sair-de-todos`.
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
- Dependências verificadas com OWASP Dependency-Check (back) e `npm audit` (front).

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
