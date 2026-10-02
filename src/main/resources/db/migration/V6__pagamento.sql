-- =============================================================================
-- COE Serviços · V6 — Pagamento, custódia e livro-razão (ledger)
--
-- Ledger de partidas dobradas: cada transacao_financeira tem lançamentos cuja
-- soma de débitos = soma de créditos, conferida no COMMIT (transação vazia
-- também é barrada). Lançamento só entra na mesma transação do banco em que a
-- transacao_financeira foi criada: depois do COMMIT ela está fechada.
-- Lançamento nunca é alterado nem apagado; correção = nova transação de ajuste/estorno.
-- Saldo de uma conta = créditos − débitos (view saldo_conta).
--
-- Fluxos (exemplo: diária R$ 280, comissão 10% paga pelo cliente = R$ 28):
--   pagamento confirmado  D gateway 308      C custodia 308
--   liberação da diária   D custodia 308     C profissional 280 + C receita_coe 28
--   repasse Pix           D profissional 280 C gateway 280
--   reembolso (PA06: devolve tudo)
--                         D custodia 308     C cliente 308
--   estorno enviado       D cliente 308      C gateway 308
-- =============================================================================

CREATE TABLE cobranca (
  id              uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
  contrato_id     uuid          NOT NULL REFERENCES contrato(id),
  gateway         text          NOT NULL,                    -- 'falso' no local; PA02 decide o real
  id_externo      text,                                      -- id da cobrança no gateway
  metodo          text          NOT NULL CHECK (metodo IN ('pix','cartao')),
  valor           numeric(12,2) NOT NULL CHECK (valor > 0),
  status          text          NOT NULL DEFAULT 'pendente'
                  CHECK (status IN ('pendente','confirmada','falhou','expirada','estornada')),
  pix_copia_cola  text,
  expira_em       timestamptz,
  confirmada_em   timestamptz,
  motivo_falha    text,
  criado_em       timestamptz   NOT NULL DEFAULT now(),
  atualizado_em   timestamptz   NOT NULL DEFAULT now(),
  versao          bigint        NOT NULL DEFAULT 0,
  CONSTRAINT uq_cobranca_externo UNIQUE (gateway, id_externo),
  CONSTRAINT ck_cobranca_confirmada CHECK (status <> 'confirmada' OR confirmada_em IS NOT NULL)
);
CREATE TRIGGER trg_cobranca_toca BEFORE UPDATE ON cobranca
  FOR EACH ROW EXECUTE FUNCTION fn_toca_registro();
CREATE INDEX ix_cobranca_contrato ON cobranca (contrato_id);
-- no máximo uma cobrança confirmada por contrato
CREATE UNIQUE INDEX uq_cobranca_confirmada ON cobranca (contrato_id) WHERE status = 'confirmada';

-- Webhooks recebidos: idempotência pelo id do evento (RNF06)
CREATE TABLE evento_gateway (
  id                 uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  gateway            text        NOT NULL,
  id_evento          text        NOT NULL,
  tipo               text        NOT NULL,
  payload            jsonb       NOT NULL,
  assinatura_valida  boolean     NOT NULL,
  recebido_em        timestamptz NOT NULL DEFAULT now(),
  processado_em      timestamptz,
  erro               text,
  CONSTRAINT uq_evento_gateway UNIQUE (gateway, id_evento)
);
CREATE INDEX ix_evento_nao_processado ON evento_gateway (recebido_em) WHERE processado_em IS NULL;

-- Contas do livro-razão
CREATE TABLE conta_razao (
  id               uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  tipo             text        NOT NULL CHECK (tipo IN ('gateway','custodia','receita_coe','profissional','cliente')),
  profissional_id  uuid        REFERENCES profissional(id),
  usuario_id       uuid        REFERENCES usuario(id),
  nome             text        NOT NULL,
  criado_em        timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT uq_conta_razao UNIQUE NULLS NOT DISTINCT (tipo, profissional_id, usuario_id),
  CONSTRAINT ck_conta_dono CHECK (
       (tipo IN ('gateway','custodia','receita_coe') AND profissional_id IS NULL AND usuario_id IS NULL)
    OR (tipo = 'profissional' AND profissional_id IS NOT NULL AND usuario_id IS NULL)
    OR (tipo = 'cliente'      AND usuario_id IS NOT NULL AND profissional_id IS NULL))
);

INSERT INTO conta_razao (tipo, nome) VALUES
 ('gateway',     'Dinheiro no gateway'),
 ('custodia',    'Custódia COE (dinheiro guardado dos clientes)'),
 ('receita_coe', 'Receita de comissão da COE');

CREATE TABLE transacao_financeira (
  id                  uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  tipo                text        NOT NULL CHECK (tipo IN ('pagamento','liberacao','repasse','reembolso','estorno','ajuste')),
  chave_idempotencia  text        NOT NULL,     -- ex.: 'liberacao:<diaria_id>' — impede liberar duas vezes
  contrato_id         uuid        REFERENCES contrato(id),
  diaria_id           uuid        REFERENCES diaria(id),
  cobranca_id         uuid        REFERENCES cobranca(id),
  descricao           text        NOT NULL,
  criado_por          uuid        REFERENCES usuario(id),     -- nulo = sistema
  xid_criacao         xid8        NOT NULL DEFAULT pg_current_xact_id(),  -- transação do banco que a abriu
  criado_em           timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT uq_transacao_idempotencia UNIQUE (chave_idempotencia)
);
CREATE INDEX ix_transacao_contrato ON transacao_financeira (contrato_id);
CREATE INDEX ix_transacao_diaria   ON transacao_financeira (diaria_id);
CREATE TRIGGER trg_transacao_somente_insercao
  BEFORE UPDATE OR DELETE ON transacao_financeira
  FOR EACH ROW EXECUTE FUNCTION fn_somente_insercao();
CREATE TRIGGER trg_transacao_financeira_sem_truncate
  BEFORE TRUNCATE ON transacao_financeira
  FOR EACH STATEMENT EXECUTE FUNCTION fn_somente_insercao();
REVOKE UPDATE, DELETE, TRUNCATE ON transacao_financeira FROM coe_app;

-- xid_criacao não aceita valor vindo de fora
CREATE OR REPLACE FUNCTION fn_carimba_xid_transacao() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  NEW.xid_criacao := pg_current_xact_id();
  RETURN NEW;
END $$;

CREATE TRIGGER trg_transacao_carimba_xid
  BEFORE INSERT ON transacao_financeira
  FOR EACH ROW EXECUTE FUNCTION fn_carimba_xid_transacao();

CREATE TABLE lancamento (
  id            bigint        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  transacao_id  uuid          NOT NULL REFERENCES transacao_financeira(id),
  conta_id      uuid          NOT NULL REFERENCES conta_razao(id),
  natureza      char(1)       NOT NULL CHECK (natureza IN ('D','C')),
  valor         numeric(12,2) NOT NULL CHECK (valor > 0),
  criado_em     timestamptz   NOT NULL DEFAULT now()
);
CREATE INDEX ix_lancamento_transacao ON lancamento (transacao_id);
CREATE INDEX ix_lancamento_conta     ON lancamento (conta_id, criado_em);
CREATE TRIGGER trg_lancamento_somente_insercao
  BEFORE UPDATE OR DELETE ON lancamento
  FOR EACH ROW EXECUTE FUNCTION fn_somente_insercao();
CREATE TRIGGER trg_lancamento_sem_truncate
  BEFORE TRUNCATE ON lancamento
  FOR EACH STATEMENT EXECUTE FUNCTION fn_somente_insercao();
REVOKE UPDATE, DELETE, TRUNCATE ON lancamento FROM coe_app;

-- Transação fechada: lançamento só entra na transação do banco que criou a transacao_financeira
CREATE OR REPLACE FUNCTION fn_lancamento_transacao_aberta() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
  v_xid xid8;
BEGIN
  SELECT xid_criacao INTO v_xid FROM transacao_financeira WHERE id = NEW.transacao_id;
  IF FOUND AND v_xid <> pg_current_xact_id() THEN
    RAISE EXCEPTION 'Transação % já fechada: lançamentos só entram junto com a criação dela', NEW.transacao_id
      USING ERRCODE = 'check_violation';
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER trg_lancamento_transacao_aberta
  BEFORE INSERT ON lancamento
  FOR EACH ROW EXECUTE FUNCTION fn_lancamento_transacao_aberta();

-- Partidas dobradas: no COMMIT, cada transação tem lançamentos e débitos = créditos.
-- Uma checagem por transacao_financeira (não por lançamento).
CREATE OR REPLACE FUNCTION fn_confere_partidas() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
  v_debito  numeric;
  v_credito numeric;
BEGIN
  SELECT COALESCE(SUM(valor) FILTER (WHERE natureza = 'D'), 0),
         COALESCE(SUM(valor) FILTER (WHERE natureza = 'C'), 0)
    INTO v_debito, v_credito
    FROM lancamento WHERE transacao_id = NEW.id;
  IF v_debito = 0 AND v_credito = 0 THEN
    RAISE EXCEPTION 'Transação % sem lançamentos', NEW.id
      USING ERRCODE = 'check_violation';
  END IF;
  IF v_debito <> v_credito THEN
    RAISE EXCEPTION 'Transação % desbalanceada: débitos % ≠ créditos %', NEW.id, v_debito, v_credito
      USING ERRCODE = 'check_violation';
  END IF;
  RETURN NULL;
END $$;

CREATE CONSTRAINT TRIGGER trg_transacao_partidas
  AFTER INSERT ON transacao_financeira
  DEFERRABLE INITIALLY DEFERRED
  FOR EACH ROW EXECUTE FUNCTION fn_confere_partidas();

CREATE VIEW saldo_conta WITH (security_invoker = true) AS
SELECT c.id AS conta_id, c.tipo, c.profissional_id, c.usuario_id, c.nome,
       COALESCE(SUM(l.valor) FILTER (WHERE l.natureza = 'C'), 0)
     - COALESCE(SUM(l.valor) FILTER (WHERE l.natureza = 'D'), 0) AS saldo
FROM conta_razao c
LEFT JOIN lancamento l ON l.conta_id = c.id
GROUP BY c.id;

-- Repasse ao profissional por Pix
CREATE TABLE repasse (
  id                 uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
  profissional_id    uuid          NOT NULL REFERENCES profissional(id),
  transacao_id       uuid          REFERENCES transacao_financeira(id),
  valor              numeric(12,2) NOT NULL CHECK (valor > 0),
  chave_pix_tipo     text          NOT NULL CHECK (chave_pix_tipo IN ('cpf','celular','email','aleatoria')),
  chave_pix_cifrada  bytea         NOT NULL,     -- cópia da chave no momento do envio
  status             text          NOT NULL DEFAULT 'pendente'
                     CHECK (status IN ('pendente','enviado','confirmado','falhou')),
  id_externo         text,
  tentativas         smallint      NOT NULL DEFAULT 0,
  erro               text,
  enviado_em         timestamptz,
  confirmado_em      timestamptz,
  criado_em          timestamptz   NOT NULL DEFAULT now(),
  atualizado_em      timestamptz   NOT NULL DEFAULT now(),
  versao             bigint        NOT NULL DEFAULT 0,
  CONSTRAINT ck_repasse_confirmado CHECK (status <> 'confirmado' OR (confirmado_em IS NOT NULL AND transacao_id IS NOT NULL))
);
CREATE TRIGGER trg_repasse_toca BEFORE UPDATE ON repasse
  FOR EACH ROW EXECUTE FUNCTION fn_toca_registro();
CREATE INDEX ix_repasse_profissional ON repasse (profissional_id, criado_em DESC);
CREATE INDEX ix_repasse_pendente ON repasse (criado_em) WHERE status IN ('pendente','falhou');

-- Quais diárias compõem cada repasse (cada diária entra em um só repasse)
CREATE TABLE repasse_diaria (
  repasse_id uuid NOT NULL REFERENCES repasse(id),
  diaria_id  uuid NOT NULL REFERENCES diaria(id),
  PRIMARY KEY (repasse_id, diaria_id),
  CONSTRAINT uq_repasse_diaria UNIQUE (diaria_id)
);

-- Reembolso de uma diária ao cliente (PA06 decidido: devolve diária + comissão)
CREATE TABLE reembolso (
  id              uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
  diaria_id       uuid          NOT NULL REFERENCES diaria(id),
  cobranca_id     uuid          NOT NULL REFERENCES cobranca(id),
  transacao_id    uuid          REFERENCES transacao_financeira(id),
  motivo          text          NOT NULL CHECK (motivo IN ('disputa','falta_profissional','cancelamento','outro')),
  valor_diaria    numeric(12,2) NOT NULL CHECK (valor_diaria > 0),
  valor_comissao  numeric(12,2) NOT NULL CHECK (valor_comissao >= 0),
  valor_total     numeric(12,2) NOT NULL,
  status          text          NOT NULL DEFAULT 'pendente'
                  CHECK (status IN ('pendente','enviado','confirmado','falhou')),
  id_externo      text,
  solicitado_por  uuid          REFERENCES usuario(id),
  erro            text,
  criado_em       timestamptz   NOT NULL DEFAULT now(),
  concluido_em    timestamptz,
  CONSTRAINT uq_reembolso_diaria UNIQUE (diaria_id),
  CONSTRAINT ck_reembolso_total CHECK (valor_total = valor_diaria + valor_comissao)
);
CREATE INDEX ix_reembolso_cobranca ON reembolso (cobranca_id);
