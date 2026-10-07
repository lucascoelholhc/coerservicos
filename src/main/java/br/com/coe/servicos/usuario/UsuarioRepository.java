package br.com.coe.servicos.usuario;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
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

    /** Só o id (sem carregar a conta antes da trava). */
    @Query("SELECT u.id FROM Usuario u WHERE u.celular = :celular")
    Optional<UUID> buscarIdPeloCelular(String celular);

    @Query("SELECT u.id FROM Usuario u WHERE u.email = :email")
    Optional<UUID> buscarIdPeloEmail(String email);

    /** Trava as contas em ordem de id (duas transferências cruzadas não se travam uma à outra). */
    @Query(value = "SELECT id FROM usuario WHERE id IN (:ids) ORDER BY id FOR UPDATE", nativeQuery = true)
    List<UUID> travar(Collection<UUID> ids);

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
