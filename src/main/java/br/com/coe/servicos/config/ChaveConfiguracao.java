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
    VERSAO_TERMOS,
    DIARIA_MINIMA,
    MEIA_DIARIA_MINIMA,
    SOBRE_MINIMO_CARACTERES,
    /** RN44d: quantas faltas acendem o alerta do profissional no painel (DB-14). */
    FALTAS_ALERTA,
    /** RN44d: a janela, em dias, para contar as faltas (DB-14). */
    FALTAS_JANELA_DIAS
}
