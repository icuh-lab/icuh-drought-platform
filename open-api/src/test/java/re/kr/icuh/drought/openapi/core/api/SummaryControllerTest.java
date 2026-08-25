package re.kr.icuh.drought.openapi.core.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import re.kr.icuh.drought.application.openapi.summary.response.SummaryResponse;
import re.kr.icuh.drought.application.openapi.summary.service.SummaryService;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SummaryController.class)
class SummaryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SummaryService summaryService;

    @Test
    @DisplayName("종합 현황은 프론트 명세의 SUCCESS 래퍼와 data 구조로 응답한다")
    void returnsSummaryContract() throws Exception {
        when(summaryService.getSummary()).thenReturn(SummaryResponse.empty());

        mockMvc.perform(get("/v1/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.generatedAt").isString())
                .andExpect(jsonPath("$.data.alerts").isArray())
                .andExpect(jsonPath("$.data.kpis").isArray());
    }
}
