package re.kr.icuh.drought.openapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
        "re.kr.icuh.drought.openapi",
        "re.kr.icuh.drought.application.openapi"
})
public class IcuhPlatformApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(IcuhPlatformApiApplication.class, args);
    }

}
