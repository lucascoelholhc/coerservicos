package br.com.coe.servicos.profissional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface DocumentoVerificacaoRepository extends JpaRepository<DocumentoVerificacao, UUID> {

    List<DocumentoVerificacao> findByProfissionalId(UUID profissionalId);

    /** Já filtra pelo dono: documento de outro profissional = vazio = 404 (anti-IDOR). */
    Optional<DocumentoVerificacao> findByIdAndProfissionalId(UUID id, UUID profissionalId);

    Optional<DocumentoVerificacao> findByProfissionalIdAndTipo(UUID profissionalId, String tipo);
}
