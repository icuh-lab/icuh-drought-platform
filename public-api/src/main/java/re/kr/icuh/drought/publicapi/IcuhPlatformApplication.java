package re.kr.icuh.drought.publicapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan(basePackages = {
		"re.kr.icuh.drought.publicapi",
		"re.kr.icuh.drought.persistence.article"
})
@EnableJpaRepositories(basePackages = {
		"re.kr.icuh.drought.publicapi",
		"re.kr.icuh.drought.persistence.article"
})
public class IcuhPlatformApplication {

	public static void main(String[] args) {
		SpringApplication.run(IcuhPlatformApplication.class, args);
	}

}
