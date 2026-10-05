-- =============================================================================
-- COE Serviços · V12 — Autenticação com JWT (DB-13, PA03). V1–V11 não são editadas.
--
-- - Sessão do Spring sai (o login é por JWT de acesso + refresh token rotativo).
-- - refresh_token: só o hash SHA-256 do token (o token em claro nunca chega ao banco);
--   uma família = um login = um aparelho; conteúdo imutável, só uso, revogação e
--   sucessor mudam, uma vez; a aplicação não apaga (expurgo dos vencidos, que também
--   apaga aparelho e IP, dado pessoal: job futuro). Não é estritamente só de inserção.
-- - Datas do refresh_token vêm da aplicação (Clock injetado), sem default do banco.
-- - usuario.mfa_sms_ativo e finalidade 'mfa' no codigo_sms (segundo passo, CORE-04).
-- - Comissão com teto de 30% (decisão de 05/10/2026).
--
-- Premissa (como na V11): aplicada antes de existir produção. Com dados reais, os
-- CHECKs novos usariam ADD CONSTRAINT ... NOT VALID e depois VALIDATE CONSTRAINT.
-- =============================================================================

SET LOCAL lock_timeout = '5s';

-- -----------------------------------------------------------------------------
-- 1. Sessão do Spring (V2) não é mais usada
-- -----------------------------------------------------------------------------
DROP TABLE spring_session_attributes;
DROP TABLE spring_session;

-- -----------------------------------------------------------------------------
-- 2. Refresh token
-- -----------------------------------------------------------------------------
CREATE TABLE refresh_token (
  id                uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  usuario_id        uuid        NOT NULL,
  familia_id        uuid        NOT NULL,
  token_hash        bytea       NOT NULL,
  aparelho          text,
  ip                inet,
  criado_em         timestamptz NOT NULL,
  expira_em         timestamptz NOT NULL,
  usado_em          timestamptz,
  revogado_em       timestamptz,
  motivo_revogacao  text,
  substituido_por   uuid,
  CONSTRAINT fk_refresh_token_usuario  FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE RESTRICT,
  -- sucessor sempre da mesma família e do mesmo usuário; NO ACTION (checado no fim da
  -- instrução) para o expurgo apagar a cadeia inteira de uma vez
  CONSTRAINT uq_refresh_token_identidade UNIQUE (id, familia_id, usuario_id),
  CONSTRAINT fk_refresh_token_sucessor FOREIGN KEY (substituido_por, familia_id, usuario_id)
    REFERENCES refresh_token (id, familia_id, usuario_id),
  CONSTRAINT uq_refresh_token_hash     UNIQUE (token_hash),
  CONSTRAINT uq_refresh_token_sucessor UNIQUE (substituido_por),
  CONSTRAINT ck_refresh_token_hash     CHECK (octet_length(token_hash) = 32),
  CONSTRAINT ck_refresh_token_validade CHECK (expira_em > criado_em),
  CONSTRAINT ck_refresh_token_aparelho CHECK (aparelho IS NULL OR char_length(aparelho) <= 120),
  CONSTRAINT ck_refresh_token_uso      CHECK (usado_em IS NULL OR usado_em >= criado_em),
  CONSTRAINT ck_refresh_token_revogacao CHECK ((revogado_em IS NULL) = (motivo_revogacao IS NULL)),
  CONSTRAINT ck_refresh_token_revogacao_data CHECK (revogado_em IS NULL OR revogado_em >= criado_em),
  CONSTRAINT ck_refresh_token_motivo   CHECK (motivo_revogacao IN ('logout','sair_todos','troca_senha','reuso','admin')),
  CONSTRAINT ck_refresh_token_sucessor CHECK (substituido_por IS NULL OR (usado_em IS NOT NULL AND substituido_por <> id))
);

-- Completo (não parcial): a regra do schema é "toda FK tem índice que começa pela coluna dela".
CREATE INDEX ix_refresh_token_usuario ON refresh_token (usuario_id);
-- familia_id não é FK: parcial, só o que ainda pode ser revogado.
CREATE INDEX ix_refresh_token_familia ON refresh_token (familia_id) WHERE revogado_em IS NULL;

-- A aplicação lê, cria e marca uso/revogação; nunca apaga (expurgo só pelo dono do schema).
REVOKE DELETE, TRUNCATE ON refresh_token FROM coe_app;

CREATE OR REPLACE FUNCTION fn_refresh_token_imutavel() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF (NEW.id, NEW.usuario_id, NEW.familia_id, NEW.token_hash, NEW.aparelho, NEW.ip,
      NEW.criado_em, NEW.expira_em)
     IS DISTINCT FROM
     (OLD.id, OLD.usuario_id, OLD.familia_id, OLD.token_hash, OLD.aparelho, OLD.ip,
      OLD.criado_em, OLD.expira_em)
     OR (OLD.usado_em IS NOT NULL AND NEW.usado_em IS DISTINCT FROM OLD.usado_em)
     OR (OLD.revogado_em IS NOT NULL
         AND (NEW.revogado_em, NEW.motivo_revogacao) IS DISTINCT FROM (OLD.revogado_em, OLD.motivo_revogacao))
     OR (OLD.substituido_por IS NOT NULL AND NEW.substituido_por IS DISTINCT FROM OLD.substituido_por) THEN
    RAISE EXCEPTION 'Tabela refresh_token: só uso, revogação e sucessor podem mudar, uma única vez'
      USING ERRCODE = 'insufficient_privilege';
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER trg_refresh_token_imutavel
  BEFORE UPDATE ON refresh_token
  FOR EACH ROW EXECUTE FUNCTION fn_refresh_token_imutavel();

-- -----------------------------------------------------------------------------
-- 3. Segundo passo por SMS (MFA): obrigatório para ADMIN, opcional para os demais
-- -----------------------------------------------------------------------------
ALTER TABLE usuario ADD COLUMN mfa_sms_ativo boolean NOT NULL DEFAULT false;

ALTER TABLE codigo_sms DROP CONSTRAINT codigo_sms_finalidade_check;
ALTER TABLE codigo_sms ADD CONSTRAINT ck_codigo_sms_finalidade
  CHECK (finalidade IN ('login','verificar_celular','trocar_celular','mfa'));

-- -----------------------------------------------------------------------------
-- 4. Comissão com teto de 30% (as demais faixas da V11 ficam iguais)
-- -----------------------------------------------------------------------------
ALTER TABLE configuracao DROP CONSTRAINT ck_configuracao_faixas;
ALTER TABLE configuracao ADD CONSTRAINT ck_configuracao_faixas CHECK (
  CASE
    WHEN chave = 'COMISSAO' THEN
      CASE WHEN valor ~ '^[0-9]{1,9}(\.[0-9]{1,6})?$' THEN valor::numeric >= 0 AND valor::numeric <= 0.30
           ELSE false END
    WHEN chave = 'TAXA_PAGA_POR' THEN valor IN ('cliente','profissional')
    WHEN chave IN ('AUTO_LIBERA_HORAS','PRAZO_DISPUTA_HORAS','TENTATIVAS_ANTES_ANALISE',
                   'MEIA_DIARIA_HORAS','PAGAMENTO_EXPIRA_MINUTOS') THEN
      CASE WHEN valor ~ '^[0-9]{1,9}$' THEN valor::int > 0 ELSE false END
    ELSE true
  END);
