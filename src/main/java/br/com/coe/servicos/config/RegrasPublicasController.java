package br.com.coe.servicos.config;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.coe.servicos.compartilhado.seguranca.Publico;

/** Regras de negócio que o front mostra em texto (comissão, prazo de liberação, quem paga), sem login. */
@RestController
@RequestMapping("/api/publico")
class RegrasPublicasController {

    private final ConfiguracaoNegocio configuracao;

    RegrasPublicasController(ConfiguracaoNegocio configuracao) {
        this.configuracao = configuracao;
    }

    /** Um snapshot só: os três valores vêm da mesma leitura da configuração. */
    @GetMapping("/regras")
    @Publico
    RegrasPublicasResposta regras() {
        return RegrasPublicasResposta.de(configuracao.parametros());
    }
}
