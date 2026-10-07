-- =============================================================================
-- COE Serviços · V14 — Códigos SMS, MFA, confirmação de contato, RN61 e senha
-- (DB-16; CORE-04/05). V1–V13 não são editadas.
--
-- - codigo_sms: código guardado como HMAC-SHA256 (32 bytes; chave da aplicação), dono,
--   desafio do MFA (hash, único), finalidades novas; um ativo por celular e finalidade;
--   conteúdo imutável: só tentativas (subindo), uso e invalidação mudam; datas vindas do
--   Clock (sem DEFAULT now()).
-- - token_verificacao: links por e-mail (confirmar, provar posse, recuperar senha) e
--   comprovantes de posse da RN61, só o hash do token; um ativo por destino e finalidade;
--   só uso e invalidação mudam, uma vez.
-- - celular e e-mail guardados nos códigos e links: expurgo pelo dono do schema em prazo
--   curto (LGPD), registrado no plano.
-- - token_senha (V2, nunca usada) sai: a recuperação vai pela token_verificacao (link)
--   ou pelo codigo_sms (código).
-- - usuario: e-mail confirmado; RN61: a conta pode ficar sem celular OU sem e-mail
--   (perdeu o dado para o dono verdadeiro), nunca sem os dois, e nunca sem senha.
-- - refresh_token: motivo 'contato_transferido' (sessões da conta que perdeu o dado).
--
-- Trava: token_senha e codigo_sms precisam estar vazias (nenhum código as usava). Com
-- linhas, a migração para com erro em vez de perder dado.
-- =============================================================================

SET LOCAL lock_timeout = '5s';

-- Trava forte antes de conferir: nenhuma aplicação antiga insere entre a conferência e os ALTERs.
LOCK TABLE token_senha, codigo_sms IN ACCESS EXCLUSIVE MODE;

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM token_senha) THEN
    RAISE EXCEPTION 'V14: token_senha tem linhas; migre os dados antes de aplicar a V14';
  END IF;
  IF EXISTS (SELECT 1 FROM codigo_sms) THEN
    RAISE EXCEPTION 'V14: codigo_sms tem linhas; migre os dados antes de aplicar a V14';
  END IF;
END $$;

-- -----------------------------------------------------------------------------
-- 1. usuario: e-mail confirmado e credenciais da RN61
-- -----------------------------------------------------------------------------
ALTER TABLE usuario ADD COLUMN email_verificado_em timestamptz;

ALTER TABLE usuario DROP CONSTRAINT ck_usuario_credenciais;
ALTER TABLE usuario ADD CONSTRAINT ck_usuario_credenciais
  CHECK (status = 'excluido' OR (senha_hash IS NOT NULL AND (celular IS NOT NULL OR email IS NOT NULL)));
ALTER TABLE usuario ADD CONSTRAINT ck_usuario_celular_verificado
  CHECK (celular_verificado_em IS NULL OR celular IS NOT NULL);
ALTER TABLE usuario ADD CONSTRAINT ck_usuario_email_verificado
  CHECK (email_verificado_em IS NULL OR email IS NOT NULL);
-- MFA é por SMS: ligado só com celular confirmado (a RN61 só transfere celular não confirmado).
ALTER TABLE usuario ADD CONSTRAINT ck_usuario_mfa_celular
  CHECK (NOT mfa_sms_ativo OR celular_verificado_em IS NOT NULL);

-- -----------------------------------------------------------------------------
-- 2. codigo_sms (vazia, conferido acima)
-- -----------------------------------------------------------------------------
ALTER TABLE codigo_sms DROP COLUMN codigo_hash;
ALTER TABLE codigo_sms ADD COLUMN codigo_hmac bytea NOT NULL;
ALTER TABLE codigo_sms ADD COLUMN usuario_id uuid;
ALTER TABLE codigo_sms ADD COLUMN desafio_hash bytea;
-- Substituído por um código novo ou travado na 5ª tentativa (diferente de usado com sucesso).
ALTER TABLE codigo_sms ADD COLUMN invalidado_em timestamptz;
ALTER TABLE codigo_sms ALTER COLUMN criado_em DROP DEFAULT;

ALTER TABLE codigo_sms ADD CONSTRAINT fk_codigo_sms_usuario
  FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE RESTRICT;
ALTER TABLE codigo_sms ADD CONSTRAINT uq_codigo_sms_desafio UNIQUE (desafio_hash);
ALTER TABLE codigo_sms ADD CONSTRAINT ck_codigo_sms_hmac CHECK (octet_length(codigo_hmac) = 32);
ALTER TABLE codigo_sms ADD CONSTRAINT ck_codigo_sms_desafio
  CHECK (desafio_hash IS NULL OR (finalidade = 'mfa' AND octet_length(desafio_hash) = 32));
-- Sem dono só para provar a posse no cadastro (a conta ainda não existe).
ALTER TABLE codigo_sms ADD CONSTRAINT ck_codigo_sms_dono
  CHECK (usuario_id IS NOT NULL OR finalidade = 'comprovar_posse');
ALTER TABLE codigo_sms ADD CONSTRAINT ck_codigo_sms_mfa_com_desafio
  CHECK (finalidade <> 'mfa' OR desafio_hash IS NOT NULL);
ALTER TABLE codigo_sms ADD CONSTRAINT ck_codigo_sms_uso CHECK (usado_em IS NULL OR usado_em >= criado_em);
ALTER TABLE codigo_sms ADD CONSTRAINT ck_codigo_sms_invalidacao
  CHECK (invalidado_em IS NULL OR invalidado_em >= criado_em);
ALTER TABLE codigo_sms ADD CONSTRAINT ck_codigo_sms_uso_ou_invalidado
  CHECK (usado_em IS NULL OR invalidado_em IS NULL);

-- 'mfa' = segundo passo do login (com desafio); 'configurar_mfa' = ligar/desligar o MFA.
ALTER TABLE codigo_sms DROP CONSTRAINT ck_codigo_sms_finalidade;
ALTER TABLE codigo_sms ADD CONSTRAINT ck_codigo_sms_finalidade
  CHECK (finalidade IN ('login','verificar_celular','trocar_celular','mfa','configurar_mfa',
                        'comprovar_posse','recuperar_senha'));

-- Completo (não parcial): toda FK tem índice começando pela coluna dela.
CREATE INDEX ix_codigo_sms_usuario ON codigo_sms (usuario_id);
-- Um código ativo por celular e finalidade: a aplicação invalida o anterior (vencido ou não) na
-- mesma transação, antes de inserir; dois envios simultâneos não criam dois ativos. Serve também
-- de índice para buscar o código ativo.
CREATE UNIQUE INDEX uq_codigo_sms_ativo ON codigo_sms (celular, finalidade)
  WHERE usado_em IS NULL AND invalidado_em IS NULL;

-- A aplicação não apaga e só altera as colunas de uso (o gatilho também confere).
REVOKE DELETE, TRUNCATE, UPDATE ON codigo_sms FROM coe_app;
GRANT UPDATE (tentativas, usado_em, invalidado_em) ON codigo_sms TO coe_app;

CREATE OR REPLACE FUNCTION fn_codigo_sms_imutavel() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF (NEW.id, NEW.usuario_id, NEW.celular, NEW.finalidade, NEW.codigo_hmac, NEW.desafio_hash,
      NEW.expira_em, NEW.ip, NEW.criado_em)
     IS DISTINCT FROM
     (OLD.id, OLD.usuario_id, OLD.celular, OLD.finalidade, OLD.codigo_hmac, OLD.desafio_hash,
      OLD.expira_em, OLD.ip, OLD.criado_em)
     OR NEW.tentativas < OLD.tentativas
     OR (OLD.usado_em IS NOT NULL AND NEW.usado_em IS DISTINCT FROM OLD.usado_em)
     OR (OLD.invalidado_em IS NOT NULL AND NEW.invalidado_em IS DISTINCT FROM OLD.invalidado_em) THEN
    RAISE EXCEPTION 'Tabela codigo_sms: só tentativas (subindo) e uso podem mudar'
      USING ERRCODE = 'insufficient_privilege';
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER trg_codigo_sms_imutavel
  BEFORE UPDATE ON codigo_sms
  FOR EACH ROW EXECUTE FUNCTION fn_codigo_sms_imutavel();

-- -----------------------------------------------------------------------------
-- 3. token_verificacao: links por e-mail e comprovantes de posse (só o hash)
-- -----------------------------------------------------------------------------
CREATE TABLE token_verificacao (
  id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  usuario_id  uuid,
  canal       text        NOT NULL,
  destino     text        NOT NULL,
  finalidade  text        NOT NULL,
  token_hash  bytea       NOT NULL,
  criado_em   timestamptz NOT NULL,
  expira_em   timestamptz NOT NULL,
  usado_em    timestamptz,
  invalidado_em timestamptz,
  CONSTRAINT fk_token_verificacao_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE RESTRICT,
  CONSTRAINT uq_token_verificacao_hash UNIQUE (token_hash),
  CONSTRAINT ck_token_verificacao_hash CHECK (octet_length(token_hash) = 32),
  CONSTRAINT ck_token_verificacao_finalidade
    CHECK (finalidade IN ('confirmar_email','link_posse','recuperar_senha','comprovante_posse')),
  -- Link é sempre por e-mail; o comprovante de posse vale para os dois canais.
  CONSTRAINT ck_token_verificacao_canal
    CHECK (canal = 'email' OR (canal = 'celular' AND finalidade = 'comprovante_posse')),
  -- Destino normalizado: e-mail em minúsculas no formato do usuario.email; celular só com dígitos.
  CONSTRAINT ck_token_verificacao_destino CHECK (char_length(destino) <= 254 AND
    CASE canal
      WHEN 'email'   THEN destino ~ '^[^@\s]+@[^@\s]+\.[^@\s]+$' AND destino = lower(destino)
      WHEN 'celular' THEN destino ~ '^[0-9]{10,11}$'
      ELSE false
    END),
  -- Sem dono só para provar posse e para o comprovante (cadastro de quem ainda não tem conta).
  CONSTRAINT ck_token_verificacao_dono
    CHECK (usuario_id IS NOT NULL OR finalidade IN ('link_posse','comprovante_posse')),
  CONSTRAINT ck_token_verificacao_validade CHECK (expira_em > criado_em),
  CONSTRAINT ck_token_verificacao_uso CHECK (usado_em IS NULL OR usado_em >= criado_em),
  CONSTRAINT ck_token_verificacao_invalidacao CHECK (invalidado_em IS NULL OR invalidado_em >= criado_em),
  CONSTRAINT ck_token_verificacao_uso_ou_invalidado CHECK (usado_em IS NULL OR invalidado_em IS NULL)
);

CREATE INDEX ix_token_verificacao_usuario ON token_verificacao (usuario_id);
-- Limite de envios por endereço (1 a cada 60 s, 5 por hora), contado no banco.
CREATE INDEX ix_token_verificacao_destino ON token_verificacao (destino, criado_em DESC);
-- Um link (ou comprovante) ativo por destino e finalidade: o novo invalida o anterior.
CREATE UNIQUE INDEX uq_token_verificacao_ativo ON token_verificacao (destino, finalidade)
  WHERE usado_em IS NULL AND invalidado_em IS NULL;

REVOKE DELETE, TRUNCATE, UPDATE ON token_verificacao FROM coe_app;
GRANT UPDATE (usado_em, invalidado_em) ON token_verificacao TO coe_app;

CREATE OR REPLACE FUNCTION fn_token_verificacao_imutavel() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF (NEW.id, NEW.usuario_id, NEW.canal, NEW.destino, NEW.finalidade, NEW.token_hash,
      NEW.criado_em, NEW.expira_em)
     IS DISTINCT FROM
     (OLD.id, OLD.usuario_id, OLD.canal, OLD.destino, OLD.finalidade, OLD.token_hash,
      OLD.criado_em, OLD.expira_em)
     OR (OLD.usado_em IS NOT NULL AND NEW.usado_em IS DISTINCT FROM OLD.usado_em)
     OR (OLD.invalidado_em IS NOT NULL AND NEW.invalidado_em IS DISTINCT FROM OLD.invalidado_em) THEN
    RAISE EXCEPTION 'Tabela token_verificacao: só o uso pode mudar, uma única vez'
      USING ERRCODE = 'insufficient_privilege';
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER trg_token_verificacao_imutavel
  BEFORE UPDATE ON token_verificacao
  FOR EACH ROW EXECUTE FUNCTION fn_token_verificacao_imutavel();

-- -----------------------------------------------------------------------------
-- 4. token_senha sai (vazia, conferido acima)
-- -----------------------------------------------------------------------------
DROP TABLE token_senha;

-- -----------------------------------------------------------------------------
-- 5. refresh_token: motivo de revogação da RN61
-- -----------------------------------------------------------------------------
ALTER TABLE refresh_token DROP CONSTRAINT ck_refresh_token_motivo;
ALTER TABLE refresh_token ADD CONSTRAINT ck_refresh_token_motivo
  CHECK (motivo_revogacao IN ('logout','sair_todos','troca_senha','reuso','admin','teto','contato_transferido'));
