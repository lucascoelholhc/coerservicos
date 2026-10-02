package br.com.coe.servicos.config;

/** Chaves da tabela {@code configuracao} que a aplicação usa (CORE-12). Nunca use a chave como texto solto. */
public enum ChaveConfiguracao {
    COMISSAO,
    TAXA_PAGA_POR,
    AUTO_LIBERA_HORAS,
    PRAZO_DISPUTA_HORAS,
    /** O nome é antigo: o valor é o limite de diárias numa janela móvel de 7 dias (RN51), não por semana fixa. */
    LIMITE_DOMESTICO_SEMANA,
    TENTATIVAS_ANTES_ANALISE,
    MEIA_DIARIA_HORAS,
    PAGAMENTO_EXPIRA_MINUTOS,
    VERSAO_TERMOS
}
