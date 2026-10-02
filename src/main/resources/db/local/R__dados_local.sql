-- =============================================================================
-- COE Serviços · R__dados_local — SOMENTE no perfil local (nunca em produção)
-- Configure no application-local.yml:
--   spring.flyway.locations: classpath:db/migration,classpath:db/local
-- Repeatable: reaplicado sempre que este arquivo mudar.
--
-- Profissionais e contratos de exemplo NÃO ficam aqui: CPF, Pix e endereço são
-- cifrados com a chave da aplicação, então essa massa é criada por um seeder
-- Java no perfil local (DB-11), usando os mesmos dados do protótipo.
-- =============================================================================

-- Cidades do lançamento (Vale do Itajaí e vizinhas).
-- Códigos IBGE e coordenadas a conferir na tabela oficial do IBGE antes de produção.
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
ON CONFLICT (codigo_ibge) DO UPDATE
  SET nome = EXCLUDED.nome, latitude = EXCLUDED.latitude,
      longitude = EXCLUDED.longitude, ativa = EXCLUDED.ativa;

-- Usuários locais de teste — senha de todos: coe-local-123 (BCrypt)
INSERT INTO usuario (id, nome, celular, celular_verificado_em, email, senha_hash, cep, status)
VALUES
 ('00000000-0000-0000-0000-00000000a001', 'Admin COE (local)', '47900000001', now(), 'admin@coe.local',
  '$2b$10$zOKDWSG5FMKsE/sN70vnTu1CwZwHPYncMFywJ0QaR6x2ZhP3KE23y', '89010000', 'ativo'),
 ('00000000-0000-0000-0000-00000000c001', 'Juliana (cliente de teste)', '47996553021', now(), 'juliana@coe.local',
  '$2b$10$zOKDWSG5FMKsE/sN70vnTu1CwZwHPYncMFywJ0QaR6x2ZhP3KE23y', '89010000', 'ativo')
ON CONFLICT (id) DO NOTHING;

UPDATE usuario SET cidade_id = (SELECT id FROM cidade WHERE codigo_ibge = 4202404)
 WHERE id IN ('00000000-0000-0000-0000-00000000a001','00000000-0000-0000-0000-00000000c001');

INSERT INTO usuario_papel (usuario_id, papel) VALUES
 ('00000000-0000-0000-0000-00000000a001', 'ADMIN'),
 ('00000000-0000-0000-0000-00000000c001', 'CLIENTE')
ON CONFLICT DO NOTHING;
