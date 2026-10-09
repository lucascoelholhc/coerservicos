package br.com.coe.servicos.usuario;

import br.com.coe.servicos.compartilhado.erro.ConflitoException;

/**
 * 409 neutro (decisão de 07/10): contato confirmado ou não confirmado em outra conta dá o mesmo
 * tipo, a mesma mensagem e o mesmo corpo, para não revelar o estado do contato. O front sempre
 * oferece provar a posse; se o contato for confirmado, a prova de posse responde igual e não envia.
 */
final class ContatoEmUso {

    static final String CODIGO = "contato-em-uso";
    static final String MENSAGEM =
            "Esse contato já está em uso. Se ele for seu, confirme que é seu para usar nesta conta.";

    private ContatoEmUso() {}

    /** {@code campo} = "celular" ou "email" (o mesmo nos dois estados do contato). */
    static ConflitoException no(String campo) {
        return new ConflitoException(CODIGO, MENSAGEM, campo);
    }
}
