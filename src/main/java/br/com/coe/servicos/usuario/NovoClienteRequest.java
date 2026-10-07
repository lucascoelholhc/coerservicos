package br.com.coe.servicos.usuario;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import br.com.coe.servicos.usuario.validacao.CelularBrasileiro;
import br.com.coe.servicos.usuario.validacao.CepValido;
import br.com.coe.servicos.usuario.validacao.EmailValido;
import br.com.coe.servicos.usuario.validacao.NomeDePessoa;
import br.com.coe.servicos.usuario.validacao.SenhaPermitida;

/**
 * Pedido de cadastro de cliente (RF03). Só estes campos: qualquer outro no JSON é recusado (400),
 * então ninguém consegue mandar papel, status ou id (mass assignment).
 */
@SenhaPermitida
public record NovoClienteRequest(
        @NotBlank @NomeDePessoa String nome,
        @NotBlank @CelularBrasileiro String celular,
        @NotBlank @EmailValido String email,
        @NotBlank @CepValido String cep,
        @NotBlank String senha,
        @NotBlank @Size(max = 20) String versaoTermosAceita,
        @Size(max = 64) String comprovanteCelular,
        @Size(max = 64) String comprovanteEmail) {

    /** Cadastro sem comprovante de posse (o caso comum). */
    public NovoClienteRequest(
            String nome, String celular, String email, String cep, String senha, String versaoTermosAceita) {
        this(nome, celular, email, cep, senha, versaoTermosAceita, null, null);
    }

    /** Nunca mostra a senha nem os contatos (nem em log de depuração). */
    @Override
    public String toString() {
        return "NovoClienteRequest[dados omitidos]";
    }
}
