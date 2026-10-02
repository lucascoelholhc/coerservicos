-- =============================================================================
-- COE Serviços · V3 — Catálogo: cidades, áreas, profissões e serviços
-- Dados de referência do produto (valem em todos os ambientes).
-- =============================================================================

CREATE TABLE cidade (
  id           uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
  codigo_ibge  integer      NOT NULL CHECK (codigo_ibge BETWEEN 1000000 AND 9999999),
  nome         text         NOT NULL,
  uf           char(2)      NOT NULL CHECK (uf ~ '^[A-Z]{2}$'),
  latitude     numeric(9,6) NOT NULL CHECK (latitude  BETWEEN -90  AND 90),
  longitude    numeric(9,6) NOT NULL CHECK (longitude BETWEEN -180 AND 180),
  ativa        boolean      NOT NULL DEFAULT false,   -- cidade liberada para operação
  criado_em    timestamptz  NOT NULL DEFAULT now(),
  CONSTRAINT uq_cidade_ibge UNIQUE (codigo_ibge)
);
CREATE INDEX ix_cidade_nome ON cidade USING gin (f_sem_acento(nome) gin_trgm_ops);
CREATE INDEX ix_cidade_ativa ON cidade (uf, nome) WHERE ativa;

ALTER TABLE usuario ADD COLUMN cidade_id uuid REFERENCES cidade(id);
CREATE INDEX ix_usuario_cidade ON usuario (cidade_id);

CREATE TABLE area (
  id      uuid     PRIMARY KEY DEFAULT gen_random_uuid(),
  codigo  text     NOT NULL CHECK (codigo ~ '^[a-z_]{2,30}$'),
  nome    text     NOT NULL,
  ordem   smallint NOT NULL DEFAULT 0,
  CONSTRAINT uq_area_codigo UNIQUE (codigo)
);

CREATE TABLE profissao (
  id                   uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
  area_id              uuid          NOT NULL REFERENCES area(id),
  codigo               text          NOT NULL CHECK (codigo ~ '^[a-z_]{2,30}$'),
  nome                 text          NOT NULL,
  nome_plural          text          NOT NULL,
  icone                text          NOT NULL,
  ativa                boolean       NOT NULL DEFAULT false,   -- inativa = "em breve"
  exige_nr10           boolean       NOT NULL DEFAULT false,   -- eletricista: selo NR-10
  sujeita_lc150        boolean       NOT NULL DEFAULT false,   -- diarista: limite semanal
  faixa_diaria_min     numeric(12,2) CHECK (faixa_diaria_min > 0),
  faixa_diaria_max     numeric(12,2),
  ordem                smallint      NOT NULL DEFAULT 0,
  CONSTRAINT uq_profissao_codigo UNIQUE (codigo),
  CONSTRAINT ck_profissao_faixa CHECK (faixa_diaria_max IS NULL OR faixa_diaria_max >= faixa_diaria_min)
);
CREATE INDEX ix_profissao_area ON profissao (area_id);

CREATE TABLE servico (
  id            uuid     PRIMARY KEY DEFAULT gen_random_uuid(),
  profissao_id  uuid     NOT NULL REFERENCES profissao(id),
  nome          text     NOT NULL,
  ativo         boolean  NOT NULL DEFAULT true,
  ordem         smallint NOT NULL DEFAULT 0,
  CONSTRAINT uq_servico_nome UNIQUE (profissao_id, nome)
);

-- ---- Dados de referência (vindos do protótipo) ------------------------------
INSERT INTO area (codigo, nome, ordem) VALUES
 ('obra', 'Obra e reforma', 1),
 ('casa', 'Casa e jardim',  2);

INSERT INTO profissao (area_id, codigo, nome, nome_plural, icone, ativa, exige_nr10, sujeita_lc150, faixa_diaria_min, faixa_diaria_max, ordem)
SELECT a.id, p.codigo, p.nome, p.plural, p.icone, p.ativa, p.nr10, p.lc150, p.fmin, p.fmax, p.ordem
FROM (VALUES
  ('obra','pedreiro',   'Pedreiro',           'Pedreiros',    'brick',  true,  false, false, 250, 330, 1),
  ('obra','pintor',     'Pintor',             'Pintores',     'roller', true,  false, false, 220, 300, 2),
  ('obra','eletricista','Eletricista',        'Eletricistas', 'bolt',   true,  true,  false, 280, 360, 3),
  ('obra','encanador',  'Encanador',          'Encanadores',  'drop',   false, false, false, 250, 320, 4),
  ('casa','diarista',   'Diarista',           'Diaristas',    'broom',  true,  false, true,  160, 220, 1),
  ('casa','jardineiro', 'Jardineiro',         'Jardineiros',  'leaf',   true,  false, false, 170, 240, 2),
  ('casa','montador',   'Montador de móveis', 'Montadores',   'tools',  false, false, false, 180, 260, 3)
) AS p(area, codigo, nome, plural, icone, ativa, nr10, lc150, fmin, fmax, ordem)
JOIN area a ON a.codigo = p.area;

INSERT INTO servico (profissao_id, nome, ordem)
SELECT pr.id, s.nome, s.ordem
FROM (VALUES
  ('pedreiro','Alvenaria',1),('pedreiro','Reboco',2),('pedreiro','Contrapiso',3),('pedreiro','Assentamento de piso',4),
  ('pedreiro','Muro e calçada',5),('pedreiro','Telhado e calhas',6),('pedreiro','Conserto de trincas',7),('pedreiro','Pequenos reparos',8),
  ('pintor','Pintura interna',1),('pintor','Pintura externa',2),('pintor','Massa corrida',3),('pintor','Textura e grafiato',4),
  ('pintor','Portas e janelas',5),('pintor','Pintura de muro',6),
  ('eletricista','Tomadas e interruptores',1),('eletricista','Chuveiro e resistência',2),('eletricista','Quadro de luz e disjuntor',3),
  ('eletricista','Luminárias e ventilador',4),('eletricista','Fiação nova',5),('eletricista','Padrão de entrada',6),
  ('encanador','Vazamentos',1),('encanador','Desentupimento',2),('encanador','Instalação de louças',3),
  ('diarista','Limpeza completa',1),('diarista','Limpeza pesada',2),('diarista','Pós-obra',3),('diarista','Passar roupa',4),
  ('diarista','Organização',5),('diarista','Vidros e janelas',6),
  ('jardineiro','Corte de grama',1),('jardineiro','Poda de árvores e cercas',2),('jardineiro','Limpeza de terreno',3),
  ('jardineiro','Plantio',4),('jardineiro','Manutenção mensal',5),('jardineiro','Recolher entulho verde',6),
  ('montador','Montagem',1),('montador','Desmontagem',2),('montador','Instalação de prateleiras',3)
) AS s(prof, nome, ordem)
JOIN profissao pr ON pr.codigo = s.prof;
