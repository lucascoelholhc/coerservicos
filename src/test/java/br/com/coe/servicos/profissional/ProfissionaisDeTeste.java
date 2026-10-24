package br.com.coe.servicos.profissional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import jakarta.validation.Validator;

import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.coe.servicos.compartilhado.pessoal.CpfsDeTeste;
import br.com.coe.servicos.config.ConfiguracaoNegocio;

/**
 * Profissional ativo de verdade para testes de outros domínios (chat, contrato): passa pelo mesmo caminho do
 * seeder local (cadastro + aprovação), com dados únicos (o banco não é limpo entre testes).
 */
public final class ProfissionaisDeTeste {

    private static final AtomicLong SEQUENCIA = new AtomicLong(63_000);

    /** O perfil (id público) e a conta (id do login) do profissional criado. */
    public record Criado(UUID profissional, UUID usuario, String nome) {}

    private ProfissionaisDeTeste() {}

    /** Jardineiro em Itajaí, R$ 200,00, de segunda a sexta, sem meia diária. */
    public static Criado ativo(ApplicationContext contexto) {
        return criar(contexto, "jardineiro", "Corte de grama", Set.of(1, 2, 3, 4, 5));
    }

    /** Diarista (sujeita à LC 150) em Itajaí, R$ 200,00, todos os dias da semana, sem meia diária. */
    public static Criado diarista(ApplicationContext contexto) {
        return criar(contexto, "diarista", "Limpeza completa", Set.of(0, 1, 2, 3, 4, 5, 6));
    }

    private static Criado criar(ApplicationContext contexto, String profissao, String servico, Set<Integer> dias) {
        long n = SEQUENCIA.incrementAndGet();
        UUID usuario = UUID.randomUUID();
        UUID profissional = UUID.randomUUID();
        String nome = (profissao.equals("diarista") ? "Diarista" : "Jardineiro") + " Teste" + n + " Silva" + n;
        ExemplosLocais.Exemplo exemplo = new ExemplosLocais.Exemplo(
                usuario,
                profissional,
                nome,
                String.valueOf(47_900_000_000L + n),
                "profissional" + n + "@teste.coe.local",
                CpfsDeTeste.unico().digitos(),
                LocalDate.of(1985, 3, 1),
                profissao,
                List.of(),
                List.of(new ExemplosLocais.Servico(profissao, servico)),
                5,
                "sim",
                false,
                "200.00",
                null,
                "7h às 16h",
                4208203,
                5,
                List.of(),
                dias,
                "Cuido de jardins com capricho e levo minhas ferramentas.",
                UUID.randomUUID().toString(),
                true,
                false,
                0x2E6B3A,
                List.of(new ExemplosLocais.Foto("Serviço pronto", servico)));
        new SeederLocal(
                        List.of(exemplo),
                        "local",
                        false,
                        contexto.getBean(CadastroProfissionalService.class),
                        contexto.getBean(ArquivosDoCadastroService.class),
                        contexto.getBean(ProfissionalRepository.class),
                        contexto.getBean(FotoPortfolioRepository.class),
                        contexto.getBean(ContaDoCadastro.class),
                        contexto.getBean(JdbcTemplate.class),
                        contexto.getBean(PasswordEncoder.class),
                        contexto.getBean(TransactionTemplate.class),
                        contexto.getBean(ConfiguracaoNegocio.class),
                        contexto.getBean(Validator.class),
                        contexto.getBean(Clock.class))
                .semear();
        return new Criado(profissional, usuario, nome);
    }
}
