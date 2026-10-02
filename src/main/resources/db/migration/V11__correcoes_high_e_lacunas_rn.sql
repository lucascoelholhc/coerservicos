-- =============================================================================
-- COE Serviços · V11 — Correções HIGH da revisão de banco e lacunas do schema
-- frente às regras de negócio (DB-12). V1–V10 não são editadas.
--
-- Premissa: aplicada antes de existir produção. Os CHECKs novos validam as linhas
-- existentes; nenhum ambiente tem profissional fora do rascunho, contrato,
-- cobrança ou reembolso. Com dados reais, usar ADD CONSTRAINT ... NOT VALID,
-- corrigir as linhas e depois VALIDATE CONSTRAINT.
-- =============================================================================

SET LOCAL lock_timeout = '5s';   -- não enfileira atrás de transações longas

-- -----------------------------------------------------------------------------
-- 1. Webhook (RNF06): só evento com assinatura válida ocupa o id; corpo bruto
--    guardado; conteúdo do evento imutável (só processado_em e erro mudam).
-- -----------------------------------------------------------------------------
ALTER TABLE evento_gateway DROP CONSTRAINT uq_evento_gateway;
CREATE UNIQUE INDEX uq_evento_gateway ON evento_gateway (gateway, id_evento) WHERE assinatura_valida;

-- corpo_bruto: bytes exatos recebidos, para revalidar a assinatura. As linhas
-- anteriores à V11 recebem o payload jsonb normalizado e não permitem revalidar.
ALTER TABLE evento_gateway ADD COLUMN corpo_bruto bytea;
UPDATE evento_gateway SET corpo_bruto = convert_to(payload::text, 'UTF8');
ALTER TABLE evento_gateway ALTER COLUMN corpo_bruto SET NOT NULL;
ALTER TABLE evento_gateway ADD CONSTRAINT ck_evento_corpo_tamanho
  CHECK (octet_length(corpo_bruto) <= 1048576);   -- endpoint público: no máximo 1 MiB

-- Evento forjado nunca é processado e não entra na fila do worker
ALTER TABLE evento_gateway ADD CONSTRAINT ck_evento_processado_valido
  CHECK (assinatura_valida OR processado_em IS NULL);
DROP INDEX ix_evento_nao_processado;
CREATE INDEX ix_evento_nao_processado ON evento_gateway (recebido_em)
  WHERE processado_em IS NULL AND assinatura_valida;

-- Registro do webhook: a aplicação não apaga (retenção/purga só pelo dono do schema)
REVOKE DELETE, TRUNCATE ON evento_gateway FROM coe_app;

CREATE OR REPLACE FUNCTION fn_evento_gateway_imutavel() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF (NEW.id, NEW.gateway, NEW.id_evento, NEW.tipo, NEW.payload, NEW.corpo_bruto,
      NEW.assinatura_valida, NEW.recebido_em)
     IS DISTINCT FROM
     (OLD.id, OLD.gateway, OLD.id_evento, OLD.tipo, OLD.payload, OLD.corpo_bruto,
      OLD.assinatura_valida, OLD.recebido_em) THEN
    RAISE EXCEPTION 'Tabela evento_gateway: só processado_em e erro podem mudar'
      USING ERRCODE = 'insufficient_privilege';
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER trg_evento_gateway_imutavel
  BEFORE UPDATE ON evento_gateway
  FOR EACH ROW EXECUTE FUNCTION fn_evento_gateway_imutavel();

-- -----------------------------------------------------------------------------
-- 2. Uma liberação OU um reembolso por diária (RN39, RNF11).
--    diaria_id obrigatório nesses tipos, senão o NULL furaria o índice único.
-- -----------------------------------------------------------------------------
ALTER TABLE transacao_financeira
  ADD CONSTRAINT ck_transacao_diaria_obrigatoria
    CHECK (tipo NOT IN ('liberacao','reembolso') OR diaria_id IS NOT NULL),
  ADD CONSTRAINT ck_transacao_pagamento_cobranca
    CHECK (tipo <> 'pagamento' OR cobranca_id IS NOT NULL);

CREATE UNIQUE INDEX uq_transacao_diaria_destino ON transacao_financeira (diaria_id)
  WHERE tipo IN ('liberacao','reembolso');

-- -----------------------------------------------------------------------------
-- 3. Cidades do lançamento (PA16), em todos os ambientes. Saem do seed local.
--    Códigos IBGE conferidos com a tabela oficial; coordenadas do centro da cidade.
-- -----------------------------------------------------------------------------
INSERT INTO cidade (codigo_ibge, nome, uf, latitude, longitude, ativa) VALUES
 (4202404, 'Blumenau',           'SC', -26.919400, -49.066100, true),
 (4205902, 'Gaspar',             'SC', -26.933600, -48.958600, true),
 (4213203, 'Pomerode',           'SC', -26.740600, -49.176900, true),
 (4207502, 'Indaial',            'SC', -26.897800, -49.231700, true),
 (4218202, 'Timbó',              'SC', -26.823100, -49.271700, true),
 (4202909, 'Brusque',            'SC', -27.097700, -48.910700, true),
 (4208203, 'Itajaí',             'SC', -26.907800, -48.661900, true),
 (4202008, 'Balneário Camboriú', 'SC', -26.990600, -48.634700, true),
 (4208906, 'Jaraguá do Sul',     'SC', -26.485100, -49.071300, true),
 (4211306, 'Navegantes',         'SC', -26.898900, -48.654200, true)
ON CONFLICT (codigo_ibge) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 4. Pedido (RN27): serviços marcados no contrato; descrição opcional (15–600).
--    A regra "ao menos um serviço OU descrição" é validada no serviço (DOM-06).
-- -----------------------------------------------------------------------------
CREATE TABLE contrato_servico (
  contrato_id uuid NOT NULL REFERENCES contrato(id),
  servico_id  uuid NOT NULL REFERENCES servico(id),
  CONSTRAINT pk_contrato_servico PRIMARY KEY (contrato_id, servico_id)
);
CREATE INDEX ix_contrato_servico_servico ON contrato_servico (servico_id);

ALTER TABLE contrato DROP CONSTRAINT contrato_descricao_check;
ALTER TABLE contrato ALTER COLUMN descricao DROP NOT NULL;
ALTER TABLE contrato ADD CONSTRAINT ck_contrato_descricao
  CHECK (descricao IS NULL OR char_length(btrim(descricao)) BETWEEN 15 AND 600);

-- -----------------------------------------------------------------------------
-- 5–8. Profissional: pausado (RN20, RN56), correção pedida (RN13),
--      data de nascimento (RN08; os 18 anos são validados no serviço),
--      raio 5/10/20/40 km (RN17) e cpf_hash de 32 bytes (HMAC-SHA256).
-- -----------------------------------------------------------------------------
ALTER TABLE profissional
  ADD COLUMN motivo_correcao text,
  ADD COLUMN data_nascimento date;

ALTER TABLE profissional DROP CONSTRAINT profissional_status_check;
ALTER TABLE profissional ADD CONSTRAINT ck_profissional_status
  CHECK (status IN ('rascunho','em_analise','correcao_pedida','ativo','pausado','suspenso','recusado'));

ALTER TABLE profissional ADD CONSTRAINT ck_profissional_correcao
  CHECK (status <> 'correcao_pedida' OR motivo_correcao IS NOT NULL);

-- só pausa quem já foi aprovado (RN56: o profissional ativo pausa o perfil)
ALTER TABLE profissional ADD CONSTRAINT ck_profissional_pausado
  CHECK (status <> 'pausado' OR aprovado_em IS NOT NULL);

ALTER TABLE profissional ADD CONSTRAINT ck_profissional_nascimento
  CHECK (data_nascimento > DATE '1900-01-01');

-- fora do rascunho, o cadastro tem que estar completo (agora com data de nascimento)
ALTER TABLE profissional DROP CONSTRAINT ck_profissional_completo;
ALTER TABLE profissional ADD CONSTRAINT ck_profissional_completo CHECK (
  status = 'rascunho' OR (
    profissao_principal_id IS NOT NULL AND experiencia_anos IS NOT NULL AND
    valor_diaria IS NOT NULL AND cidade_base_id IS NOT NULL AND raio_km IS NOT NULL AND
    cpf_cifrado IS NOT NULL AND cpf_hash IS NOT NULL AND
    chave_pix_tipo IS NOT NULL AND chave_pix_cifrada IS NOT NULL AND
    data_nascimento IS NOT NULL AND enviado_analise_em IS NOT NULL));

ALTER TABLE profissional DROP CONSTRAINT profissional_raio_km_check;
ALTER TABLE profissional ADD CONSTRAINT ck_profissional_raio CHECK (raio_km IN (5, 10, 20, 40));

ALTER TABLE profissional ADD CONSTRAINT ck_profissional_cpf_hash CHECK (octet_length(cpf_hash) = 32);

-- Documento: correção pedida com motivo (RN13); motivo_recusa fica só para recusa
ALTER TABLE documento_verificacao ADD COLUMN motivo_correcao text;
ALTER TABLE documento_verificacao DROP CONSTRAINT documento_verificacao_status_check;
ALTER TABLE documento_verificacao ADD CONSTRAINT ck_documento_status
  CHECK (status IN ('pendente','aprovado','correcao_pedida','recusado'));
ALTER TABLE documento_verificacao ADD CONSTRAINT ck_documento_correcao
  CHECK (status <> 'correcao_pedida' OR motivo_correcao IS NOT NULL);

-- -----------------------------------------------------------------------------
-- 9. Configuração: valor conforme o tipo; limite da LC 150 nunca acima de 2 (RN52).
--    CASE garante a ordem: o cast só roda depois de conferir que é número.
-- -----------------------------------------------------------------------------
ALTER TABLE configuracao ADD CONSTRAINT ck_configuracao_valor_tipo CHECK (
  CASE tipo
    WHEN 'decimal'  THEN valor ~ '^-?[0-9]{1,9}(\.[0-9]{1,6})?$'
    WHEN 'inteiro'  THEN valor ~ '^-?[0-9]{1,9}$'          -- cabe num int do Java
    WHEN 'booleano' THEN valor IN ('true','false')
    ELSE true
  END);

ALTER TABLE configuracao ADD CONSTRAINT ck_configuracao_limite_lc150 CHECK (
  CASE
    WHEN chave <> 'LIMITE_DOMESTICO_SEMANA' THEN true
    WHEN valor ~ '^[0-9]{1,2}$'             THEN valor::int BETWEEN 1 AND 2
    ELSE false
  END);

-- Faixas das demais chaves conhecidas (a tabela é só de inserção: valor errado não se apaga).
-- CASE aninhado: AND não garante a ordem de avaliação, e o cast não pode rodar antes da regex.
ALTER TABLE configuracao ADD CONSTRAINT ck_configuracao_faixas CHECK (
  CASE
    WHEN chave = 'COMISSAO' THEN
      CASE WHEN valor ~ '^[0-9]{1,9}(\.[0-9]{1,6})?$' THEN valor::numeric >= 0 AND valor::numeric < 1
           ELSE false END
    WHEN chave = 'TAXA_PAGA_POR' THEN valor IN ('cliente','profissional')
    WHEN chave IN ('AUTO_LIBERA_HORAS','PRAZO_DISPUTA_HORAS','TENTATIVAS_ANTES_ANALISE',
                   'MEIA_DIARIA_HORAS','PAGAMENTO_EXPIRA_MINUTOS') THEN
      CASE WHEN valor ~ '^[0-9]{1,9}$' THEN valor::int > 0 ELSE false END
    ELSE true
  END);

-- -----------------------------------------------------------------------------
-- 10. Trava otimista também fora do JPA: todo UPDATE sobe a versão. Se o UPDATE
--     já subiu (Hibernate @Version), mantém. Vale para as tabelas com trg_*_toca,
--     que têm todas a coluna versao: tabela nova com trg_*_toca PRECISA ter versao.
--     Efeito: UPDATE nativo (ex.: ultimo_login_em) gera OptimisticLock numa edição
--     JPA concorrente; o serviço trata o erro.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_toca_registro() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  NEW.atualizado_em := now();
  IF NEW.versao <= OLD.versao THEN
    NEW.versao := OLD.versao + 1;
  END IF;
  RETURN NEW;
END $$;

-- -----------------------------------------------------------------------------
-- 11. Ninguém contrata a si mesmo (evita lavar comissão e se autoavaliar).
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_contrato_sem_autocontratacao() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF EXISTS (SELECT 1 FROM profissional
             WHERE id = NEW.profissional_id AND usuario_id = NEW.cliente_id) THEN
    RAISE EXCEPTION 'Contrato com o próprio profissional não é permitido'
      USING ERRCODE = 'check_violation';
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER trg_contrato_sem_autocontratacao
  BEFORE INSERT OR UPDATE OF cliente_id, profissional_id ON contrato
  FOR EACH ROW EXECUTE FUNCTION fn_contrato_sem_autocontratacao();

-- -----------------------------------------------------------------------------
-- 12. Estados finais exigem seus carimbos de tempo.
-- -----------------------------------------------------------------------------
ALTER TABLE contrato ADD CONSTRAINT ck_contrato_concluido
  CHECK (status <> 'concluido' OR concluido_em IS NOT NULL);
ALTER TABLE cobranca ADD CONSTRAINT ck_cobranca_estornada
  CHECK (status <> 'estornada' OR confirmada_em IS NOT NULL);
ALTER TABLE reembolso ADD CONSTRAINT ck_reembolso_confirmado
  CHECK (status <> 'confirmado' OR (concluido_em IS NOT NULL AND transacao_id IS NOT NULL));

-- -----------------------------------------------------------------------------
-- 13. Id externo do gateway único em repasse e reembolso (o webhook localiza o registro).
-- -----------------------------------------------------------------------------
CREATE UNIQUE INDEX uq_repasse_externo   ON repasse   (id_externo) WHERE id_externo IS NOT NULL;
CREATE UNIQUE INDEX uq_reembolso_externo ON reembolso (id_externo) WHERE id_externo IS NOT NULL;

-- -----------------------------------------------------------------------------
-- 14. Exclusão de conta por anonimização (RN60): usuario nunca é apagado.
--     Excluído pode ficar sem celular, e-mail e senha; os demais exigem os três
--     (RF03: a conta de cliente pede e-mail). Os UNIQUE de celular e e-mail
--     continuam valendo para os não nulos.
--     Provas (aceite dos termos, evidências, anexos de disputa) não apagam em cascata.
--     CPF, chave Pix e data de nascimento do profissional ficam retidos pelo prazo
--     legal mesmo após a exclusão (dados financeiros e fiscais; RNF16).
-- -----------------------------------------------------------------------------
ALTER TABLE usuario ALTER COLUMN celular DROP NOT NULL;
ALTER TABLE usuario ALTER COLUMN senha_hash DROP NOT NULL;
ALTER TABLE usuario ADD CONSTRAINT ck_usuario_credenciais
  CHECK (status = 'excluido' OR (celular IS NOT NULL AND email IS NOT NULL AND senha_hash IS NOT NULL));

CREATE OR REPLACE FUNCTION fn_usuario_sem_delete() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'usuario não pode ser apagado: a exclusão de conta é por anonimização (RN60)'
    USING ERRCODE = 'insufficient_privilege';
END $$;

CREATE TRIGGER trg_usuario_sem_delete
  BEFORE DELETE ON usuario
  FOR EACH ROW EXECUTE FUNCTION fn_usuario_sem_delete();
REVOKE DELETE ON usuario FROM coe_app;

ALTER TABLE aceite_termos DROP CONSTRAINT aceite_termos_usuario_id_fkey;
ALTER TABLE aceite_termos ADD CONSTRAINT fk_aceite_termos_usuario
  FOREIGN KEY (usuario_id) REFERENCES usuario(id) ON DELETE RESTRICT;

ALTER TABLE evidencia_diaria DROP CONSTRAINT evidencia_diaria_diaria_id_fkey;
ALTER TABLE evidencia_diaria ADD CONSTRAINT fk_evidencia_diaria
  FOREIGN KEY (diaria_id) REFERENCES diaria(id) ON DELETE RESTRICT;

ALTER TABLE anexo_disputa DROP CONSTRAINT anexo_disputa_disputa_id_fkey;
ALTER TABLE anexo_disputa ADD CONSTRAINT fk_anexo_disputa
  FOREIGN KEY (disputa_id) REFERENCES disputa(id) ON DELETE RESTRICT;

-- -----------------------------------------------------------------------------
-- 15. Toda FK com índice que começa pela coluna dela (consultas e checagem da FK).
-- -----------------------------------------------------------------------------
CREATE INDEX ix_configuracao_criado_por        ON configuracao (criado_por);
CREATE INDEX ix_profissional_aprovado_por      ON profissional (aprovado_por);
CREATE INDEX ix_profissional_cidade_base       ON profissional (cidade_base_id);
CREATE INDEX ix_profissional_profissao         ON profissional (profissao_principal_id);
CREATE INDEX ix_documento_analisado_por        ON documento_verificacao (analisado_por);
CREATE INDEX ix_portfolio_servico              ON foto_portfolio (servico_id);
CREATE INDEX ix_diaria_cliente                 ON diaria (cliente_id, data);
CREATE INDEX ix_diaria_profissional            ON diaria (profissional_id, data);
CREATE INDEX ix_historico_diaria_ator          ON historico_diaria (ator_id);
CREATE INDEX ix_conta_razao_profissional       ON conta_razao (profissional_id);
CREATE INDEX ix_conta_razao_usuario            ON conta_razao (usuario_id);
CREATE INDEX ix_transacao_cobranca             ON transacao_financeira (cobranca_id);
CREATE INDEX ix_transacao_criado_por           ON transacao_financeira (criado_por);
CREATE INDEX ix_repasse_transacao              ON repasse (transacao_id);
CREATE INDEX ix_reembolso_solicitado_por       ON reembolso (solicitado_por);
CREATE INDEX ix_reembolso_transacao            ON reembolso (transacao_id);
CREATE INDEX ix_mensagem_autor                 ON mensagem (autor_id);
CREATE INDEX ix_tentativa_mensagem             ON tentativa_contato (mensagem_id);
CREATE INDEX ix_avaliacao_autor                ON avaliacao (autor_id);
CREATE INDEX ix_avaliacao_avaliado_fk          ON avaliacao (avaliado_id);   -- o parcial (visivel) não serve à FK
CREATE INDEX ix_disputa_aberta_por             ON disputa (aberta_por);
CREATE INDEX ix_disputa_decidida_por           ON disputa (decidida_por);
CREATE INDEX ix_disputa_diaria                 ON disputa (diaria_id);
CREATE INDEX ix_anexo_disputa_autor            ON anexo_disputa (autor_id);
CREATE INDEX ix_denuncia_denunciante           ON denuncia (denunciante_id);
CREATE INDEX ix_denuncia_mensagem              ON denuncia (mensagem_id);
CREATE INDEX ix_denuncia_tratada_por           ON denuncia (tratada_por);

-- -----------------------------------------------------------------------------
-- 16. pgcrypto não é usada: gen_random_uuid() é nativa desde o PostgreSQL 13.
-- -----------------------------------------------------------------------------
DROP EXTENSION IF EXISTS pgcrypto;
