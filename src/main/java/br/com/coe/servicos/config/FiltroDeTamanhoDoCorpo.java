package br.com.coe.servicos.config;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.filter.OncePerRequestFilter;

import tools.jackson.databind.json.JsonMapper;

/**
 * Corpo de requisição em /api/** com no máximo {@code coe.limites.corpo-maximo} (CORE-07; uploads da
 * CORE-09 terão o deles). Content-Length acima = 413 sem ler nada. Sem Content-Length (chunked): lê até
 * o limite + 1 byte; passou, 413; senão segue com o corpo já lido (os corpos da API são JSON pequenos).
 */
final class FiltroDeTamanhoDoCorpo extends OncePerRequestFilter {

    private static final String PREFIXO_DA_API = "/api/";

    private final long maximo;
    private final JsonMapper json;

    FiltroDeTamanhoDoCorpo(long maximo, JsonMapper json) {
        this.maximo = maximo;
        this.json = json;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest requisicao) {
        String caminho = CaminhoDaRequisicao.de(requisicao);
        String metodo = requisicao.getMethod();
        boolean temCorpo =
                "POST".equalsIgnoreCase(metodo) || "PUT".equalsIgnoreCase(metodo) || "PATCH".equalsIgnoreCase(metodo);
        return !temCorpo || !caminho.toLowerCase(java.util.Locale.ROOT).startsWith(PREFIXO_DA_API);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requisicao, HttpServletResponse resposta, FilterChain cadeia)
            throws ServletException, IOException {
        long declarado = requisicao.getContentLengthLong();
        if (declarado > maximo) {
            recusar(resposta);
            return;
        }
        if (declarado >= 0) {
            cadeia.doFilter(requisicao, resposta);
            return;
        }
        byte[] corpo = requisicao.getInputStream().readNBytes((int) maximo + 1);
        if (corpo.length > maximo) {
            recusar(resposta);
            return;
        }
        cadeia.doFilter(new CorpoJaLido(requisicao, corpo), resposta);
    }

    private void recusar(HttpServletResponse resposta) throws IOException {
        RespostaDeProblema.escrever(
                resposta,
                json,
                HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                "conteudo-muito-grande",
                "Conteúdo muito grande",
                "O que foi enviado passa do tamanho permitido.");
    }

    /** Requisição com o corpo já lido em memória (até o limite). */
    private static final class CorpoJaLido extends HttpServletRequestWrapper {

        private final byte[] corpo;

        CorpoJaLido(HttpServletRequest requisicao, byte[] corpo) {
            super(requisicao);
            this.corpo = corpo;
        }

        @Override
        public int getContentLength() {
            return corpo.length;
        }

        @Override
        public long getContentLengthLong() {
            return corpo.length;
        }

        @Override
        public java.io.BufferedReader getReader() {
            java.nio.charset.Charset charset = getCharacterEncoding() == null
                    ? java.nio.charset.StandardCharsets.UTF_8
                    : java.nio.charset.Charset.forName(getCharacterEncoding());
            return new java.io.BufferedReader(new java.io.InputStreamReader(getInputStream(), charset));
        }

        @Override
        public ServletInputStream getInputStream() {
            InputStream bytes = new ByteArrayInputStream(corpo);
            return new ServletInputStream() {
                @Override
                public int read() throws IOException {
                    return bytes.read();
                }

                @Override
                public int read(byte[] destino, int inicio, int tamanho) throws IOException {
                    return bytes.read(destino, inicio, tamanho);
                }

                @Override
                public boolean isFinished() {
                    try {
                        return bytes.available() == 0;
                    } catch (IOException erro) {
                        return true;
                    }
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener ouvinte) {
                    throw new UnsupportedOperationException("Leitura assíncrona não usada na API");
                }
            };
        }
    }
}
