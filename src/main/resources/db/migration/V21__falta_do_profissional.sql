-- =============================================================================
-- COE Serviços · V21 — Falta do profissional (DB-14; PA07, RN44a–RN44e)
--
-- 1. ocorrencia_profissional: toda falta confirmada (disputa "nao_compareceu"
--    decidida a favor do cliente) vira uma linha, só de inserção, retida mesmo
--    se a conta for excluída (a exclusão anonimiza, nunca apaga o profissional).
-- 2. Parâmetros do alerta do painel (RN44d): FALTAS_ALERTA faltas em
--    FALTAS_JANELA_DIAS dias. Nunca fixos no código.
-- 3. disputa.demais_diarias: a escolha do cliente sobre as outras diárias do
--    contrato depois de uma falta confirmada (RN44b): manter ou cancelar.
-- =============================================================================

CREATE TABLE ocorrencia_profissional (
  id               uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  profissional_id  uuid        NOT NULL REFERENCES profissional(id) ON DELETE RESTRICT,
  tipo             text        NOT NULL CHECK (tipo IN ('falta')),
  diaria_id        uuid        NOT NULL REFERENCES diaria(id) ON DELETE RESTRICT,
  disputa_id       uuid        NOT NULL REFERENCES disputa(id) ON DELETE RESTRICT,
  registrada_por   uuid        NOT NULL REFERENCES usuario(id) ON DELETE RESTRICT,
  criado_em        timestamptz NOT NULL DEFAULT now(),
  -- uma falta por disputa (a decisão é uma só)
  CONSTRAINT uq_ocorrencia_disputa UNIQUE (disputa_id)
);
-- o alerta do painel conta as faltas recentes de cada profissional
CREATE INDEX ix_ocorrencia_profissional ON ocorrencia_profissional (profissional_id, criado_em DESC);
CREATE INDEX ix_ocorrencia_diaria ON ocorrencia_profissional (diaria_id);
CREATE INDEX ix_ocorrencia_registrada_por ON ocorrencia_profissional (registrada_por);

CREATE TRIGGER trg_ocorrencia_somente_insercao
  BEFORE UPDATE OR DELETE ON ocorrencia_profissional
  FOR EACH ROW EXECUTE FUNCTION fn_somente_insercao();
CREATE TRIGGER trg_ocorrencia_sem_truncate
  BEFORE TRUNCATE ON ocorrencia_profissional
  FOR EACH STATEMENT EXECUTE FUNCTION fn_somente_insercao();
REVOKE UPDATE, DELETE, TRUNCATE ON ocorrencia_profissional FROM coe_app;

INSERT INTO configuracao (chave, valor, tipo, descricao, vigente_desde) VALUES
 ('FALTAS_ALERTA',      '2',  'inteiro', 'Quantas faltas na janela acendem o alerta do profissional no painel (RN44d)', '2026-01-01'),
 ('FALTAS_JANELA_DIAS', '90', 'inteiro', 'Janela, em dias, para contar as faltas do alerta (RN44d)',                   '2026-01-01');

ALTER TABLE configuracao ADD CONSTRAINT ck_configuracao_faltas CHECK (
  CASE
    WHEN chave NOT IN ('FALTAS_ALERTA','FALTAS_JANELA_DIAS') THEN true
    WHEN valor ~ '^[0-9]{1,4}$' THEN valor::int > 0
    ELSE false
  END);

ALTER TABLE disputa
  ADD COLUMN demais_diarias        text CHECK (demais_diarias IN ('manter','cancelar')),
  ADD COLUMN demais_escolhidas_em  timestamptz;

ALTER TABLE disputa ADD CONSTRAINT ck_disputa_demais_diarias CHECK (
  demais_diarias IS NULL
  OR (status = 'favor_cliente' AND motivo = 'nao_compareceu' AND demais_escolhidas_em IS NOT NULL));
