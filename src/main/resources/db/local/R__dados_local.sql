-- =============================================================================
-- COE Serviços · R__dados_local — SOMENTE no perfil local (nunca em produção)
-- Configure no application-local.yml:
--   spring.flyway.locations: classpath:db/migration,classpath:db/local
-- Repeatable: reaplicado sempre que este arquivo mudar.
--
-- As cidades do lançamento vêm da V11 (valem em todos os ambientes).
-- Profissionais e contratos de exemplo NÃO ficam aqui: CPF, Pix e endereço são
-- cifrados com a chave da aplicação, então essa massa é criada por um seeder
-- Java no perfil local (DB-11), usando os mesmos dados do protótipo.
-- =============================================================================

-- Trava: o placeholder ${ambiente} só existe no application-local.yml. Em qualquer
-- outro ambiente o Flyway nem aplica este arquivo (placeholder sem valor) ou cai aqui.
DO $$
BEGIN
  IF '${ambiente}' <> 'local' THEN
    RAISE EXCEPTION 'R__dados_local só pode rodar no perfil local (ambiente = %)', '${ambiente}';
  END IF;
END $$;

-- Usuários locais de teste — senha de todos: coe-local-123 (BCrypt). Celulares fictícios.
INSERT INTO usuario (id, nome, celular, celular_verificado_em, email, senha_hash, cep, status, cidade_id)
VALUES
 ('00000000-0000-0000-0000-00000000a001', 'Admin COE (local)', '47900000001', now(), 'admin@coe.local',
  '$2b$10$zOKDWSG5FMKsE/sN70vnTu1CwZwHPYncMFywJ0QaR6x2ZhP3KE23y', '89010000', 'ativo',
  (SELECT id FROM cidade WHERE codigo_ibge = 4202404)),
 ('00000000-0000-0000-0000-00000000c001', 'Juliana (cliente de teste)', '47999990002', now(), 'juliana@coe.local',
  '$2b$10$zOKDWSG5FMKsE/sN70vnTu1CwZwHPYncMFywJ0QaR6x2ZhP3KE23y', '89010000', 'ativo',
  (SELECT id FROM cidade WHERE codigo_ibge = 4202404))
ON CONFLICT DO NOTHING;

-- Bancos locais criados antes desta versão do seed ainda têm o celular antigo da cliente
UPDATE usuario SET celular = '47999990002'
 WHERE id = '00000000-0000-0000-0000-00000000c001' AND celular IS DISTINCT FROM '47999990002';

INSERT INTO usuario_papel (usuario_id, papel) VALUES
 ('00000000-0000-0000-0000-00000000a001', 'ADMIN'),
 ('00000000-0000-0000-0000-00000000c001', 'CLIENTE')
ON CONFLICT DO NOTHING;
