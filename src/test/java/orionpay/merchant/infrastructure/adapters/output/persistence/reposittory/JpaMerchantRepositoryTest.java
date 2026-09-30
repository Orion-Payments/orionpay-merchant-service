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
import orionpay.merchant.infrastructure.adapters.output.persistence.entity.MerchantEntity;
import orionpay.merchant.infrastructure.adapters.output.persistence.entity.BankAccountEntity;
import orionpay.merchant.domain.model.enums.MerchantStatus;

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
class JpaMerchantRepositoryTest {

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
    private JpaMerchantRepository repository;

    @Test
    void shouldFindMerchantByDocument() {
        // Given
        MerchantEntity merchant = new MerchantEntity();
        merchant.setId(UUID.randomUUID());
        merchant.setDocument("12345678909");
        merchant.setLegalName("Test Company");
        merchant.setEmail("merchant@test.com");
        merchant.setStatus(MerchantStatus.ACTIVE);
        merchant.setCreatedAt(LocalDateTime.now());
        
        repository.save(merchant);

        // When
        Optional<MerchantEntity> found = repository.findByDocument("12345678909");

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getLegalName()).isEqualTo("Test Company");
    }

    @Test
    void shouldNotFindMerchantByUnknownDocument() {
        Optional<MerchantEntity> found = repository.findByDocument("00000000000");
        assertThat(found).isNotPresent();
    }
}
