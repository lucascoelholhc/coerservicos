package br.com.coe.servicos.profissional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.UnaryOperator;
import javax.imageio.ImageIO;

import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import br.com.coe.servicos.compartilhado.pessoal.Cpf;
import br.com.coe.servicos.compartilhado.pessoal.CpfsDeTeste;

/** Monta um cadastro de profissional pela API (etapa a etapa), como o app faria. */
final class CadastroDeTeste {

    static final String BASE = "/api/profissional/cadastro";

    private final MockMvc mockMvc;
    private final String autorizacao;

    CadastroDeTeste(MockMvc mockMvc, String autorizacao) {
        this.mockMvc = mockMvc;
        this.autorizacao = autorizacao;
    }

    ResultActions etapa(String nome, String json) throws Exception {
        return mockMvc.perform(put(BASE + "/" + nome)
                .header("Authorization", autorizacao)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    ResultActions enviarArquivo(
            String rota,
            byte[] bytes,
            String nomeDoArquivo,
            String tipoDoArquivo,
            UnaryOperator<MockMultipartHttpServletRequestBuilder> parametros)
            throws Exception {
        MockMultipartHttpServletRequestBuilder pedido = multipart(BASE + "/" + rota)
                .file(new MockMultipartFile("arquivo", nomeDoArquivo, tipoDoArquivo, bytes));
        parametros.apply(pedido);
        return mockMvc.perform(pedido.header("Authorization", autorizacao).with(comTamanho(bytes.length + 512)));
    }

    ResultActions documento(String tipo) throws Exception {
        return enviarArquivo("documentos", jpeg(), "foto.jpg", "image/jpeg", p -> p.param("tipo", tipo));
    }

    ResultActions foto(String servico, String legenda) throws Exception {
        return enviarArquivo("fotos", jpeg(), "obra.jpg", "image/jpeg", p -> {
            if (servico != null) {
                p.param("servico", servico);
            }
            if (legenda != null) {
                p.param("legenda", legenda);
            }
            return p;
        });
    }

    /** Todas as etapas preenchidas e válidas (pedreiro em Blumenau), com um CPF novo. Devolve o CPF. */
    Cpf completo() throws Exception {
        Cpf cpf = CpfsDeTeste.unico();
        etapa("trabalho", """
                {"profissao":"pedreiro","outras":[]}""").andExpect(status().isOk());
        etapa("dados", "{\"cpf\":\"" + cpf.formatado() + "\",\"dataNascimento\":\"1990-05-20\"}")
                .andExpect(status().isOk());
        documento("rg_frente").andExpect(status().isCreated());
        documento("selfie").andExpect(status().isCreated());
        etapa("servicos", """
                {"servicos":[{"profissao":"pedreiro","nome":"Alvenaria"},{"profissao":"pedreiro","nome":"Reboco"}]}""").andExpect(status().isOk());
        etapa("experiencia", """
                {"anos":5,"ferramentas":"sim","mei":false}""").andExpect(status().isOk());
        etapa("valor", """
                {"valorDiaria":"280.00","valorMeiaDiaria":"150.00","horario":"7h às 17h"}""").andExpect(status().isOk());
        etapa("onde", """
                {"cidade":4202404,"raioKm":20,"outrasCidades":[4208203]}""").andExpect(status().isOk());
        etapa("dias", """
                {"dias":[1,2,3,4,5]}""").andExpect(status().isOk());
        foto("Alvenaria", "Muro de 12 m no Garcia").andExpect(status().isCreated());
        etapa("sobre", """
                {"frases":["Sou caprichoso e pontual."],"texto":"Trabalho com obra há 12 anos."}""").andExpect(status().isOk());
        etapa("pix", """
                {"tipo":"aleatoria","chave":"123e4567-e89b-12d3-a456-426614174000"}""").andExpect(status().isOk());
        return cpf;
    }

    /** MockMvc não põe Content-Length no multipart; o filtro de corpo exige nas rotas de upload. */
    static RequestPostProcessor comTamanho(int tamanho) {
        return requisicao -> {
            requisicao.setContent(new byte[tamanho]);
            return requisicao;
        };
    }

    static byte[] jpeg() {
        BufferedImage imagem = new BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = imagem.createGraphics();
        g.setColor(Color.ORANGE);
        g.fillRect(0, 0, 64, 48);
        g.dispose();
        try {
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            ImageIO.write(imagem, "jpg", saida);
            return saida.toByteArray();
        } catch (IOException erro) {
            throw new UncheckedIOException(erro);
        }
    }
}
