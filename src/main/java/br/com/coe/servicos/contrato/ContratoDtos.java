package br.com.coe.servicos.contrato;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import br.com.coe.servicos.compartilhado.dinheiro.Dinheiro;

/** Pedidos e respostas da contratação (DOM-06). Valores sempre em {@link Dinheiro} (texto "280.00" no JSON). */
final class ContratoDtos {

    private ContratoDtos() {}

    record Dia(
            @NotNull LocalDate data,
            @NotNull @Pattern(regexp = "inteira|meia") String tipo) {}

    /** O endereço do serviço: guardado cifrado e só mostrado ao profissional depois do pagamento (RN29). */
    record Endereco(
            @NotBlank @Pattern(regexp = "\\d{5}-?\\d{3}") String cep,
            @NotNull Integer cidade,
            @NotBlank @Size(max = 120) String logradouro,
            @NotBlank @Size(max = 20) String numero,
            @Size(max = 80) String bairro,
            @Size(max = 80) String complemento) {

        @Override
        public String toString() {
            return "Endereco[cidade=" + cidade + "]";
        }
    }

    record NovoContrato(
            @NotNull UUID profissionalId,
            @NotEmpty @Size(max = 28) List<@NotNull @Valid Dia> dias,
            @NotNull @Size(max = 20) List<@NotBlank @Size(max = 80) String> servicos,
            @Size(max = 600) String descricao,

            @NotNull @Pattern(regexp = "cliente_tem|cliente_compra|combinar")
            String material,

            @NotNull @Valid Endereco endereco,
            @NotBlank String versaoTermosAceita) {}

    record Cidade(int codigoIbge, String nome, String uf) {}

    record DiaDoContrato(UUID id, LocalDate data, String tipo, String status, Dinheiro valor) {}

    /** O que o endereço guarda (rua, número, bairro, complemento); a cidade e o CEP ficam em claro no contrato. */
    record EnderecoGuardado(String logradouro, String numero, String bairro, String complemento) {

        /** Nunca o endereço em claro (log, exceção, depuração). */
        @Override
        public String toString() {
            return "EnderecoGuardado[***]";
        }
    }

    record Contrato(
            UUID id,
            long numero,
            String status,
            UUID profissionalId,
            Cidade cidade,
            String cep,
            List<DiaDoContrato> dias,
            List<String> servicos,
            String descricao,
            String material,
            Dinheiro valorServicos,
            Dinheiro valorComissao,
            Dinheiro valorTotal,
            Instant pagamentoExpiraEm,
            EnderecoGuardado endereco) {}
}
