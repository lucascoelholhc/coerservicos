-- =============================================================================
-- COE Serviços · V29 — Foto nova de perfil já aprovado espera o admin (RN15a, RN57, 11/11)
--
-- Com o perfil ativo, a foto nova nasce oculta (visivel = false) e pendente; o
-- admin aprova (aparece na busca) ou recusa (continua oculta). Documento novo usa
-- o status pendente que já existe em documento_verificacao.
-- =============================================================================

ALTER TABLE foto_portfolio ADD COLUMN pendente boolean NOT NULL DEFAULT false;
CREATE INDEX ix_foto_portfolio_pendente ON foto_portfolio (profissional_id) WHERE pendente;
