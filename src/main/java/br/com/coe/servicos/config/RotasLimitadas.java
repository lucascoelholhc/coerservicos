package br.com.coe.servicos.config;

import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpMethod;

import br.com.coe.servicos.compartilhado.limite.Balde;

/**
 * Limites por IP e globais de cada rota sensível (CORE-07), cobrados pelo {@link FiltroDeLimites} antes
 * do Spring Security, na ordem da lista (IP antes do global). Os limites por login, celular e usuário
 * dependem do corpo ou do token e ficam nos serviços. Números em {@code coe.limites.regras} (decididos em
 * 14/10, revistos com dados reais depois do lançamento). O {@code FiltroDeLimitesTest} confere que cada
 * rota daqui existe num controller.
 */
final class RotasLimitadas {

    /** Uma cobrança: a regra e se o balde é do IP de quem chama ou o global da regra. */
    record Cobranca(String regra, boolean global) {

        static Cobranca porIp(String regra) {
            return new Cobranca(regra, false);
        }

        static Cobranca global(String regra) {
            return new Cobranca(regra, true);
        }

        Balde balde(String chaveDoIp) {
            return global ? Balde.global(regra) : Balde.porChave(regra, chaveDoIp);
        }
    }

    record RotaLimitada(HttpMethod metodo, String caminho, List<Cobranca> cobrancas) {

        RotaLimitada(HttpMethod metodo, String caminho, Cobranca... cobrancas) {
            this(metodo, caminho, List.of(cobrancas));
        }
    }

    /** Envios de prioridade normal (prova de posse e confirmação de contato) somam um global só. */
    private static final Cobranca ENVIO_NORMAL_GLOBAL = Cobranca.global("envio-normal-global");

    static final List<RotaLimitada> ROTAS = List.of(
            new RotaLimitada(HttpMethod.POST, "/api/auth/entrar", Cobranca.porIp("entrar-ip")),
            new RotaLimitada(HttpMethod.POST, "/api/auth/segundo-passo", Cobranca.porIp("segundo-passo-ip")),
            new RotaLimitada(HttpMethod.POST, "/api/auth/codigo", Cobranca.porIp("codigo-ip")),
            new RotaLimitada(HttpMethod.POST, "/api/auth/entrar-com-codigo", Cobranca.porIp("entrar-com-codigo-ip")),
            new RotaLimitada(HttpMethod.POST, "/api/auth/renovar", Cobranca.porIp("renovar-ip")),
            new RotaLimitada(HttpMethod.POST, "/api/auth/senha/esqueci", Cobranca.porIp("esqueci-ip")),
            new RotaLimitada(HttpMethod.POST, "/api/auth/senha/redefinir", Cobranca.porIp("redefinir-ip")),
            new RotaLimitada(
                    HttpMethod.POST,
                    "/api/contas/cliente",
                    Cobranca.porIp("cadastro-ip"),
                    Cobranca.global("cadastro-global")),
            new RotaLimitada(
                    HttpMethod.POST, "/api/contas/posse/celular", Cobranca.porIp("posse-ip"), ENVIO_NORMAL_GLOBAL),
            new RotaLimitada(
                    HttpMethod.POST, "/api/contas/posse/email", Cobranca.porIp("posse-ip"), ENVIO_NORMAL_GLOBAL),
            new RotaLimitada(
                    HttpMethod.POST, "/api/contas/posse/celular/confirmar", Cobranca.porIp("posse-confirmar-ip")),
            new RotaLimitada(
                    HttpMethod.POST, "/api/contas/posse/email/confirmar", Cobranca.porIp("posse-confirmar-ip")),
            // As rotas logadas de envio normal (/api/contas/eu/...) cobram o global no @LimitadoPorUsuario,
            // DEPOIS do limite do usuário: aqui ele seria gasto antes (revisão de segurança, H2).
            new RotaLimitada(HttpMethod.POST, "/api/contas/email/confirmar", Cobranca.porIp("confirmar-email-ip")),
            // uploads do cadastro do profissional (DOM-02): também por usuário, no @LimitadoPorUsuario
            new RotaLimitada(HttpMethod.POST, "/api/profissional/cadastro/documentos", Cobranca.porIp("upload-ip")),
            new RotaLimitada(HttpMethod.POST, "/api/profissional/cadastro/fotos", Cobranca.porIp("upload-ip")),
            // fotos do serviço do dia (DOM-08): também por usuário, no @LimitadoPorUsuario
            new RotaLimitada(HttpMethod.POST, "/api/diarias/fotos", Cobranca.porIp("upload-ip")),
            // CPF e nascimento: limite curto para ninguém testar CPFs pelo 409 (enumeração)
            new RotaLimitada(HttpMethod.PUT, "/api/profissional/cadastro/dados", Cobranca.porIp("dados-ip")),
            // webhook do gateway (DOM-07): por IP antes do global, para quem não tem o token não esgotar o teto
            // global e o gateway de verdade (poucos IPs) receber 429; lista de IPs do Asaas na AWS-09
            new RotaLimitada(
                    HttpMethod.POST,
                    "/api/webhooks/pagamento",
                    Cobranca.porIp("webhook-ip"),
                    Cobranca.global("webhook-global")));

    private RotasLimitadas() {}

    /** A rota do método e caminho (comparação sem diferenciar maiúsculas: casar a mais é seguro). */
    static Optional<RotaLimitada> buscar(String metodo, String caminho) {
        return ROTAS.stream()
                .filter(rota -> rota.metodo().matches(metodo) && rota.caminho().equalsIgnoreCase(caminho))
                .findFirst();
    }
}
