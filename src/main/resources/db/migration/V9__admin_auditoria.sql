-- =============================================================================
-- COE Serviços · V9 — Admin: auditoria, notificações e denúncias
-- =============================================================================

-- Auditoria imutável de ações do admin e de toda movimentação de dinheiro (RNF08)
CREATE TABLE log_auditoria (
  id           bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  ator_id      uuid        REFERENCES usuario(id),     -- nulo = sistema
  ator_papel   text        CHECK (ator_papel IN ('CLIENTE','PROFISSIONAL','ADMIN','SISTEMA')),
  acao         text        NOT NULL,                    -- ex.: 'profissional.aprovar'
  entidade     text        NOT NULL,                    -- ex.: 'profissional'
  entidade_id  uuid,
  antes        jsonb,                                   -- sem CPF/Pix/endereço em claro
  depois       jsonb,
  ip           inet,
  criado_em    timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_auditoria_entidade ON log_auditoria (entidade, entidade_id, criado_em DESC);
CREATE INDEX ix_auditoria_ator     ON log_auditoria (ator_id, criado_em DESC);
CREATE TRIGGER trg_auditoria_somente_insercao
  BEFORE UPDATE OR DELETE ON log_auditoria
  FOR EACH ROW EXECUTE FUNCTION fn_somente_insercao();
CREATE TRIGGER trg_log_auditoria_sem_truncate
  BEFORE TRUNCATE ON log_auditoria
  FOR EACH STATEMENT EXECUTE FUNCTION fn_somente_insercao();
REVOKE UPDATE, DELETE, TRUNCATE ON log_auditoria FROM coe_app;

CREATE TABLE notificacao (
  id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  usuario_id  uuid        NOT NULL REFERENCES usuario(id),
  canal       text        NOT NULL CHECK (canal IN ('sms','email','app')),
  tipo        text        NOT NULL,                     -- ex.: 'diaria.aguardando_aprovacao'
  titulo      text        NOT NULL,
  corpo       text        NOT NULL,
  dados       jsonb       NOT NULL DEFAULT '{}',
  status      text        NOT NULL DEFAULT 'pendente' CHECK (status IN ('pendente','enviada','falhou','lida')),
  tentativas  smallint    NOT NULL DEFAULT 0,
  enviada_em  timestamptz,
  lida_em     timestamptz,
  criado_em   timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_notificacao_usuario  ON notificacao (usuario_id, criado_em DESC);
CREATE INDEX ix_notificacao_pendente ON notificacao (criado_em) WHERE status IN ('pendente','falhou');

CREATE TABLE denuncia (
  id               uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  denunciante_id   uuid        NOT NULL REFERENCES usuario(id),
  alvo_usuario_id  uuid        NOT NULL REFERENCES usuario(id),
  mensagem_id      uuid        REFERENCES mensagem(id),
  motivo           text        NOT NULL CHECK (motivo IN ('contato_fora','golpe','ofensa','perfil_falso','outro')),
  descricao        text        CHECK (char_length(descricao) <= 1000),
  status           text        NOT NULL DEFAULT 'aberta' CHECK (status IN ('aberta','em_analise','procedente','improcedente')),
  tratada_por      uuid        REFERENCES usuario(id),
  tratada_em       timestamptz,
  criado_em        timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_denuncia_pessoas CHECK (denunciante_id <> alvo_usuario_id),
  CONSTRAINT ck_denuncia_tratada CHECK (status IN ('aberta','em_analise') OR (tratada_por IS NOT NULL AND tratada_em IS NOT NULL))
);
CREATE INDEX ix_denuncia_fila ON denuncia (criado_em) WHERE status IN ('aberta','em_analise');
CREATE INDEX ix_denuncia_alvo ON denuncia (alvo_usuario_id);

-- configuracao (V1) passa a apontar para quem alterou
ALTER TABLE configuracao
  ADD CONSTRAINT fk_configuracao_criado_por FOREIGN KEY (criado_por) REFERENCES usuario(id);
