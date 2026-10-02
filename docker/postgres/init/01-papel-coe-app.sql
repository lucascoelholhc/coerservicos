-- Roda só na primeira subida do volume (docker-entrypoint-initdb.d).
-- A V1 também cria o papel se faltar, mas exige CREATEROLE; aqui garantimos que ele existe.
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'coe_app') THEN
    CREATE ROLE coe_app NOLOGIN;
  END IF;
END $$;
