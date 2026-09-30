package orionpay.merchant.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(basePackages = "orionpay.merchant.infrastructure.adapters.output.persistence")
@EntityScan(basePackages = "orionpay.merchant.infrastructure.adapters.output.persistence.entity")
public class JpaConfig {
}
