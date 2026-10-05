package br.com.coe.servicos.usuario.validacao;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import br.com.coe.servicos.usuario.Contato;

public class CepValidoValidador implements ConstraintValidator<CepValido, String> {

    @Override
    public boolean isValid(String valor, ConstraintValidatorContext contexto) {
        return valor == null || Contato.cepValido(valor);
    }
}
