package br.com.coe.servicos.compartilhado.notificacao;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.json.JsonMapper;

import br.com.coe.servicos.compartilhado.mensageria.MensagemPronta;
import br.com.coe.servicos.compartilhado.mensageria.MensagemPronta.Canal;
import br.com.coe.servicos.compartilhado.mensageria.RegistroDeEnvio;

/**
 * Avisos sem segredo ao cliente e ao profissional (DOM-11 parte 2b, RN35): depois do commit de quem pediu, numa
 * transação própria, uma linha de {@code notificacao} por canal e a mensagem na fila normal (rollback não avisa; erro
 * no aviso não desfaz a ação). Só para contato confirmado (RN61); se
 * nenhum canal preferido estiver confirmado, vai pelo outro (menos os avisos só por e-mail). O texto leva só o
 * primeiro nome de quem recebe e os {@code valores} dados (número do pedido, data); nunca contato, endereço, código ou
 * link. O despacho grava aqui se o aviso saiu ou falhou.
 */
@Component
public class Avisos implements RegistroDeEnvio {

    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM");
    /** Valores que vão no texto mas não na coluna {@code dados}: texto livre que já passou pela censura. */
    private static final Set<String> SO_NO_TEXTO = Set.of("nome", "mensagem");

    /** Cada aviso: tipo gravado, título (assunto do e-mail), texto e canais preferidos. */
    public enum Aviso {
        PAGAMENTO_CONFIRMADO(
                "pagamento.confirmado",
                "Pagamento confirmado",
                "COE: {nome}, recebemos o pagamento do pedido nº {pedido}. O profissional já foi avisado.",
                Set.of(Canal.EMAIL)),
        NOVO_TRABALHO(
                "pagamento.novo-trabalho",
                "Trabalho novo",
                "COE: {nome}, o pedido nº {pedido} foi pago. Veja os dias e o endereço no app.",
                Set.of(Canal.SMS, Canal.EMAIL)),
        CHEGUEI(
                "diaria.cheguei",
                "O profissional chegou",
                "COE: {nome}, o profissional chegou para a diária de {data} (pedido nº {pedido}).",
                Set.of(Canal.SMS)),
        TERMINEI(
                "diaria.terminei",
                "Dia terminado",
                "COE: {nome}, o profissional terminou a diária de {data} (pedido nº {pedido}). Veja as fotos e"
                        + " aprove o dia no app.",
                Set.of(Canal.SMS, Canal.EMAIL)),
        LIBERADA(
                "diaria.liberada",
                "Diária liberada",
                "COE: {nome}, a diária de {data} (pedido nº {pedido}) foi liberada para você.",
                Set.of(Canal.SMS)),
        DIARIA_CANCELADA(
                "diaria.cancelada",
                "Diária cancelada",
                "COE: {nome}, o cliente cancelou a diária de {data} (pedido nº {pedido}). O dia ficou livre na sua"
                        + " agenda.",
                Set.of(Canal.SMS)),
        DIARIA_REEMBOLSADA_SUSPENSAO(
                "diaria.reembolsada-suspensao",
                "Diária cancelada",
                "COE: {nome}, a diária de {data} (pedido nº {pedido}) foi cancelada porque o profissional não está"
                        + " atendendo. Você recebe de volta tudo o que pagou por ela.",
                Set.of(Canal.SMS, Canal.EMAIL)),
        DISPUTA_ABERTA(
                "disputa.aberta",
                "Reclamação aberta",
                "COE: {nome}, o cliente fez uma reclamação da diária de {data} (pedido nº {pedido}). Responda pelo"
                        + " app.",
                Set.of(Canal.SMS, Canal.EMAIL)),
        DISPUTA_DECIDIDA(
                "disputa.decidida",
                "Reclamação decidida",
                "COE: {nome}, a equipe decidiu a reclamação da diária de {data} (pedido nº {pedido}). Veja no app.",
                Set.of(Canal.SMS, Canal.EMAIL)),
        CADASTRO_APROVADO("cadastro.aprovado", "Cadastro aprovado", "{mensagem}", Set.of(Canal.SMS, Canal.EMAIL)),
        CADASTRO_CORRECAO("cadastro.correcao", "Corrija seu cadastro", "{mensagem}", Set.of(Canal.SMS, Canal.EMAIL)),
        CADASTRO_RECUSADO("cadastro.recusado", "Cadastro recusado", "{mensagem}", Set.of(Canal.SMS, Canal.EMAIL)),
        CONTA_SUSPENSA(
                "conta.suspensa",
                "Conta suspensa",
                "COE: {nome}, sua conta foi suspensa pela equipe da COE. Para saber mais, fale com o suporte.",
                Set.of(Canal.SMS, Canal.EMAIL)),
        CONTA_REATIVADA(
                "conta.reativada",
                "Conta reativada",
                "COE: {nome}, sua conta foi reativada. Você já pode usar o app de novo.",
                Set.of(Canal.SMS, Canal.EMAIL)),
        PERFIL_SUSPENSO(
                "profissional.suspenso",
                "Perfil suspenso",
                "COE: {nome}, seu perfil de profissional foi suspenso pela equipe e saiu da busca. Para saber mais,"
                        + " fale com o suporte.",
                Set.of(Canal.SMS, Canal.EMAIL)),
        PERFIL_REATIVADO(
                "profissional.reativado",
                "Perfil reativado",
                "COE: {nome}, seu perfil de profissional foi reativado e voltou para a busca.",
                Set.of(Canal.SMS, Canal.EMAIL)),
        /** Só por e-mail, sem trocar de canal: aviso de segurança não vira SMS. */
        REUSO_DE_SESSAO(
                "sessao.reuso-refresh",
                "Aviso de segurança",
                "COE: {nome}, encerramos uma sessão da sua conta num aparelho porque ela foi usada de um jeito"
                        + " estranho. Se não foi você, troque sua senha no app.",
                Set.of(Canal.EMAIL));

        private final String tipo;
        private final String titulo;
        private final String texto;
        private final Set<Canal> canais;

        Aviso(String tipo, String titulo, String texto, Set<Canal> canais) {
            this.tipo = tipo;
            this.titulo = titulo;
            this.texto = texto;
            this.canais = canais;
        }

        public String tipo() {
            return tipo;
        }

        private boolean trocaDeCanal() {
            return this != REUSO_DE_SESSAO;
        }
    }

    private record Contato(String nome, String celular, String email) {

        String destino(Canal canal) {
            return canal == Canal.SMS ? celular : email;
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(Avisos.class);

    private final NamedParameterJdbcTemplate jdbc;
    private final ApplicationEventPublisher eventos;
    private final JsonMapper json;
    private final Clock clock;
    /** O aviso grava numa transação própria, depois do commit de quem pediu: erro no aviso nunca desfaz a ação. */
    private final TransactionTemplate novaTransacao;

    Avisos(
            NamedParameterJdbcTemplate jdbc,
            ApplicationEventPublisher eventos,
            JsonMapper json,
            Clock clock,
            PlatformTransactionManager transacoes) {
        this.jdbc = jdbc;
        this.eventos = eventos;
        this.json = json;
        this.clock = clock;
        this.novaTransacao = new TransactionTemplate(transacoes);
        this.novaTransacao.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Quem recebe um aviso sobre uma diária ou um contrato. */
    public enum Para {
        CLIENTE,
        PROFISSIONAL
    }

    /** Aviso sobre uma diária ao cliente ou ao profissional dela, com o número do pedido e a data. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void avisarSobreDiaria(UUID diaria, Para para, Aviso aviso) {
        depoisDoCommit(aviso, () -> gravarSobreDiaria(diaria, para, aviso));
    }

    private void gravarSobreDiaria(UUID diaria, Para para, Aviso aviso) {
        Map<String, Object> d = jdbc.queryForMap("""
                SELECT d.data, c.numero, d.cliente_id, p.usuario_id AS profissional
                  FROM diaria d
                  JOIN contrato c ON c.id = d.contrato_id
                  JOIN profissional p ON p.id = d.profissional_id
                 WHERE d.id = :id
                """, new MapSqlParameterSource("id", diaria));
        gravar(
                (UUID) d.get(para == Para.CLIENTE ? "cliente_id" : "profissional"),
                aviso,
                Map.of(
                        "pedido", String.valueOf(((Number) d.get("numero")).longValue()),
                        "data", dia(((java.sql.Date) d.get("data")).toLocalDate())));
    }

    /** Aviso sobre um contrato ao cliente ou ao profissional dele, com o número do pedido. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void avisarSobreContrato(UUID contrato, Para para, Aviso aviso) {
        depoisDoCommit(aviso, () -> gravarSobreContrato(contrato, para, aviso));
    }

    private void gravarSobreContrato(UUID contrato, Para para, Aviso aviso) {
        Map<String, Object> c = jdbc.queryForMap("""
                SELECT c.numero, c.cliente_id, p.usuario_id AS profissional
                  FROM contrato c JOIN profissional p ON p.id = c.profissional_id
                 WHERE c.id = :id
                """, new MapSqlParameterSource("id", contrato));
        gravar(
                (UUID) c.get(para == Para.CLIENTE ? "cliente_id" : "profissional"),
                aviso,
                Map.of("pedido", String.valueOf(((Number) c.get("numero")).longValue())));
    }

    /** A data de uma diária como sai no aviso ("23/10"). */
    public static String dia(LocalDate data) {
        return data.format(DIA);
    }

    /**
     * Agenda o aviso para depois do commit de quem chama (rollback não avisa). Conta excluída ou sem contato
     * confirmado: nada.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void avisar(UUID usuario, Aviso aviso, Map<String, String> valores) {
        Map<String, String> copia = Map.copyOf(valores);
        depoisDoCommit(aviso, () -> gravar(usuario, aviso, copia));
    }

    /**
     * Depois do commit, numa transação nova: um erro aqui (linha sumida, texto que o banco recusa) só vai para o log
     * e nunca desfaz o pagamento, a liberação ou a decisão que pediu o aviso.
     */
    private void depoisDoCommit(Aviso aviso, Runnable gravacao) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    novaTransacao.executeWithoutResult(status -> gravacao.run());
                } catch (RuntimeException erro) {
                    LOG.error(
                            "Aviso {} não gravado: {}",
                            aviso.tipo,
                            erro.getClass().getSimpleName());
                }
            }
        });
    }

    private void gravar(UUID usuario, Aviso aviso, Map<String, String> valores) {
        List<Contato> encontrado = jdbc.query(
                """
                SELECT nome,
                       CASE WHEN celular_verificado_em IS NOT NULL THEN celular END AS celular,
                       CASE WHEN email_verificado_em IS NOT NULL THEN email END AS email
                  FROM usuario WHERE id = :id AND excluido_em IS NULL
                """,
                new MapSqlParameterSource("id", usuario),
                (rs, n) -> new Contato(rs.getString("nome"), rs.getString("celular"), rs.getString("email")));
        if (encontrado.isEmpty()) {
            return;
        }
        Contato contato = encontrado.get(0);
        List<Canal> canais = aviso.canais.stream()
                .filter(canal -> contato.destino(canal) != null)
                .sorted()
                .toList();
        if (canais.isEmpty() && aviso.trocaDeCanal()) {
            canais = Stream.of(Canal.values())
                    .filter(canal -> contato.destino(canal) != null)
                    .limit(1)
                    .toList();
        }
        Map<String, String> tudo = new LinkedHashMap<>(valores);
        tudo.put("nome", primeiroNome(contato.nome()));
        String texto = preencher(aviso.texto, tudo);
        Map<String, String> dados = new LinkedHashMap<>(valores);
        dados.keySet().removeAll(SO_NO_TEXTO);
        for (Canal canal : canais) {
            UUID id = jdbc.queryForObject(
                    """
                    INSERT INTO notificacao (usuario_id, canal, tipo, titulo, corpo, dados)
                    VALUES (:usuario, :canal, :tipo, :titulo, :corpo, CAST(:dados AS jsonb))
                    RETURNING id
                    """,
                    new MapSqlParameterSource("usuario", usuario)
                            .addValue("canal", canal.name().toLowerCase(Locale.ROOT))
                            .addValue("tipo", aviso.tipo)
                            .addValue("titulo", aviso.titulo)
                            .addValue("corpo", texto)
                            .addValue("dados", json.writeValueAsString(dados)),
                    UUID.class);
            eventos.publishEvent(MensagemPronta.aviso(canal, contato.destino(canal), aviso.titulo, texto, id));
        }
    }

    @Override
    public void registrar(UUID notificacao, boolean enviada) {
        jdbc.update(
                """
                UPDATE notificacao
                   SET status = :status, tentativas = tentativas + 1,
                       enviada_em = CASE WHEN :enviada THEN CAST(:agora AS timestamptz) END
                 WHERE id = :id AND status IN ('pendente', 'falhou')
                """,
                new MapSqlParameterSource("id", notificacao)
                        .addValue("status", enviada ? "enviada" : "falhou")
                        .addValue("enviada", enviada)
                        .addValue("agora", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)));
    }

    static String primeiroNome(String nome) {
        String limpo = nome == null ? "" : nome.strip();
        int espaco = limpo.indexOf(' ');
        return espaco < 0 ? limpo : limpo.substring(0, espaco);
    }

    private static String preencher(String modelo, Map<String, String> valores) {
        String texto = modelo;
        for (Map.Entry<String, String> valor : valores.entrySet()) {
            texto = texto.replace("{" + valor.getKey() + "}", valor.getValue());
        }
        return texto;
    }
}
