-- =============================================================================
-- COE Serviços · V26 — PA02 conta única (decisões de 11/11, RN34a–RN34e, RN15)
--
-- O dinheiro entra na conta Asaas da COE; a COE repassa por Pix na liberação de
-- cada diária, estorna o que precisa voltar e concilia o saldo todo dia.
--   recebido sem pedido   D gateway       C a_devolver   (sem_pedido)
--   devolvido             D a_devolver    C gateway      (devolucao)
--   taxa do gateway       D despesa_coe   C gateway      (taxa)
-- Contas novas são da COE (sem dono). A chave Pix só sai depois da conferência do
-- admin (RN15); trocar a chave desfaz a conferência (gatilho abaixo).
-- =============================================================================

-- 1. Contas e tipos de transação novos
ALTER TABLE conta_razao DROP CONSTRAINT conta_razao_tipo_check;
ALTER TABLE conta_razao ADD CONSTRAINT conta_razao_tipo_check
  CHECK (tipo IN ('gateway','custodia','receita_coe','profissional','cliente','a_devolver','despesa_coe'));
ALTER TABLE conta_razao DROP CONSTRAINT ck_conta_dono;
ALTER TABLE conta_razao ADD CONSTRAINT ck_conta_dono CHECK (
     (tipo IN ('gateway','custodia','receita_coe','a_devolver','despesa_coe')
        AND profissional_id IS NULL AND usuario_id IS NULL)
  OR (tipo = 'profissional' AND profissional_id IS NOT NULL AND usuario_id IS NULL)
  OR (tipo = 'cliente'      AND usuario_id IS NOT NULL AND profissional_id IS NULL));

INSERT INTO conta_razao (tipo, nome) VALUES
 ('a_devolver',  'Dinheiro recebido sem pedido, a devolver ao pagador'),
 ('despesa_coe', 'Taxas do gateway (despesa da COE)');

ALTER TABLE transacao_financeira DROP CONSTRAINT transacao_financeira_tipo_check;
ALTER TABLE transacao_financeira ADD CONSTRAINT transacao_financeira_tipo_check
  CHECK (tipo IN ('pagamento','liberacao','repasse','reembolso','estorno','ajuste','taxa','sem_pedido','devolucao'));

-- 2. Conferência da chave Pix pelo admin (RN15); trocar a chave desfaz a conferência
ALTER TABLE profissional
  ADD COLUMN pix_conferido_em  timestamptz,
  ADD COLUMN pix_conferido_por uuid REFERENCES usuario(id);
ALTER TABLE profissional ADD CONSTRAINT ck_profissional_pix_conferido
  CHECK ((pix_conferido_em IS NULL) = (pix_conferido_por IS NULL));
CREATE INDEX ix_profissional_pix_conferido_por ON profissional (pix_conferido_por);

CREATE OR REPLACE FUNCTION fn_pix_trocado_desfaz_conferencia() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF NEW.chave_pix_cifrada IS DISTINCT FROM OLD.chave_pix_cifrada
     OR NEW.chave_pix_tipo IS DISTINCT FROM OLD.chave_pix_tipo THEN
    NEW.pix_conferido_em  := NULL;
    NEW.pix_conferido_por := NULL;
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER trg_profissional_pix_conferencia
  BEFORE UPDATE OF chave_pix_cifrada, chave_pix_tipo ON profissional
  FOR EACH ROW EXECUTE FUNCTION fn_pix_trocado_desfaz_conferencia();

-- 3. Estorno do reembolso no gateway (RN34b): tentativas e envio
ALTER TABLE reembolso
  ADD COLUMN tentativas smallint    NOT NULL DEFAULT 0,
  ADD COLUMN enviado_em timestamptz;
CREATE INDEX ix_reembolso_pendente ON reembolso (criado_em) WHERE status IN ('pendente','falhou');

-- 4. Dinheiro recebido sem pedido: devolução integral (RN34c)
CREATE TABLE devolucao (
  id             uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
  cobranca_id    uuid          NOT NULL REFERENCES cobranca(id),
  transacao_id   uuid          NOT NULL REFERENCES transacao_financeira(id),
  valor          numeric(12,2) NOT NULL CHECK (valor > 0),
  motivo         text          NOT NULL CHECK (motivo ~ '^[a-z-]{1,40}$'),
  status         text          NOT NULL DEFAULT 'pendente'
                 CHECK (status IN ('pendente','enviado','confirmado','falhou')),
  id_externo     text,
  tentativas     smallint      NOT NULL DEFAULT 0,
  erro           text,
  enviado_em     timestamptz,
  confirmado_em  timestamptz,
  criado_em      timestamptz   NOT NULL DEFAULT now(),
  atualizado_em  timestamptz   NOT NULL DEFAULT now(),
  versao         bigint        NOT NULL DEFAULT 0,
  -- uma devolução integral por cobrança paga sem pedido
  CONSTRAINT uq_devolucao_cobranca UNIQUE (cobranca_id),
  CONSTRAINT ck_devolucao_confirmada CHECK (status <> 'confirmado' OR confirmado_em IS NOT NULL)
);
CREATE TRIGGER trg_devolucao_toca BEFORE UPDATE ON devolucao
  FOR EACH ROW EXECUTE FUNCTION fn_toca_registro();
CREATE INDEX ix_devolucao_transacao ON devolucao (transacao_id);
CREATE INDEX ix_devolucao_pendente  ON devolucao (criado_em) WHERE status IN ('pendente','falhou');
CREATE UNIQUE INDEX uq_devolucao_externo ON devolucao (id_externo) WHERE id_externo IS NOT NULL;

-- 5. Alertas do financeiro no painel do admin (repasse com falha, conciliação, dinheiro sem pedido)
CREATE TABLE alerta_financeiro (
  id             uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  tipo           text        NOT NULL CHECK (tipo IN ('repasse-falhou','estorno-falhou','devolucao-falhou',
                                                      'conciliacao','dinheiro-sem-pedido','cobranca-desconhecida')),
  referencia     uuid,                         -- repasse, reembolso, devolução ou evento; nulo na conciliação
  detalhe        jsonb       NOT NULL DEFAULT '{}',  -- só dado não pessoal (código do erro, valores)
  criado_em      timestamptz NOT NULL DEFAULT now(),
  resolvido_em   timestamptz,
  resolvido_por  uuid        REFERENCES usuario(id),
  CONSTRAINT ck_alerta_resolvido CHECK ((resolvido_em IS NULL) = (resolvido_por IS NULL))
);
CREATE INDEX ix_alerta_aberto        ON alerta_financeiro (criado_em DESC) WHERE resolvido_em IS NULL;
CREATE INDEX ix_alerta_resolvido_por ON alerta_financeiro (resolvido_por);
CREATE INDEX ix_alerta_referencia    ON alerta_financeiro (referencia);

-- 6. Taxas do Asaas (RN34d): R$ 0,00 até o comercial confirmar (A DEFINIR); despesa da COE, nunca do profissional
INSERT INTO configuracao (chave, valor, tipo, descricao, vigente_desde) VALUES
 ('TAXA_GATEWAY_PIX',               '0.00',   'decimal', 'Taxa do gateway por Pix recebido, em reais (A DEFINIR com o comercial do Asaas)', '2026-01-01'),
 ('TAXA_GATEWAY_CARTAO_PERCENTUAL', '0.0000', 'decimal', 'Taxa do gateway por cartão, percentual do valor (A DEFINIR)',                  '2026-01-01'),
 ('TAXA_GATEWAY_CARTAO_FIXA',       '0.00',   'decimal', 'Taxa fixa do gateway por cartão, em reais (A DEFINIR)',                       '2026-01-01'),
 ('TAXA_GATEWAY_TRANSFERENCIA',     '0.00',   'decimal', 'Taxa do gateway por transferência Pix ao profissional, em reais (A DEFINIR)', '2026-01-01');
