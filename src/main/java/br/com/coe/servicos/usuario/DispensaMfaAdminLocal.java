package br.com.coe.servicos.usuario;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Até o CORE-04 (SMS), deixa o ADMIN do seed entrar sem o segundo passo. Trava dupla: só existe no
 * perfil {@code local} E com {@code coe.seguranca.dispensar-mfa-admin=true} (só no
 * application-local.yml). Em test e prod não existe (com teste).
 */
@Component
@Profile("local")
@ConditionalOnBooleanProperty("coe.seguranca.dispensar-mfa-admin")
public class DispensaMfaAdminLocal {

    private static final Logger LOG = LoggerFactory.getLogger(DispensaMfaAdminLocal.class);

    DispensaMfaAdminLocal() {
        LOG.warn("MFA do ADMIN DISPENSADO: só no perfil local, até o CORE-04 (SMS). Nunca use em produção.");
    }
}
