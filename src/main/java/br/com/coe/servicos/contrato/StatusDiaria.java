package br.com.coe.servicos.contrato;

import java.util.EnumSet;
import java.util.Set;

import br.com.coe.servicos.compartilhado.erro.RegraDeNegocioException;

/**
 * Estados da diária e as únicas transições permitidas (RN40, mapa-banco). Toda mudança de status passa por
 * {@link #exigirIrPara}: transição fora do diagrama é erro, sem exceção. Liberada, reembolsada e cancelada são finais.
 */
public enum StatusDiaria {
    AGENDADA("agendada"),
    PAGA("paga"),
    ANDAMENTO("andamento"),
    AGUARDANDO("aguardando"),
    LIBERADA("liberada"),
    CONTESTADA("contestada"),
    REEMBOLSADA("reembolsada"),
    CANCELADA("cancelada");

    private final String codigo;

    StatusDiaria(String codigo) {
        this.codigo = codigo;
    }

    /** O valor da coluna {@code diaria.status}. */
    public String codigo() {
        return codigo;
    }

    public static StatusDiaria doCodigo(String codigo) {
        for (StatusDiaria status : values()) {
            if (status.codigo.equals(codigo)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Status de diária desconhecido: " + codigo);
    }

    public boolean finalizado() {
        return proximos().isEmpty();
    }

    public boolean podeIrPara(StatusDiaria destino) {
        return proximos().contains(destino);
    }

    /** Lança 422 {@code transicao-invalida} se o diagrama não permite ir daqui para o destino. */
    public void exigirIrPara(StatusDiaria destino) {
        if (!podeIrPara(destino)) {
            throw new RegraDeNegocioException(
                    "transicao-invalida", "Essa diária não pode ir de " + codigo + " para " + destino.codigo + ".");
        }
    }

    private Set<StatusDiaria> proximos() {
        return switch (this) {
            case AGENDADA -> EnumSet.of(PAGA, CANCELADA);
            // RN44b: depois de uma falta confirmada, o cliente pode cancelar as outras diárias pagas (reembolso)
            case PAGA -> EnumSet.of(ANDAMENTO, CONTESTADA, REEMBOLSADA);
            case ANDAMENTO -> EnumSet.of(AGUARDANDO, CONTESTADA);
            case AGUARDANDO -> EnumSet.of(LIBERADA, CONTESTADA);
            case CONTESTADA -> EnumSet.of(LIBERADA, REEMBOLSADA);
            case LIBERADA, REEMBOLSADA, CANCELADA -> EnumSet.noneOf(StatusDiaria.class);
        };
    }
}
