-- =============================================================================
-- COE Serviços · V4 — Profissional: perfil, cadastro em 11 etapas, verificação
-- O perfil nasce em 'rascunho', vai para 'em_analise' ao concluir o cadastro e
-- só aparece na busca como 'ativo' (aprovado pelo admin).
-- =============================================================================

CREATE TABLE profissional (
  id                     uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
  usuario_id             uuid          NOT NULL REFERENCES usuario(id),
  status                 text          NOT NULL DEFAULT 'rascunho'
                         CHECK (status IN ('rascunho','em_analise','ativo','suspenso','recusado')),
  etapa_cadastro         smallint      NOT NULL DEFAULT 1 CHECK (etapa_cadastro BETWEEN 1 AND 11),
  profissao_principal_id uuid          REFERENCES profissao(id),
  experiencia_anos       smallint      CHECK (experiencia_anos IN (0,1,3,5,10,20)),  -- início da faixa escolhida
  ferramentas            text          CHECK (ferramentas IN ('sim','parte','nao')),
  horario                text          CHECK (char_length(horario) <= 30),           -- ex.: '7h às 17h'
  frases                 text[]        NOT NULL DEFAULT '{}',                        -- frases prontas do "Sobre você"
  bio                    text          CHECK (char_length(bio) <= 300),
  valor_diaria           numeric(12,2) CHECK (valor_diaria > 0),
  aceita_meia_diaria     boolean       NOT NULL DEFAULT false,
  valor_meia_diaria      numeric(12,2) CHECK (valor_meia_diaria > 0),
  cidade_base_id         uuid          REFERENCES cidade(id),
  raio_km                smallint      CHECK (raio_km BETWEEN 1 AND 100),
  cpf_cifrado            bytea,
  cpf_hash               bytea,        -- HMAC-SHA256 do CPF: unicidade sem expor o número
  chave_pix_tipo         text          CHECK (chave_pix_tipo IN ('cpf','celular','email','aleatoria')),
  chave_pix_cifrada      bytea,
  mei                    boolean       NOT NULL DEFAULT false,
  nr10_status            text          NOT NULL DEFAULT 'nao_tem'
                         CHECK (nr10_status IN ('nao_tem','em_analise','verificado','recusado')),
  -- números do perfil (mantidos pela aplicação a cada avaliação/diária liberada)
  nota_media             numeric(3,2)  CHECK (nota_media BETWEEN 1 AND 5),
  total_avaliacoes       integer       NOT NULL DEFAULT 0 CHECK (total_avaliacoes >= 0),
  total_diarias          integer       NOT NULL DEFAULT 0 CHECK (total_diarias >= 0),
  enviado_analise_em     timestamptz,
  aprovado_em            timestamptz,
  aprovado_por           uuid          REFERENCES usuario(id),
  motivo_recusa          text,
  criado_em              timestamptz   NOT NULL DEFAULT now(),
  atualizado_em          timestamptz   NOT NULL DEFAULT now(),
  versao                 bigint        NOT NULL DEFAULT 0,
  CONSTRAINT uq_profissional_usuario UNIQUE (usuario_id),
  CONSTRAINT uq_profissional_cpf     UNIQUE (cpf_hash),
  CONSTRAINT ck_profissional_meia    CHECK (aceita_meia_diaria OR valor_meia_diaria IS NULL),
  CONSTRAINT ck_profissional_meia_valor CHECK (valor_meia_diaria IS NULL OR valor_meia_diaria < valor_diaria),
  -- fora do rascunho, o cadastro tem que estar completo
  CONSTRAINT ck_profissional_completo CHECK (
    status = 'rascunho' OR (
      profissao_principal_id IS NOT NULL AND experiencia_anos IS NOT NULL AND
      valor_diaria IS NOT NULL AND cidade_base_id IS NOT NULL AND raio_km IS NOT NULL AND
      cpf_cifrado IS NOT NULL AND cpf_hash IS NOT NULL AND
      chave_pix_tipo IS NOT NULL AND chave_pix_cifrada IS NOT NULL AND
      enviado_analise_em IS NOT NULL)),
  CONSTRAINT ck_profissional_aprovado CHECK (status <> 'ativo' OR aprovado_em IS NOT NULL),
  CONSTRAINT ck_profissional_recusa   CHECK (status <> 'recusado' OR motivo_recusa IS NOT NULL)
);
CREATE TRIGGER trg_profissional_toca BEFORE UPDATE ON profissional
  FOR EACH ROW EXECUTE FUNCTION fn_toca_registro();
CREATE INDEX ix_profissional_fila_analise ON profissional (enviado_analise_em) WHERE status = 'em_analise';

-- Outras profissões além da principal
CREATE TABLE profissional_profissao (
  profissional_id uuid NOT NULL REFERENCES profissional(id) ON DELETE CASCADE,
  profissao_id    uuid NOT NULL REFERENCES profissao(id),
  PRIMARY KEY (profissional_id, profissao_id)
);
CREATE INDEX ix_prof_profissao_profissao ON profissional_profissao (profissao_id);

-- Serviços que o profissional faz
CREATE TABLE profissional_servico (
  profissional_id uuid NOT NULL REFERENCES profissional(id) ON DELETE CASCADE,
  servico_id      uuid NOT NULL REFERENCES servico(id),
  PRIMARY KEY (profissional_id, servico_id)
);
CREATE INDEX ix_prof_servico_servico ON profissional_servico (servico_id);

-- Cidades atendidas além do raio da cidade base
CREATE TABLE profissional_cidade (
  profissional_id uuid NOT NULL REFERENCES profissional(id) ON DELETE CASCADE,
  cidade_id       uuid NOT NULL REFERENCES cidade(id),
  PRIMARY KEY (profissional_id, cidade_id)
);
CREATE INDEX ix_prof_cidade_cidade ON profissional_cidade (cidade_id);

-- Dias da semana em que trabalha (0 = domingo … 6 = sábado)
CREATE TABLE disponibilidade_semanal (
  profissional_id uuid     NOT NULL REFERENCES profissional(id) ON DELETE CASCADE,
  dia_semana      smallint NOT NULL CHECK (dia_semana BETWEEN 0 AND 6),
  PRIMARY KEY (profissional_id, dia_semana)
);

-- Dias bloqueados na agenda (folga, outro serviço fora do app)
CREATE TABLE bloqueio_agenda (
  id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  profissional_id uuid        NOT NULL REFERENCES profissional(id) ON DELETE CASCADE,
  data            date        NOT NULL,
  motivo          text        CHECK (char_length(motivo) <= 120),
  criado_em       timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT uq_bloqueio_agenda UNIQUE (profissional_id, data)
);

-- Documentos para verificação (bucket privado; aqui só a chave do arquivo)
CREATE TABLE documento_verificacao (
  id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  profissional_id uuid        NOT NULL REFERENCES profissional(id) ON DELETE CASCADE,
  tipo            text        NOT NULL CHECK (tipo IN ('rg_frente','rg_verso','cnh','selfie','certificado_nr10','comprovante_mei')),
  chave_arquivo   text        NOT NULL,
  status          text        NOT NULL DEFAULT 'pendente' CHECK (status IN ('pendente','aprovado','recusado')),
  motivo_recusa   text,
  analisado_por   uuid        REFERENCES usuario(id),
  analisado_em    timestamptz,
  excluir_apos    date,       -- retenção LGPD: data a partir da qual o arquivo pode ser apagado
  criado_em       timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT uq_documento_arquivo UNIQUE (chave_arquivo),
  CONSTRAINT ck_documento_analise CHECK (status = 'pendente' OR (analisado_por IS NOT NULL AND analisado_em IS NOT NULL)),
  CONSTRAINT ck_documento_recusa  CHECK (status <> 'recusado' OR motivo_recusa IS NOT NULL)
);
CREATE INDEX ix_documento_profissional ON documento_verificacao (profissional_id);
CREATE INDEX ix_documento_pendente ON documento_verificacao (criado_em) WHERE status = 'pendente';

-- Fotos de trabalhos já feitos (portfólio público)
CREATE TABLE foto_portfolio (
  id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  profissional_id uuid        NOT NULL REFERENCES profissional(id) ON DELETE CASCADE,
  servico_id      uuid        REFERENCES servico(id),
  chave_arquivo   text        NOT NULL,
  legenda         text        CHECK (char_length(legenda) <= 140),
  ordem           smallint    NOT NULL DEFAULT 0,
  visivel         boolean     NOT NULL DEFAULT true,      -- admin pode ocultar
  criado_em       timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT uq_portfolio_arquivo UNIQUE (chave_arquivo)
);
CREATE INDEX ix_portfolio_profissional ON foto_portfolio (profissional_id, ordem);
