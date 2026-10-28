-- =============================================================================
-- COE Serviços · V23 — Suspensão do profissional pelo admin (DOM-11; RN44e)
-- =============================================================================
-- Inativar é sempre manual e reversível: status 'suspenso' com o motivo escrito pelo admin e a data. A
-- reativação limpa os dois. O motivo é texto livre do admin: fica na linha, nunca na auditoria.
-- Nenhuma linha estava 'suspenso' antes desta versão (nenhum fluxo suspendia).

ALTER TABLE profissional
  ADD COLUMN motivo_suspensao text CHECK (char_length(btrim(motivo_suspensao)) BETWEEN 10 AND 300),
  ADD COLUMN suspenso_em      timestamptz;

ALTER TABLE profissional ADD CONSTRAINT ck_profissional_suspenso
  CHECK ((status = 'suspenso') = (motivo_suspensao IS NOT NULL AND suspenso_em IS NOT NULL));
