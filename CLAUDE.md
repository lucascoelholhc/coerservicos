# COE Serviços

Marketplace de serviços por diária (pedreiro, diarista e afins), começando em Blumenau/SC.
Perfis: **CLIENTE**, **PROFISSIONAL** e **ADMIN**. O cliente paga as diárias antecipadamente, o valor fica em custódia e é liberado ao profissional dia a dia, após aprovação do cliente.
Público com pouca familiaridade com tecnologia: telas simples, poucos campos, botões grandes.

## Documentação de apoio (leia antes de mexer no domínio)
- `docs/regras-negocio.md`: catálogo, cadastro, contato censurado, pagamento, LC 150, avaliações, máquina de estados, admin
- `docs/identidade-visual.md`: cores, fontes, acessibilidade, tom dos textos (ler antes de qualquer tarefa de front)
- Itens marcados **A DEFINIR** não estão decididos. **Pergunte antes de implementar**, nunca suponha.

## Stack e comandos
- Java + Spring Boot + Maven. As versões exatas estão no `pom.xml`.
- Banco: **A DEFINIR** (cogitando MySQL). Migrations com **Flyway**.
- Front final: **A DEFINIR**. O protótipo HTML/CSS/JS navegável é a referência de telas e fluxos.

```powershell
./mvnw clean verify          # build completo + testes
./mvnw test                  # só testes
./mvnw spring-boot:run       # rodar local
```
<!-- Ajustar se os comandos do projeto forem diferentes -->

## Status atual
<!-- Atualizar a cada funcionalidade concluída -->
- Projeto em fase inicial. Nenhum domínio implementado ainda.

## Estrutura de pacotes (por domínio, não por camada)
`usuario` · `profissional` (cadastro, verificação, portfólio) · `catalogo` · `contrato` (contratos e diárias) · `pagamento` (gateway, webhooks, ledger) · `disputa` · `mensagem` (chat + censura) · `moderacao` · `admin` · `config`

- Cada domínio tem seu controller, service, repository, entidades e DTOs.
- **Nunca expor entidade JPA na API.**

## Regras técnicas inegociáveis

### Dinheiro
- **BigDecimal** no Java e **NUMERIC(12,2)** no banco (percentuais em NUMERIC(5,4)). Nunca double ou float.
- **Ledger imutável**: toda movimentação é uma linha nova (recebido, retido, liberado, reembolsado, comissão). O saldo é calculado, nunca editado.
- Liberar, reembolsar e mudar status acontecem **em transação**, com trava de concorrência.
- Webhooks do gateway: assinatura validada, **idempotentes** e com o evento bruto registrado.
- A liberação automática roda por job agendado, idempotente e com log.
- O dinheiro em custódia fica **no gateway** (subconta/split), nunca em conta própria da COE.

### Máquina de estados da diária
- Validada **só no backend**. Transição fora da tabela em `docs/regras-negocio.md` = erro.

### Banco
- Todo schema versionado por migration Flyway. `ddl-auto=update` proibido fora de testes.
- Nunca rodar migration em produção sem pedir.

### Segurança (prioridade máxima)
- Spring Security com papéis CLIENTE, PROFISSIONAL e ADMIN.
- **Autorização por objeto em toda consulta** (anti-IDOR): cada usuário só acessa os próprios contratos e diárias. Recurso de outro usuário retorna 403 ou 404.
- Senhas com BCrypt ou Argon2. Autenticação (JWT curto + refresh ou cookie HttpOnly/Secure/SameSite): **A DEFINIR**.
- Rate limit em login, envio de SMS, chat e criação de conta.
- Bean Validation em toda entrada. Nunca confiar no front.
- **LGPD:**
  - CPF e chave Pix criptografados em repouso.
  - CPF também guardado como hash (HMAC com chave secreta) para busca e unicidade.
  - Documentos e selfies em bucket **privado**, acessados só por URL assinada e temporária.
  - CPF, telefone e tokens mascarados em logs.
- **Uploads:** validar tipo real (magic bytes) e tamanho, remover EXIF, gerar nome aleatório.
- Segredos só em variáveis de ambiente. Nunca commitar `.env`, chaves ou credenciais.
- Erros sem stack trace, no formato Problem Details (RFC 9457).
- CORS restrito, headers de segurança e HTTPS em produção.
- Toda ação do admin gera **auditoria**: quem, quando, o quê, antes e depois.
- Dependências verificadas com OWASP Dependency-Check.

### Testes
- **TDD obrigatório**: teste falhando primeiro, depois a implementação.
- Ferramentas: JUnit 5, AssertJ, Mockito, MockMvc e Testcontainers (banco real).
- Cobertura mínima de **80%**. **100%** em dinheiro, máquina de estados, censura de contato e limite da LC 150.
- Casos obrigatórios:
  - transições inválidas de status
  - webhook duplicado
  - liberação automática concorrendo com aprovação manual
  - dois clientes reservando o mesmo dia
  - acesso a recurso de outro usuário
  - censura: frases que devem bloquear e frases que devem passar (valores em R$, medidas, datas)

## Fluxo de trabalho (ECC)
Toda mudança segue estas etapas, sem pular nenhuma:

1. `/ecc:plan "<funcionalidade>"`: plano revisado e aprovado por mim antes de codar
2. skill `tdd-workflow`: RED → GREEN → REFACTOR, com evidência do teste falhando
3. `/code-review` e agente `java-reviewer`: revisão com contexto limpo
4. skill `verification-loop`: build, testes e cobertura passando

- Pagamento, segurança, autenticação ou dados pessoais passam também por `/security-scan` (agente `security-reviewer`).
- Queries e migrations novas: agente `database-reviewer`.
- Commits pequenos em Conventional Commits (`feat:`, `fix:`, `test:`, `refactor:`). Uma branch por funcionalidade.
- **Nunca** fazer push na `main`.
- Ao encerrar o dia: `/save-session`. Ao retomar: `/resume-session`.
- Na dúvida sobre regra de negócio: **perguntar**, não supor.
