-- =============================================================================
-- COE Serviços · V28 — Reativar a conta zera as tentativas de contato (RN44g, 11/11)
--
-- As tentativas (tentativa_contato, só inserção) nunca são apagadas: a contagem
-- para mandar a conta para análise passa a valer a partir de tentativas_desde,
-- que a reativação pelo admin preenche (o histórico fica na tabela e na auditoria).
-- =============================================================================

ALTER TABLE usuario ADD COLUMN tentativas_desde timestamptz;
