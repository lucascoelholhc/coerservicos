package br.com.coe.servicos.compartilhado.dinheiro;

import java.util.function.Function;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.exc.InvalidFormatException;
import tools.jackson.databind.exc.MismatchedInputException;

/**
 * Formato JSON de {@link Dinheiro} e {@link Percentual}: sempre texto ("280.00", "0.1000"), para o
 * valor nunca passar por número de ponto flutuante no caminho. Número no JSON é recusado, e texto
 * inválido (3 casas, negativo, acima do teto, não numérico) vira erro de leitura: a API responde 400.
 */
final class DinheiroJson {

    private DinheiroJson() {}

    static final class Serializador extends ValueSerializer<Dinheiro> {
        @Override
        public void serialize(Dinheiro dinheiro, JsonGenerator gerador, SerializationContext contexto) {
            gerador.writeString(dinheiro.toString());
        }
    }

    static final class Desserializador extends ValueDeserializer<Dinheiro> {
        @Override
        public Dinheiro deserialize(JsonParser leitor, DeserializationContext contexto) {
            return ler(leitor, Dinheiro.class, Dinheiro::de, "Valor em dinheiro vai como texto, ex.: \"280.00\"");
        }
    }

    static final class SerializadorPercentual extends ValueSerializer<Percentual> {
        @Override
        public void serialize(Percentual percentual, JsonGenerator gerador, SerializationContext contexto) {
            gerador.writeString(percentual.toString());
        }
    }

    static final class DesserializadorPercentual extends ValueDeserializer<Percentual> {
        @Override
        public Percentual deserialize(JsonParser leitor, DeserializationContext contexto) {
            return ler(leitor, Percentual.class, Percentual::de, "Percentual vai como texto, ex.: \"0.10\"");
        }
    }

    private static <T> T ler(JsonParser leitor, Class<T> tipo, Function<String, T> criar, String formato) {
        if (leitor.currentToken() != JsonToken.VALUE_STRING) {
            throw MismatchedInputException.from(leitor, tipo, formato);
        }
        String texto = leitor.getString();
        try {
            return criar.apply(texto);
        } catch (RuntimeException erro) {
            throw InvalidFormatException.from(leitor, formato, texto, tipo);
        }
    }
}
