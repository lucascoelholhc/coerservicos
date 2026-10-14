-- =============================================================================
-- V16 (CORE-07, dia 11): baldes do rate limit e devolução da cota de envio.
-- =============================================================================
SET LOCAL lock_timeout = '5s';

-- -----------------------------------------------------------------------------
-- Baldes do Bucket4j (PostgreSQL, SELECT ... FOR UPDATE) dos endpoints sensíveis e dos limites
-- globais. Vale com várias instâncias sem infraestrutura nova. A chave nunca leva dado pessoal em
-- claro: login, celular e e-mail entram como HMAC; o IP entra como está (IPv6 agrupado por /64).
-- UNLOGGED: o estado é descartável (numa queda do banco os limites recomeçam do zero) e não vale o
-- custo de WAL a cada requisição. Num failover do RDS Multi-AZ ou na recuperação de uma queda a
-- tabela volta vazia: os limites zeram. Nenhuma defesa crítica fica só aqui (os limites por destino e
-- o contador de falhas estão em codigo_sms e token_verificacao, tabelas normais). Não é acessível em
-- réplica de leitura nem vai por replicação lógica.
-- O Bucket4j grava o vencimento em milissegundos desde a época (bigint) e apaga os vencidos em lotes
-- com FOR UPDATE SKIP LOCKED: várias instâncias podem limpar ao mesmo tempo, sem ShedLock.
-- O coe_app recebe SELECT/INSERT/UPDATE/DELETE pelos default privileges (V1).
-- -----------------------------------------------------------------------------
CREATE UNLOGGED TABLE limite_balde (
  chave      text COLLATE "C" PRIMARY KEY,   -- ASCII (prefixo, IP, base64url): comparação binária
  estado     bytea,                -- nulo entre o INSERT e o primeiro UPDATE do Bucket4j
  expira_em  bigint,               -- epoch em ms; nulo até o primeiro UPDATE
  CONSTRAINT ck_limite_balde_chave CHECK (char_length(chave) BETWEEN 1 AND 200)
)
-- Todo UPDATE do Bucket4j muda expira_em (coluna indexada, sem HOT): fillfactor e autovacuum mais
-- frequente limitam o inchaço. Índice parcial: o NULL só existe dentro da transação do Bucket4j.
WITH (fillfactor = 70, autovacuum_vacuum_scale_factor = 0.02);
CREATE INDEX ix_limite_balde_expira ON limite_balde (expira_em) WHERE expira_em IS NOT NULL;

COMMENT ON TABLE limite_balde IS 'CORE-07: estado dos baldes do rate limit (Bucket4j); chave sem dado pessoal em claro';

-- -----------------------------------------------------------------------------
-- Devolução da cota (CORE-07): mensagem descartada pela fila de envio cheia nunca chegou. A linha
-- ganha descartado_em (e fica invalidada: o código ou link não chegou) e sai das contagens da espera
-- de 60 s e do teto de 5 por hora. Marcado uma única vez. Sem exigir descartado_em >= criado_em:
-- o descarte roda em outra transação (às vezes outra instância); um relógio atrasado não pode
-- impedir a devolução da cota.
-- -----------------------------------------------------------------------------
ALTER TABLE codigo_sms ADD COLUMN descartado_em timestamptz;
ALTER TABLE codigo_sms ADD CONSTRAINT ck_codigo_sms_descarte
  CHECK (descartado_em IS NULL OR invalidado_em IS NOT NULL);
GRANT UPDATE (descartado_em) ON codigo_sms TO coe_app;

ALTER TABLE token_verificacao ADD COLUMN descartado_em timestamptz;
ALTER TABLE token_verificacao ADD CONSTRAINT ck_token_verificacao_descarte
  CHECK (descartado_em IS NULL OR invalidado_em IS NOT NULL);
GRANT UPDATE (descartado_em) ON token_verificacao TO coe_app;

-- Os gatilhos de conteúdo imutável (V14) passam a barrar também a mudança de um descarte já marcado.
CREATE OR REPLACE FUNCTION fn_codigo_sms_imutavel() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF (NEW.id, NEW.usuario_id, NEW.celular, NEW.finalidade, NEW.codigo_hmac, NEW.desafio_hash,
      NEW.expira_em, NEW.ip, NEW.criado_em)
     IS DISTINCT FROM
     (OLD.id, OLD.usuario_id, OLD.celular, OLD.finalidade, OLD.codigo_hmac, OLD.desafio_hash,
      OLD.expira_em, OLD.ip, OLD.criado_em)
     OR NEW.tentativas < OLD.tentativas
     OR (OLD.usado_em IS NOT NULL AND NEW.usado_em IS DISTINCT FROM OLD.usado_em)
     OR (OLD.invalidado_em IS NOT NULL AND NEW.invalidado_em IS DISTINCT FROM OLD.invalidado_em)
     OR (OLD.descartado_em IS NOT NULL AND NEW.descartado_em IS DISTINCT FROM OLD.descartado_em) THEN
    RAISE EXCEPTION 'Tabela codigo_sms: só tentativas (subindo) e uso podem mudar (e o descarte, uma vez)'
      USING ERRCODE = 'insufficient_privilege';
  END IF;
  RETURN NEW;
END $$;

CREATE OR REPLACE FUNCTION fn_token_verificacao_imutavel() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF (NEW.id, NEW.usuario_id, NEW.canal, NEW.destino, NEW.finalidade, NEW.token_hash,
      NEW.criado_em, NEW.expira_em)
     IS DISTINCT FROM
     (OLD.id, OLD.usuario_id, OLD.canal, OLD.destino, OLD.finalidade, OLD.token_hash,
      OLD.criado_em, OLD.expira_em)
     OR (OLD.usado_em IS NOT NULL AND NEW.usado_em IS DISTINCT FROM OLD.usado_em)
     OR (OLD.invalidado_em IS NOT NULL AND NEW.invalidado_em IS DISTINCT FROM OLD.invalidado_em)
     OR (OLD.descartado_em IS NOT NULL AND NEW.descartado_em IS DISTINCT FROM OLD.descartado_em) THEN
    RAISE EXCEPTION 'Tabela token_verificacao: só o uso pode mudar, uma única vez (e o descarte, uma vez)'
      USING ERRCODE = 'insufficient_privilege';
  END IF;
  RETURN NEW;
END $$;
