package br.com.coe.servicos.config;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import br.com.coe.servicos.compartilhado.auditoria.RegistroDeAuditoria;
import br.com.coe.servicos.compartilhado.erro.RecursoNaoEncontradoException;
import br.com.coe.servicos.compartilhado.erro.RegraDeNegocioException;
import br.com.coe.servicos.compartilhado.seguranca.UsuarioAutenticado;

/**
 * Configuração de negócio pelo admin (DOM-11; RN31, RN52, RN59). Mudar = nova vigência (a tabela é só de inserção),
 * só com valor que a aplicação aceitaria na subida ({@link ConfiguracaoNegocio#validar}), uma mudança por vez
 * (trava), cache invalidado depois do commit e auditoria com o valor antes e depois. Contrato já fechado não muda:
 * comissão e taxa ficam copiadas nele.
 */
@Service
class AdminConfiguracaoService {

    private final NamedParameterJdbcTemplate jdbc;
    private final FonteConfiguracao fonte;
    private final ConfiguracaoNegocio configuracao;
    private final UsuarioAutenticado usuario;
    private final RegistroDeAuditoria auditoria;

    AdminConfiguracaoService(
            NamedParameterJdbcTemplate jdbc,
            FonteConfiguracao fonte,
            ConfiguracaoNegocio configuracao,
            UsuarioAutenticado usuario,
            RegistroDeAuditoria auditoria) {
        this.jdbc = jdbc;
        this.fonte = fonte;
        this.configuracao = configuracao;
        this.usuario = usuario;
        this.auditoria = auditoria;
    }

    record Parametro(String chave, String valor, String tipo, String descricao, Instant vigenteDesde) {}

    record Lista(List<Parametro> itens) {}

    record Mudanca(@NotBlank @Size(max = 60) String valor) {}

    @Transactional(readOnly = true)
    Lista listar() {
        return new Lista(jdbc.query(
                "SELECT chave, valor, tipo, descricao, vigente_desde FROM configuracao_vigente ORDER BY chave",
                new MapSqlParameterSource(),
                (linha, n) -> new Parametro(
                        linha.getString("chave"),
                        linha.getString("valor"),
                        linha.getString("tipo"),
                        linha.getString("descricao"),
                        linha.getObject("vigente_desde", OffsetDateTime.class).toInstant())));
    }

    @Transactional
    Parametro mudar(String chave, Mudanca pedido) {
        if (Arrays.stream(ChaveConfiguracao.values()).noneMatch(c -> c.name().equals(chave))) {
            throw new RecursoNaoEncontradoException("Parâmetro não encontrado.");
        }
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtext('configuracao'))", new MapSqlParameterSource());
        String valor = pedido.valor().strip();
        Map<String, String> vigentes = new HashMap<>(fonte.lerVigentes());
        String antes = vigentes.get(chave);
        if (valor.equals(antes)) {
            throw new RegraDeNegocioException("configuracao-igual", "Esse já é o valor em vigor.");
        }
        vigentes.put(chave, valor);
        try {
            ConfiguracaoNegocio.validar(vigentes);
        } catch (IllegalStateException invalido) {
            throw new RegraDeNegocioException(
                    "configuracao-invalida", "Valor não aceito para " + chave + ". Confira o formato e o limite.");
        }
        UUID id = jdbc.queryForObject(
                """
                INSERT INTO configuracao (chave, valor, tipo, descricao, criado_por)
                SELECT chave, :valor, tipo, descricao, :admin FROM configuracao_vigente WHERE chave = :chave
                RETURNING id
                """,
                new MapSqlParameterSource("chave", chave)
                        .addValue("valor", valor)
                        .addValue("admin", usuario.id()),
                UUID.class);
        auditoria.registrar(
                usuario.id(),
                "ADMIN",
                "configuracao.alterar",
                "configuracao",
                id,
                Map.of("chave", chave, "valor", antes == null ? "" : antes),
                Map.of("chave", chave, "valor", valor),
                null);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                configuracao.invalidarCache();
            }
        });
        return listar().itens().stream()
                .filter(p -> p.chave().equals(chave))
                .findFirst()
                .map(p -> new Parametro(p.chave(), valor, p.tipo(), p.descricao(), p.vigenteDesde()))
                .orElseThrow();
    }
}
