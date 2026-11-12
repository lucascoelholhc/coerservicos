-- =============================================================================
-- COE Serviços · V27 — Ajustes da revisão de banco do PA02 (conta única)
--
-- 1. Um repasse leva uma diária só (o envio e a confirmação contam com isso).
-- 2. Alertas e devoluções não são apagados pela aplicação (histórico do dinheiro):
--    o papel coe_app perde DELETE e TRUNCATE nas duas tabelas.
-- =============================================================================

ALTER TABLE repasse_diaria ADD CONSTRAINT uq_repasse_diaria_repasse UNIQUE (repasse_id);

REVOKE DELETE, TRUNCATE ON alerta_financeiro FROM coe_app;
REVOKE DELETE, TRUNCATE ON devolucao FROM coe_app;
