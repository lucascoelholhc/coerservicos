-- =============================================================================
-- COE Serviços · V1 — Base: extensões, funções utilitárias e configuração
-- Banco: coeservicos (PostgreSQL 16)
-- Convenções de todo o schema:
--   * PK uuid gen_random_uuid()          * dinheiro numeric(12,2), % numeric(5,4)
--   * instantes timestamptz (UTC)        * dia de serviço date
--   * status = text + CHECK              * criado_em / atualizado_em / versao
--   * CPF, chave Pix e endereço cifrados pela aplicação (bytea, AES-GCM)
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;   -- gen_random_uuid(), digest()
CREATE EXTENSION IF NOT EXISTS citext;     -- e-mail sem diferenciar maiúsculas
CREATE EXTENSION IF NOT EXISTS unaccent;   -- busca sem acento
CREATE EXTENSION IF NOT EXISTS pg_trgm;    -- busca por trecho de nome

-- -----------------------------------------------------------------------------
-- Papel da aplicação. O Flyway roda com o usuário DONO do schema; a aplicação
-- conecta com um login membro de coe_app, que não é dono: não altera tabela,
-- não desliga trigger e não faz UPDATE/DELETE/TRUNCATE nas tabelas só de inserção.
-- Criar papel exige CREATEROLE: se o usuário do Flyway não tiver, um admin roda
-- uma vez  CREATE ROLE coe_app NOLOGIN;  antes da primeira migração.
-- -----------------------------------------------------------------------------
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'coe_app') THEN
    CREATE ROLE coe_app NOLOGIN;
  END IF;
END $$;

REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO coe_app;
-- Vale para tudo que o dono criar daqui em diante (V1..Vn); as tabelas só de
-- inserção retiram UPDATE/DELETE/TRUNCATE logo após serem criadas.
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO coe_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO coe_app;

-- Texto normalizado (minúsculo e sem acento) — IMMUTABLE para poder indexar
CREATE OR REPLACE FUNCTION f_sem_acento(texto text) RETURNS text
LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT
AS $$ SELECT public.unaccent('public.unaccent'::regdictionary, lower(texto)) $$;

-- Atualiza atualizado_em em todo UPDATE (a coluna versao é controlada pelo JPA @Version)
CREATE OR REPLACE FUNCTION fn_toca_registro() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  NEW.atualizado_em := now();
  RETURN NEW;
END $$;

-- Tabelas só de inserção (ledger, auditoria, histórico): barra UPDATE e DELETE
-- (gatilho por linha) e TRUNCATE (gatilho por comando, que também pega o CASCADE)
CREATE OR REPLACE FUNCTION fn_somente_insercao() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'Tabela % aceita apenas INSERT (operação % negada)', TG_TABLE_NAME, TG_OP
    USING ERRCODE = 'insufficient_privilege';
END $$;

-- -----------------------------------------------------------------------------
-- configuracao — parâmetros de negócio com vigência (o admin muda sem deploy).
-- Um novo valor é um novo registro; o histórico fica.
-- -----------------------------------------------------------------------------
CREATE TABLE configuracao (
  id             uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  chave          text        NOT NULL CHECK (chave ~ '^[A-Z0-9_]{2,60}$'),
  valor          text        NOT NULL,
  tipo           text        NOT NULL CHECK (tipo IN ('decimal','inteiro','texto','booleano')),
  descricao      text        NOT NULL,
  vigente_desde  timestamptz NOT NULL DEFAULT now(),
  criado_por     uuid,                      -- FK para usuario adicionada na V9
  criado_em      timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT uq_configuracao_chave_vigencia UNIQUE (chave, vigente_desde)
);

CREATE TRIGGER trg_configuracao_somente_insercao
  BEFORE UPDATE OR DELETE ON configuracao
  FOR EACH ROW EXECUTE FUNCTION fn_somente_insercao();
CREATE TRIGGER trg_configuracao_sem_truncate
  BEFORE TRUNCATE ON configuracao
  FOR EACH STATEMENT EXECUTE FUNCTION fn_somente_insercao();
REVOKE UPDATE, DELETE, TRUNCATE ON configuracao FROM coe_app;

-- Valor em vigor de cada chave
CREATE VIEW configuracao_vigente WITH (security_invoker = true) AS
SELECT DISTINCT ON (chave) chave, valor, tipo, descricao, vigente_desde
FROM configuracao
WHERE vigente_desde <= now()
ORDER BY chave, vigente_desde DESC;

INSERT INTO configuracao (chave, valor, tipo, descricao, vigente_desde) VALUES
 ('COMISSAO',                 '0.10',    'decimal',  'Comissão da COE sobre cada diária',                         '2026-01-01'),
 ('TAXA_PAGA_POR',            'cliente', 'texto',    'Quem paga a comissão: cliente (somada) ou profissional (descontada) — PA05', '2026-01-01'),
 ('AUTO_LIBERA_HORAS',        '12',      'inteiro',  'Horas sem resposta do cliente até a liberação automática', '2026-01-01'),
 ('PRAZO_DISPUTA_HORAS',      '48',      'inteiro',  'Prazo da equipe para decidir uma disputa',                  '2026-01-01'),
 ('LIMITE_DOMESTICO_SEMANA',  '2',       'inteiro',  'LC 150/2015: diárias por semana, mesma diarista e cliente', '2026-01-01'),
 ('TENTATIVAS_ANTES_ANALISE', '3',       'inteiro',  'Tentativas de passar contato antes de a conta ir para análise', '2026-01-01'),
 ('MEIA_DIARIA_HORAS',        '4',       'inteiro',  'Duração máxima da meia diária — PA10',                      '2026-01-01'),
 ('PAGAMENTO_EXPIRA_MINUTOS', '60',      'inteiro',  'Tempo para pagar antes de o pedido expirar',               '2026-01-01'),
 ('VERSAO_TERMOS',            '1.0',     'texto',    'Versão vigente dos termos de uso e privacidade',            '2026-01-01');
