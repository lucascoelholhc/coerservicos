package br.com.coe.servicos;

import java.time.Duration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /** A mesma do docker-compose ({@code ArmazenamentoS3Test} confere): nunca "latest". */
    public static final String IMAGEM_SEAWEEDFS = "chrislusf/seaweedfs:4.48";

    private static final int PORTA_S3 = 8333;
    // Chaves SÓ do S3 de teste (contêiner descartável, não são segredo).
    private static final String CHAVE_DE_ACESSO = "chave-de-teste";
    private static final String CHAVE_SECRETA = "segredo-de-teste-1234";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));
    }

    /** S3 local (CORE-09), subido pelo mesmo script do docker-compose, que gera as identidades. */
    @Bean
    GenericContainer<?> seaweedfsContainer() {
        return new GenericContainer<>(DockerImageName.parse(IMAGEM_SEAWEEDFS))
                .withCopyFileToContainer(MountableFile.forHostPath("docker/seaweedfs/iniciar.sh"), "/iniciar.sh")
                .withCreateContainerCmdModifier(comando -> comando.withEntrypoint("sh", "/iniciar.sh"))
                .withEnv("S3_ACCESS_KEY", CHAVE_DE_ACESSO)
                .withEnv("S3_SECRET_KEY", CHAVE_SECRETA)
                .withExposedPorts(PORTA_S3)
                .waitingFor(Wait.forHttp("/healthz").forPort(PORTA_S3).withStartupTimeout(Duration.ofMinutes(1)));
    }

    @Bean
    DynamicPropertyRegistrar armazenamentoDeTeste(GenericContainer<?> seaweedfsContainer) {
        return propriedades -> {
            propriedades.add(
                    "coe.armazenamento.endpoint",
                    () -> "http://" + seaweedfsContainer.getHost() + ":" + seaweedfsContainer.getMappedPort(PORTA_S3));
            propriedades.add("coe.armazenamento.chave-de-acesso", () -> CHAVE_DE_ACESSO);
            propriedades.add("coe.armazenamento.chave-secreta", () -> CHAVE_SECRETA);
        };
    }
}
