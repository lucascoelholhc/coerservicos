package br.com.coe.servicos.profissional;

import java.util.UUID;

/**
 * O admin suspendeu o perfil (RN44e). Publicado dentro da transação da suspensão: quem escuta (contrato) reembolsa as
 * diárias pagas e não iniciadas na mesma transação (RN44f), sem o pacote do profissional depender do de contratos.
 */
public record ProfissionalSuspenso(UUID profissional, UUID admin) {}
