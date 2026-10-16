package br.com.coe.servicos.compartilhado.cripto;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import br.com.coe.servicos.IntegracaoTest;
import br.com.coe.servicos.compartilhado.pessoal.Cpf;
import br.com.coe.servicos.compartilhado.pessoal.CpfsDeTeste;

/**
 * Com a configuração real (chaves do perfil test): no banco só vai cifra e HMAC, nunca o texto. O banco não
 * é limpo entre testes e o HMAC do CPF é único: cada teste usa um CPF novo ({@link CpfsDeTeste}).
 */
class CifraNoBancoTest extends IntegracaoTest {

    @Autowired
    private CifraDeCampo cifra;

    @Autowired
    private HmacDeCpf hmac;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("a coluna do CPF e a da chave Pix não contêm o texto em claro; o HMAC tem 32 bytes")
    void colunaSemTextoEmClaro() {
        Cpf cpf = CpfsDeTeste.unico();
        String pix = "pix-" + UUID.randomUUID() + "@teste.coe.local";
        UUID usuario = novoUsuario();
        jdbc.update(
                "INSERT INTO profissional (usuario_id, cpf_cifrado, cpf_hash, chave_pix_tipo, chave_pix_cifrada)"
                        + " VALUES (?, ?, ?, 'email', ?)",
                usuario,
                cifra.cifrar(cpf.digitos(), Campo.CPF),
                hmac.de(cpf),
                cifra.cifrar(pix, Campo.CHAVE_PIX));

        byte[] cpfNoBanco = coluna("cpf_cifrado", usuario);
        byte[] pixNoBanco = coluna("chave_pix_cifrada", usuario);

        assertThat(new String(cpfNoBanco, StandardCharsets.ISO_8859_1)).doesNotContain(cpf.digitos());
        assertThat(new String(pixNoBanco, StandardCharsets.ISO_8859_1))
                .doesNotContain(pix)
                .doesNotContain("@");
        assertThat(coluna("cpf_hash", usuario)).hasSize(32).isEqualTo(hmac.de(cpf));
        assertThat(cifra.decifrar(cpfNoBanco, Campo.CPF)).isEqualTo(cpf.digitos());
        assertThat(cifra.decifrar(pixNoBanco, Campo.CHAVE_PIX)).isEqualTo(pix);
    }

    @Autowired
    private jakarta.persistence.EntityManagerFactory fabrica;

    @Test
    @DisplayName("pelo Hibernate (@Convert): grava cifrado, relê decifrado; a coluna não tem o texto")
    void peloHibernate() {
        Cpf cpf = CpfsDeTeste.unico();
        String pix = "pix-" + UUID.randomUUID() + "@teste.coe.local";
        UUID usuario = novoUsuario();
        jakarta.persistence.EntityManager gravacao = fabrica.createEntityManager();
        gravacao.getTransaction().begin();
        EntidadeCifradaDeTeste entidade = new EntidadeCifradaDeTeste(usuario, cpf, hmac.de(cpf), pix);
        gravacao.persist(entidade);
        gravacao.getTransaction().commit();
        gravacao.close();

        jakarta.persistence.EntityManager leitura = fabrica.createEntityManager();
        EntidadeCifradaDeTeste relida = leitura.find(EntidadeCifradaDeTeste.class, entidade.id());
        leitura.close();

        assertThat(relida.cpf()).isEqualTo(cpf);
        assertThat(relida.chavePix()).isEqualTo(pix);
        assertThat(new String(coluna("cpf_cifrado", usuario), StandardCharsets.ISO_8859_1))
                .doesNotContain(cpf.digitos());
        assertThat(new String(coluna("chave_pix_cifrada", usuario), StandardCharsets.ISO_8859_1))
                .doesNotContain(pix);
    }

    @Test
    @DisplayName("busca exata pelo HMAC, sem decifrar nada")
    void buscaPeloHmac() {
        Cpf cpf = CpfsDeTeste.unico();
        UUID usuario = novoUsuario();
        jdbc.update(
                "INSERT INTO profissional (usuario_id, cpf_cifrado, cpf_hash) VALUES (?, ?, ?)",
                usuario,
                cifra.cifrar(cpf.digitos(), Campo.CPF),
                hmac.de(cpf));

        UUID achado = jdbc.queryForObject(
                "SELECT usuario_id FROM profissional WHERE cpf_hash = ?", UUID.class, hmac.de(Cpf.de(cpf.formatado())));

        assertThat(achado).isEqualTo(usuario);
    }

    private UUID novoUsuario() {
        return jdbc.queryForObject(
                "INSERT INTO usuario (nome, email, senha_hash) VALUES ('Pessoa da cifra', ?, 'x') RETURNING id",
                UUID.class,
                "cifra-" + UUID.randomUUID() + "@teste.coe.local");
    }

    private byte[] coluna(String nome, UUID usuario) {
        return jdbc.queryForObject("SELECT " + nome + " FROM profissional WHERE usuario_id = ?", byte[].class, usuario);
    }
}
