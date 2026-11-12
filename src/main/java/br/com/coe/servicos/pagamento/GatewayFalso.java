package br.com.coe.servicos.pagamento;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import br.com.coe.servicos.compartilhado.dinheiro.Dinheiro;
import br.com.coe.servicos.compartilhado.erro.RecursoNaoEncontradoException;

/**
 * Gateway falso no formato do Asaas (ids {@code pay_...}, {@code tra_...}, {@code ref_...}), só nos perfis local e
 * test: não chama rede nenhuma e guarda tudo na memória. O link aponta para o front local; o copia-e-cola do Pix é
 * marcado como falso. Transferência e estorno nascem {@code PENDING} e são concluídos pelo webhook (como no Asaas); com
 * {@code coe.gateway.falso.imediato=true} (perfil local) já voltam {@code DONE}, para o fluxo andar no navegador. O
 * saldo é simulado: o espelho do livro-caixa menos o que ainda está a caminho, mais uma diferença que o teste pode
 * forçar ({@link #simularDiferencaNoSaldo}).
 */
@Component
@Profile({"local", "test"})
public class GatewayFalso implements GatewayPagamento {

    public static final String NOME = "falso";

    private static final SecureRandom SORTEIO = new SecureRandom();

    private final Map<String, CobrancaNoGateway> cobrancas = new ConcurrentHashMap<>();
    private final Map<String, TransferenciaNoGateway> transferencias = new ConcurrentHashMap<>();
    /** Ordem de criação de cada transferência (a consulta por referência devolve a mais recente). */
    private final Map<String, Long> ordem = new ConcurrentHashMap<>();

    private final java.util.concurrent.atomic.AtomicLong sequencia = new java.util.concurrent.atomic.AtomicLong();
    private final Map<String, EstornoPendente> estornos = new ConcurrentHashMap<>();
    private final AtomicReference<String> proximaFalha = new AtomicReference<>();
    private final AtomicReference<BigDecimal> diferenca = new AtomicReference<>(BigDecimal.ZERO);
    private final String urlBase;
    private final boolean imediato;
    private final NamedParameterJdbcTemplate jdbc;

    private record EstornoPendente(Dinheiro valor, boolean concluido) {}

    GatewayFalso(
            @Value("${coe.front.url-base}") String urlBase,
            @Value("${coe.gateway.falso.imediato:false}") boolean imediato,
            NamedParameterJdbcTemplate jdbc) {
        this.urlBase = urlBase;
        this.imediato = imediato;
        this.jdbc = jdbc;
    }

    @Override
    public String nome() {
        return NOME;
    }

    @Override
    public CobrancaNoGateway criarCobranca(NovaCobranca pedido) {
        String id = id("pay_");
        String pix = pedido.metodo() == Metodo.PIX
                ? "00020101COEFALSO" + id + "5204000053039865406"
                        + pedido.valor().valor().toPlainString()
                : null;
        CobrancaNoGateway cobranca = new CobrancaNoGateway(
                id, "PENDING", pedido.valor(), urlBase + "/pagamento-falso/" + id, pix, pedido.vencimento());
        cobrancas.put(id, cobranca);
        return cobranca;
    }

    @Override
    public CobrancaNoGateway consultar(String idExterno) {
        CobrancaNoGateway cobranca = cobrancas.get(idExterno);
        if (cobranca == null) {
            throw new RecursoNaoEncontradoException("Cobrança não encontrada no gateway.");
        }
        return cobranca;
    }

    @Override
    public TransferenciaNoGateway transferirPix(NovaTransferencia pedido) {
        String falha = proximaFalha.getAndSet(null);
        String status = falha != null ? "FAILED" : imediato ? "DONE" : "PENDING";
        TransferenciaNoGateway transferencia =
                new TransferenciaNoGateway(id("tra_"), pedido.referencia(), status, pedido.valor(), falha);
        ordem.put(transferencia.idExterno(), sequencia.incrementAndGet());
        transferencias.put(transferencia.idExterno(), transferencia);
        return transferencia;
    }

    @Override
    public Optional<TransferenciaNoGateway> transferenciaPorReferencia(String referencia) {
        return transferencias.values().stream()
                .filter(t -> t.referencia().equals(referencia))
                .max(java.util.Comparator.comparingLong(t -> ordem.getOrDefault(t.idExterno(), 0L)));
    }

    @Override
    public EstornoNoGateway estornar(String cobrancaExterna, Dinheiro valor, String referencia) {
        String id = id("ref_");
        estornos.put(id, new EstornoPendente(valor, imediato));
        return new EstornoNoGateway(id, imediato ? "DONE" : "PENDING");
    }

    /**
     * O espelho do livro-caixa (o que a conta {@code gateway} diz que está lá), menos as transferências e os estornos
     * ainda a caminho (já saíram do gateway, mas a COE só lança na confirmação), mais a diferença simulada.
     */
    @Override
    public Dinheiro saldo() {
        BigDecimal espelho = jdbc.queryForObject(
                "SELECT COALESCE(-SUM(saldo), 0) FROM saldo_conta WHERE tipo = 'gateway'", Map.of(), BigDecimal.class);
        BigDecimal aCaminho = transferencias.values().stream()
                .filter(t -> "PENDING".equals(t.status()))
                .map(t -> t.valor().valor())
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .add(estornos.values().stream()
                        .filter(e -> !e.concluido())
                        .map(e -> e.valor().valor())
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
        return new Dinheiro(espelho.subtract(aCaminho).add(diferenca.get()).setScale(2));
    }

    /** Teste: a próxima transferência volta {@code FAILED} com esse motivo (ex.: {@code chave-invalida}). */
    public void falharProximaTransferencia(String motivo) {
        proximaFalha.set(motivo);
    }

    /** Teste: marca a transferência como concluída ou falha no gateway (o webhook do teste conta o resto). */
    public void concluirTransferencia(String idExterno, boolean concluida, String motivo) {
        transferencias.computeIfPresent(
                idExterno,
                (id, t) -> new TransferenciaNoGateway(
                        id, t.referencia(), concluida ? "DONE" : "FAILED", t.valor(), concluida ? null : motivo));
    }

    /** Teste: o estorno saiu do gateway (o webhook do teste conta à COE). */
    public void concluirEstorno(String idExterno) {
        estornos.computeIfPresent(idExterno, (id, e) -> new EstornoPendente(e.valor(), true));
    }

    /** Teste: soma essa diferença ao saldo do gateway (para a conciliação achar). */
    public void simularDiferencaNoSaldo(BigDecimal valor) {
        diferenca.set(valor);
    }

    /** Teste: volta ao estado sem falha nem diferença. */
    public void restaurar() {
        proximaFalha.set(null);
        diferenca.set(BigDecimal.ZERO);
    }

    private static String id(String prefixo) {
        byte[] bytes = new byte[8];
        SORTEIO.nextBytes(bytes);
        return prefixo + HexFormat.of().formatHex(bytes);
    }
}
