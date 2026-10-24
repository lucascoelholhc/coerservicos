-- =============================================================================
-- COE Serviços · V19 — Link de pagamento da cobrança (DOM-07)
-- O gateway (Asaas: invoiceUrl) devolve a página onde o cliente paga com cartão (ou Pix): a COE nunca vê dado
-- de cartão. Coluna nova, sem valor padrão: nenhuma reescrita da tabela e nenhuma cobrança real fora do local.
-- =============================================================================

ALTER TABLE cobranca
    ADD COLUMN link_pagamento text,
    ADD CONSTRAINT ck_cobranca_link
        CHECK (link_pagamento IS NULL OR (link_pagamento ~ '^https?://' AND length(link_pagamento) <= 500));
