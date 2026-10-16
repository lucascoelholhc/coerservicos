package br.com.coe.servicos.profissional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface FotoPortfolioRepository extends JpaRepository<FotoPortfolio, UUID> {

    List<FotoPortfolio> findByProfissionalIdOrderByOrdemAsc(UUID profissionalId);

    /** Já filtra pelo dono: foto de outro profissional = vazio = 404 (anti-IDOR). */
    Optional<FotoPortfolio> findByIdAndProfissionalId(UUID id, UUID profissionalId);

    long countByProfissionalId(UUID profissionalId);
}
