-- =============================================================================
-- COE Serviços · V13 — Teto absoluto da sessão (DB-15, PA03): 90 dias desde o login.
-- V1–V12 não são editadas.
--
-- - refresh_token.sessao_iniciada_em: gravado no login e copiado sem mudança para cada
--   sucessor. Não se usa min(criado_em) em tempo de execução: o expurgo futuro dos tokens
--   vencidos poderia apagar a primeira linha da família e esticar a sessão.
-- - Imutável (gatilho) e amarrado ao sucessor pela FK composta: o banco recusa sucessor
--   com outro início.
-- - Motivo de revogação 'teto'. O prazo (90 dias) fica na configuração da aplicação.
--
-- Premissa (como na V11/V12): antes de existir produção; as linhas que existem (perfil
-- local) recebem o min(criado_em) da família.
-- =============================================================================

SET LOCAL lock_timeout = '5s';

-- 1. Coluna e preenchimento. O gatilho da V12 ainda não conhece a coluna, então o UPDATE passa.
ALTER TABLE refresh_token ADD COLUMN sessao_iniciada_em timestamptz;

UPDATE refresh_token r
   SET sessao_iniciada_em = f.inicio
  FROM (SELECT familia_id, min(criado_em) AS inicio FROM refresh_token GROUP BY familia_id) f
 WHERE r.familia_id = f.familia_id;

ALTER TABLE refresh_token ALTER COLUMN sessao_iniciada_em SET NOT NULL;
ALTER TABLE refresh_token ADD CONSTRAINT ck_refresh_token_sessao CHECK (sessao_iniciada_em <= criado_em);

-- 2. Início da sessão imutável, como o resto do conteúdo do token.
CREATE OR REPLACE FUNCTION fn_refresh_token_imutavel() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF (NEW.id, NEW.usuario_id, NEW.familia_id, NEW.token_hash, NEW.aparelho, NEW.ip,
      NEW.criado_em, NEW.expira_em, NEW.sessao_iniciada_em)
     IS DISTINCT FROM
     (OLD.id, OLD.usuario_id, OLD.familia_id, OLD.token_hash, OLD.aparelho, OLD.ip,
      OLD.criado_em, OLD.expira_em, OLD.sessao_iniciada_em)
     OR (OLD.usado_em IS NOT NULL AND NEW.usado_em IS DISTINCT FROM OLD.usado_em)
     OR (OLD.revogado_em IS NOT NULL
         AND (NEW.revogado_em, NEW.motivo_revogacao) IS DISTINCT FROM (OLD.revogado_em, OLD.motivo_revogacao))
     OR (OLD.substituido_por IS NOT NULL AND NEW.substituido_por IS DISTINCT FROM OLD.substituido_por) THEN
    RAISE EXCEPTION 'Tabela refresh_token: só uso, revogação e sucessor podem mudar, uma única vez'
      USING ERRCODE = 'insufficient_privilege';
  END IF;
  RETURN NEW;
END $$;

-- 3. Sucessor com o mesmo início: entra na FK composta (o índice do UNIQUE do sucessor,
--    que começa por substituido_por, continua servindo à FK).
ALTER TABLE refresh_token DROP CONSTRAINT fk_refresh_token_sucessor;
ALTER TABLE refresh_token DROP CONSTRAINT uq_refresh_token_identidade;
ALTER TABLE refresh_token ADD CONSTRAINT uq_refresh_token_identidade
  UNIQUE (id, familia_id, usuario_id, sessao_iniciada_em);
ALTER TABLE refresh_token ADD CONSTRAINT fk_refresh_token_sucessor
  FOREIGN KEY (substituido_por, familia_id, usuario_id, sessao_iniciada_em)
  REFERENCES refresh_token (id, familia_id, usuario_id, sessao_iniciada_em);

-- 4. Motivo 'teto' (sessão chegou ao limite absoluto).
ALTER TABLE refresh_token DROP CONSTRAINT ck_refresh_token_motivo;
ALTER TABLE refresh_token ADD CONSTRAINT ck_refresh_token_motivo
  CHECK (motivo_revogacao IN ('logout','sair_todos','troca_senha','reuso','admin','teto'));
