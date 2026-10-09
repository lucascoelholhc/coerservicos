-- =============================================================================
-- V15 (dia 7b): índice do contador de falhas por conta (CORE-04).
--
-- As consultas "erros da conta nas últimas 24 h" e "último erro da conta" filtram por usuario_id
-- e por faixa de criado_em; o ix_codigo_sms_usuario (V14) só tem usuario_id e lê todo o histórico
-- da conta. Os outros índices das contagens já existem e servem (conferido com EXPLAIN no
-- MigracaoV15Test): ix_codigo_sms_celular (celular, criado_em DESC), da V2, para os envios por
-- celular e a prova de posse; ix_token_verificacao_destino (destino, criado_em DESC), da V14, para
-- os e-mails por endereço. Só índice novo: nenhuma outra mudança de schema. CREATE INDEX comum
-- (tabelas pequenas; o Flyway roda a migração numa transação).
-- =============================================================================

CREATE INDEX ix_codigo_sms_usuario_criado ON codigo_sms (usuario_id, criado_em DESC);
