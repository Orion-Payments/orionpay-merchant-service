package orionpay.merchant.infrastructure.adapters.output.persistence.reposittory;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import orionpay.merchant.config.JpaConfig;
import orionpay.merchant.domain.model.enums.UserRole;
import orionpay.merchant.infrastructure.adapters.output.persistence.entity.AuthUserEntity;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(JpaConfig.class)
@org.springframework.test.context.TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true"
})
class JpaAuthUserRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("orionpay_test")
            .withUsername("test")
            .withPassword("test");

    @org.springframework.test.context.DynamicPropertySource
    static void configureProperties(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private JpaAuthUserRepository repository;

    @Test
    void shouldFindUserByEmailIgnoreCase() {
        // Given
        AuthUserEntity user = new AuthUserEntity();
        user.setId(UUID.randomUUID());
        user.setMerchantId(UUID.randomUUID());
        user.setEmail("TEST@orionpay.com");
        user.setPasswordHash("hashedpassword");
        user.setRole(UserRole.ROLE_MERCHANT);
        user.setCreatedAt(LocalDateTime.now());
        user.setEnabled(true);
        repository.save(user);

        // When
        Optional<AuthUserEntity> found = repository.findByEmailIgnoreCase("test@orionpay.com");

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("TEST@orionpay.com");
    }

    @Test
    void shouldNotFindUserByUnknownEmail() {
        // When
        Optional<AuthUserEntity> found = repository.findByEmailIgnoreCase("unknown@test.com");

        // Then
        assertThat(found).isNotPresent();
    }
}
