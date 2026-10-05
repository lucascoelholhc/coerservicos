package br.com.coe.servicos.usuario;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    boolean existsByCelular(String celular);

    boolean existsByEmail(String email);

    Optional<Usuario> findByCelular(String celular);

    Optional<Usuario> findByEmail(String email);

    /** Só regrava se o hash ainda for o lido no login (uma troca de senha no meio não é desfeita). */
    @Modifying
    @Query(
            value = "UPDATE usuario SET senha_hash = :hashNovo WHERE id = :id AND senha_hash = :hashAntigo",
            nativeQuery = true)
    int atualizarSenhaHash(UUID id, String hashAntigo, String hashNovo);

    @Modifying
    @Query(value = "UPDATE usuario SET ultimo_login_em = :agora WHERE id = :id", nativeQuery = true)
    int registrarLogin(UUID id, Instant agora);
}
