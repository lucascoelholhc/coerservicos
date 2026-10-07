package br.com.coe.servicos.usuario;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface CodigoSmsRepository extends JpaRepository<CodigoSms, UUID> {

    /** Serializa os envios para o mesmo celular até o fim da transação (limites contados no banco). */
    @Query(value = "SELECT 1 FROM pg_advisory_xact_lock(hashtext(:chave))", nativeQuery = true)
    Integer travar(String chave);

    @Query(
            value = "SELECT count(*) FROM codigo_sms WHERE celular = :celular AND criado_em > :desde",
            nativeQuery = true)
    long contarEnviosDesde(String celular, Instant desde);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CodigoSms c WHERE c.celular = :celular AND c.finalidade = :finalidade"
            + " AND c.usadoEm IS NULL AND c.invalidadoEm IS NULL")
    Optional<CodigoSms> buscarAtivo(String celular, String finalidade);

    /** Erros (tentativas) da conta desde o instante; o código certo não soma. */
    @Query(
            "SELECT coalesce(sum(c.tentativas), 0) FROM CodigoSms c WHERE c.usuarioId = :usuarioId AND c.criadoEm > :desde")
    Number somarErrosDaConta(UUID usuarioId, Instant desde);

    /** Criação do código com erro mais recente (erra o instante do erro em até a validade, 5 min). */
    @Query("SELECT max(c.criadoEm) FROM CodigoSms c WHERE c.usuarioId = :usuarioId AND c.tentativas > 0"
            + " AND c.criadoEm > :desde")
    Optional<Instant> ultimoErroDaConta(UUID usuarioId, Instant desde);

    @Query("SELECT c.usuarioId FROM CodigoSms c WHERE c.desafioHash = :desafioHash")
    Optional<UUID> buscarDonoDoDesafio(byte[] desafioHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CodigoSms c WHERE c.desafioHash = :desafioHash")
    Optional<CodigoSms> buscarPorDesafio(byte[] desafioHash);
}
