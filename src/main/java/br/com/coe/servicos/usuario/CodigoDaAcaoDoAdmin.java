package br.com.coe.servicos.usuario;

import java.util.UUID;

import org.springframework.stereotype.Component;

import br.com.coe.servicos.compartilhado.erro.MuitasTentativasException;
import br.com.coe.servicos.compartilhado.erro.RegraDeNegocioException;

/**
 * O código SMS da ação do admin (finalidade {@code acao_admin}), pedido em {@code POST /api/admin/equipe/codigo} e
 * conferido na hora da ação: Equipe e, desde 11/11, mudar a configuração (RN44g). Chamado fora da transação da ação,
 * para a tentativa errada ficar gravada mesmo quando a ação é recusada.
 */
@Component
public class CodigoDaAcaoDoAdmin {

    private final ServicoDeCodigoSms codigos;

    CodigoDaAcaoDoAdmin(ServicoDeCodigoSms codigos) {
        this.codigos = codigos;
    }

    public void exigir(UUID conta, String codigo) {
        if (codigos.bloqueado(conta)) {
            throw new MuitasTentativasException(
                    "Muitos códigos errados. Aguarde um pouco e tente de novo.", codigos.esperaDoBloqueio(conta));
        }
        if (codigos.conferirDaConta(conta, FinalidadeSms.ACAO_ADMIN, codigo).isEmpty()) {
            throw new RegraDeNegocioException("codigo-invalido", "Código incorreto ou vencido. Peça um novo.");
        }
    }
}
