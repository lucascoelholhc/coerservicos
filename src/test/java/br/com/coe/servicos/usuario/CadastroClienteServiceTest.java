package br.com.coe.servicos.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.SQLException;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;

import br.com.coe.servicos.compartilhado.erro.ConflitoException;
import br.com.coe.servicos.config.ConfiguracaoNegocio;

/** Corrida no cadastro: a violação de UNIQUE que escapa da checagem prévia vira 409 (ou segue como erro). */
class CadastroClienteServiceTest {

    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final PasswordEncoder codificador = mock(PasswordEncoder.class);
    private final ConfiguracaoNegocio configuracao = mock(ConfiguracaoNegocio.class);
    private final PlatformTransactionManager transacoes = mock(PlatformTransactionManager.class);
    private final CadastroClienteService servico = new CadastroClienteService(
            usuarios,
            mock(AceiteTermosRepository.class),
            codificador,
            configuracao,
            transacoes,
            mock(ReivindicacaoDeContato.class),
            mock(ServicoDeConfirmacaoDeEmail.class));

    private final NovoClienteRequest pedido = new NovoClienteRequest(
            "Ana Silva", "47900000901", "ana901@teste.coe.local", "89010000", "Casa-Azul-2026", "1.0", null, null);

    @BeforeEach
    void preparar() {
        when(configuracao.versaoTermos()).thenReturn("1.0");
        when(codificador.encode(any())).thenReturn("{bcrypt}hash");
    }

    @Test
    void emailCadastradoAoMesmoTempoViraConflitoNoCampoEmail() {
        when(usuarios.saveAndFlush(any())).thenThrow(violacao("uq_usuario_email"));

        assertThatThrownBy(() -> servico.cadastrar(pedido, "127.0.0.1", "Teste/1.0"))
                .isInstanceOfSatisfying(ConflitoException.class, erro -> {
                    assertThat(erro.getCodigo()).isEqualTo("email-ja-cadastrado");
                    assertThat(erro.getCampo()).isEqualTo("email");
                });
    }

    @Test
    void outraViolacaoNaoViraConflito() {
        DataIntegrityViolationException erro = violacao("ck_usuario_cep");
        when(usuarios.saveAndFlush(any())).thenThrow(erro);

        assertThatThrownBy(() -> servico.cadastrar(pedido, "127.0.0.1", "Teste/1.0"))
                .isSameAs(erro);
    }

    @Test
    void violacaoSemCausaDoHibernateNaoViraConflito() {
        DataIntegrityViolationException erro = new DataIntegrityViolationException("sem causa");
        when(usuarios.saveAndFlush(any())).thenThrow(erro);

        assertThatThrownBy(() -> servico.cadastrar(pedido, "127.0.0.1", "Teste/1.0"))
                .isSameAs(erro);
    }

    private static DataIntegrityViolationException violacao(String constraint) {
        return new DataIntegrityViolationException(
                "violação", new ConstraintViolationException("violação", new SQLException("23505"), constraint));
    }

    @Test
    void hashDaSenhaAntesDeAbrirATransacao() {
        // BCrypt custo 12 leva centenas de ms: dentro da transação, prenderia uma conexão do pool.
        ClienteCriadoResponse criado = servico.cadastrar(pedido, "127.0.0.1", "Teste/1.0");

        InOrder ordem = inOrder(codificador, transacoes);
        ordem.verify(codificador).encode("Casa-Azul-2026");
        ordem.verify(transacoes).getTransaction(any());
        assertThat(criado.nome()).isEqualTo("Ana Silva");
        assertThat(criado.id()).isNotNull();
    }

    @Test
    void violacaoEmbrulhadaMaisDeUmNivelAindaViraConflito() {
        when(usuarios.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("fora", violacao("uq_usuario_celular")));

        assertThatThrownBy(() -> servico.cadastrar(pedido, "127.0.0.1", "Teste/1.0"))
                .isInstanceOfSatisfying(
                        ConflitoException.class,
                        erro -> assertThat(erro.getCampo()).isEqualTo("celular"));
    }
}
