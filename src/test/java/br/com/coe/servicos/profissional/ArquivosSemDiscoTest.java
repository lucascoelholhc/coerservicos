package br.com.coe.servicos.profissional;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import jakarta.servlet.MultipartConfigElement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;

import br.com.coe.servicos.IntegracaoTest;
import br.com.coe.servicos.compartilhado.limite.LimitadoPorUsuario;

/** Revisão de segurança da fatia 2: documento nunca no disco em claro e URL de arquivo com limite por usuário. */
class ArquivosSemDiscoTest extends IntegracaoTest {

    @Autowired
    private MultipartConfigElement multipart;

    @Test
    @DisplayName("upload fica na memória: o limiar de disco do multipart passa do maior arquivo aceito")
    void semDisco() {
        assertThat(multipart.getFileSizeThreshold()).isGreaterThan((int) multipart.getMaxFileSize());
    }

    @Test
    @DisplayName("GET da URL de documento e de foto (gravam auditoria ou assinam URL) têm limite por usuário")
    void urlsComLimite() {
        Method[] urls = Arrays.stream(CadastroProfissionalController.class.getDeclaredMethods())
                .filter(metodo -> metodo.isAnnotationPresent(GetMapping.class))
                .filter(metodo -> Arrays.stream(
                                metodo.getAnnotation(GetMapping.class).value())
                        .anyMatch(caminho -> caminho.endsWith("/url")))
                .toArray(Method[]::new);

        assertThat(urls).hasSize(2);
        assertThat(urls)
                .allSatisfy(metodo -> assertThat(metodo.isAnnotationPresent(LimitadoPorUsuario.class))
                        .as(metodo.getName())
                        .isTrue());
    }
}
