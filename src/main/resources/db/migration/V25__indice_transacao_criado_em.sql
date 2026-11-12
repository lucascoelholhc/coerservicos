-- =============================================================================
-- COE Serviços · V25 — Índice do período no livro-caixa (DOM-11 parte 2b)
--
-- O financeiro do admin soma as transações de um período (até um ano) pelo
-- horário em que foram gravadas; sem índice, cada consulta varria a tabela.
-- Tabela pequena no lançamento: CREATE INDEX comum (sem CONCURRENTLY, que não
-- roda dentro da transação do Flyway). Com volume, criar CONCURRENTLY à parte.
-- =============================================================================

CREATE INDEX ix_transacao_criado_em ON transacao_financeira (criado_em);
