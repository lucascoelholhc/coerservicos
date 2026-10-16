-- =============================================================================
-- COE Serviços · V17 — Parâmetros do cadastro do profissional (DOM-02)
-- RN09: valor mínimo da diária (R$ 80) e da meia diária (R$ 50, sempre menor que a diária).
-- RN10: o "Sobre você" precisa de pelo menos 25 caracteres.
-- Números de negócio ficam na configuracao (nunca no código); mudar = nova vigência.
-- =============================================================================

INSERT INTO configuracao (chave, valor, tipo, descricao, vigente_desde) VALUES
 ('DIARIA_MINIMA',           '80.00', 'decimal', 'Valor mínimo da diária do profissional (RN09)',                   '2026-01-01'),
 ('MEIA_DIARIA_MINIMA',      '50.00', 'decimal', 'Valor mínimo da meia diária; sempre menor que a diária (RN09)', '2026-01-01'),
 ('SOBRE_MINIMO_CARACTERES', '25',    'inteiro', 'Tamanho mínimo do texto "Sobre você" do profissional (RN10)',   '2026-01-01');
