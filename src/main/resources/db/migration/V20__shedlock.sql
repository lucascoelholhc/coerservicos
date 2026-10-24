-- =============================================================================
-- COE Serviços · V20 — Trava dos jobs entre instâncias (ShedLock, DOM-08)
-- Uma linha por job: quem segura a trava e até quando. A liberação automática das diárias roda numa instância
-- por vez; a idempotência continua no banco (status + uq_transacao_diaria_destino). Formato da tabela pedido
-- pela biblioteca (net.javacrumbs.shedlock), com o horário do próprio banco (usingDbTime).
-- =============================================================================

CREATE TABLE shedlock (
  name        varchar(64)  NOT NULL PRIMARY KEY,
  lock_until  timestamp    NOT NULL,
  locked_at   timestamp    NOT NULL,
  locked_by   varchar(255) NOT NULL
);
