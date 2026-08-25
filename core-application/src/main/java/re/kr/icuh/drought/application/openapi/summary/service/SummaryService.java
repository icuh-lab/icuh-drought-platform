package re.kr.icuh.drought.application.openapi.summary.service;

import org.springframework.stereotype.Service;
import re.kr.icuh.drought.application.openapi.summary.response.SummaryResponse;

@Service
public class SummaryService {

    public SummaryResponse getSummary() {
        return SummaryResponse.empty();
    }
}
