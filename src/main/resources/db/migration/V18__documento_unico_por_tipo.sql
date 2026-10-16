-- =============================================================================
-- COE Serviços · V18 — Um documento de cada tipo por profissional (revisão do DOM-02)
-- O serviço já substitui o documento do mesmo tipo; o banco garante também (dois envios ao mesmo tempo).
-- O índice único cobre as buscas por profissional_id (coluna da esquerda): o índice simples sai.
-- Nenhum ambiente fora das máquinas de desenvolvimento tem cadastro (V11); num banco local com repetidos,
-- fica o documento mais novo de cada tipo (o arquivo do antigo continua no bucket local, sem uso).
-- =============================================================================

DELETE FROM documento_verificacao antigo
 USING documento_verificacao novo
 WHERE antigo.profissional_id = novo.profissional_id
   AND antigo.tipo = novo.tipo
   AND (antigo.criado_em, antigo.id) < (novo.criado_em, novo.id);

ALTER TABLE documento_verificacao
    ADD CONSTRAINT uq_documento_profissional_tipo UNIQUE (profissional_id, tipo);

DROP INDEX ix_documento_profissional;
