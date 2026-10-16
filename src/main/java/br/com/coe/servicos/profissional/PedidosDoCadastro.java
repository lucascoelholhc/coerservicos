package br.com.coe.servicos.profissional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import br.com.coe.servicos.compartilhado.dinheiro.Dinheiro;

/** Corpo de cada etapa do cadastro (DOM-02). Cada PUT valida só o seu pedaço; o envio confere tudo de novo. */
final class PedidosDoCadastro {

    private PedidosDoCadastro() {}

    /** Etapa 1: profissão principal e outras (código do catálogo público). */
    record Trabalho(
            @NotBlank @Size(max = 30) String profissao,
            @NotNull @Size(max = 4) List<@NotBlank @Size(max = 30) String> outras) {}

    /** Etapa 2: nome e celular vêm da conta; aqui só CPF e nascimento. */
    record Dados(
            @NotBlank @Size(max = 20) String cpf, @NotNull LocalDate dataNascimento) {

        /** Nunca o CPF em log. */
        @Override
        public String toString() {
            return "Dados[cpf=***, dataNascimento=***]";
        }
    }

    record ServicoEscolhido(
            @NotBlank @Size(max = 30) String profissao,
            @NotBlank @Size(max = 80) String nome) {}

    /** Etapa 4: serviços das profissões escolhidas. */
    record Servicos(@NotEmpty @Size(max = 40) List<@NotNull @Valid ServicoEscolhido> servicos) {}

    /** Etapa 5: anos (início da faixa: 0, 1, 3, 5, 10 ou 20), ferramentas e MEI (se souber). */
    record Experiencia(
            @NotNull Integer anos,
            @NotBlank @Pattern(regexp = "sim|parte|nao") String ferramentas,
            Boolean mei) {}

    /** Etapa 6: diária, meia diária (opcional) e horário. */
    record Valor(
            @NotNull Dinheiro valorDiaria,
            Dinheiro valorMeiaDiaria,
            @NotBlank @Size(max = 30) String horario) {}

    /** Etapa 7: cidade onde mora (código IBGE), até onde vai (RN17) e outras cidades. */
    record Onde(
            @NotNull Integer cidade,
            @NotNull Integer raioKm,
            @NotNull @Size(max = 20) List<@NotNull Integer> outrasCidades) {

        @AssertTrue(message = "use 5, 10, 20 ou 40 km")
        boolean isRaioValido() {
            return raioKm == null || raioKm == 5 || raioKm == 10 || raioKm == 20 || raioKm == 40;
        }
    }

    /** Etapa 8: dias da semana (0 = domingo … 6 = sábado). */
    record Dias(@NotEmpty @Size(max = 7) Set<@NotNull @Min(0) @Max(6) Integer> dias) {}

    /** Etapa 10: frases prontas e texto livre do "Sobre você". */
    record Sobre(
            @NotNull @Size(max = 10) List<@NotBlank @Size(max = 120) String> frases,
            @Size(max = 300) String texto) {}

    /** Etapa 11: chave Pix. */
    record Pix(
            @NotBlank @Pattern(regexp = "cpf|celular|email|aleatoria")
            String tipo,

            @NotBlank @Size(max = 120) String chave) {

        /** Nunca a chave em log. */
        @Override
        public String toString() {
            return "Pix[tipo=" + tipo + ", chave=***]";
        }
    }

    /** Legenda e serviço de uma foto já enviada. */
    record Foto(
            @Size(max = 80) String servico, @Size(max = 140) String legenda) {}

    /** Envio para análise: versão dos termos aceita e a regra de não passar contato (protótipo, etapa 11). */
    record Envio(
            @NotBlank @Size(max = 20) String versaoTermosAceita,
            @AssertTrue boolean aceitoRegraDeContato) {}
}
