package br.com.coe.servicos.config;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;
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
 * Corpo de requisição em /api/** com no máximo {@code coe.limites.corpo-maximo} (CORE-07). Só as rotas de
 * upload ({@link RotasDeUpload}), e só com {@code multipart/form-data}, usam o limite do upload
 * ({@code coe.limites.corpo-maximo-upload}, CORE-09); multipart em qualquer outra rota fica nos 64 KB.
 * Content-Length acima = 413 sem ler nada. Sem Content-Length (chunked): lê até o limite + 1 byte;
 * passou, 413; senão segue com o corpo já lido.
 */
final class FiltroDeTamanhoDoCorpo extends OncePerRequestFilter {

    private static final String PREFIXO_DA_API = "/api/";

    private final long maximo;
    private final long maximoDeUpload;
    private final Set<String> rotasDeUpload;
    private final JsonMapper json;

    FiltroDeTamanhoDoCorpo(long maximo, long maximoDeUpload, Set<String> rotasDeUpload, JsonMapper json) {
        this.maximo = maximo;
        this.maximoDeUpload = maximoDeUpload;
        this.rotasDeUpload = Set.copyOf(rotasDeUpload);
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
        boolean upload = ehUpload(requisicao);
        long limite = upload ? maximoDeUpload : maximo;
        long declarado = requisicao.getContentLengthLong();
        if (upload && declarado < 0) {
            // o navegador sempre manda Content-Length no multipart; chunked aqui seria ler até 11 MB antes do login
            RespostaDeProblema.escrever(
                    resposta,
                    json,
                    HttpServletResponse.SC_LENGTH_REQUIRED,
                    "tamanho-nao-informado",
                    "Tamanho não informado",
                    "Envie a foto de novo.");
            return;
        }
        if (declarado > limite) {
            recusar(resposta, upload);
            return;
        }
        if (declarado >= 0) {
            cadeia.doFilter(requisicao, resposta);
            return;
        }
        byte[] corpo = requisicao.getInputStream().readNBytes((int) limite + 1);
        if (corpo.length > limite) {
            recusar(resposta, upload);
            return;
        }
        cadeia.doFilter(new CorpoJaLido(requisicao, corpo), resposta);
    }

    private boolean ehUpload(HttpServletRequest requisicao) {
        String tipo = requisicao.getContentType();
        return tipo != null
                && tipo.toLowerCase(Locale.ROOT).startsWith("multipart/form-data")
                && rotasDeUpload.contains(CaminhoDaRequisicao.de(requisicao));
    }

    private void recusar(HttpServletResponse resposta, boolean upload) throws IOException {
        if (upload) {
            RespostaDeProblema.escrever(
                    resposta,
                    json,
                    HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                    "arquivo-grande-demais",
                    "Arquivo não aceito",
                    "A foto é grande demais.");
            return;
        }
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
