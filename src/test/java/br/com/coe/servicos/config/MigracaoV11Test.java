package br.com.coe.servicos.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.postgresql.util.PSQLException;

/**
 * DB-12: correções HIGH e lacunas do schema frente às regras de negócio, aplicadas na V11.
 * Cada grupo prova que o banco barra o inválido e aceita o válido. A preparação fica fora
 * do {@code erroDoBanco}: dentro dele só o comando que deve falhar.
 */
class MigracaoV11Test extends BancoIntegracaoTest {

    @Nested
    @DisplayName("webhook: só evento com assinatura válida ocupa o id, com corpo bruto guardado (RNF06)")
    class Webhook {

        @Test
        @DisplayName("barra dois eventos válidos com o mesmo id do mesmo gateway")
        void barraEventoValidoRepetido() {
            fixtures.eventoGateway("falso", "evt_1", true);

            PSQLException erro = erroDoBanco(() -> fixtures.eventoGateway("falso", "evt_1", true));

            assertConstraint(erro, UNIQUE_VIOLATION, "uq_evento_gateway");
        }

        @Test
        @DisplayName("evento forjado (assinatura inválida) não impede o legítimo com o mesmo id")
        void forjadoNaoEnvenenaOLegitimo() {
            fixtures.eventoGateway("falso", "evt_2", false);
            fixtures.eventoGateway("falso", "evt_2", false);

            assertThatCode(() -> fixtures.eventoGateway("falso", "evt_2", true)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("exige o corpo bruto do webhook")
        void exigeCorpoBruto() {
            PSQLException erro = erroDoBanco(() -> jdbc.update("""
                    INSERT INTO evento_gateway (gateway, id_evento, tipo, payload, assinatura_valida)
                    VALUES ('falso', 'evt_3', 'cobranca.confirmada', '{}'::jsonb, true)
                    """));

            assertThat(erro.getSQLState()).isEqualTo(NOT_NULL_VIOLATION);
            assertThat(erro.getServerErrorMessage().getColumn()).isEqualTo("corpo_bruto");
        }

        @ParameterizedTest(name = "barra alterar {0}")
        @CsvSource(delimiter = '|', quoteCharacter = '~', textBlock = """
                payload           | '{"x":1}'::jsonb
                corpo_bruto       | convert_to('outro', 'UTF8')
                assinatura_valida | false
                id_evento         | 'evt_outro'
                gateway           | 'outro'
                tipo              | 'outro.tipo'
                recebido_em       | now() - interval '1 day'
                """)
        void barraAlterarConteudo(String coluna, String novoValor) {
            UUID evento = fixtures.eventoGateway("falso", "evt_4", true);

            PSQLException erro = erroDoBanco(() -> jdbc.update(
                    "UPDATE evento_gateway SET " + coluna + " = " + novoValor + " WHERE id = ?", evento));

            assertMensagem(erro, INSUFFICIENT_PRIVILEGE, "só processado_em e erro");
        }

        @Test
        @DisplayName("aceita marcar o evento como processado ou com erro")
        void aceitaMarcarProcessamento() {
            UUID evento = fixtures.eventoGateway("falso", "evt_5", true);

            assertThatCode(() -> jdbc.update(
                    "UPDATE evento_gateway SET processado_em = now(), erro = 'falhou' WHERE id = ?", evento))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("barra marcar como processado um evento com assinatura inválida")
        void barraProcessarForjado() {
            UUID evento = fixtures.eventoGateway("falso", "evt_6", false);

            PSQLException erro = erroDoBanco(() -> jdbc.update(
                    "UPDATE evento_gateway SET processado_em = now() WHERE id = ?", evento));

            assertConstraint(erro, CHECK_VIOLATION, "ck_evento_processado_valido");
        }

        @Test
        @DisplayName("barra corpo bruto acima de 1 MiB")
        void barraCorpoGrande() {
            PSQLException erro = erroDoBanco(() -> jdbc.update("""
                    INSERT INTO evento_gateway (gateway, id_evento, tipo, payload, corpo_bruto, assinatura_valida)
                    VALUES ('falso', 'evt_7', 'cobranca.confirmada', '{}'::jsonb,
                            convert_to(repeat('x', 1048577), 'UTF8'), false)
                    """));

            assertConstraint(erro, CHECK_VIOLATION, "ck_evento_corpo_tamanho");
        }
    }

    @Nested
    @DisplayName("uma liberação OU um reembolso por diária (RN39, RNF11)")
    class DestinoUnicoDaDiaria {

        @Test
        @DisplayName("barra liberar e reembolsar a mesma diária")
        void barraLiberarEReembolsar() {
            UUID diaria = fixtures.diariaAvulsa(DIA);
            fixtures.transacaoFinanceira("liberacao", "liberacao:" + diaria, diaria, null);

            PSQLException erro = erroDoBanco(
                    () -> fixtures.transacaoFinanceira("reembolso", "reembolso:" + diaria, diaria, null));

            assertConstraint(erro, UNIQUE_VIOLATION, "uq_transacao_diaria_destino");
        }

        @Test
        @DisplayName("barra duas liberações da mesma diária com chaves diferentes")
        void barraDuasLiberacoes() {
            UUID diaria = fixtures.diariaAvulsa(DIA);
            fixtures.transacaoFinanceira("liberacao", "liberacao:a:" + diaria, diaria, null);

            PSQLException erro = erroDoBanco(
                    () -> fixtures.transacaoFinanceira("liberacao", "liberacao:b:" + diaria, diaria, null));

            assertConstraint(erro, UNIQUE_VIOLATION, "uq_transacao_diaria_destino");
        }

        @Test
        @DisplayName("aceita liberar diárias diferentes")
        void aceitaDiariasDiferentes() {
            UUID primeira = fixtures.diariaAvulsa(DIA);
            UUID segunda = fixtures.diariaAvulsa(DIA);
            fixtures.transacaoFinanceira("liberacao", "liberacao:" + primeira, primeira, null);

            assertThatCode(() -> fixtures.transacaoFinanceira("liberacao", "liberacao:" + segunda, segunda, null))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("barra liberação ou reembolso sem diária (o NULL furaria a unicidade)")
        void barraLiberacaoSemDiaria() {
            PSQLException erro = erroDoBanco(
                    () -> fixtures.transacaoFinanceira("liberacao", "liberacao:sem-diaria", null, null));

            assertConstraint(erro, CHECK_VIOLATION, "ck_transacao_diaria_obrigatoria");
        }

        @Test
        @DisplayName("barra pagamento sem cobrança")
        void barraPagamentoSemCobranca() {
            PSQLException erro = erroDoBanco(
                    () -> fixtures.transacaoFinanceira("pagamento", "pagamento:sem-cobranca", null, null));

            assertConstraint(erro, CHECK_VIOLATION, "ck_transacao_pagamento_cobranca");
        }

        @Test
        @DisplayName("aceita pagamento com cobrança")
        void aceitaPagamentoComCobranca() {
            UUID cobranca = fixtures.cobranca(fixtures.contrato(fixtures.usuario(), fixtures.profissional()));

            assertThatCode(() -> fixtures.transacaoFinanceira("pagamento", "pagamento:" + cobranca, null, cobranca))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("as 10 cidades do lançamento vêm de migração versionada (o perfil de teste não carrega o seed local)")
    void cidadesDoLancamentoVersionadas() {
        List<Integer> codigos = jdbc.queryForList("SELECT codigo_ibge FROM cidade WHERE ativa", Integer.class);

        assertThat(codigos).contains(4202008, 4202404, 4202909, 4205902, 4207502,
                4208203, 4208906, 4211306, 4213203, 4218202);
    }

    @Nested
    @DisplayName("pedido: serviços marcados e descrição opcional (RN27)")
    class Pedido {

        @ParameterizedTest(name = "barra descrição com {0} caracteres úteis")
        @ValueSource(ints = {14, 601})
        void barraDescricaoForaDaFaixa(int tamanho) {
            String descricao = "x".repeat(tamanho);

            PSQLException erro = erroDoBanco(() -> fixtures.contratoComDescricao(descricao));

            assertConstraint(erro, CHECK_VIOLATION, "ck_contrato_descricao");
        }

        @Test
        @DisplayName("barra descrição só com espaços")
        void barraDescricaoEmBranco() {
            PSQLException erro = erroDoBanco(() -> fixtures.contratoComDescricao(" ".repeat(20)));

            assertConstraint(erro, CHECK_VIOLATION, "ck_contrato_descricao");
        }

        @ParameterizedTest(name = "aceita descrição com {0} caracteres")
        @ValueSource(ints = {15, 600})
        void aceitaDescricaoNaFaixa(int tamanho) {
            assertThatCode(() -> fixtures.contratoComDescricao("x".repeat(tamanho))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("aceita contrato sem descrição (a regra serviço OU descrição fica no serviço)")
        void aceitaSemDescricao() {
            assertThatCode(() -> fixtures.contratoComDescricao(null)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("guarda os serviços marcados, sem repetir o mesmo serviço no contrato")
        void servicosDoPedido() {
            UUID contrato = fixtures.contratoComDescricao(null);
            String inserir = """
                    INSERT INTO contrato_servico (contrato_id, servico_id)
                    VALUES (?, (SELECT s.id FROM servico s JOIN profissao p ON p.id = s.profissao_id
                                WHERE p.codigo = 'pedreiro' AND s.nome = 'Reboco'))
                    """;
            jdbc.update(inserir, contrato);

            PSQLException erro = erroDoBanco(() -> jdbc.update(inserir, contrato));

            assertConstraint(erro, UNIQUE_VIOLATION, "pk_contrato_servico");
        }
    }

    @Nested
    @DisplayName("status do profissional: pausado e correção pedida (RN20, RN56, RN13)")
    class StatusProfissional {

        @Test
        @DisplayName("aceita pausar um perfil completo e já aprovado")
        void aceitaPausarAprovado() {
            UUID profissional = fixtures.profissionalEmAnalise();

            assertThatCode(() -> jdbc.update(
                    "UPDATE profissional SET status = 'pausado', aprovado_em = now() WHERE id = ?", profissional))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("barra pausar um perfil que nunca foi aprovado")
        void barraPausarSemAprovacao() {
            UUID profissional = fixtures.profissionalEmAnalise();

            PSQLException erro = erroDoBanco(
                    () -> jdbc.update("UPDATE profissional SET status = 'pausado' WHERE id = ?", profissional));

            assertConstraint(erro, CHECK_VIOLATION, "ck_profissional_pausado");
        }

        @Test
        @DisplayName("barra pausar um perfil em rascunho (incompleto)")
        void barraPausarIncompleto() {
            UUID profissional = fixtures.profissional();

            PSQLException erro = erroDoBanco(() -> jdbc.update(
                    "UPDATE profissional SET status = 'pausado', aprovado_em = now() WHERE id = ?", profissional));

            assertConstraint(erro, CHECK_VIOLATION, "ck_profissional_completo");
        }

        @Test
        @DisplayName("barra pedir correção sem motivo")
        void barraCorrecaoSemMotivo() {
            UUID profissional = fixtures.profissionalEmAnalise();

            PSQLException erro = erroDoBanco(() -> jdbc.update(
                    "UPDATE profissional SET status = 'correcao_pedida' WHERE id = ?", profissional));

            assertConstraint(erro, CHECK_VIOLATION, "ck_profissional_correcao");
        }

        @Test
        @DisplayName("aceita pedir correção com motivo")
        void aceitaCorrecaoComMotivo() {
            UUID profissional = fixtures.profissionalEmAnalise();

            assertThatCode(() -> jdbc.update("""
                    UPDATE profissional SET status = 'correcao_pedida', motivo_correcao = 'Selfie sem o documento'
                    WHERE id = ?""", profissional)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("documento: correção pedida exige motivo (RN13)")
    class CorrecaoDocumento {

        private static final String PEDIR_CORRECAO = """
                UPDATE documento_verificacao
                SET status = 'correcao_pedida', analisado_por = ?, analisado_em = now(), motivo_correcao = ?
                WHERE id = ?""";

        @Test
        @DisplayName("barra pedir correção do documento sem motivo")
        void barraSemMotivo() {
            UUID documento = fixtures.documentoPendente(fixtures.profissionalEmAnalise());
            UUID admin = fixtures.usuario();

            PSQLException erro = erroDoBanco(() -> jdbc.update(PEDIR_CORRECAO, admin, null, documento));

            assertConstraint(erro, CHECK_VIOLATION, "ck_documento_correcao");
        }

        @Test
        @DisplayName("aceita pedir correção do documento com motivo")
        void aceitaComMotivo() {
            UUID documento = fixtures.documentoPendente(fixtures.profissionalEmAnalise());
            UUID admin = fixtures.usuario();

            assertThatCode(() -> jdbc.update(PEDIR_CORRECAO, admin, "Foto ilegível", documento))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("data de nascimento obrigatória fora do rascunho (RN08)")
    class DataNascimento {

        @Test
        @DisplayName("barra cadastro enviado para análise sem data de nascimento")
        void barraSemDataNascimento() {
            PSQLException erro = erroDoBanco(
                    () -> fixtures.profissionalEmAnalise(null, FixturesBanco.RAIO_PADRAO_KM));

            assertConstraint(erro, CHECK_VIOLATION, "ck_profissional_completo");
        }

        @Test
        @DisplayName("barra data de nascimento absurda")
        void barraDataAbsurda() {
            PSQLException erro = erroDoBanco(() -> fixtures.profissionalEmAnalise(
                    LocalDate.of(1899, 12, 31), FixturesBanco.RAIO_PADRAO_KM));

            assertConstraint(erro, CHECK_VIOLATION, "ck_profissional_nascimento");
        }
    }

    @Nested
    @DisplayName("raio de atendimento só 5, 10, 20 ou 40 km (RN17)")
    class Raio {

        @ParameterizedTest(name = "aceita {0} km")
        @ValueSource(ints = {5, 10, 20, 40})
        void aceitaRaiosPermitidos(int raio) {
            assertThatCode(() -> fixtures.profissionalEmAnalise(FixturesBanco.NASCIMENTO_ADULTO, raio))
                    .doesNotThrowAnyException();
        }

        @ParameterizedTest(name = "barra {0} km")
        @ValueSource(ints = {1, 15, 50})
        void barraRaioForaDaLista(int raio) {
            PSQLException erro = erroDoBanco(
                    () -> fixtures.profissionalEmAnalise(FixturesBanco.NASCIMENTO_ADULTO, raio));

            assertConstraint(erro, CHECK_VIOLATION, "ck_profissional_raio");
        }
    }

    @Nested
    @DisplayName("configuração: valor conforme o tipo, faixas por chave e LC 150 até 2 (RN52)")
    class Configuracao {

        private static final String INSERIR = """
                INSERT INTO configuracao (chave, valor, tipo, descricao, vigente_desde)
                VALUES (?, ?, ?, 'teste', now() + interval '1 day')""";

        @ParameterizedTest(name = "barra {0} = ''{1}'' ({2}) por {3}")
        @CsvSource({
                "OUTRO_DECIMAL,           abc,          decimal,  ck_configuracao_valor_tipo",
                "OUTRO_DECIMAL,           '1,5',        decimal,  ck_configuracao_valor_tipo",
                "OUTRO_DECIMAL,           1.,           decimal,  ck_configuracao_valor_tipo",
                "OUTRO_INTEIRO,           9999999999,   inteiro,  ck_configuracao_valor_tipo",
                "COMISSAO,                1.5,          decimal,  ck_configuracao_faixas",
                "COMISSAO,                -0.1,         decimal,  ck_configuracao_faixas",
                "COMISSAO,                abc,          texto,    ck_configuracao_faixas",
                "AUTO_LIBERA_HORAS,       0,            inteiro,  ck_configuracao_faixas",
                "TAXA_PAGA_POR,           ninguem,      texto,    ck_configuracao_faixas",
                "LIMITE_DOMESTICO_SEMANA, 3,            inteiro,  ck_configuracao_limite_lc150",
                "LIMITE_DOMESTICO_SEMANA, 0,            inteiro,  ck_configuracao_limite_lc150",
                "LIMITE_DOMESTICO_SEMANA, 99,           inteiro,  ck_configuracao_limite_lc150",
                "LIMITE_DOMESTICO_SEMANA, abc,          texto,    ck_configuracao_limite_lc150",
                "NOTIFICAR_POR_EMAIL,     sim,          booleano, ck_configuracao_valor_tipo"
        })
        void barraValorInvalido(String chave, String valor, String tipo, String constraint) {
            PSQLException erro = erroDoBanco(() -> jdbc.update(INSERIR, chave, valor, tipo));

            assertConstraint(erro, CHECK_VIOLATION, constraint);
        }

        @ParameterizedTest(name = "aceita {0} = ''{1}'' ({2})")
        @CsvSource({
                "COMISSAO,                0.10,         decimal",
                "COMISSAO,                0,            decimal",
                "AUTO_LIBERA_HORAS,       24,           inteiro",
                "TAXA_PAGA_POR,           profissional, texto",
                "LIMITE_DOMESTICO_SEMANA, 2,            inteiro",
                "LIMITE_DOMESTICO_SEMANA, 1,            inteiro",
                "NOTIFICAR_POR_EMAIL,     true,         booleano"
        })
        void aceitaValorValido(String chave, String valor, String tipo) {
            assertThatCode(() -> jdbc.update(INSERIR, chave, valor, tipo)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("trava otimista: versao sobe em todo UPDATE, mesmo fora do JPA (RN39)")
    class Versao {

        @Test
        @DisplayName("UPDATE por SQL que não mexe em versao incrementa a versão")
        void incrementaSemJpa() {
            UUID usuario = fixtures.usuario();

            jdbc.update("UPDATE usuario SET nome = 'Outro nome' WHERE id = ?", usuario);

            assertThat(versaoDo(usuario)).isEqualTo(1L);
        }

        @Test
        @DisplayName("UPDATE no estilo do Hibernate (versao + 1) não incrementa duas vezes")
        void compativelComHibernate() {
            UUID usuario = fixtures.usuario();

            jdbc.update("UPDATE usuario SET nome = 'Outro nome', versao = versao + 1 WHERE id = ? AND versao = 0",
                    usuario);

            assertThat(versaoDo(usuario)).isEqualTo(1L);
        }

        private Long versaoDo(UUID usuario) {
            return jdbc.queryForObject("SELECT versao FROM usuario WHERE id = ?", Long.class, usuario);
        }
    }

    @Nested
    @DisplayName("ninguém contrata a si mesmo")
    class AutoContratacao {

        @Test
        @DisplayName("barra criar contrato em que o cliente é o próprio profissional")
        void barraNoInsert() {
            UUID usuario = fixtures.usuario();
            UUID profissional = fixtures.profissionalDoUsuario(usuario);

            PSQLException erro = erroDoBanco(() -> fixtures.contrato(usuario, profissional));

            assertGatilho(erro);
        }

        @Test
        @DisplayName("barra trocar o cliente do contrato pelo próprio profissional")
        void barraNoUpdate() {
            UUID usuarioDoProfissional = fixtures.usuario();
            UUID profissional = fixtures.profissionalDoUsuario(usuarioDoProfissional);
            UUID contrato = fixtures.contrato(fixtures.usuario(), profissional);

            PSQLException erro = erroDoBanco(() -> jdbc.update(
                    "UPDATE contrato SET cliente_id = ? WHERE id = ?", usuarioDoProfissional, contrato));

            assertGatilho(erro);
        }

        private void assertGatilho(PSQLException erro) {
            assertThat(erro.getSQLState()).isEqualTo(CHECK_VIOLATION);
            assertThat(erro.getServerErrorMessage().getWhere()).contains("fn_contrato_sem_autocontratacao");
        }
    }

    @Nested
    @DisplayName("estados finais exigem seus carimbos de tempo")
    class CarimbosDeEstado {

        @Test
        @DisplayName("barra contrato concluído sem data de conclusão")
        void barraContratoConcluidoSemData() {
            UUID contrato = fixtures.contrato(fixtures.usuario(), fixtures.profissional());

            PSQLException erro = erroDoBanco(() -> jdbc.update(
                    "UPDATE contrato SET status = 'concluido', pago_em = now() WHERE id = ?", contrato));

            assertConstraint(erro, CHECK_VIOLATION, "ck_contrato_concluido");
        }

        @Test
        @DisplayName("aceita contrato concluído com data de conclusão")
        void aceitaContratoConcluido() {
            UUID contrato = fixtures.contrato(fixtures.usuario(), fixtures.profissional());

            assertThatCode(() -> jdbc.update("""
                    UPDATE contrato SET status = 'concluido', pago_em = now(), concluido_em = now()
                    WHERE id = ?""", contrato)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("barra cobrança estornada sem ter sido confirmada")
        void barraCobrancaEstornadaSemConfirmacao() {
            UUID cobranca = fixtures.cobranca(fixtures.contrato(fixtures.usuario(), fixtures.profissional()));

            PSQLException erro = erroDoBanco(
                    () -> jdbc.update("UPDATE cobranca SET status = 'estornada' WHERE id = ?", cobranca));

            assertConstraint(erro, CHECK_VIOLATION, "ck_cobranca_estornada");
        }

        @Test
        @DisplayName("aceita cobrança estornada que foi confirmada")
        void aceitaCobrancaEstornada() {
            UUID cobranca = fixtures.cobranca(fixtures.contrato(fixtures.usuario(), fixtures.profissional()));

            assertThatCode(() -> jdbc.update(
                    "UPDATE cobranca SET status = 'estornada', confirmada_em = now() WHERE id = ?", cobranca))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("barra reembolso confirmado sem data de conclusão e sem transação")
        void barraReembolsoConfirmadoSemNada() {
            UUID reembolso = inserirReembolso(prepararReembolso(), fixtures.idExterno());

            PSQLException erro = erroDoBanco(
                    () -> jdbc.update("UPDATE reembolso SET status = 'confirmado' WHERE id = ?", reembolso));

            assertConstraint(erro, CHECK_VIOLATION, "ck_reembolso_confirmado");
        }

        @Test
        @DisplayName("barra reembolso confirmado com data de conclusão mas sem transação")
        void barraReembolsoConfirmadoSemTransacao() {
            UUID reembolso = inserirReembolso(prepararReembolso(), fixtures.idExterno());

            PSQLException erro = erroDoBanco(() -> jdbc.update(
                    "UPDATE reembolso SET status = 'confirmado', concluido_em = now() WHERE id = ?", reembolso));

            assertConstraint(erro, CHECK_VIOLATION, "ck_reembolso_confirmado");
        }

        @Test
        @DisplayName("aceita reembolso confirmado com data de conclusão e transação")
        void aceitaReembolsoConfirmado() {
            DadosReembolso dados = prepararReembolso();
            UUID reembolso = inserirReembolso(dados, fixtures.idExterno());
            UUID transacao = fixtures.transacaoFinanceira(
                    "reembolso", "reembolso:" + dados.diaria(), dados.diaria(), dados.cobranca());

            assertThatCode(() -> jdbc.update("""
                    UPDATE reembolso SET status = 'confirmado', concluido_em = now(), transacao_id = ?
                    WHERE id = ?""", transacao, reembolso)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("id externo do gateway é único em repasse e reembolso (quando preenchido)")
    class IdExterno {

        private static final String INSERIR_REPASSE = """
                INSERT INTO repasse (profissional_id, valor, chave_pix_tipo, chave_pix_cifrada, id_externo)
                VALUES (?, 280.00, 'cpf', '\\x02'::bytea, ?)""";

        @Test
        @DisplayName("barra dois repasses com o mesmo id externo")
        void barraRepasseRepetido() {
            UUID profissional = fixtures.profissional();
            String idExterno = fixtures.idExterno();
            jdbc.update(INSERIR_REPASSE, profissional, idExterno);

            PSQLException erro = erroDoBanco(() -> jdbc.update(INSERIR_REPASSE, profissional, idExterno));

            assertConstraint(erro, UNIQUE_VIOLATION, "uq_repasse_externo");
        }

        @Test
        @DisplayName("aceita vários repasses ainda sem id externo")
        void aceitaRepassesSemIdExterno() {
            UUID profissional = fixtures.profissional();
            jdbc.update(INSERIR_REPASSE, profissional, null);

            assertThatCode(() -> jdbc.update(INSERIR_REPASSE, profissional, null)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("barra dois reembolsos com o mesmo id externo")
        void barraReembolsoRepetido() {
            String idExterno = fixtures.idExterno();
            inserirReembolso(prepararReembolso(), idExterno);
            DadosReembolso outro = prepararReembolso();

            PSQLException erro = erroDoBanco(() -> inserirReembolso(outro, idExterno));

            assertConstraint(erro, UNIQUE_VIOLATION, "uq_reembolso_externo");
        }

        @Test
        @DisplayName("aceita vários reembolsos ainda sem id externo")
        void aceitaReembolsosSemIdExterno() {
            inserirReembolso(prepararReembolso(), null);
            DadosReembolso outro = prepararReembolso();

            assertThatCode(() -> inserirReembolso(outro, null)).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("cpf_hash tem exatamente 32 bytes (HMAC-SHA256)")
    void cpfHashCom32Bytes() {
        UUID profissional = fixtures.profissional();

        PSQLException erro = erroDoBanco(() -> jdbc.update(
                "UPDATE profissional SET cpf_hash = '\\x0102'::bytea WHERE id = ?", profissional));

        assertConstraint(erro, CHECK_VIOLATION, "ck_profissional_cpf_hash");
    }

    @Test
    @DisplayName("a extensão pgcrypto não é mais instalada (gen_random_uuid é nativa)")
    void semPgcrypto() {
        Integer instaladas = jdbc.queryForObject(
                "SELECT count(*) FROM pg_extension WHERE extname = 'pgcrypto'", Integer.class);

        assertThat(instaladas).isZero();
    }

    @Nested
    @DisplayName("exclusão de conta por anonimização (RN60) e provas preservadas")
    class ExclusaoDeConta {

        @Test
        @DisplayName("barra DELETE de usuário")
        void barraDeleteDeUsuario() {
            UUID usuario = fixtures.usuario();

            PSQLException erro = erroDoBanco(() -> jdbc.update("DELETE FROM usuario WHERE id = ?", usuario));

            assertMensagem(erro, INSUFFICIENT_PRIVILEGE, "anonimiz");
        }

        @Test
        @DisplayName("aceita usuário excluído sem celular e sem senha (anonimizado)")
        void aceitaAnonimizado() {
            UUID usuario = fixtures.usuario();

            assertThatCode(() -> jdbc.update("""
                    UPDATE usuario SET status = 'excluido', excluido_em = now(), nome = 'Conta excluída',
                                       celular = NULL, email = NULL, senha_hash = NULL
                    WHERE id = ?""", usuario)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("barra usuário ativo sem celular")
        void barraAtivoSemCelular() {
            UUID usuario = fixtures.usuario();

            PSQLException erro = erroDoBanco(
                    () -> jdbc.update("UPDATE usuario SET celular = NULL WHERE id = ?", usuario));

            assertConstraint(erro, CHECK_VIOLATION, "ck_usuario_credenciais");
        }

        @Test
        @DisplayName("barra usuário ativo sem senha")
        void barraAtivoSemSenha() {
            UUID usuario = fixtures.usuario();

            PSQLException erro = erroDoBanco(
                    () -> jdbc.update("UPDATE usuario SET senha_hash = NULL WHERE id = ?", usuario));

            assertConstraint(erro, CHECK_VIOLATION, "ck_usuario_credenciais");
        }

        @Test
        @DisplayName("barra apagar diária que tem foto de evidência")
        void evidenciaPreservada() {
            UUID diaria = fixtures.diariaAvulsa(DIA);
            jdbc.update("""
                    INSERT INTO evidencia_diaria (diaria_id, tipo, chave_arquivo)
                    VALUES (?, 'chegada', ?)""", diaria, "evidencias/" + UUID.randomUUID());

            PSQLException erro = erroDoBanco(() -> jdbc.update("DELETE FROM diaria WHERE id = ?", diaria));

            assertConstraint(erro, FOREIGN_KEY_VIOLATION, "fk_evidencia_diaria");
        }

        @Test
        @DisplayName("aceite dos termos, evidências e anexos de disputa não apagam em cascata")
        void semCascataEmProvas() {
            List<String> acoes = jdbc.queryForList("""
                    SELECT conname || '=' || confdeltype::text FROM pg_constraint
                    WHERE conname IN ('fk_aceite_termos_usuario', 'fk_evidencia_diaria', 'fk_anexo_disputa')
                    ORDER BY 1""", String.class);

            assertThat(acoes).containsExactly(
                    "fk_aceite_termos_usuario=r", "fk_anexo_disputa=r", "fk_evidencia_diaria=r");
        }
    }

    private record DadosReembolso(UUID diaria, UUID cobranca) {
    }

    private DadosReembolso prepararReembolso() {
        UUID cliente = fixtures.usuario();
        UUID profissional = fixtures.profissional();
        UUID contrato = fixtures.contrato(cliente, profissional);
        return new DadosReembolso(fixtures.diaria(contrato, cliente, profissional, DIA), fixtures.cobranca(contrato));
    }

    private UUID inserirReembolso(DadosReembolso dados, String idExterno) {
        return jdbc.queryForObject("""
                INSERT INTO reembolso (diaria_id, cobranca_id, motivo, valor_diaria, valor_comissao,
                                       valor_total, id_externo)
                VALUES (?, ?, 'disputa', 280.00, 28.00, 308.00, ?) RETURNING id""",
                UUID.class, dados.diaria(), dados.cobranca(), idExterno);
    }
}
