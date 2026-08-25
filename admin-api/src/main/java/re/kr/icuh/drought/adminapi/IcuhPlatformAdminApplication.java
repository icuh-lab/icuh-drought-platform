package re.kr.icuh.drought.adminapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan(basePackages = {
		"re.kr.icuh.drought.adminapi",
		"re.kr.icuh.drought.persistence.article"
})
@EnableJpaRepositories(basePackages = {
		"re.kr.icuh.drought.adminapi",
		"re.kr.icuh.drought.persistence.article"
})
public class IcuhPlatformAdminApplication {

	public static void main(String[] args) {
		SpringApplication.run(IcuhPlatformAdminApplication.class, args);
	}

}
