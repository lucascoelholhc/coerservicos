package br.com.coe.servicos.compartilhado.log;

import java.util.Set;

import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;

/**
 * Log em JSON do prod (ECS, {@code logging.structured.json.customizer}): a mesma máscara do texto em
 * {@code message} e {@code error.message}, e só nas mensagens do {@code error.stack_trace} (CORE-11).
 */
public class MascaraNoJsonDoLog implements StructuredLoggingJsonMembersCustomizer<Object> {

    private static final Set<String> MENSAGENS = Set.of("message", "error.message");
    private static final String PILHA = "error.stack_trace";

    @Override
    public void customize(JsonWriter.Members<Object> membros) {
        membros.applyingValueProcessor(JsonWriter.ValueProcessor.of(String.class, MascaraDeLog::mascarar)
                .whenHasPath(caminho -> MENSAGENS.contains(caminho.toUnescapedString())));
        membros.applyingValueProcessor(JsonWriter.ValueProcessor.of(String.class, MascaraDeLog::mascararPilha)
                .whenHasPath(caminho -> PILHA.equals(caminho.toUnescapedString())));
    }
}
