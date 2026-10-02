package br.com.coe.servicos.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** O seed do perfil local (R__dados_local.sql) bate com o que está documentado e não traz dado real. */
class DadosLocaisTest {

    private static final String SENHA_DOCUMENTADA = "coe-local-123";
    private static final Pattern HASH_BCRYPT = Pattern.compile("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");
    /** Cada linha de usuário do seed: ('uuid', 'nome', 'celular', now(), 'email', 'senha_hash', ... */
    private static final Pattern LINHA_USUARIO = Pattern.compile(
            "\\('[0-9a-f-]{36}',\\s*'[^']*',\\s*'(?<celular>[^']*)',\\s*now\\(\\),\\s*'[^']*',\\s*'(?<senha>[^']*)'");
    /** Celulares claramente falsos reservados para o seed: 47 9 0000 00xx (47900000001, 47900000002…). */
    private static final Pattern CELULAR_FICTICIO = Pattern.compile("^479000000\\d{2}$");
    private static final Pattern INSERT_CIDADE = Pattern.compile("(?i)INSERT\\s+INTO\\s+(public\\.)?cidade\\b");

    private static String seed;
    private static List<MatchResult> usuarios;

    @BeforeAll
    static void lerSeed() throws IOException {
        try (InputStream arquivo = DadosLocaisTest.class.getResourceAsStream("/db/local/R__dados_local.sql")) {
            assertThat(arquivo).as("seed local no classpath").isNotNull();
            seed = new String(arquivo.readAllBytes(), StandardCharsets.UTF_8);
        }
        usuarios = LINHA_USUARIO.matcher(seed).results().toList();
    }

    @Test
    @DisplayName("todo usuário do seed tem senha em BCrypt igual à documentada (coe-local-123)")
    void senhaDocumentada() {
        BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();
        List<String> senhas = usuarios.stream().map(usuario -> usuario.group("senha")).toList();

        assertThat(senhas).isNotEmpty();
        assertThat(senhas).allMatch(senha -> HASH_BCRYPT.matcher(senha).matches(), "hash BCrypt bem formado");
        assertThat(senhas).allMatch(senha -> bcrypt.matches(SENHA_DOCUMENTADA, senha), "senha documentada");
    }

    @Test
    @DisplayName("todo celular do seed é fictício")
    void celularesFicticios() {
        List<String> celulares = usuarios.stream().map(usuario -> usuario.group("celular")).toList();

        assertThat(celulares).isNotEmpty();
        assertThat(celulares).allMatch(celular -> CELULAR_FICTICIO.matcher(celular).matches(), "celular fictício");
    }

    @Test
    @DisplayName("as cidades não ficam mais no seed local (vêm da migração versionada)")
    void semCidades() {
        assertThat(INSERT_CIDADE.matcher(seed).find()).isFalse();
    }
}
