package br.com.coe.servicos.pagamento;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import br.com.coe.servicos.compartilhado.erro.RecursoNaoEncontradoException;

/**
 * Gateway falso no formato do Asaas (ids {@code pay_...}, status {@code PENDING}), só nos perfis local e test: não
 * chama rede nenhuma e guarda as cobranças na memória. O link aponta para o front local; o copia-e-cola do Pix é
 * marcado como falso e não paga nada. O pagamento chega pelo webhook (DOM-07, dia 28).
 */
@Component
@Profile({"local", "test"})
public class GatewayFalso implements GatewayPagamento {

    public static final String NOME = "falso";

    private static final SecureRandom SORTEIO = new SecureRandom();

    private final Map<String, CobrancaNoGateway> cobrancas = new ConcurrentHashMap<>();
    private final String urlBase;

    GatewayFalso(@Value("${coe.front.url-base}") String urlBase) {
        this.urlBase = urlBase;
    }

    @Override
    public String nome() {
        return NOME;
    }

    @Override
    public CobrancaNoGateway criarCobranca(NovaCobranca pedido) {
        byte[] bytes = new byte[8];
        SORTEIO.nextBytes(bytes);
        String id = "pay_" + HexFormat.of().formatHex(bytes);
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
}
