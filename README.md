# COE Serviços

Marketplace de serviços pagos por diária (pedreiro, pintor, eletricista, jardineiro e diarista), começando pelo Vale do Itajaí/SC. O cliente paga antes, o dinheiro fica guardado e cada diária é liberada ao profissional quando o cliente aprova o dia.

- Regras de negócio: [`docs/regras-negocio.md`](docs/regras-negocio.md)
- Plano e cronograma: [`docs/plano-desenvolvimento.md`](docs/plano-desenvolvimento.md)
- Banco de dados: [`docs/mapa-banco.md`](docs/mapa-banco.md)
- Guia para quem desenvolve com o Claude Code: [`CLAUDE.md`](CLAUDE.md)

## Pré-requisitos

- JDK 21
- Docker (para os testes, que sobem um Postgres real com Testcontainers, e para o `docker compose`)
- Um PostgreSQL 16 ou mais novo: instalado na máquina **ou** o do `docker compose`

O Maven não precisa ser instalado: use o `./mvnw` da raiz (no Windows, `mvnw.cmd`).

## 1. Configurar o `.env`

```bash
cp .env.example .env
```

Preencha `DB_USER` e `DB_PASSWORD`. O `.env` não vai para o git. A aplicação lê o arquivo sozinha (`spring.config.import`), e o `docker compose` também.

## 2. Subir o banco

**Opção A: Docker** (Postgres 16 na porta **5433** e Mailpit)

```bash
docker compose up -d
```

No `.env`, use `DB_URL=jdbc:postgresql://localhost:5433/coeservicos`. O papel `coe_app` é criado na primeira subida.

| Serviço | Endereço |
|---|---|
| Postgres 16 | `localhost:5433`, banco `coeservicos` |
| Mailpit (e-mails e SMS falsos) | SMTP `localhost:1025`, painel http://localhost:8025 |

O S3 local (arquivos) entra no CORE-09.

**Opção B: Postgres instalado na máquina** (porta 5432)

Crie o banco `coeservicos` com um usuário dono dele e, uma vez, como superusuário:

```sql
CREATE ROLE coe_app NOLOGIN;
```

## 3. Rodar a aplicação

```bash
./mvnw spring-boot:run "-Dspring-boot.run.profiles=local"
```

O Flyway aplica as migrações e o seed local (dois usuários de teste, senha `coe-local-123`). Confira:

```bash
curl http://localhost:8080/actuator/health    # {"status":"UP"}
```

## 4. Testar

```bash
./mvnw clean verify        # testes + formatação (Spotless) + cobertura (JaCoCo, mínimo 80%)
./mvnw test                # só os testes
./mvnw spotless:apply      # corrige a formatação
```

Os testes sobem um Postgres 16 descartável com Testcontainers; o Docker precisa estar rodando. O relatório de cobertura fica em `target/site/jacoco/index.html`.

## 5. Recriar o banco local do zero

Apaga tudo do banco local (só dados de desenvolvimento) e reaplica as migrações na próxima subida.

**Docker:**

```bash
docker compose down -v      # remove o volume do Postgres
docker compose up -d
```

**Postgres instalado** (como o usuário dono do banco):

```sql
DROP SCHEMA public CASCADE;
CREATE SCHEMA public;
```

Depois, rode a aplicação com o perfil `local` (passo 3).

## Perfis

| Perfil | Uso |
|---|---|
| `local` | Desenvolvimento: lê o `.env`, aplica o seed local (`db/local`) |
| `test` | Testes automatizados: banco do Testcontainers, sem seed local |
| `prod` | Produção e homologação: só migrações versionadas, Flyway sem `clean`, Actuator só com `health` |

## Usuários do banco

- O **Flyway** roda com o usuário **dono** do schema.
- A **aplicação**, fora do ambiente local, conecta com um login membro do papel **`coe_app`**, que não é dono: não altera tabelas, não desliga triggers e não apaga o livro-razão. Em produção, o dono vai em `SPRING_FLYWAY_USER`/`SPRING_FLYWAY_PASSWORD` e o login da aplicação em `DB_USER`/`DB_PASSWORD`.
- No ambiente local, usar o dono nos dois funciona, mas sem essa proteção.

## Erros da API

Todo erro sai em Problem Details (RFC 9457), com textos em português e sem detalhes internos. O campo `type` identifica o erro (`urn:coe:erro:<codigo>`, por exemplo `urn:coe:erro:validacao`); erros de validação trazem a lista `campos`.
