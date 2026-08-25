package re.kr.icuh.drought.publicapi.health.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/health")
    @ResponseStatus(value = HttpStatus.OK)
    public String health() {
        return "OK";
    }
}
