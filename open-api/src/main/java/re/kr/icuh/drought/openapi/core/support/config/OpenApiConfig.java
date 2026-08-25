package re.kr.icuh.drought.openapi.core.support.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI icuhOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("ICUH Platform API")
                .description("농산물(agrimarket)/신선식품(freshfood)/수력(hydropower)/산불(wildfire) 예측 플랫폼 API")
                .version("v1"));
    }
}
