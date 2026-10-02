-- =============================================================================
-- COE Serviços · V10 — Busca de profissionais
-- Filtros: profissão (principal ou outras), cidade dentro do raio, dia livre,
-- valor, experiência e nota. Só perfis 'ativo'.
-- =============================================================================

-- Distância em km entre dois pontos (Haversine) — usada para o raio de atendimento
CREATE OR REPLACE FUNCTION fn_distancia_km(lat1 numeric, lon1 numeric, lat2 numeric, lon2 numeric)
RETURNS numeric
LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT AS $$
  SELECT round((6371 * 2 * asin(sqrt(
           power(sin(radians((lat2 - lat1)::float8) / 2), 2) +
           cos(radians(lat1::float8)) * cos(radians(lat2::float8)) *
           power(sin(radians((lon2 - lon1)::float8) / 2), 2))))::numeric, 1)
$$;

CREATE INDEX ix_busca_profissional ON profissional (profissao_principal_id, valor_diaria)
  WHERE status = 'ativo';
CREATE INDEX ix_busca_nota ON profissional (nota_media DESC NULLS LAST, total_avaliacoes DESC)
  WHERE status = 'ativo';
CREATE INDEX ix_busca_cidade_base ON profissional (cidade_base_id) WHERE status = 'ativo';
CREATE INDEX ix_usuario_nome_busca ON usuario USING gin (f_sem_acento(nome) gin_trgm_ops);
CREATE INDEX ix_servico_nome_busca ON servico USING gin (f_sem_acento(nome) gin_trgm_ops);

-- Quem atende cada cidade: pelo raio da cidade base OU por cidade marcada
CREATE VIEW profissional_atende_cidade WITH (security_invoker = true) AS
SELECT p.id AS profissional_id, c.id AS cidade_id,
       fn_distancia_km(cb.latitude, cb.longitude, c.latitude, c.longitude) AS distancia_km
FROM profissional p
JOIN cidade cb ON cb.id = p.cidade_base_id
JOIN cidade c  ON c.ativa
WHERE p.status = 'ativo'
  AND (fn_distancia_km(cb.latitude, cb.longitude, c.latitude, c.longitude) <= p.raio_km
       OR EXISTS (SELECT 1 FROM profissional_cidade pc
                  WHERE pc.profissional_id = p.id AND pc.cidade_id = c.id));
