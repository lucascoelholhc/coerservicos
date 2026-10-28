package br.com.coe.servicos.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import br.com.coe.servicos.usuario.Papel;

/**
 * Profissionais no painel do admin (DOM-11; RN44d, RN44e, RF52): faltas com alerta ({@code FALTAS_ALERTA} faltas em
 * {@code FALTAS_JANELA_DIAS} dias), inativar e reativar só pela mão do admin, com motivo e auditoria.
 */
class AdminProfissionaisTest extends ExecucaoDeDiariaTeste {

    private UUID admin;

    @Override
    protected int quantasDiarias() {
        return 3;
    }

    @BeforeEach
    void prepararAdmin() throws Exception {
        pagar();
        admin = contas.criarConfirmada(Papel.ADMIN).id();
    }

    private String doAdmin() {
        return bearer(admin, Papel.ADMIN);
    }

    /** Uma falta confirmada na diária (disputa decidida a favor do cliente), registrada no instante dado. */
    private void falta(UUID diaria, Instant quando) {
        UUID disputa = jdbc.queryForObject(
                """
                INSERT INTO disputa (diaria_id, aberta_por, motivo, descricao, prazo_decisao_em, status,
                                     decidida_por, decidida_em, justificativa)
                VALUES (?, ?, 'nao_compareceu', 'Não apareceu no dia.', ?, 'favor_cliente', ?, ?, 'Falta confirmada.')
                RETURNING id""",
                UUID.class,
                diaria,
                cliente,
                Timestamp.from(quando.plus(Duration.ofHours(48))),
                admin,
                Timestamp.from(quando));
        jdbc.update("""
                INSERT INTO ocorrencia_profissional (profissional_id, tipo, diaria_id, disputa_id, registrada_por, criado_em)
                VALUES (?, 'falta', ?, ?, ?, ?)""", profissional.profissional(), diaria, disputa, admin, Timestamp.from(quando));
    }

    private ResultActions detalhe() throws Exception {
        return mockMvc.perform(
                get("/api/admin/profissionais/" + profissional.profissional()).header("Authorization", doAdmin()));
    }

    private ResultActions suspender(String autorizacao, String motivo) throws Exception {
        return mockMvc.perform(post("/api/admin/profissionais/" + profissional.profissional() + "/suspender")
                .header("Authorization", autorizacao)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"" + motivo + "\"}"));
    }

    private ResultActions reativar() throws Exception {
        return mockMvc.perform(post("/api/admin/profissionais/" + profissional.profissional() + "/reativar")
                .header("Authorization", doAdmin()));
    }

    private String statusDoPerfil() {
        return jdbc.queryForObject(
                "SELECT status FROM profissional WHERE id = ?", String.class, profissional.profissional());
    }

    @Test
    @DisplayName("alerta com 2 faltas na janela de 90 dias; sem alerta com uma falta a menos ou fora da janela (RN44d)")
    void alertaDeFaltas() throws Exception {
        Instant agora = relogio.instant();
        falta(diarias.get(0), agora.minus(Duration.ofDays(100)));
        falta(diarias.get(1), agora.minus(Duration.ofDays(10)));
        detalhe()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.faltasNaJanela").value(1))
                .andExpect(jsonPath("$.alertaFaltas").value(false))
                .andExpect(jsonPath("$.faltas.length()").value(2));

        falta(diarias.get(2), agora.minus(Duration.ofDays(1)));
        detalhe()
                .andExpect(jsonPath("$.faltasNaJanela").value(2))
                .andExpect(jsonPath("$.alertaFaltas").value(true));
        mockMvc.perform(get("/api/admin/profissionais")
                        .param("alerta", "true")
                        .param("busca", profissional.nome())
                        .header("Authorization", doAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[?(@.id == '%s')].alertaFaltas".formatted(profissional.profissional()))
                        .value(true));
        // nenhuma quantidade de faltas muda o status sozinha (RN44e)
        assertThat(statusDoPerfil()).isEqualTo("ativo");
    }

    @Test
    @DisplayName("suspender exige motivo, tira da busca e audita; reativar volta a ativo, também auditado (RN44e)")
    void suspenderEReativar() throws Exception {
        suspender(doAdmin(), "").andExpect(status().isBadRequest());
        suspender(doAdmin(), "Três faltas seguidas sem aviso ao cliente.")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("suspenso"))
                .andExpect(jsonPath("$.motivoSuspensao").value("Três faltas seguidas sem aviso ao cliente."));
        assertThat(statusDoPerfil()).isEqualTo("suspenso");
        mockMvc.perform(get("/api/publico/profissionais/" + profissional.profissional()))
                .andExpect(status().isNotFound());
        suspender(doAdmin(), "De novo, por engano.")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:coe:erro:status-invalido"));

        reativar().andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ativo"));
        assertThat(statusDoPerfil()).isEqualTo("ativo");
        reativar().andExpect(status().isConflict());
        mockMvc.perform(get("/api/publico/profissionais/" + profissional.profissional()))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForList(
                        "SELECT acao FROM log_auditoria WHERE entidade_id = ? AND ator_id = ? ORDER BY id",
                        String.class,
                        profissional.profissional(),
                        admin))
                .containsExactly("profissional.suspender", "profissional.reativar");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM log_auditoria WHERE entidade_id = ? AND depois::text LIKE '%faltas seguidas%'",
                        Long.class, profissional.profissional()))
                .isZero();
    }

    @Test
    @DisplayName("só ADMIN: CLIENTE e PROFISSIONAL 403, sem token 401; profissional inexistente 404")
    void soAdmin() throws Exception {
        suspender(doCliente(), "Motivo qualquer de teste.").andExpect(status().isForbidden());
        suspender(doProfissional(), "Motivo qualquer de teste.").andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/profissionais")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/profissionais/" + UUID.randomUUID()).header("Authorization", doAdmin()))
                .andExpect(status().isNotFound());
        assertThat(statusDoPerfil()).isEqualTo("ativo");
    }
}
