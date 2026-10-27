package br.com.coe.servicos.config;

import java.time.Duration;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Falha cedo: o relógio deslocado ({@code coe.relogio.deslocamento}) só existe no perfil local, nunca junto do prod.
 * Configurado em outro perfil, a aplicação não sobe (em vez de ignorar em silêncio).
 */
@Component
class ConferenciaDoRelogio implements SmartInitializingSingleton {

    private final Environment ambiente;

    ConferenciaDoRelogio(Environment ambiente) {
        this.ambiente = ambiente;
    }

    @Override
    public void afterSingletonsInstantiated() {
        String texto = ambiente.getProperty("coe.relogio.deslocamento", "");
        if (texto.isBlank() || Duration.parse(texto.strip()).isZero()) {
            return;
        }
        boolean soLocal =
                ambiente.acceptsProfiles(Profiles.of("local")) && !ambiente.acceptsProfiles(Profiles.of("prod"));
        if (!soLocal) {
            throw new IllegalStateException(
                    "coe.relogio.deslocamento só pode ser usado no perfil local (sem o prod): tire a variável.");
        }
    }
}
