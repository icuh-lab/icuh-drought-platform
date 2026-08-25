package re.kr.icuh.drought.openapi.core.support.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EntityScan(basePackages = "re.kr.icuh.drought.persistence.openapi")
@EnableJpaRepositories(basePackages = "re.kr.icuh.drought.persistence.openapi")
public class PersistenceScanConfig {
}
