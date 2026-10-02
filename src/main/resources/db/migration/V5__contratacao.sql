-- =============================================================================
-- COE Serviços · V5 — Contratação: contrato (pedido), fotos do pedido, diárias
-- Valores e regras (comissão, quem paga) são COPIADOS para o contrato no momento
-- da compra: mudar a configuração depois não altera contrato já fechado.
-- Máquina de estados da diária (RN40) — transições validadas na aplicação:
--   agendada → paga → andamento → aguardando → liberada
--   paga | andamento | aguardando → contestada → liberada | reembolsada
--   agendada → cancelada (pedido não pago / expirado)
-- =============================================================================

CREATE TABLE contrato (
  id                   uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
  numero               bigint        GENERATED ALWAYS AS IDENTITY,   -- número legível p/ suporte (COE-000123)
  cliente_id           uuid          NOT NULL REFERENCES usuario(id),
  profissional_id      uuid          NOT NULL REFERENCES profissional(id),
  profissao_id         uuid          NOT NULL REFERENCES profissao(id),
  status               text          NOT NULL DEFAULT 'aguardando_pagamento'
                       CHECK (status IN ('aguardando_pagamento','pago','em_andamento','concluido','cancelado','expirado')),
  -- o pedido ("Conte o que precisa")
  descricao            text          NOT NULL CHECK (char_length(btrim(descricao)) BETWEEN 10 AND 600),
  material             text          NOT NULL CHECK (material IN ('cliente_tem','cliente_compra','combinar')),
  cep                  char(8)       NOT NULL CHECK (cep ~ '^[0-9]{8}$'),
  cidade_id            uuid          NOT NULL REFERENCES cidade(id),
  endereco_cifrado     bytea         NOT NULL,     -- rua, número, complemento: só aparece após o pagamento
  -- valores congelados na compra
  valor_diaria         numeric(12,2) NOT NULL CHECK (valor_diaria > 0),
  valor_meia_diaria    numeric(12,2) CHECK (valor_meia_diaria > 0),
  comissao_pct         numeric(5,4)  NOT NULL CHECK (comissao_pct >= 0 AND comissao_pct < 1),
  taxa_paga_por        text          NOT NULL CHECK (taxa_paga_por IN ('cliente','profissional')),
  valor_servicos       numeric(12,2) NOT NULL CHECK (valor_servicos > 0),     -- soma das diárias
  valor_comissao       numeric(12,2) NOT NULL CHECK (valor_comissao >= 0),
  valor_total          numeric(12,2) NOT NULL CHECK (valor_total > 0),        -- o que o cliente paga
  versao_termos        text          NOT NULL,
  pagamento_expira_em  timestamptz   NOT NULL,
  pago_em              timestamptz,
  contato_liberado_em  timestamptz,  -- telefone e endereço visíveis a partir daqui
  concluido_em         timestamptz,
  cancelado_em         timestamptz,
  motivo_cancelamento  text,
  criado_em            timestamptz   NOT NULL DEFAULT now(),
  atualizado_em        timestamptz   NOT NULL DEFAULT now(),
  versao               bigint        NOT NULL DEFAULT 0,
  CONSTRAINT uq_contrato_numero UNIQUE (numero),
  -- alvo das FKs compostas da diária (garante que diária e contrato têm as mesmas partes)
  CONSTRAINT uq_contrato_partes UNIQUE (id, cliente_id, profissional_id),
  CONSTRAINT ck_contrato_total CHECK (
    valor_total = valor_servicos + CASE WHEN taxa_paga_por = 'cliente' THEN valor_comissao ELSE 0 END),
  CONSTRAINT ck_contrato_pago      CHECK (status IN ('aguardando_pagamento','cancelado','expirado') OR pago_em IS NOT NULL),
  CONSTRAINT ck_contrato_contato   CHECK (contato_liberado_em IS NULL OR pago_em IS NOT NULL),
  CONSTRAINT ck_contrato_cancelado CHECK (status <> 'cancelado' OR cancelado_em IS NOT NULL)
);
CREATE TRIGGER trg_contrato_toca BEFORE UPDATE ON contrato
  FOR EACH ROW EXECUTE FUNCTION fn_toca_registro();
CREATE INDEX ix_contrato_cliente      ON contrato (cliente_id, criado_em DESC);
CREATE INDEX ix_contrato_profissional ON contrato (profissional_id, criado_em DESC);
CREATE INDEX ix_contrato_profissao    ON contrato (profissao_id);
CREATE INDEX ix_contrato_cidade       ON contrato (cidade_id);
CREATE INDEX ix_contrato_expira       ON contrato (pagamento_expira_em) WHERE status = 'aguardando_pagamento';

-- Fotos que o cliente manda no pedido
CREATE TABLE foto_pedido (
  id             uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  contrato_id    uuid        NOT NULL REFERENCES contrato(id) ON DELETE CASCADE,
  chave_arquivo  text        NOT NULL,
  ordem          smallint    NOT NULL DEFAULT 0,
  criado_em      timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT uq_foto_pedido_arquivo UNIQUE (chave_arquivo)
);
CREATE INDEX ix_foto_pedido_contrato ON foto_pedido (contrato_id, ordem);

CREATE TABLE diaria (
  id               uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
  contrato_id      uuid          NOT NULL,
  cliente_id       uuid          NOT NULL,     -- copiado do contrato (consulta da LC 150)
  profissional_id  uuid          NOT NULL,     -- copiado do contrato (agenda)
  data             date          NOT NULL,
  tipo             text          NOT NULL DEFAULT 'inteira' CHECK (tipo IN ('inteira','meia')),
  valor            numeric(12,2) NOT NULL CHECK (valor > 0),       -- valor do serviço no dia
  valor_comissao   numeric(12,2) NOT NULL CHECK (valor_comissao >= 0),
  status           text          NOT NULL DEFAULT 'agendada'
                   CHECK (status IN ('agendada','paga','andamento','aguardando','liberada','contestada','reembolsada','cancelada')),
  chegou_em        timestamptz,   -- "Cheguei"
  terminou_em      timestamptz,   -- "Terminei o dia" (com foto)
  auto_libera_em   timestamptz,   -- terminou_em + AUTO_LIBERA_HORAS (PA08)
  aprovada_em      timestamptz,
  liberada_em      timestamptz,
  liberada_por     text          CHECK (liberada_por IN ('cliente','automatica','admin')),
  reembolsada_em   timestamptz,
  cancelada_em     timestamptz,
  criado_em        timestamptz   NOT NULL DEFAULT now(),
  atualizado_em    timestamptz   NOT NULL DEFAULT now(),
  versao           bigint        NOT NULL DEFAULT 0,
  CONSTRAINT fk_diaria_contrato FOREIGN KEY (contrato_id, cliente_id, profissional_id)
    REFERENCES contrato (id, cliente_id, profissional_id),
  CONSTRAINT fk_diaria_cliente      FOREIGN KEY (cliente_id)      REFERENCES usuario(id),
  CONSTRAINT fk_diaria_profissional FOREIGN KEY (profissional_id) REFERENCES profissional(id),
  CONSTRAINT uq_diaria_contrato_data UNIQUE (contrato_id, data),
  CONSTRAINT ck_diaria_andamento  CHECK (status NOT IN ('andamento','aguardando') OR chegou_em IS NOT NULL),
  CONSTRAINT ck_diaria_aguardando CHECK (status <> 'aguardando' OR (terminou_em IS NOT NULL AND auto_libera_em IS NOT NULL)),
  CONSTRAINT ck_diaria_liberada   CHECK (status <> 'liberada' OR (liberada_em IS NOT NULL AND liberada_por IS NOT NULL)),
  CONSTRAINT ck_diaria_reembolso  CHECK (status <> 'reembolsada' OR reembolsada_em IS NOT NULL),
  CONSTRAINT ck_diaria_cancelada  CHECK (status <> 'cancelada' OR cancelada_em IS NOT NULL)
);
CREATE TRIGGER trg_diaria_toca BEFORE UPDATE ON diaria
  FOR EACH ROW EXECUTE FUNCTION fn_toca_registro();

-- Profissional não pode ter duas diárias ativas no mesmo dia
CREATE UNIQUE INDEX uq_diaria_agenda_profissional ON diaria (profissional_id, data)
  WHERE status NOT IN ('cancelada','reembolsada');
-- Job da liberação automática
CREATE INDEX ix_diaria_auto_libera ON diaria (auto_libera_em) WHERE status = 'aguardando';
-- LC 150: diárias da mesma diarista com o mesmo cliente na semana
CREATE INDEX ix_diaria_lc150 ON diaria (cliente_id, profissional_id, data)
  WHERE status NOT IN ('cancelada','reembolsada');
CREATE INDEX ix_diaria_contrato ON diaria (contrato_id, data);

-- Fotos da diária (chegada e fim do dia)
CREATE TABLE evidencia_diaria (
  id             uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  diaria_id      uuid        NOT NULL REFERENCES diaria(id) ON DELETE CASCADE,
  tipo           text        NOT NULL CHECK (tipo IN ('chegada','fim_do_dia')),
  chave_arquivo  text        NOT NULL,
  observacao     text        CHECK (char_length(observacao) <= 300),
  criado_em      timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT uq_evidencia_arquivo UNIQUE (chave_arquivo)
);
CREATE INDEX ix_evidencia_diaria ON evidencia_diaria (diaria_id);

-- Toda mudança de estado da diária (somente inserção)
CREATE TABLE historico_diaria (
  id          bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  diaria_id   uuid        NOT NULL REFERENCES diaria(id),
  de_status   text,
  para_status text        NOT NULL,
  origem      text        NOT NULL CHECK (origem IN ('cliente','profissional','admin','sistema','gateway')),
  ator_id     uuid        REFERENCES usuario(id),
  motivo      text,
  criado_em   timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_historico_diaria ON historico_diaria (diaria_id, criado_em);
CREATE TRIGGER trg_historico_diaria_somente_insercao
  BEFORE UPDATE OR DELETE ON historico_diaria
  FOR EACH ROW EXECUTE FUNCTION fn_somente_insercao();
CREATE TRIGGER trg_historico_diaria_sem_truncate
  BEFORE TRUNCATE ON historico_diaria
  FOR EACH STATEMENT EXECUTE FUNCTION fn_somente_insercao();
REVOKE UPDATE, DELETE, TRUNCATE ON historico_diaria FROM coe_app;
