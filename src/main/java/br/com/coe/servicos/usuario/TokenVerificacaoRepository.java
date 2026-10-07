package br.com.coe.servicos.usuario;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface TokenVerificacaoRepository extends JpaRepository<TokenVerificacao, UUID> {

    /** Serializa os envios para o mesmo destino até o fim da transação (limites contados no banco). */
    @Query(value = "SELECT 1 FROM pg_advisory_xact_lock(hashtext(:chave))", nativeQuery = true)
    Integer travar(String chave);

    /** E-mails enviados ao endereço desde o instante (comprovante não é enviado, não conta). */
    @Query("SELECT count(t) FROM TokenVerificacao t WHERE t.destino = :destino AND t.canal = 'email'"
            + " AND t.finalidade <> 'comprovante_posse' AND t.criadoEm > :desde")
    long contarEmailsDesde(String destino, Instant desde);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TokenVerificacao t WHERE t.destino = :destino AND t.finalidade = :finalidade"
            + " AND t.usadoEm IS NULL AND t.invalidadoEm IS NULL")
    Optional<TokenVerificacao> buscarAtivo(String destino, String finalidade);

    /** RN61: o e-mail mudou de dono; nenhum link pendente dele continua valendo. */
    @Modifying
    @Query("UPDATE TokenVerificacao t SET t.invalidadoEm = :agora WHERE t.destino = :destino"
            + " AND t.usadoEm IS NULL AND t.invalidadoEm IS NULL")
    int invalidarAtivosDoDestino(String destino, Instant agora);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TokenVerificacao t WHERE t.tokenHash = :tokenHash")
    Optional<TokenVerificacao> buscarPorHash(byte[] tokenHash);
}
