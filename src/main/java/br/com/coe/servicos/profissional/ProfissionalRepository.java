package br.com.coe.servicos.profissional;

import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

interface ProfissionalRepository extends JpaRepository<Profissional, UUID> {

    /** O dono vem só do token (anti-IDOR): um perfil por usuário. */
    Optional<Profissional> findByUsuarioId(UUID usuarioId);

    /**
     * Cria o rascunho se ainda não existir; duas primeiras etapas ao mesmo tempo (duplo clique) não brigam pelo
     * índice único de {@code usuario_id}: a segunda espera a primeira e não faz nada.
     */
    @Modifying
    @Query(
            value =
                    "INSERT INTO profissional (id, usuario_id) VALUES (:id, :usuario) ON CONFLICT (usuario_id) DO NOTHING",
            nativeQuery = true)
    void criarRascunhoSeNaoExiste(@Param("id") UUID id, @Param("usuario") UUID usuario);

    /**
     * O cadastro de quem chama, travado até o fim da transação: etapas, uploads, exclusões e o envio do mesmo
     * cadastro passam um de cada vez (limite de fotos, um documento por tipo, envio com o que foi conferido).
     * Espera no máximo 5 s pela trava (senão 409 {@code edicao-concorrente}): uma transação presa não segura as
     * outras requisições da pessoa até esgotar as conexões.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("SELECT p FROM Profissional p WHERE p.usuarioId = :usuario")
    Optional<Profissional> travarPorUsuario(@Param("usuario") UUID usuario);

    /** Outro cadastro já usa este CPF (pelo HMAC, sem decifrar nada)? */
    @Query(
            value = "SELECT EXISTS (SELECT 1 FROM profissional WHERE cpf_hash = :hash AND usuario_id <> :usuario)",
            nativeQuery = true)
    boolean cpfEmUsoPorOutro(@Param("hash") byte[] hash, @Param("usuario") UUID usuario);
}
