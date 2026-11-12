package br.com.coe.servicos.profissional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface FotoPortfolioRepository extends JpaRepository<FotoPortfolio, UUID> {

    List<FotoPortfolio> findByProfissionalIdOrderByOrdemAsc(UUID profissionalId);

    /** Já filtra pelo dono: foto de outro profissional = vazio = 404 (anti-IDOR). */
    Optional<FotoPortfolio> findByIdAndProfissionalId(UUID id, UUID profissionalId);

    long countByProfissionalId(UUID profissionalId);

    /** Próxima posição: maior + 1 (a contagem repetiria a ordem depois de apagar uma foto do meio). */
    @Query("SELECT COALESCE(MAX(f.ordem) + 1, 0) FROM FotoPortfolio f WHERE f.profissionalId = :profissional")
    int proximaOrdem(@Param("profissional") UUID profissional);

    /** Foto nova de perfil aprovado: oculta e pendente até o admin aprovar (RN15a). */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(
            value = "UPDATE foto_portfolio SET visivel = false, pendente = true WHERE id = :id",
            nativeQuery = true)
    void marcarPendente(@org.springframework.data.repository.query.Param("id") UUID id);
}
