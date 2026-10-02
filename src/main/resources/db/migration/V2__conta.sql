-- =============================================================================
-- COE Serviços · V2 — Conta: usuários, papéis, login por SMS, senha, termos
-- Um único login serve para cliente e profissional (papéis em usuario_papel).
-- =============================================================================

CREATE TABLE usuario (
  id                    uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  nome                  text        NOT NULL CHECK (char_length(btrim(nome)) BETWEEN 2 AND 120),
  celular               text        NOT NULL CHECK (celular ~ '^[0-9]{10,11}$'),   -- só dígitos, com DDD
  celular_verificado_em timestamptz,
  email                 citext      CHECK (email ~ '^[^@\s]+@[^@\s]+\.[^@\s]+$'),
  senha_hash            text        NOT NULL,                                       -- Argon2/BCrypt
  cep                   char(8)     CHECK (cep ~ '^[0-9]{8}$'),
  -- cidade_id: FK adicionada na V3 (catálogo)
  status                text        NOT NULL DEFAULT 'ativo'
                        CHECK (status IN ('ativo','em_analise','suspenso','excluido')),
  motivo_status         text,
  ultimo_login_em       timestamptz,
  excluido_em           timestamptz,             -- LGPD: dados anonimizados nessa data
  criado_em             timestamptz NOT NULL DEFAULT now(),
  atualizado_em         timestamptz NOT NULL DEFAULT now(),
  versao                bigint      NOT NULL DEFAULT 0,
  CONSTRAINT uq_usuario_celular UNIQUE (celular),
  CONSTRAINT uq_usuario_email   UNIQUE (email),
  CONSTRAINT ck_usuario_excluido CHECK (status <> 'excluido' OR excluido_em IS NOT NULL)
);
CREATE TRIGGER trg_usuario_toca BEFORE UPDATE ON usuario
  FOR EACH ROW EXECUTE FUNCTION fn_toca_registro();

CREATE TABLE usuario_papel (
  usuario_id  uuid        NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
  papel       text        NOT NULL CHECK (papel IN ('CLIENTE','PROFISSIONAL','ADMIN')),
  criado_em   timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (usuario_id, papel)
);

-- Código de 6 dígitos por SMS (login e verificação do celular). Guardado só como hash.
CREATE TABLE codigo_sms (
  id           uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  celular      text        NOT NULL CHECK (celular ~ '^[0-9]{10,11}$'),
  finalidade   text        NOT NULL CHECK (finalidade IN ('login','verificar_celular','trocar_celular')),
  codigo_hash  text        NOT NULL,
  tentativas   smallint    NOT NULL DEFAULT 0 CHECK (tentativas BETWEEN 0 AND 5),
  expira_em    timestamptz NOT NULL,
  usado_em     timestamptz,
  ip           inet,
  criado_em    timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_codigo_sms_validade CHECK (expira_em > criado_em)
);
CREATE INDEX ix_codigo_sms_celular ON codigo_sms (celular, criado_em DESC);

-- Recuperação de senha: token de uso único, guardado só como hash
CREATE TABLE token_senha (
  id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  usuario_id  uuid        NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
  token_hash  text        NOT NULL,
  expira_em   timestamptz NOT NULL,
  usado_em    timestamptz,
  criado_em   timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT uq_token_senha_hash UNIQUE (token_hash)
);
CREATE INDEX ix_token_senha_usuario ON token_senha (usuario_id);

-- Aceite dos termos com versão e data (RNF18)
CREATE TABLE aceite_termos (
  id             uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  usuario_id     uuid        NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
  versao_termos  text        NOT NULL,
  aceito_em      timestamptz NOT NULL DEFAULT now(),
  ip             inet,
  user_agent     text,
  CONSTRAINT uq_aceite_termos UNIQUE (usuario_id, versao_termos)
);

-- -----------------------------------------------------------------------------
-- Spring Session JDBC (schema oficial para PostgreSQL).
-- Usado se o PA03 ficar em "sessão em cookie". Se for JWT, remover estas tabelas.
-- -----------------------------------------------------------------------------
CREATE TABLE spring_session (
  primary_id            char(36)     NOT NULL,
  session_id            char(36)     NOT NULL,
  creation_time         bigint       NOT NULL,
  last_access_time      bigint       NOT NULL,
  max_inactive_interval int          NOT NULL,
  expiry_time           bigint       NOT NULL,
  principal_name        varchar(100),
  CONSTRAINT spring_session_pk PRIMARY KEY (primary_id)
);
CREATE UNIQUE INDEX spring_session_ix1 ON spring_session (session_id);
CREATE INDEX spring_session_ix2 ON spring_session (expiry_time);
CREATE INDEX spring_session_ix3 ON spring_session (principal_name);

CREATE TABLE spring_session_attributes (
  session_primary_id char(36)     NOT NULL,
  attribute_name     varchar(200) NOT NULL,
  attribute_bytes    bytea        NOT NULL,
  CONSTRAINT spring_session_attributes_pk PRIMARY KEY (session_primary_id, attribute_name),
  CONSTRAINT spring_session_attributes_fk FOREIGN KEY (session_primary_id)
    REFERENCES spring_session (primary_id) ON DELETE CASCADE
);
