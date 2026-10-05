package br.com.coe.servicos.usuario;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /** Trava a linha (FOR UPDATE): duas renovações do mesmo token nunca passam juntas. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RefreshToken r where r.tokenHash = :hash")
    Optional<RefreshToken> buscarParaRenovar(byte[] hash);

    Optional<RefreshToken> findByTokenHash(byte[] tokenHash);

    @Modifying
    @Query("update RefreshToken r set r.revogadoEm = :agora, r.motivoRevogacao = :motivo"
            + " where r.familiaId = :familia and r.revogadoEm is null")
    int revogarFamilia(UUID familia, Instant agora, String motivo);

    @Modifying
    @Query("update RefreshToken r set r.revogadoEm = :agora, r.motivoRevogacao = :motivo"
            + " where r.usuarioId = :usuario and r.revogadoEm is null")
    int revogarDoUsuario(UUID usuario, Instant agora, String motivo);
}
