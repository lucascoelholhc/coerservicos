-- =============================================================================
-- COE Serviços · V22 — Ajustes da revisão de banco do DB-14 (V21)
-- 1. A escolha das outras diárias e a data dela andam juntas (a V21 só garantia um sentido).
-- 2. Uma falta por diária (além de uma por disputa).
-- =============================================================================

ALTER TABLE disputa ADD CONSTRAINT ck_disputa_demais_com_data CHECK (
  (demais_diarias IS NULL) = (demais_escolhidas_em IS NULL));

DROP INDEX ix_ocorrencia_diaria;
CREATE UNIQUE INDEX uq_ocorrencia_diaria ON ocorrencia_profissional (diaria_id);
