-- =============================================================================
-- COE Serviços · V8 — Avaliações e disputas
-- =============================================================================

-- Uma avaliação por lado e por contrato; só quem pagou pelo app avalia
CREATE TABLE avaliacao (
  id           uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  contrato_id  uuid        NOT NULL REFERENCES contrato(id),
  autor_id     uuid        NOT NULL REFERENCES usuario(id),
  avaliado_id  uuid        NOT NULL REFERENCES usuario(id),
  papel_autor  text        NOT NULL CHECK (papel_autor IN ('cliente','profissional')),
  nota         smallint    NOT NULL CHECK (nota BETWEEN 1 AND 5),
  comentario   text        CHECK (char_length(comentario) <= 500),
  visivel      boolean     NOT NULL DEFAULT true,     -- admin pode ocultar
  criado_em    timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT uq_avaliacao_contrato_autor UNIQUE (contrato_id, autor_id),
  CONSTRAINT ck_avaliacao_pessoas CHECK (autor_id <> avaliado_id)
);
CREATE INDEX ix_avaliacao_avaliado ON avaliacao (avaliado_id, criado_em DESC) WHERE visivel;

-- Reclamação de UMA diária: só ela trava até a decisão da equipe
CREATE TABLE disputa (
  id                     uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  diaria_id              uuid        NOT NULL REFERENCES diaria(id),
  aberta_por             uuid        NOT NULL REFERENCES usuario(id),
  motivo                 text        NOT NULL CHECK (motivo IN ('nao_compareceu','servico_incompleto','qualidade','dano','outro')),
  descricao              text        NOT NULL CHECK (char_length(btrim(descricao)) BETWEEN 10 AND 1000),
  resposta_profissional  text        CHECK (char_length(resposta_profissional) <= 1000),
  respondida_em          timestamptz,
  status                 text        NOT NULL DEFAULT 'aberta'
                         CHECK (status IN ('aberta','em_analise','favor_profissional','favor_cliente','cancelada')),
  prazo_decisao_em       timestamptz NOT NULL,      -- criado_em + PRAZO_DISPUTA_HORAS
  decidida_por           uuid        REFERENCES usuario(id),
  decidida_em            timestamptz,
  justificativa          text,
  criado_em              timestamptz NOT NULL DEFAULT now(),
  atualizado_em          timestamptz NOT NULL DEFAULT now(),
  versao                 bigint      NOT NULL DEFAULT 0,
  CONSTRAINT ck_disputa_decisao CHECK (
    status NOT IN ('favor_profissional','favor_cliente')
    OR (decidida_por IS NOT NULL AND decidida_em IS NOT NULL AND justificativa IS NOT NULL))
);
CREATE TRIGGER trg_disputa_toca BEFORE UPDATE ON disputa
  FOR EACH ROW EXECUTE FUNCTION fn_toca_registro();
-- uma disputa em aberto por diária
CREATE UNIQUE INDEX uq_disputa_aberta ON disputa (diaria_id) WHERE status IN ('aberta','em_analise');
CREATE INDEX ix_disputa_fila ON disputa (prazo_decisao_em) WHERE status IN ('aberta','em_analise');

CREATE TABLE anexo_disputa (
  id             uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  disputa_id     uuid        NOT NULL REFERENCES disputa(id) ON DELETE CASCADE,
  autor_id       uuid        NOT NULL REFERENCES usuario(id),
  chave_arquivo  text        NOT NULL,
  criado_em      timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT uq_anexo_disputa_arquivo UNIQUE (chave_arquivo)
);
CREATE INDEX ix_anexo_disputa ON anexo_disputa (disputa_id);
