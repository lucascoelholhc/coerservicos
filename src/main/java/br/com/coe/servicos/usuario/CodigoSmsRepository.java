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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CodigoSms c WHERE c.desafioHash = :desafioHash")
    Optional<CodigoSms> buscarPorDesafio(byte[] desafioHash);
}
