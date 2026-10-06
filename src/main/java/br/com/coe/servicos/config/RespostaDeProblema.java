package br.com.coe.servicos.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import jakarta.servlet.http.HttpServletResponse;

import tools.jackson.databind.json.JsonMapper;

/** Erro escrito pelos filtros de segurança, no mesmo formato do TratadorDeErros (RFC 9457). */
final class RespostaDeProblema {

    private static final String PROBLEM_JSON = "application/problem+json";

    private RespostaDeProblema() {}

    static void escrever(
            HttpServletResponse resposta, JsonMapper json, int status, String codigo, String titulo, String detalhe)
            throws IOException {
        Map<String, Object> problema = new LinkedHashMap<>();
        problema.put("type", "urn:coe:erro:" + codigo);
        problema.put("title", titulo);
        problema.put("status", status);
        problema.put("detail", detalhe);
        resposta.setStatus(status);
        resposta.setContentType(PROBLEM_JSON);
        resposta.getOutputStream().write(json.writeValueAsString(problema).getBytes(StandardCharsets.UTF_8));
    }
}
