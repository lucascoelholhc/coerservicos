package br.com.coe.servicos.usuario;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import br.com.coe.servicos.compartilhado.auditoria.RegistroDeAuditoria;
import br.com.coe.servicos.compartilhado.erro.ConflitoException;
import br.com.coe.servicos.compartilhado.erro.RegraDeNegocioException;
import br.com.coe.servicos.compartilhado.mensageria.Mascara;

/**
 * RN61, dentro da transação do cadastro: usa os comprovantes de posse e tira o dado da conta
 * antiga (que nunca o confirmou). Trava as contas em ordem de id, confere de novo depois da trava,
 * encerra as sessões da antiga (motivo contato_transferido), invalida os códigos e links pendentes
 * do dado e grava a auditoria mascarada. Dado confirmado nunca é tomado; conta que ficaria sem
 * nenhum contato, ou conta da equipe (ADMIN), vai para o suporte (409 transferencia-indisponivel).
 */
@Component
class ReivindicacaoDeContato {

    private static final Logger LOG = LoggerFactory.getLogger(ReivindicacaoDeContato.class);

    /** O que a conta nova recebe já confirmado, e quando. */
    record Resultado(boolean celularComprovado, boolean emailComprovado, Instant em) {}

    private final UsuarioRepository usuarios;
    private final ServicoDeTokens tokens;
    private final CodigoSmsRepository codigos;
    private final TokenVerificacaoRepository links;
    private final RefreshTokenRepository sessoes;
    private final RegistroDeAuditoria auditoria;
    private final Clock clock;

    @SuppressWarnings("java:S107") // tudo que a transferência toca numa transação só
    ReivindicacaoDeContato(
            UsuarioRepository usuarios,
            ServicoDeTokens tokens,
            CodigoSmsRepository codigos,
            TokenVerificacaoRepository links,
            RefreshTokenRepository sessoes,
            RegistroDeAuditoria auditoria,
            Clock clock) {
        this.usuarios = usuarios;
        this.tokens = tokens;
        this.codigos = codigos;
        this.links = links;
        this.sessoes = sessoes;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    /**
     * Travas consultivas dos contatos do cadastro, no início da transação e na mesma ordem dos envios
     * (celular, depois e-mail): o cadastro não cruza a espera com uma reivindicação ou um envio.
     */
    void travarContatos(String celular, String email) {
        codigos.travar("codigo_sms:" + celular);
        links.travar("token_verificacao:" + email);
    }

    /** Chamar dentro da transação que grava a conta nova (com id {@code novaConta}). */
    Resultado aplicar(
            UUID novaConta,
            String celular,
            String comprovanteCelular,
            String email,
            String comprovanteEmail,
            String ip) {
        Instant agora = clock.instant();
        boolean celularComprovado = comprovanteCelular != null;
        boolean emailComprovado = comprovanteEmail != null;
        // Mesma ordem dos envios (trava consultiva do destino, depois linhas): sem deadlock com um
        // envio de código ou link para o mesmo contato na mesma hora.
        if (celularComprovado) {
            codigos.travar("codigo_sms:" + celular);
        }
        if (emailComprovado) {
            links.travar("token_verificacao:" + email);
        }
        if (celularComprovado && !usarComprovante(comprovanteCelular, TokenVerificacao.CANAL_CELULAR, celular)) {
            throw comprovanteInvalido();
        }
        if (emailComprovado && !usarComprovante(comprovanteEmail, TokenVerificacao.CANAL_EMAIL, email)) {
            throw comprovanteInvalido();
        }
        Optional<UUID> donoDoCelular = celularComprovado ? usuarios.buscarIdPeloCelular(celular) : Optional.empty();
        Optional<UUID> donoDoEmail = emailComprovado ? usuarios.buscarIdPeloEmail(email) : Optional.empty();
        TreeSet<UUID> donos = new TreeSet<>();
        donoDoCelular.ifPresent(donos::add);
        donoDoEmail.ifPresent(donos::add);
        if (!donos.isEmpty()) {
            usuarios.travar(donos);
        }
        Usuario antigoDoCelular = donoDoCelular
                .flatMap(usuarios::findById)
                .filter(conta -> celular.equals(conta.getCelular()))
                .orElse(null);
        Usuario antigoDoEmail = donoDoEmail
                .flatMap(usuarios::findById)
                .filter(conta -> email.equals(conta.getEmail()))
                .orElse(null);
        conferir(antigoDoCelular, antigoDoEmail);
        if (antigoDoCelular != null) {
            antigoDoCelular.perderCelular();
            codigos.invalidarAtivosDoCelular(celular, agora);
            encerrar(antigoDoCelular, "celular", Mascara.celular(celular), novaConta, ip, agora);
        }
        if (antigoDoEmail != null) {
            antigoDoEmail.perderEmail();
            links.invalidarAtivosDoDestino(email, agora);
            encerrar(antigoDoEmail, "email", Mascara.email(email), novaConta, ip, agora);
        }
        return new Resultado(celularComprovado, emailComprovado, agora);
    }

    private boolean usarComprovante(String comprovante, String canal, String destino) {
        return tokens.consumir(
                        comprovante,
                        FinalidadeToken.COMPROVANTE_POSSE,
                        token -> canal.equals(token.getCanal()) && destino.equals(token.getDestino()))
                .isPresent();
    }

    /** Depois da trava: dado confirmado nunca é tomado; a conta antiga nunca fica sem contato. */
    private static void conferir(Usuario antigoDoCelular, Usuario antigoDoEmail) {
        if (antigoDoCelular != null && antigoDoCelular.isCelularConfirmado()) {
            throw new ConflitoException(
                    "celular-ja-cadastrado",
                    "Este celular já tem cadastro. Entre na sua conta ou recupere a senha.",
                    "celular");
        }
        if (antigoDoEmail != null && antigoDoEmail.isEmailConfirmado()) {
            throw new ConflitoException(
                    "email-ja-cadastrado",
                    "Este e-mail já tem cadastro. Entre na sua conta ou recupere a senha.",
                    "email");
        }
        boolean mesmaConta = antigoDoCelular != null
                && antigoDoEmail != null
                && antigoDoCelular.getId().equals(antigoDoEmail.getId());
        boolean admin = (antigoDoCelular != null && antigoDoCelular.getPapeis().contains(Papel.ADMIN))
                || (antigoDoEmail != null && antigoDoEmail.getPapeis().contains(Papel.ADMIN));
        boolean celularSemOutro = antigoDoCelular != null && antigoDoCelular.getEmail() == null;
        boolean emailSemOutro = antigoDoEmail != null && antigoDoEmail.getCelular() == null;
        if (admin || mesmaConta || celularSemOutro || emailSemOutro) {
            throw new ConflitoException(
                    "transferencia-indisponivel",
                    "Não conseguimos passar este contato para você agora. Fale com a equipe da COE.",
                    null);
        }
    }

    private void encerrar(Usuario antiga, String canal, String mascarado, UUID novaConta, String ip, Instant agora) {
        usuarios.saveAndFlush(antiga); // libera o dado antes do INSERT da conta nova
        sessoes.revogarDoUsuario(antiga.getId(), agora, "contato_transferido");
        auditoria.registrarDoSistema(
                "contato.transferir",
                "usuario",
                antiga.getId(),
                Map.of(canal, mascarado),
                Map.of(canal, "removido", "paraConta", novaConta.toString()),
                ip);
        LOG.info("Contato ({}) transferido da conta {} para a conta {}", canal, antiga.getId(), novaConta);
    }

    private static RegraDeNegocioException comprovanteInvalido() {
        return new RegraDeNegocioException(
                "comprovante-invalido", "A confirmação deste contato venceu ou não vale. Confirme de novo.");
    }
}
