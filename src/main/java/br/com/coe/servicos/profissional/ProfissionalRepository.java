package br.com.coe.servicos.profissional;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ProfissionalRepository extends JpaRepository<Profissional, UUID> {

    /** O dono vem só do token (anti-IDOR): um perfil por usuário. */
    Optional<Profissional> findByUsuarioId(UUID usuarioId);

    /** Outro cadastro já usa este CPF (pelo HMAC, sem decifrar nada)? */
    @Query(
            value = "SELECT EXISTS (SELECT 1 FROM profissional WHERE cpf_hash = :hash AND usuario_id <> :usuario)",
            nativeQuery = true)
    boolean cpfEmUsoPorOutro(@Param("hash") byte[] hash, @Param("usuario") UUID usuario);
}
