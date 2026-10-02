-- =============================================================================
-- COE Serviços · V7 — Chat e censura de contato
-- Antes do pagamento, mensagem com telefone, e-mail, link ou rede social é
-- guardada com o motivo e NÃO é entregue. Cada tentativa vira uma linha em
-- tentativa_contato; ao passar de TENTATIVAS_ANTES_ANALISE a conta vai para análise.
-- =============================================================================

CREATE TABLE conversa (
  id                   uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  cliente_id           uuid        NOT NULL REFERENCES usuario(id),
  profissional_id      uuid        NOT NULL REFERENCES profissional(id),
  contato_liberado_em  timestamptz,           -- após o 1º contrato pago entre os dois
  ultima_mensagem_em   timestamptz,
  criado_em            timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT uq_conversa_partes UNIQUE (cliente_id, profissional_id)
);
CREATE INDEX ix_conversa_profissional ON conversa (profissional_id, ultima_mensagem_em DESC);
CREATE INDEX ix_conversa_cliente      ON conversa (cliente_id, ultima_mensagem_em DESC);

CREATE TABLE mensagem (
  id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  conversa_id     uuid        NOT NULL REFERENCES conversa(id),
  autor_id        uuid        NOT NULL REFERENCES usuario(id),
  texto           text        NOT NULL CHECK (char_length(btrim(texto)) BETWEEN 1 AND 2000),
  bloqueada       boolean     NOT NULL DEFAULT false,
  regras_violadas text[]      NOT NULL DEFAULT '{}',   -- telefone, email, link, perfil, zap, ...
  lida_em         timestamptz,
  criado_em       timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_mensagem_bloqueio CHECK (bloqueada = (cardinality(regras_violadas) > 0))
);
CREATE INDEX ix_mensagem_conversa ON mensagem (conversa_id, criado_em);
CREATE INDEX ix_mensagem_nao_lida ON mensagem (conversa_id) WHERE lida_em IS NULL AND NOT bloqueada;

-- Tentativas de passar contato em qualquer campo de texto livre
CREATE TABLE tentativa_contato (
  id               uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  usuario_id       uuid        NOT NULL REFERENCES usuario(id),
  origem           text        NOT NULL CHECK (origem IN ('chat','bio','pedido','legenda','avaliacao')),
  mensagem_id      uuid        REFERENCES mensagem(id),
  regras           text[]      NOT NULL CHECK (cardinality(regras) > 0),
  trecho_mascarado text,        -- ex.: '(47) 9****-**21' — nunca o dado inteiro
  criado_em        timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_tentativa_chat CHECK (origem <> 'chat' OR mensagem_id IS NOT NULL)
);
CREATE INDEX ix_tentativa_usuario ON tentativa_contato (usuario_id, criado_em DESC);
