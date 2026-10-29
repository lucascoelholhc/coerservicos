-- =============================================================================
-- COE Serviços · V24 — Equipe de administradores (DOM-11, lacuna de 26/10)
-- =============================================================================
-- - Finalidade 'acao_admin' no codigo_sms: toda ação da Equipe (convidar, aceitar, remover) pede um código
--   na hora, mandado ao celular confirmado de quem age.
-- - convite_admin: o admin convida uma conta existente; a pessoa aceita com o próprio código. Um convite
--   pendente por pessoa; vale até expira_em; aceito ou cancelado, fica como histórico.

ALTER TABLE codigo_sms DROP CONSTRAINT ck_codigo_sms_finalidade;
ALTER TABLE codigo_sms ADD CONSTRAINT ck_codigo_sms_finalidade
  CHECK (finalidade IN ('login','verificar_celular','trocar_celular','mfa','configurar_mfa',
                        'comprovar_posse','recuperar_senha','acao_admin'));

CREATE TABLE convite_admin (
  id             uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  convidado_id   uuid        NOT NULL REFERENCES usuario(id),
  convidado_por  uuid        NOT NULL REFERENCES usuario(id),
  criado_em      timestamptz NOT NULL DEFAULT now(),
  expira_em      timestamptz NOT NULL,
  aceito_em      timestamptz,
  cancelado_em   timestamptz,
  CONSTRAINT ck_convite_admin_pessoas CHECK (convidado_id <> convidado_por),
  CONSTRAINT ck_convite_admin_prazo   CHECK (expira_em > criado_em),
  CONSTRAINT ck_convite_admin_fim     CHECK (aceito_em IS NULL OR cancelado_em IS NULL)
);
CREATE UNIQUE INDEX uq_convite_admin_pendente ON convite_admin (convidado_id)
  WHERE aceito_em IS NULL AND cancelado_em IS NULL;
CREATE INDEX ix_convite_admin_convidado     ON convite_admin (convidado_id);   -- o parcial único não serve à FK
CREATE INDEX ix_convite_admin_convidado_por ON convite_admin (convidado_por);
